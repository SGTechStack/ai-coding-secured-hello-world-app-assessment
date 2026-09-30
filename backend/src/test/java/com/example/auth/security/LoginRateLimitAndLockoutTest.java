package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Login rate limits and account lockout (ADR-0005) through the real filter chain.
 * Because the per-(IP, username) limit (3) trips before the lockout (5), lockout scenarios spread
 * their failures across source IPs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LoginRateLimitAndLockoutTest {

    private static final String USERNAME = "rl-user";
    private static final String PASSWORD = "RateLimitPass123!";
    private static final String WRONG = "not-the-password";
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

    @BeforeEach
    void setUp() {
        TestUsers.reset(userRepository, passwordEncoder, USERNAME, PASSWORD, Role.USER);
        rateLimiters.resetAll();
    }

    private ResultActions login(String ip, String username, String password) throws Exception {
        return new ApiSession(mockMvc, objectMapper).from(ip).login(username, password);
    }

    private User reload() {
        return userRepository.findByUsername(USERNAME).orElseThrow();
    }

    @Test
    void perIpAndUsernameLimitTripsOnTheFourthAttemptBeforeTheAccountLocks() throws Exception {
        String ip = "10.0.1.1";
        for (int i = 0; i < 3; i++) {
            login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        }

        try (LogCapture logs = LogCapture.start()) {
            // Even the right password is refused from this source now -- without a BCrypt check.
            login(ip, USERNAME, PASSWORD)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
            assertThat(logs.audit("Request rate limited").getFirst().kv("labels.limiter"))
                    .isEqualTo("login_ip_username");
            assertThat(logs.audit("User authentication failed")).isEmpty();
        }

        User user = reload();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNull();

        // One source can't lock the account for everyone else.
        login("10.0.1.2", USERNAME, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void retryAfterIsTheRemainingWindowInWholeSeconds() throws Exception {
        String ip = "10.0.2.1";
        for (int i = 0; i < 3; i++) {
            login(ip, USERNAME, WRONG);
        }
        String retryAfter = login(ip, USERNAME, WRONG)
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        assertThat(Long.parseLong(retryAfter)).isBetween(890L, 900L);
    }

    @Test
    void perIpUsernameBucketIsCaseAndWhitespaceInsensitive() throws Exception {
        String ip = "10.0.3.1";
        login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        login(ip, "RL-USER", WRONG).andExpect(status().isUnauthorized());
        login(ip, " rl-user ", WRONG).andExpect(status().isUnauthorized());

        login(ip, "Rl-User", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginClearsThePerIpUsernameBucket() throws Exception {
        String ip = "10.0.4.1";
        login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        login(ip, USERNAME, PASSWORD).andExpect(status().isOk());

        login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        login(ip, USERNAME, WRONG).andExpect(status().isUnauthorized());
        login(ip, USERNAME, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void fiveFailuresFromDifferentSourcesLockTheAccountAndTheCorrectPasswordThenGetsTheGenericBody()
            throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            for (int i = 1; i <= 5; i++) {
                login("10.0.5." + i, USERNAME, WRONG)
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
            }
            User locked = reload();
            assertThat(logs.audit("Account locked after repeated failures")).hasSize(1).first()
                    .satisfies(e -> assertThat(e.mdc()).containsEntry(AuditLogger.USER_ID, locked.getPublicId().toString()));
            assertThat(logs.events()).noneMatch(e -> e.everything().contains(PASSWORD) || e.everything().contains(WRONG));
        }

        User user = reload();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.isLocked(Instant.now())).isTrue();
        assertThat(Duration.between(Instant.now(), user.getLockedUntil()))
                .isBetween(Duration.ofMinutes(19), Duration.ofMinutes(20));

        // Locked out: even the correct password, from a fresh source, gets the exact same body.
        try (LogCapture logs = LogCapture.start()) {
            login("10.0.5.100", USERNAME, PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
            assertThat(logs.audit("User authentication failed").getFirst().kv("event.reason")).isEqualTo("locked");
        }
        new ApiSession(mockMvc, objectMapper).from("10.0.5.100").get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordOnALockedAccountIsTheSameGenericBodyAndDoesNotExtendTheLock() throws Exception {
        Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(7));
        User user = reload();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(lockedUntil);
        userRepository.save(user);
        Instant storedLockedUntil = reload().getLockedUntil();

        for (int i = 1; i <= 2; i++) {
            login("10.0.6." + i, USERNAME, WRONG)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
        }

        User after = reload();
        assertThat(after.getLockedUntil()).isEqualTo(storedLockedUntil);
        assertThat(after.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void perUsernameLimitAllowsTenAttemptsPerMinuteThenRejects() throws Exception {
        for (int i = 1; i <= 10; i++) {
            login("10.0.7." + i, USERNAME, PASSWORD).andExpect(status().isOk());
        }

        String retryAfter = login("10.0.7.11", USERNAME, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    @Test
    void perIpFailureLimitBlocksAFreshUsernameFromTheSameSource() throws Exception {
        String ip = "10.0.8.1";
        for (int i = 0; i < 20; i++) {
            login(ip, "rl-spray-" + i, WRONG).andExpect(status().isUnauthorized());
        }

        try (LogCapture logs = LogCapture.start()) {
            String retryAfter = login(ip, USERNAME, PASSWORD)
                    .andExpect(status().isTooManyRequests())
                    .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
            assertThat(Long.parseLong(retryAfter)).isBetween(890L, 900L);
            assertThat(logs.audit("Request rate limited").getFirst().kv("labels.limiter")).isEqualTo("login_ip");
        }

        login("10.0.8.2", USERNAME, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void oversizedUsernameIsAGenericUnauthorizedNotAServerError() throws Exception {
        login("10.0.9.1", "u".repeat(65), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
        login("10.0.9.1", "u".repeat(10_000), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
    }

    @Test
    void oversizedPasswordIsAGenericUnauthorizedNotAServerErrorAndIsNotCounted() throws Exception {
        login("10.0.10.1", USERNAME, "p".repeat(73))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
        login("10.0.10.1", USERNAME, "😀".repeat(19))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));
        login("10.0.10.1", "rl-unknown-user", "p".repeat(1000))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(INVALID_CREDENTIALS_BODY, true));

        login("10.0.10.1", USERNAME, PASSWORD).andExpect(status().isOk());
    }
}
