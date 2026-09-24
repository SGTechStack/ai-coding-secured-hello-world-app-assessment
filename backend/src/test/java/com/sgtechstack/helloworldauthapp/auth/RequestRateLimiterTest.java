package com.sgtechstack.helloworldauthapp.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level behaviour of the counter itself: the threshold, per-key and
 * per-limit separation, and the memory bound.
 *
 * <p>The bound matters for the same reason it did on {@link IpLoginThrottle}: a
 * limiter that grows a map entry per distinct source address is itself a
 * memory-exhaustion vector, and unlike the login throttle there is no
 * "successful login" event that would ever clean up after a spray.
 */
class RequestRateLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);

    @Test
    void permitsUpToTheLimitThenRejects() {
        RequestRateLimiter limiter = new RequestRateLimiter();

        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 2, WINDOW)).isFalse();
        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 2, WINDOW)).isFalse();
        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 2, WINDOW)).isTrue();
    }

    @Test
    void countsEachCallerSeparately() {
        RequestRateLimiter limiter = new RequestRateLimiter();

        limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW);
        limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW);

        // One caller exhausting a limit must not affect another.
        assertThat(limiter.exceedsLimit("register", "5.6.7.8", 1, WINDOW)).isFalse();
    }

    @Test
    void countsEachLimitSeparatelyForTheSameCaller() {
        RequestRateLimiter limiter = new RequestRateLimiter();

        limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW);
        limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW);

        // Different endpoints have different costs and thresholds; exhausting
        // one must not spill onto another.
        assertThat(limiter.exceedsLimit("password-reset-request", "1.2.3.4", 1, WINDOW)).isFalse();
    }

    @Test
    void startsAFreshWindowOnceTheOldOneHasPassed() throws InterruptedException {
        RequestRateLimiter limiter = new RequestRateLimiter();
        Duration shortWindow = Duration.ofMillis(20);

        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 1, shortWindow)).isFalse();
        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 1, shortWindow)).isTrue();

        Thread.sleep(shortWindow.toMillis() * 3);

        // A limit is a rate, not a ban: once the window passes the caller must
        // be served again rather than blocked permanently.
        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 1, shortWindow)).isFalse();
    }

    @Test
    void staysBoundedUnderAFloodOfDistinctCallers() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        int flood = RequestRateLimiter.MAX_TRACKED_KEYS + 500;

        for (int i = 0; i < flood; i++) {
            limiter.exceedsLimit("register", "10.3." + (i / 256) + "." + (i % 256), 5, WINDOW);
        }

        assertThat(limiter.trackedKeyCount())
                .isLessThanOrEqualTo(RequestRateLimiter.MAX_TRACKED_KEYS);
    }

    @Test
    void resetClearsEveryCounter() {
        RequestRateLimiter limiter = new RequestRateLimiter();

        limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW);
        limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW);
        limiter.reset();

        assertThat(limiter.trackedKeyCount()).isZero();
        assertThat(limiter.exceedsLimit("register", "1.2.3.4", 1, WINDOW)).isFalse();
    }
}
