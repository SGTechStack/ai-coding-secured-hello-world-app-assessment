package com.example.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Integration tests for Account Lockout (Stories 10–12, 45).
 * Uses the Clock seam to advance time deterministically — no Thread.sleep.
 * IP throttle threshold is set to 100 so lockout tests (5 failures each) never
 * accidentally trip the IP throttle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:lockouttest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.security.lockout.max-attempts=5",
    "app.security.lockout.duration-minutes=15",
    "app.security.ip-throttle.max-attempts=100"   // prevent IP throttle from interfering
})
class LockoutIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    IpThrottleService ipThrottleService;

    @MockBean
    Clock clock;

    @BeforeEach
    void setup() {
        Mockito.when(clock.instant()).thenReturn(BASE);
        Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        ipThrottleService.clearForTesting();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractCsrfCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    return c.substring("XSRF-TOKEN=".length()).split(";")[0];
                }
            }
        }
        return null;
    }

    private HttpEntity<String> jsonWithCsrf(String body) {
        String csrf = extractCsrfCookie(
                restTemplate.exchange("/api/auth/csrf", HttpMethod.GET, null, Void.class).getHeaders());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (csrf != null) {
            h.set("X-XSRF-TOKEN", csrf);
            h.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        }
        return new HttpEntity<>(body, h);
    }

    private void register(String username, String email) {
        restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                jsonWithCsrf(String.format(
                        "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"secure-pass-12\"}",
                        username, email)),
                Void.class);
    }

    private ResponseEntity<String> loginAttempt(String username, String password) {
        return restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                jsonWithCsrf(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)),
                String.class);
    }

    // ── lockout behaviour ─────────────────────────────────────────────────────

    @Test
    void fiveConsecutiveFailures_locksAccount() {
        // Story 11: 5 failures → account locked
        register("alice", "alice@lockout.test");

        for (int i = 0; i < 5; i++) {
            assertThat(loginAttempt("alice", "wrong-pass-1").getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Correct password now returns the same generic 401 — account is locked
        assertThat(loginAttempt("alice", "secure-pass-12").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lockedAccount_genericResponseDoesNotRevealLockoutState() {
        // Stories 9, 11: locked-account response is byte-identical to wrong-password 401
        register("bob", "bob@lockout.test");

        for (int i = 0; i < 5; i++) {
            loginAttempt("bob", "wrong-pass-1");
        }

        ResponseEntity<String> wrongPwResponse = loginAttempt("carol-nonexistent", "whatever-1");
        ResponseEntity<String> lockedResponse = loginAttempt("bob", "secure-pass-12");

        assertThat(lockedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(lockedResponse.getBody()).isEqualTo(wrongPwResponse.getBody());
        assertThat(lockedResponse.getStatusCode()).isEqualTo(wrongPwResponse.getStatusCode());
    }

    @Test
    void loginAfterCooldown_succeedsAndResetsCounter() {
        // Story 12: correct login after cooldown expires → success + counter reset
        register("carol", "carol@lockout.test");

        for (int i = 0; i < 5; i++) {
            loginAttempt("carol", "wrong-pass-1");
        }
        // Account is now locked at BASE; advance clock past 15-minute cooldown
        Mockito.when(clock.instant()).thenReturn(BASE.plusSeconds(16 * 60));

        ResponseEntity<String> response = loginAttempt("carol", "secure-pass-12");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void successfulLogin_resetsFailedCounter() {
        // Story 8: successful login resets failed_login_attempts to 0
        register("dave", "dave@lockout.test");

        // 3 failures
        for (int i = 0; i < 3; i++) {
            loginAttempt("dave", "wrong-pass-1");
        }
        // Successful login clears the counter
        assertThat(loginAttempt("dave", "secure-pass-12").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 4 more failures after the reset should NOT lock (threshold is 5 from fresh 0)
        for (int i = 0; i < 4; i++) {
            loginAttempt("dave", "wrong-pass-1");
        }
        // 4 failures post-reset → account should still be accessible (not locked yet)
        assertThat(loginAttempt("dave", "secure-pass-12").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ── concurrency / race-safe lockout (Story 45) ────────────────────────────

    @Test
    void concurrentFailedLogins_locksAccountWithoutThresholdBypass() throws InterruptedException {
        /*
         * Story 45: N concurrent wrong-password requests against one account must
         * result in the account being locked, regardless of concurrency. The
         * PESSIMISTIC_WRITE lock in LockoutService serialises the counter updates
         * so no thread can observe a stale counter and skip the lockout decision.
         */
        register("eve", "eve@lockout.test");

        int threadCount = 10; // well above the lockout threshold of 5
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    loginAttempt("eve", "wrong-pass-1");
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        // After ≥5 concurrent failures the account MUST be locked.
        // A correct-password attempt must still return 401.
        assertThat(loginAttempt("eve", "secure-pass-12").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
