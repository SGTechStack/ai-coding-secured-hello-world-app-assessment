package com.example.authapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authapp.domain.PasswordResetToken;
import com.example.authapp.domain.PasswordResetTokenRepository;
import com.example.authapp.domain.Role;
import com.example.authapp.domain.User;
import com.example.authapp.domain.UserRepository;
import com.example.authapp.service.EmailService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class AuthappApplicationTests {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailService emailService;

    @BeforeEach
    void clean() {
        tokens.deleteAll();
        users.deleteAll();
    }

    // ---------- helpers ----------

    private User createUser(String name, Role role) {
        return users.save(new User(name, name + "@example.com", encoder.encode(PASSWORD), role));
    }

    private static String uniqueIp() {
        return "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private static RequestPostProcessor fromIp(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String loginJson(String user, String pass) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
    }

    private MvcResult login(String user, String pass, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON).content(loginJson(user, pass))).andReturn();
    }

    private Cookie loginOk(String user) throws Exception {
        MvcResult result = login(user, PASSWORD, uniqueIp());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    // ---------- registration (Story 1) ----------

    @Test
    void registerCreatesUserWithHashedPassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"email\":\"New@Example.com\",\"password\":\"a-long-enough-pass\"}"))
                .andExpect(status().isCreated());
        User u = users.findByUsernameIgnoreCase("newbie").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.USER);
        assertThat(u.isEnabled()).isTrue();
        assertThat(u.getEmail()).isEqualTo("new@example.com");
        assertThat(u.getPasswordHash()).startsWith("$2").isNotEqualTo("a-long-enough-pass");
    }

    @Test
    void registerRejectsDuplicatesAndWeakPasswords() throws Exception {
        createUser("taken", Role.USER);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"TAKEN\",\"email\":\"x@example.com\",\"password\":\"a-long-enough-pass\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"other\",\"email\":\"taken@example.com\",\"password\":\"a-long-enough-pass\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"weak\",\"email\":\"weak@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(1);
    }

    // ---------- login (Story 2) ----------

    @Test
    void loginSucceedsAndHelloGreetsUser() throws Exception {
        createUser("alice", Role.USER);
        Cookie session = loginOk("alice");
        mvc.perform(get("/api/hello").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, alice"));
    }

    @Test
    void helloWithoutSessionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/hello")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveIdenticalResponses() throws Exception {
        createUser("alice", Role.USER);
        MvcResult wrong = login("alice", "not-the-password", uniqueIp());
        MvcResult unknown = login("nobody", "not-the-password", uniqueIp());
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(users.findByUsernameIgnoreCase("alice").orElseThrow().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        createUser("alice", Role.USER);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        User u = createUser("alice", Role.USER);
        u.setEnabled(false);
        users.save(u);
        assertThat(login("alice", PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- lockout & IP throttling (Story 3) ----------

    @Test
    void repeatedFailuresLockAccountEvenForCorrectPassword() throws Exception {
        createUser("alice", Role.USER);
        for (int i = 0; i < 5; i++) {
            assertThat(login("alice", "wrong-password-" + i, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
        }
        User u = users.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(u.getLockedUntil()).isAfter(Instant.now());
        assertThat(login("alice", PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void loginAfterCooldownSucceedsAndResetsCounter() throws Exception {
        User u = createUser("alice", Role.USER);
        u.setFailedLoginAttempts(5);
        u.setLockedUntil(Instant.now().minusSeconds(1));
        users.save(u);
        loginOk("alice");
        User after = users.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(after.getFailedLoginAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottleEngagesIndependentlyOfAccountLockout() throws Exception {
        createUser("alice", Role.USER);
        String attackerIp = uniqueIp();
        for (int i = 0; i < 10; i++) { // 10 different usernames: no single account is locked
            login("ghost" + i, "whatever-password", attackerIp);
        }
        assertThat(users.findByUsernameIgnoreCase("alice").orElseThrow().getLockedUntil()).isNull();
        // Even correct credentials are refused from the throttled IP...
        assertThat(login("alice", PASSWORD, attackerIp).getResponse().getStatus()).isEqualTo(429);
        // ...without touching alice's counters, and other IPs are unaffected.
        assertThat(users.findByUsernameIgnoreCase("alice").orElseThrow().getFailedLoginAttempts()).isZero();
        assertThat(login("alice", PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(200);
    }

    // ---------- logout (Story 4) ----------

    @Test
    void sessionCookieIsRejectedAfterLogout() throws Exception {
        createUser("alice", Role.USER);
        Cookie session = loginOk("alice");
        mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
    }

    // ---------- password reset (Stories 6-7) ----------

    private String requestResetAndCaptureToken(String email) throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), link.capture());
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    private MvcResult confirmReset(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}")).andReturn();
    }

    @Test
    void resetRequestResponseIsGenericForUnknownEmail() throws Exception {
        createUser("alice", Role.USER);
        String known = mvc.perform(post("/api/auth/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);
        verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), anyString());
    }

    @Test
    void resetTokenIsHashedAtRestAndSingleUse() throws Exception {
        createUser("alice", Role.USER);
        String token = requestResetAndCaptureToken("alice@example.com");
        assertThat(tokens.findAll()).allSatisfy(t -> assertThat(t.getTokenHash()).isNotEqualTo(token));

        assertThat(confirmReset(token, "brand-new-password").getResponse().getStatus()).isEqualTo(200);
        assertThat(confirmReset(token, "another-new-password").getResponse().getStatus()).isEqualTo(400);
        assertThat(login("alice", "brand-new-password", uniqueIp()).getResponse().getStatus()).isEqualTo(200);
        assertThat(login("alice", PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        createUser("alice", Role.USER);
        String token = requestResetAndCaptureToken("alice@example.com");
        PasswordResetToken t = tokens.findAll().get(0);
        t.setExpiresAt(Instant.now().minusSeconds(1));
        tokens.save(t);
        assertThat(confirmReset(token, "brand-new-password").getResponse().getStatus()).isEqualTo(400);
        assertThat(login("alice", PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void resetInvalidatesExistingSessions() throws Exception {
        createUser("alice", Role.USER);
        Cookie session = loginOk("alice");
        mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isOk());
        String token = requestResetAndCaptureToken("alice@example.com");
        assertThat(confirmReset(token, "brand-new-password").getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
    }

    // ---------- admin (Stories 8-11) ----------

    @Test
    void nonAdminGetsForbiddenOnEveryAdminEndpoint() throws Exception {
        User other = createUser("bob", Role.USER);
        createUser("alice", Role.USER);
        Cookie session = loginOk("alice");
        mvc.perform(get("/api/admin/users").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + other.getId() + "/enabled").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + other.getId() + "/role").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + other.getId()).with(csrf()).cookie(session))
                .andExpect(status().isForbidden());
        assertThat(users.findById(other.getId())).isPresent();
    }

    @Test
    void anonymousGetsUnauthorizedOnAdminEndpoint() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminListsUsersWithoutPasswordHashes() throws Exception {
        createUser("root", Role.ADMIN);
        createUser("bob", Role.USER);
        Cookie session = loginOk("root");
        mvc.perform(get("/api/admin/users").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password_hash").doesNotExist());
    }

    @Test
    void adminCannotDisableDeleteOrDemoteSelf() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        Cookie session = loginOk("root");
        mvc.perform(patch("/api/admin/users/" + admin.getId() + "/enabled").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/" + admin.getId() + "/role").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/users/" + admin.getId()).with(csrf()).cookie(session))
                .andExpect(status().isBadRequest());
        User after = users.findById(admin.getId()).orElseThrow();
        assertThat(after.isEnabled()).isTrue();
        assertThat(after.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void adminCanDisableChangeRoleAndDeleteOthers() throws Exception {
        createUser("root", Role.ADMIN);
        User bob = createUser("bob", Role.USER);
        Cookie bobSession = loginOk("bob");
        Cookie session = loginOk("root");

        mvc.perform(patch("/api/admin/users/" + bob.getId() + "/enabled").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(login("bob", PASSWORD, uniqueIp()).getResponse().getStatus()).isEqualTo(401);
        mvc.perform(get("/api/hello").cookie(bobSession)).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/admin/users/" + bob.getId() + "/role").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(delete("/api/admin/users/" + bob.getId()).with(csrf()).cookie(session))
                .andExpect(status().isNoContent());
        assertThat(users.findById(bob.getId())).isEmpty();
    }
}
