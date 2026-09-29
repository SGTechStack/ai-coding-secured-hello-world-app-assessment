package com.example.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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

/**
 * Integration tests for IP-based throttling (Story 13).
 * IP throttle threshold set low (3) to keep tests fast; lockout threshold
 * raised to 100 so it does not interfere with throttle tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:ipthrottletest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.security.ip-throttle.max-attempts=3",
    "app.security.ip-throttle.window-minutes=60",
    "app.security.lockout.max-attempts=100",   // prevent lockout from interfering
    "app.admin.bootstrap-enabled=false"        // Clock is mocked; skip startup seeder
})
class IpThrottleIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    IpThrottleService ipThrottleService;

    @MockBean
    Clock clock;

    @BeforeEach
    void setup() {
        Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
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

    // ── IP throttle behaviour ─────────────────────────────────────────────────

    @Test
    void failuresAcrossMultipleUsers_throttlesByIp() {
        /*
         * Story 13: failures from one IP targeting different users trigger throttling
         * independently of any single account's lockout state.
         * With threshold=3, the 4th attempt from the same IP must return 429.
         */
        register("user1", "user1@throttle.test");
        register("user2", "user2@throttle.test");
        register("user3", "user3@throttle.test");

        // 3 failures across different users — within limit, each returns 401
        assertThat(loginAttempt("user1", "wrong-1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginAttempt("user2", "wrong-1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginAttempt("user3", "wrong-1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 4th attempt: IP is now throttled → 429 regardless of target user
        ResponseEntity<String> throttled = loginAttempt("user1", "wrong-1");
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains("application/problem+json");
    }

    @Test
    void ipThrottle_independentOfAccountLockout() {
        /*
         * Story 13: the IP throttle must engage BEFORE any single account's
         * lockout is evaluated — so an attacker cannot lock out a victim just by
         * submitting the victim's password from a throttled IP. Verified here by
         * confirming that once the IP is throttled, even a non-locked account
         * targeting returns 429, not 401.
         */
        register("victim", "victim@throttle.test");

        // Exhaust IP throttle via unknown-user failures (no account to lock)
        loginAttempt("no-such-user-1", "wrong-1");
        loginAttempt("no-such-user-2", "wrong-1");
        loginAttempt("no-such-user-3", "wrong-1");

        // IP now throttled — the real account 'victim' is untouched, but the
        // attempt still returns 429 because the IP check fires first
        assertThat(loginAttempt("victim", "secure-pass-12").getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void throttleResponse_doesNotRevealAccountExistence() {
        // Story 48 / enumeration resistance: 429 body must not mention username/account details
        loginAttempt("some-user", "wrong-1");
        loginAttempt("some-user", "wrong-1");
        loginAttempt("some-user", "wrong-1");

        ResponseEntity<String> throttled = loginAttempt("some-user", "wrong-1");
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getBody()).doesNotContain("some-user", "username", "account");
    }
}
