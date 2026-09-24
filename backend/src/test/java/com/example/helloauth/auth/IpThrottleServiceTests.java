package com.example.helloauth.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.helloauth.MutableClock;
import com.example.helloauth.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the IP bucket semantics: threshold, sliding-window decay
 * anchored at the last failure, per-IP independence, and {@code clear()}.
 * All through the injected {@link MutableClock} — the cache's own ticker is
 * never part of correctness.
 */
class IpThrottleServiceTests {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private MutableClock clock;
    private IpThrottleService throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        AppProperties properties = new AppProperties();
        properties.getIpThrottle().setMaxFailures(2);
        properties.getIpThrottle().setAnonMaxRequests(2);
        properties.getIpThrottle().setWindow(Duration.ofMinutes(10));
        throttle = new IpThrottleService(clock, properties);
    }

    @Test
    void freshIpIsNotThrottled() {
        assertThat(throttle.isThrottled("10.0.0.1")).isFalse();
    }

    @Test
    void thresholdReachedThrottles() {
        throttle.recordFailure("10.0.0.1");
        assertThat(throttle.isThrottled("10.0.0.1")).isFalse();

        throttle.recordFailure("10.0.0.1");
        assertThat(throttle.isThrottled("10.0.0.1")).isTrue();
    }

    @Test
    void bucketsAreIndependentPerIp() {
        throttle.recordFailure("10.0.0.1");
        throttle.recordFailure("10.0.0.1");

        assertThat(throttle.isThrottled("10.0.0.1")).isTrue();
        assertThat(throttle.isThrottled("10.0.0.2")).isFalse();
    }

    @Test
    void quietPeriodOfOneFullWindowDecaysTheBucket() {
        throttle.recordFailure("10.0.0.1");
        clock.advance(Duration.ofMinutes(11));

        // Decayed: this failure starts a fresh streak instead of tripping.
        throttle.recordFailure("10.0.0.1");
        assertThat(throttle.isThrottled("10.0.0.1")).isFalse();
    }

    @Test
    void newFailureReanchorsTheWindow() {
        throttle.recordFailure("10.0.0.1");
        clock.advance(Duration.ofMinutes(9)); // inside the window
        throttle.recordFailure("10.0.0.1");   // re-anchors: throttle now
        assertThat(throttle.isThrottled("10.0.0.1")).isTrue();

        // Sustained attack stays hot — decay needs a full quiet window from
        // the LAST failure, not the first.
        clock.advance(Duration.ofMinutes(9));
        assertThat(throttle.isThrottled("10.0.0.1")).isTrue();
        clock.advance(Duration.ofMinutes(2)); // 11m since last failure
        assertThat(throttle.isThrottled("10.0.0.1")).isFalse();
    }

    @Test
    void clearDropsAllBuckets() {
        throttle.recordFailure("10.0.0.1");
        throttle.recordFailure("10.0.0.1");
        throttle.recordAnonRequest("10.0.0.2");
        throttle.recordAnonRequest("10.0.0.2");
        assertThat(throttle.isThrottled("10.0.0.1")).isTrue();
        assertThat(throttle.isAnonThrottled("10.0.0.2")).isTrue();

        throttle.clear();

        assertThat(throttle.isThrottled("10.0.0.1")).isFalse();
        assertThat(throttle.isAnonThrottled("10.0.0.2")).isFalse();
    }

    // ------------------------------------------------------------------
    // Anonymous-request bucket — the F-04 second cache
    // ------------------------------------------------------------------

    @Test
    void freshIpIsNotAnonThrottled() {
        assertThat(throttle.isAnonThrottled("10.0.0.1")).isFalse();
    }

    @Test
    void anonRequestThresholdReachedThrottles() {
        throttle.recordAnonRequest("10.0.0.1");
        assertThat(throttle.isAnonThrottled("10.0.0.1")).isFalse();

        throttle.recordAnonRequest("10.0.0.1");
        assertThat(throttle.isAnonThrottled("10.0.0.1")).isTrue();
    }

    @Test
    void anonAndFailureBudgetsAreIndependent() {
        // Failed logins don't burn the anonymous-request budget…
        throttle.recordFailure("10.0.0.1");
        throttle.recordFailure("10.0.0.1");
        assertThat(throttle.isThrottled("10.0.0.1")).isTrue();
        assertThat(throttle.isAnonThrottled("10.0.0.1")).isFalse();

        // …and register/reset hits don't feed the login-failure count —
        // a spray against the anonymous endpoints can't get a login
        // source throttled out of its (smaller) failure budget.
        throttle.recordAnonRequest("10.0.0.2");
        throttle.recordAnonRequest("10.0.0.2");
        assertThat(throttle.isAnonThrottled("10.0.0.2")).isTrue();
        assertThat(throttle.isThrottled("10.0.0.2")).isFalse();
    }

    @Test
    void anonBucketDecaysAfterAQuietWindow() {
        throttle.recordAnonRequest("10.0.0.1");
        throttle.recordAnonRequest("10.0.0.1");
        assertThat(throttle.isAnonThrottled("10.0.0.1")).isTrue();

        clock.advance(Duration.ofMinutes(11));

        // Same sliding-window semantics as the failure bucket — a quiet
        // full window resets the budget.
        assertThat(throttle.isAnonThrottled("10.0.0.1")).isFalse();
    }
}
