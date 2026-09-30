package com.example.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.audit.AuditLogger;
import com.example.auth.security.AppUserDetails;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.support.ApiSession;
import com.example.auth.support.LogCapture;
import com.example.auth.support.TestUsers;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Seam 1 (backend HTTP seam): password reset request + confirm through the real filter chain.
 *
 * <p>Not {@code @Transactional}: the reset email is sent asynchronously <em>after commit</em> and
 * session termination also runs after commit, so a rolled-back test transaction would never see
 * either. Each test uses its own freshly created user instead.
 *
 * <p>{@link EmailService} is a {@code @MockitoBean} -- the one external-I/O boundary, and how the
 * raw, never-persisted token is recovered from the reset link. Reset after every test by default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PasswordResetControllerTest {

    private static final String REQUEST_URL = "/api/password-reset/request";
    private static final String CONFIRM_URL = "/api/password-reset/confirm";
    private static final String HELLO_URL = "/api/hello";
    private static final String ORIGINAL_PASSWORD = "OriginalPassword123!";
    private static final String NEW_PASSWORD = "BrandNewPassword123!";
    private static final long ASYNC_MS = 5000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiters rateLimiters;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @MockitoBean
    private EmailService emailService;

    private User user;

    @BeforeEach
    void setUp() {
        rateLimiters.resetAll();
        user = TestUsers.create(
                userRepository, passwordEncoder, TestUsers.unique("reset"), ORIGINAL_PASSWORD, Role.USER);
    }

    private ApiSession client() {
        return new ApiSession(mockMvc, objectMapper);
    }

    private ResultActions requestReset(String email) throws Exception {
        return requestReset(email, "127.0.0.1");
    }

    private ResultActions requestReset(String email, String ip) throws Exception {
        return client().from(ip).post(REQUEST_URL, Map.of("email", email));
    }

    private ResultActions confirmReset(String token, String newPassword) throws Exception {
        return confirmReset(token, newPassword, "127.0.0.1");
    }

    private ResultActions confirmReset(String token, String newPassword, String ip) throws Exception {
        java.util.HashMap<String, String> body = new java.util.HashMap<>();
        body.put("token", token);
        body.put("newPassword", newPassword);
        return client().from(ip).post(CONFIRM_URL, body);
    }

    /** Waits for the {@code n} async emails to {@code user} and returns the raw tokens, oldest first. */
    private List<String> capturedTokens(int n) {
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(ASYNC_MS).times(n)).sendPasswordResetEmail(eq(user.getEmail()), links.capture());
        return links.getAllValues().stream().map(l -> l.substring(l.indexOf("token=") + "token=".length())).toList();
    }

    private String requestResetAndCaptureToken() throws Exception {
        requestReset(user.getEmail()).andExpect(status().isOk());
        return capturedTokens(1).getFirst();
    }

    private long unusedTokens() {
        return tokenRepository.countByUserIdAndUsedAtIsNull(user.getId());
    }

    private User reload() {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    /** Same SHA-256 + Base64 scheme as {@code PasswordResetService#hash}, to plant token rows directly. */
    private static String hash(String rawToken) throws Exception {
        byte[] hashed = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hashed);
    }

    @Test
    void requestForRegisteredEmailCreatesATokenAndSendsTheLinkAfterCommit() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            requestReset(user.getEmail())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").doesNotExist());

            ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
            verify(emailService, timeout(ASYNC_MS)).sendPasswordResetEmail(eq(user.getEmail()), link.capture());
            assertThat(link.getValue()).startsWith("http://localhost:3000/reset-password/confirm?token=");
            String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
            assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");

            // Only the hash is stored.
            PasswordResetToken row = tokenRepository.findByTokenHash(hash(rawToken)).orElseThrow();
            assertThat(row.getTokenHash()).isNotEqualTo(rawToken);
            assertThat(row.getUsedAt()).isNull();
            assertThat(row.getExpiresAt()).isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(30 * 60));
            assertThat(unusedTokens()).isEqualTo(1);

            assertThat(logs.audit("Password reset requested").getFirst().mdc())
                    .containsEntry(AuditLogger.USER_ID, user.getPublicId().toString());
            assertThat(logs.events()).noneMatch(e -> e.everything().contains(rawToken));
            assertThat(logs.audit()).noneMatch(e -> e.everything().contains(user.getEmail()));
        }
    }

    @Test
    void emailIsMatchedCaseInsensitively() throws Exception {
        requestReset(user.getEmail().toUpperCase()).andExpect(status().isOk());

        verify(emailService, timeout(ASYNC_MS)).sendPasswordResetEmail(eq(user.getEmail()), any());
    }

    @Test
    void requestForUnregisteredEmailReturnsTheIdenticalResponseAndSendsNothing() throws Exception {
        String known = requestReset(user.getEmail()).andReturn().getResponse().getContentAsString();
        String unknown = requestReset("reset-no-such-account@example.com")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        verify(emailService, after(500).never()).sendPasswordResetEmail(eq("reset-no-such-account@example.com"), any());
    }

    @Test
    void invalidEmailIsA400() throws Exception {
        requestReset("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        client().postRaw(REQUEST_URL, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is required"));
    }

    @Test
    void emailTransportFailureDoesNotLeakIntoTheResponseOrTheLog() throws Exception {
        doThrow(new IllegalStateException("smtp down for " + user.getEmail()))
                .when(emailService).sendPasswordResetEmail(any(), any());

        try (LogCapture logs = LogCapture.start()) {
            requestReset(user.getEmail()).andExpect(status().isOk());
            verify(emailService, timeout(ASYNC_MS)).sendPasswordResetEmail(eq(user.getEmail()), any());
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(5))
                    .until(() -> logs.events().stream()
                            .anyMatch(e -> e.message().equals("Password reset email could not be sent")));
            assertThat(logs.events()).noneMatch(e -> e.everything().contains(user.getEmail()));
        }
    }

    @Test
    void confirmWithValidTokenUpdatesPasswordAndMarksTokenUsed() throws Exception {
        String token = requestResetAndCaptureToken();

        try (LogCapture logs = LogCapture.start()) {
            confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

            assertThat(logs.audit("Password reset completed").getFirst().mdc())
                    .containsEntry(AuditLogger.USER_ID, user.getPublicId().toString());
            assertThat(logs.events()).noneMatch(e -> e.everything().contains(token) || e.everything().contains(NEW_PASSWORD));
        }

        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload().getPassword())).isTrue();
        assertThat(tokenRepository.findByTokenHash(hash(token)).orElseThrow().getUsedAt()).isNotNull();

        client().login(user.getUsername(), ORIGINAL_PASSWORD).andExpect(status().isUnauthorized());
        client().login(user.getUsername(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmWithMissingOrOverlongTokenIsAValidationError() throws Exception {
        confirmReset(null, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        confirmReset("t".repeat(129), NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void confirmWithUnknownTokenIsInvalidToken() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            confirmReset("definitely-not-a-real-token", NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                    .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
            assertThat(logs.audit("Password reset rejected").getFirst().kv("event.reason")).isEqualTo("invalid_token");
        }
    }

    @Test
    void confirmWithWeakPasswordIsRejectedAndTheTokenStaysUsable() throws Exception {
        String token = requestResetAndCaptureToken();

        confirmReset(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        confirmReset(token, "x".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmWithExpiredTokenIsRejectedAndPasswordUnchanged() throws Exception {
        String rawToken = "expired-token-" + user.getUsername();
        tokenRepository.save(new PasswordResetToken(user, hash(rawToken), Instant.now().minusSeconds(60)));

        confirmReset(rawToken, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, reload().getPassword())).isTrue();
    }

    @Test
    void reusedTokenIsRejectedOnTheSecondConfirmAttempt() throws Exception {
        String token = requestResetAndCaptureToken();

        confirmReset(token, "FirstNewPassword123!").andExpect(status().isOk());
        confirmReset(token, "SecondNewPassword123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        assertThat(passwordEncoder.matches("FirstNewPassword123!", reload().getPassword())).isTrue();
    }

    @Test
    void aNewerRequestInvalidatesTheOlderUnusedToken() throws Exception {
        requestReset(user.getEmail()).andExpect(status().isOk());
        requestReset(user.getEmail()).andExpect(status().isOk());
        List<String> tokens = capturedTokens(2);
        assertThat(tokens.get(0)).isNotEqualTo(tokens.get(1));
        assertThat(unusedTokens()).isEqualTo(1);

        String older = tokenRepository.findByTokenHash(hash(tokens.get(0))).orElseThrow().getUsedAt() == null
                ? tokens.get(1)
                : tokens.get(0);
        String newer = older.equals(tokens.get(0)) ? tokens.get(1) : tokens.get(0);

        confirmReset(older, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        confirmReset(newer, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulConfirmInvalidatesEveryOtherOutstandingToken() throws Exception {
        String token = requestResetAndCaptureToken();
        // A second outstanding token planted directly (as if issued concurrently).
        String stray = "stray-token-" + user.getUsername();
        tokenRepository.save(new PasswordResetToken(user, hash(stray), Instant.now().plusSeconds(600)));
        assertThat(unusedTokens()).isEqualTo(2);

        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

        assertThat(unusedTokens()).isZero();
        confirmReset(stray, "AnotherNewPassword123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload().getPassword())).isTrue();
    }

    @Test
    void confirmClearsAnAccountLockout() throws Exception {
        User locked = reload();
        locked.setFailedLoginAttempts(5);
        locked.setLockedUntil(Instant.now().plusSeconds(1200));
        userRepository.save(locked);
        client().login(user.getUsername(), ORIGINAL_PASSWORD).andExpect(status().isUnauthorized());

        String token = requestResetAndCaptureToken();
        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

        User after = reload();
        assertThat(after.getFailedLoginAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
        client().login(user.getUsername(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resettingThePasswordEndsTheExistingSession() throws Exception {
        ApiSession session = client();
        session.login(user.getUsername(), ORIGINAL_PASSWORD).andExpect(status().isOk());
        session.get(HELLO_URL).andExpect(status().isOk());

        String token = requestResetAndCaptureToken();
        confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

        session.get(HELLO_URL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void resettingThePasswordEndsEverySessionNotJustOne() throws Exception {
        // One session per user via login, so plant a second live session straight into the store
        // (as another instance or a pre-limit session would have).
        ApiSession loggedIn = client();
        loggedIn.login(user.getUsername(), ORIGINAL_PASSWORD).andExpect(status().isOk());
        ApiSession planted = client().withSessionCookie(plantAuthenticatedSession(sessionRepository, user));
        loggedIn.get(HELLO_URL).andExpect(status().isOk());
        planted.get(HELLO_URL).andExpect(status().isOk());
        assertThat(sessionRepository.findByPrincipalName(user.getUsername())).hasSize(2);

        try (LogCapture logs = LogCapture.start()) {
            String token = requestResetAndCaptureToken();
            confirmReset(token, NEW_PASSWORD).andExpect(status().isOk());

            LogCapture.Event terminated = logs.audit("User sessions terminated").getFirst();
            assertThat(terminated.kv("user.target.id")).isEqualTo(user.getPublicId().toString());
            assertThat(terminated.kv("event.reason")).isEqualTo("password_reset");
            assertThat(terminated.kv("labels.session_count")).isEqualTo("2");
        }

        assertThat(sessionRepository.findByPrincipalName(user.getUsername())).isEmpty();
        loggedIn.get(HELLO_URL).andExpect(status().isUnauthorized());
        planted.get(HELLO_URL).andExpect(status().isUnauthorized());
    }

    @Test
    void perEmailLimitSilentlyDropsTheFourthRequest() throws Exception {
        for (int i = 1; i <= 4; i++) {
            requestReset(user.getEmail(), "10.30.0." + i).andExpect(status().isOk());
        }

        verify(emailService, timeout(ASYNC_MS).times(3)).sendPasswordResetEmail(eq(user.getEmail()), any());
        verify(emailService, after(500).times(3)).sendPasswordResetEmail(eq(user.getEmail()), any());
    }

    @Test
    void perIpRequestLimitAnswers429WithRetryAfter() throws Exception {
        String ip = "10.30.1.1";
        for (int i = 1; i <= 5; i++) {
            requestReset("reset-ip-limit-" + i + "@example.com", ip).andExpect(status().isOk());
        }

        requestReset(user.getEmail(), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        verify(emailService, after(500).never()).sendPasswordResetEmail(eq(user.getEmail()), any());
    }

    @Test
    void perIpConfirmLimitAnswers429WithRetryAfter() throws Exception {
        String ip = "10.30.2.1";
        for (int i = 1; i <= 10; i++) {
            confirmReset("guess-" + i, NEW_PASSWORD, ip).andExpect(status().isBadRequest());
        }

        String token = requestResetAndCaptureToken();
        String retryAfter = confirmReset(token, NEW_PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isBetween(890L, 900L);

        // The token was not consumed and still works from another source.
        confirmReset(token, NEW_PASSWORD, "10.30.2.2").andExpect(status().isOk());
    }

    /**
     * Writes an authenticated Spring Session straight into the JDBC store and returns the value the
     * browser would carry in its {@code SESSION} cookie (Base64 of the id).
     */
    private static <S extends Session> String plantAuthenticatedSession(
            FindByIndexNameSessionRepository<S> repository, User user) {
        AppUserDetails principal = new AppUserDetails(
                user.getPublicId(), user.getUsername(), user.getPassword(), true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, user.getUsername());
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities())));
        repository.save(session);
        return Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
    }
}
