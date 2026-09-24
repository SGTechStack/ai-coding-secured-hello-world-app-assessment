package com.example.helloauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helloauth.auth.IpThrottleService;
import com.example.helloauth.config.AppProperties;
import com.example.helloauth.user.User;
import java.time.Duration;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Ticket-11 coverage at the HTTP seam: the two-layer brute-force defense.
 * Distinct source IPs come from {@code .remoteAddress(...)}; time is controlled
 * through a {@code @Primary} {@link MutableClock} bean — no sleeping (ticket 05
 * strategy). Small thresholds keep the suite fast:
 * lockout 3 / 10m window / 15m cooldown, IP throttle 2 / 10m window — note
 * ip-throttle &lt; lockout, the relationship the anti-DoS AC depends on.
 * HTTP plumbing rides the shared {@link ApiTestSupport} fixture.
 */
@SpringBootTest(properties = {
    "app.lockout.max-failures=3",
    "app.lockout.window=10m",
    "app.lockout.cooldown=15m",
    "app.ip-throttle.max-failures=2",
    "app.ip-throttle.anon-max-requests=2",
    "app.ip-throttle.window=10m",
})
@AutoConfigureMockMvc
class LockoutAndThrottleApiTests extends ApiTestSupport {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String BAD_PASSWORD = "wrong wrong wrong";
    private static final String IP_A = "10.0.0.1";
    private static final String IP_B = "10.0.0.2";
    private static final String IP_C = "10.0.0.3";

    @Autowired
    IpThrottleService ipThrottle;

    @Autowired
    MutableClock clock;

    @Autowired
    AppProperties properties;

    /**
     * Time control: a mutable clock takes precedence over the system-UTC bean
     * for every {@code Clock} injection (lock checks, window math, throttles).
     */
    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(T0);
        }
    }

    @BeforeEach
    void clean() {
        // Tokens first — password_reset_tokens.user_id FKs into users (the
        // reset-request throttle tests mint real rows).
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        // The Caffeine cache is a context-scoped singleton — reset between
        // tests or one test's failures would throttle the next test's IP.
        ipThrottle.clear();
        clock.setInstant(T0);
    }

    // ------------------------------------------------------------------
    // Account lockout
    // ------------------------------------------------------------------

    @Test
    void nFailuresWithinWindowLockTheAccount() throws Exception {
        seedUser("alice", "alice@example.com");

        // ip-throttle=2, so the third failure must come from a second IP —
        // itself a demonstration that one source can't reach the threshold.
        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("alice", BAD_PASSWORD, IP_B).andExpect(status().isUnauthorized());

        User alice = userRepository.findByUsername("alice").orElseThrow();
        assertThat(alice.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(alice.getLastFailedAt()).isEqualTo(T0);
        assertThat(alice.getLockedUntil())
            .isEqualTo(T0.plus(Duration.ofMinutes(15)));

        // A locked account rejects even the correct password — same generic
        // 401, so lockout state isn't enumerable.
        login("alice", VALID_PASSWORD, IP_C)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void loginAfterCooldownSucceedsAndResetsTheCounter() throws Exception {
        seedUser("alice", "alice@example.com");
        login("alice", BAD_PASSWORD, IP_A);
        login("alice", BAD_PASSWORD, IP_A);
        login("alice", BAD_PASSWORD, IP_B);
        assertThat(userRepository.findByUsername("alice").orElseThrow()
            .getLockedUntil()).isNotNull();

        clock.advance(Duration.ofMinutes(16));

        login("alice", VALID_PASSWORD, IP_B).andExpect(status().isOk());

        User alice = userRepository.findByUsername("alice").orElseThrow();
        assertThat(alice.getFailedLoginAttempts()).isZero();
        assertThat(alice.getLastFailedAt()).isNull();
        assertThat(alice.getLockedUntil()).isNull();
    }

    @Test
    void accountFailureWindowIsLiteralViaLastFailedAt() throws Exception {
        // Two failures more than one window ago must not count toward the
        // lockout — "N failures within a window" is enforced literally.
        seedUser("alice", "alice@example.com");
        login("alice", BAD_PASSWORD, IP_A);
        login("alice", BAD_PASSWORD, IP_B);

        clock.advance(Duration.ofMinutes(11));

        login("alice", BAD_PASSWORD, IP_C).andExpect(status().isUnauthorized());

        User alice = userRepository.findByUsername("alice").orElseThrow();
        assertThat(alice.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(alice.getLastFailedAt()).isEqualTo(T0.plus(Duration.ofMinutes(11)));
        assertThat(alice.getLockedUntil()).isNull();
    }

    // ------------------------------------------------------------------
    // IP throttle — the anti-DoS layer
    // ------------------------------------------------------------------

    @Test
    void singleSourceIpCanNeverLockAnAccount() throws Exception {
        // The literal AC: an attacker failing a victim's password from one IP
        // is throttled before the account counter can reach the lockout
        // threshold — because the IP gate is checked first.
        seedUser("victim", "victim@example.com");

        login("victim", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("victim", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());

        login("victim", BAD_PASSWORD, IP_A)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Content-Type",
                Matchers.containsString("application/problem+json")));

        // The throttled attempt moved nothing: counter below the threshold,
        // no lock — the victim can still log in from a different IP.
        User victim = userRepository.findByUsername("victim").orElseThrow();
        assertThat(victim.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(victim.getLockedUntil()).isNull();

        login("victim", VALID_PASSWORD, IP_B).andExpect(status().isOk());
    }

    @Test
    void throttleEngagesAcrossMultipleUsernames() throws Exception {
        // A spray across usernames burns the shared IP budget — independently
        // of any account's lockout state.
        seedUser("u1", "u1@example.com");
        seedUser("u2", "u2@example.com");
        seedUser("u3", "u3@example.com");

        login("u1", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("u2", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());

        login("u3", BAD_PASSWORD, IP_A).andExpect(status().isTooManyRequests());

        // The throttled attempt never reached the account layer — u3's row
        // was not even touched.
        assertThat(userRepository.findByUsername("u3").orElseThrow()
            .getFailedLoginAttempts()).isZero();
    }

    @Test
    void successfulLoginDoesNotResetTheIpBucket() throws Exception {
        // fail(1) → success → fail(2): if the success laundered the bucket,
        // the final attempt would pass instead of hitting the threshold.
        seedUser("alice", "alice@example.com");
        seedUser("bob", "bob@example.com");

        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("bob", VALID_PASSWORD, IP_A).andExpect(status().isOk());
        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());

        login("alice", BAD_PASSWORD, IP_A)
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void xForwardedForCannotEvadeTheThrottle() throws Exception {
        // The bucket is keyed on getRemoteAddr() — rotating X-Forwarded-For
        // (untrusted without a stripping proxy) changes nothing.
        seedUser("alice", "alice@example.com");

        loginWithXff("alice", BAD_PASSWORD, IP_A, "1.1.1.1")
            .andExpect(status().isUnauthorized());
        loginWithXff("alice", BAD_PASSWORD, IP_A, "2.2.2.2")
            .andExpect(status().isUnauthorized());

        loginWithXff("alice", BAD_PASSWORD, IP_A, "3.3.3.3")
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void ipBucketDecaysAfterAQuietWindow() throws Exception {
        seedUser("alice", "alice@example.com");
        login("alice", BAD_PASSWORD, IP_A);

        clock.advance(Duration.ofMinutes(11));

        // The stale failure decayed — this attempt is a fresh first failure,
        // so it is rejected for credentials (401), not throttled (429).
        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        assertThat(ipThrottle.isThrottled(IP_A)).isFalse();
    }

    // ------------------------------------------------------------------
    // Anonymous-request throttle — register & password-reset (F-04)
    // ------------------------------------------------------------------

    @Test
    void registerIsThrottledAfterTheAnonBudgetRunsOut() throws Exception {
        // anon-max-requests=2: two registrations pass, the third is the
        // 429 — and never reaches the service (no third user row).
        postWithCsrf("/api/auth/register",
                registerJson("u1", "u1@example.com"), IP_A)
            .andExpect(status().isCreated());
        postWithCsrf("/api/auth/register",
                registerJson("u2", "u2@example.com"), IP_A)
            .andExpect(status().isCreated());

        postWithCsrf("/api/auth/register",
                registerJson("u3", "u3@example.com"), IP_A)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Content-Type",
                Matchers.containsString("application/problem+json")));

        assertThat(userRepository.count()).isEqualTo(2);

        // A different source IP carries its own budget.
        postWithCsrf("/api/auth/register",
                registerJson("u3", "u3@example.com"), IP_B)
            .andExpect(status().isCreated());
    }

    @Test
    void passwordResetRequestIsThrottledAfterTheAnonBudgetRunsOut()
            throws Exception {
        seedUser("alice", "alice@example.com");

        postWithCsrf("/api/auth/password-reset/request",
                "{\"email\":\"alice@example.com\"}", IP_A)
            .andExpect(status().isOk());
        postWithCsrf("/api/auth/password-reset/request",
                "{\"email\":\"alice@example.com\"}", IP_A)
            .andExpect(status().isOk());

        postWithCsrf("/api/auth/password-reset/request",
                "{\"email\":\"alice@example.com\"}", IP_A)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Content-Type",
                Matchers.containsString("application/problem+json")));

        // The throttled hit minted no token row — the request endpoint
        // is exactly the DB-write surface F-04 is about.
        assertThat(tokenRepository.count()).isEqualTo(2);
    }

    @Test
    void anonBudgetIsIndependentOfTheLoginFailureBudget() throws Exception {
        // Exhaust IP_A's login-failure budget — the next login 429s…
        seedUser("alice", "alice@example.com");
        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("alice", BAD_PASSWORD, IP_A).andExpect(status().isUnauthorized());
        login("alice", BAD_PASSWORD, IP_A)
            .andExpect(status().isTooManyRequests());

        // …but the anonymous-request bucket is separate: register still
        // runs from the same IP.
        postWithCsrf("/api/auth/register",
                registerJson("bob", "bob@example.com"), IP_A)
            .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Configurability
    // ------------------------------------------------------------------

    @Test
    void lockoutAndThrottleTunablesBindAsAppProperties() {
        assertThat(properties.getLockout().getMaxFailures()).isEqualTo(3);
        assertThat(properties.getLockout().getWindow())
            .isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getLockout().getCooldown())
            .isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getIpThrottle().getMaxFailures()).isEqualTo(2);
        assertThat(properties.getIpThrottle().getAnonMaxRequests())
            .isEqualTo(2);
        assertThat(properties.getIpThrottle().getWindow())
            .isEqualTo(Duration.ofMinutes(10));
    }

    private static String registerJson(String username, String email) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email
            + "\",\"password\":\"" + VALID_PASSWORD + "\"}";
    }
}
