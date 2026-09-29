package hello.desk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hello.desk.auth.EmailService;
import hello.desk.security.IpThrottle;
import hello.desk.security.TokenHasher;
import hello.desk.user.PasswordResetToken;
import hello.desk.user.PasswordResetTokenRepository;
import hello.desk.user.UserAccount;
import hello.desk.user.UserAccountRepository;
import hello.desk.web.ApiMessages;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.bcrypt-strength=4",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SecurityRequirementTests {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String OTHER_PASSWORD = "another-strong-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private IpThrottle ipThrottle;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void resetState() {
        ipThrottle.reset();
        reset(emailService);
    }

    @Test
    void loginSuccessWrongPasswordAndUnknownUserShareOneMessage() throws Exception {
        String username = unique("ada");
        register(username, PASSWORD);

        MvcResult success = login(username, PASSWORD, "203.0.113.10");
        Cookie session = sessionCookie(success);
        String setCookie = sessionHeader(success);
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).doesNotContain("Secure");

        mockMvc.perform(get("/api/hello").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, " + username));

        MvcResult wrong = loginRaw(username, "not-the-password", "203.0.113.11");
        MvcResult unknown = loginRaw("missing-" + username, PASSWORD, "203.0.113.12");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString())
                .contains(ApiMessages.INVALID_CREDENTIALS);

        UserAccount stored = users.findByUsername(username).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2a$");
        assertThat(stored.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(stored.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "ChangeMe-Admin1")))
                .andExpect(status().isForbidden());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void lockoutThenCooldownResetsTheCounter(CapturedOutput output) throws Exception {
        String username = unique("locked");
        register(username, PASSWORD);
        String ip = "203.0.113.20";

        for (int attempt = 0; attempt < 5; attempt++) {
            loginRaw(username, "wrong-password-1", ip);
        }

        UserAccount locked = users.findByUsername(username).orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
        assertThat(output).contains("event=lockout_triggered");
        assertThat(output).doesNotContain(PASSWORD);

        MvcResult rejected = loginRaw(username, PASSWORD, ip);
        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
        assertThat(rejected.getResponse().getContentAsString()).contains(ApiMessages.INVALID_CREDENTIALS);

        locked.setLockedUntil(Instant.now().minusSeconds(5));
        users.saveAndFlush(locked);

        login(username, PASSWORD, "203.0.113.21");
        UserAccount cleared = users.findByUsername(username).orElseThrow();
        assertThat(cleared.getFailedLoginAttempts()).isZero();
        assertThat(cleared.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottleDoesNotLockTheAccount() throws Exception {
        String username = unique("victim");
        register(username, PASSWORD);
        String noisyIp = "203.0.113.40";

        for (int attempt = 0; attempt < 20; attempt++) {
            MvcResult failure = loginRaw("ghost-" + attempt + "-" + username, "wrong-password-1", noisyIp);
            assertThat(failure.getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult blocked = loginRaw(username, PASSWORD, noisyIp);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getContentAsString()).contains(ApiMessages.TOO_MANY_ATTEMPTS);

        UserAccount victim = users.findByUsername(username).orElseThrow();
        assertThat(victim.getFailedLoginAttempts()).isZero();
        assertThat(victim.getLockedUntil()).isNull();

        login(username, PASSWORD, "203.0.113.41");
    }

    @Test
    void reusedSessionCookieIsRejectedAfterLogout() throws Exception {
        String username = unique("bye");
        register(username, PASSWORD);
        Cookie session = sessionCookie(login(username, PASSWORD, "203.0.113.50"));

        mockMvc.perform(secured(post("/api/auth/logout"), session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hello").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ApiMessages.UNAUTHORIZED));
    }

    @Test
    void passwordResetIsSingleUseExpiresAndDropsSessions() throws Exception {
        String username = unique("reset");
        String email = emailFor(username);
        register(username, PASSWORD);
        Cookie session = sessionCookie(login(username, PASSWORD, "203.0.113.60"));

        String unknownBody = resetRequest("nobody-" + email);
        String knownBody = resetRequest(email);
        assertThat(unknownBody).isEqualTo(knownBody).contains(ApiMessages.RESET_REQUEST);
        verify(emailService, never()).sendPasswordResetEmail(eq("nobody-" + email), anyString(), anyString());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), eq(username), link.capture());
        String rawToken = UriComponentsBuilder.fromUriString(link.getValue()).build().getQueryParams().getFirst("token");
        assertThat(rawToken).isNotBlank();
        assertThat(tokens.findByTokenHash(TokenHasher.sha256(rawToken))).isPresent();
        assertThat(tokens.findAll()).allSatisfy(token -> assertThat(token.getTokenHash()).isNotEqualTo(rawToken));

        mockMvc.perform(secured(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(rawToken, OTHER_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").cookie(session))
                .andExpect(status().isUnauthorized());

        assertThat(loginRaw(username, PASSWORD, "203.0.113.61").getResponse().getStatus()).isEqualTo(401);
        login(username, OTHER_PASSWORD, "203.0.113.62");

        mockMvc.perform(secured(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"third-password-ok"}
                                """.formatted(rawToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ApiMessages.RESET_INVALID));
        assertThat(passwordEncoder.matches(OTHER_PASSWORD, users.findByUsername(username).orElseThrow().getPasswordHash()))
                .isTrue();

        UserAccount user = users.findByUsername(username).orElseThrow();
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setTokenHash(TokenHasher.sha256("expired-token-value"));
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        tokens.saveAndFlush(expired);

        mockMvc.perform(secured(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"expired-token-value","password":"brand-new-password"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ApiMessages.RESET_INVALID));
        assertThat(passwordEncoder.matches(OTHER_PASSWORD, users.findByUsername(username).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void adminCannotChangeTheirOwnAccountAndUsersAreForbidden() throws Exception {
        String username = unique("member");
        register(username, PASSWORD);
        Cookie member = sessionCookie(login(username, PASSWORD, "203.0.113.70"));
        Cookie admin = sessionCookie(login("admin", "ChangeMe-Admin1", "203.0.113.71"));
        UserAccount adminAccount = users.findByUsername("admin").orElseThrow();
        UserAccount memberAccount = users.findByUsername(username).orElseThrow();

        mockMvc.perform(get("/api/admin/users").cookie(member))
                .andExpect(status().isForbidden());
        mockMvc.perform(secured(patch("/api/admin/users/" + memberAccount.getId() + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"), member))
                .andExpect(status().isForbidden());
        mockMvc.perform(secured(delete("/api/admin/users/" + memberAccount.getId()), member))
                .andExpect(status().isForbidden());

        MvcResult directory = mockMvc.perform(get("/api/admin/users").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andReturn();
        assertThat(directory.getResponse().getContentAsString()).doesNotContain(adminAccount.getPasswordHash());

        mockMvc.perform(secured(patch("/api/admin/users/" + adminAccount.getId() + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ApiMessages.SELF_ACTION));
        mockMvc.perform(secured(patch("/api/admin/users/" + adminAccount.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"), admin))
                .andExpect(status().isConflict());
        mockMvc.perform(secured(delete("/api/admin/users/" + adminAccount.getId()), admin))
                .andExpect(status().isConflict());

        assertThat(users.findByUsername("admin").orElseThrow().isEnabled()).isTrue();
        assertThat(users.findByUsername("admin").orElseThrow().getRole().name()).isEqualTo("ADMIN");

        mockMvc.perform(secured(patch("/api/admin/users/" + memberAccount.getId() + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        MvcResult disabledLogin = loginRaw(username, PASSWORD, "203.0.113.72");
        assertThat(disabledLogin.getResponse().getStatus()).isEqualTo(401);
        assertThat(disabledLogin.getResponse().getContentAsString()).contains(ApiMessages.INVALID_CREDENTIALS);
    }

    @Test
    void weakPasswordAndDuplicateRegistrationAreRejected() throws Exception {
        String username = unique("new");
        mockMvc.perform(secured(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, emailFor(username), "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ApiMessages.WEAK_PASSWORD));
        assertThat(users.findByUsername(username)).isEmpty();

        register(username, PASSWORD);
        mockMvc.perform(secured(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "other-" + emailFor(username), PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ApiMessages.USERNAME_TAKEN));
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(secured(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, emailFor(username), password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private MvcResult login(String username, String password, String ip) throws Exception {
        MvcResult result = loginRaw(username, password, ip);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result;
    }

    private MvcResult loginRaw(String username, String password, String ip) throws Exception {
        return mockMvc.perform(secured(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }))
                .andReturn();
    }

    private String resetRequest(String email) throws Exception {
        MvcResult result = mockMvc.perform(secured(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}")))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder secured(MockHttpServletRequestBuilder builder, Cookie... extra) {
        String token = UUID.randomUUID().toString();
        Cookie[] cookies = new Cookie[extra.length + 1];
        cookies[0] = new Cookie("XSRF-TOKEN", token);
        System.arraycopy(extra, 0, cookies, 1, extra.length);
        return builder.cookie(cookies).header("X-XSRF-TOKEN", token);
    }

    private Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("HELLOSESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    private String sessionHeader(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("HELLOSESSION="))
                .findFirst()
                .orElseThrow();
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String emailFor(String username) {
        return username + "@example.com";
    }

    private String registerJson(String username, String email, String password) {
        return """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);
    }

    private String loginJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }
}
