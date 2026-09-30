package com.example.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.audit.AuditLogger;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.support.ApiSession;
import com.example.auth.support.LogCapture;
import com.example.auth.support.TestUsers;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Seam 1 (backend HTTP seam): login, {@code /me} and logout through the real filter chain, real
 * Spring Session JDBC sessions and session-stored CSRF (see {@link ApiSession}), against the real
 * H2 database. Session rotation, CSRF rejection on login, single-session eviction, logout replay and
 * security headers live in {@code SessionAndCsrfFlowTest}; rate limits and lockout in {@code
 * LoginRateLimitAndLockoutTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerTest {

    private static final String USERNAME = "authctl-johndoe";
    private static final String PASSWORD = "AuthCtlPassword123!";
    private static final String DISABLED_USERNAME = "authctl-disabled";
    private static final String ADMIN_USERNAME = "authctl-admin";
    private static final String INVALID_CREDENTIALS_BODY =
            "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Invalid username or password\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiters rateLimiters;

    private User user;

    /** Shared H2 + shared context: reset this class's users and every limiter before each test. */
    @BeforeEach
    void setUp() {
        user = TestUsers.reset(userRepository, passwordEncoder, USERNAME, PASSWORD, Role.USER);
        rateLimiters.resetAll();
    }

    private ApiSession client() {
        return new ApiSession(mockMvc, objectMapper);
    }

    @Test
    void passwordIsStoredAsABcryptHashNotPlaintext() {
        User stored = userRepository.findByUsername(USERNAME).orElseThrow();

        assertThat(stored.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(stored.getPassword()).startsWith("$2a$12$");
    }

    @Test
    void loginWithValidCredentialsAuthenticatesTheSessionAndAuditsWithoutUsernameOrPassword() throws Exception {
        ApiSession client = client();
        try (LogCapture logs = LogCapture.start()) {
            client.login(USERNAME, PASSWORD).andExpect(status().isOk());

            LogCapture.Event success = logs.audit("User authenticated").getFirst();
            assertThat(success.mdc()).containsEntry(AuditLogger.USER_ID, user.getPublicId().toString());
            assertThat(success.kv("event.outcome")).isEqualTo("success");
            assertThat(logs.events()).noneMatch(e -> e.everything().contains(PASSWORD));
            assertThat(logs.audit()).noneMatch(e -> e.everything().contains(USERNAME));
        }

        client.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorizedAndAuditsTheFailure() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            client().login(USERNAME, "wrong-password-1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));

            LogCapture.Event failure = logs.audit("User authentication failed").getFirst();
            assertThat(failure.kv("event.reason")).isEqualTo("bad_credentials");
            assertThat(failure.mdc()).containsEntry(AuditLogger.USER_ID, user.getPublicId().toString());
            assertThat(logs.events()).noneMatch(e -> e.everything().contains("wrong-password-1"));
            assertThat(logs.audit()).noneMatch(e -> e.everything().contains(USERNAME));
        }
    }

    @Test
    void loginWithUnknownUsernameReturnsTheIdenticalBodyAndAuditsWithoutAUserId() throws Exception {
        String wrongPasswordBody = client().login(USERNAME, "wrong-password-1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        try (LogCapture logs = LogCapture.start()) {
            String unknownUserBody = client().login("authctl-no-such-user", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            assertThat(unknownUserBody).isEqualTo(wrongPasswordBody);

            LogCapture.Event failure = logs.audit("User authentication failed").getFirst();
            assertThat(failure.mdc()).doesNotContainKey(AuditLogger.USER_ID);
        }
    }

    @Test
    void loginWithDisabledAccountAndCorrectPasswordReturnsTheIdenticalGenericBody() throws Exception {
        User disabled = TestUsers.reset(userRepository, passwordEncoder, DISABLED_USERNAME, PASSWORD, Role.USER);
        disabled.setEnabled(false);
        userRepository.save(disabled);

        try (LogCapture logs = LogCapture.start()) {
            client().login(DISABLED_USERNAME, PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
            assertThat(logs.audit("User authentication failed").getFirst().kv("event.reason")).isEqualTo("disabled");
        }
        // Not counted as a failed guess: the password was right.
        assertThat(userRepository.findByUsername(DISABLED_USERNAME).orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void malformedLoginBodyIsAGenericUnauthorizedNotAServerError() throws Exception {
        client().postRaw("/api/auth/login", "{not json")
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
        client().postRaw("/api/auth/login", "null")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        client().postRaw("/api/auth/login", "{}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meReturnsUnauthorizedWithoutASession() throws Exception {
        client().get("/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void meReturnsUnauthorizedWithAnUnauthenticatedSession() throws Exception {
        // Fetching the CSRF token creates a (real, JDBC-backed) session that was never logged into.
        ApiSession client = client();
        client.fetchCsrfToken();
        assertThat(client.sessionCookie()).isNotNull();

        client.get("/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meReturnsTheCurrentUserWhenTheSessionIsAuthenticated() throws Exception {
        ApiSession client = client();
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());

        client.get("/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value(USERNAME + "@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void meReturnsUnauthorizedWhenTheUserRowVanishedUnderAnAuthenticatedSession() throws Exception {
        User doomed = TestUsers.reset(userRepository, passwordEncoder, "authctl-vanishing", PASSWORD, Role.USER);
        ApiSession client = client();
        client.login(doomed.getUsername(), PASSWORD).andExpect(status().isOk());

        userRepository.delete(doomed);

        client.get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheSessionAndExpiresTheSessionCookieAndIsAudited() throws Exception {
        ApiSession client = client();
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());

        MvcResult result;
        try (LogCapture logs = LogCapture.start()) {
            result = client.logout().andExpect(status().isOk()).andReturn();
            assertThat(logs.audit("User logged out").getFirst().mdc())
                    .containsEntry(AuditLogger.USER_ID, user.getPublicId().toString());
        }

        String setCookie = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
        assertThat(setCookie).contains(ApiSession.SESSION_COOKIE + "=").contains("Max-Age=0");
        assertThat(client.sessionCookie()).isNull();
        client.get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCsrfTokenIsRejectedAndTheSessionSurvives() throws Exception {
        ApiSession client = client();
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());

        client.perform(MockMvcRequestBuilders.post("/api/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        client.get("/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void anEvictedSessionIsToldItExpired() throws Exception {
        ApiSession first = client();
        first.login(USERNAME, PASSWORD).andExpect(status().isOk());
        client().login(USERNAME, PASSWORD).andExpect(status().isOk());

        first.get("/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Session expired"));
    }

    @Test
    void adminRoleLoginGrantsAdminAccessAndUserRoleDoesNot() throws Exception {
        TestUsers.reset(userRepository, passwordEncoder, ADMIN_USERNAME, PASSWORD, Role.ADMIN);

        ApiSession admin = client();
        admin.login(ADMIN_USERNAME, PASSWORD).andExpect(status().isOk());
        admin.get("/api/auth/me").andExpect(jsonPath("$.role").value("ADMIN"));
        admin.get("/api/admin/users").andExpect(status().isOk());

        ApiSession regular = client();
        regular.login(USERNAME, PASSWORD).andExpect(status().isOk());
        regular.get("/api/admin/users")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void successfulLoginResetsThePriorFailedAttemptCounter() throws Exception {
        client().login(USERNAME, "wrong-password-1").andExpect(status().isUnauthorized());
        client().login(USERNAME, "wrong-password-2").andExpect(status().isUnauthorized());
        assertThat(userRepository.findByUsername(USERNAME).orElseThrow().getFailedLoginAttempts()).isEqualTo(2);

        client().login(USERNAME, PASSWORD).andExpect(status().isOk());

        User updated = userRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isZero();
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    void afterTheLockoutCooldownTheCorrectPasswordSucceedsAndResetsTheCounter() throws Exception {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        userRepository.save(user);

        client().login(USERNAME, PASSWORD).andExpect(status().isOk());

        User updated = userRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isZero();
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    void aFailureAfterAnExpiredLockoutStartsCountingFresh() throws Exception {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        userRepository.save(user);

        client().login(USERNAME, "wrong-password-1").andExpect(status().isUnauthorized());

        User updated = userRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    void unknownPathsAndMethodsAnswerWithTheStandardErrorBody() throws Exception {
        ApiSession client = client();
        client.login(USERNAME, PASSWORD).andExpect(status().isOk());

        client.get("/api/does-not-exist")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        client.delete("/api/auth/me")
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
