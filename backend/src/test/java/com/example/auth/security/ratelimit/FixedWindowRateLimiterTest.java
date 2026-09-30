package com.example.auth.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Unit tests for the fixed-window limiter, driven by a hand-advanced clock. */
class FixedWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    private FixedWindowRateLimiter limiter(int maxHits, Duration block, int maxKeys) {
        return new FixedWindowRateLimiter("test_limiter", maxHits, WINDOW, block, maxKeys, clock);
    }

    @Test
    void tryAcquireAllowsExactlyMaxHitsThenBlocksUntilTheWindowEnds() {
        FixedWindowRateLimiter limiter = limiter(3, Duration.ZERO, 100);

        assertThat(limiter.tryAcquire("k").blocked()).isFalse();
        assertThat(limiter.tryAcquire("k").blocked()).isFalse();
        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.tryAcquire("k").blocked()).isFalse();

        FixedWindowRateLimiter.Decision fourth = limiter.tryAcquire("k");
        assertThat(fourth.blocked()).isTrue();
        // block=0 means "until the window (started at t=0) ends": 10 minutes left.
        assertThat(fourth.retryAfter()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void checkNeverCountsAHit() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ZERO, 100);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.check("k").blocked()).isFalse();
        }
        assertThat(limiter.size()).isZero();

        limiter.record("k");
        assertThat(limiter.check("k").blocked()).isTrue();
    }

    @Test
    void recordAllowsTheHitThatReachesTheLimitAndBlocksTheNext() {
        FixedWindowRateLimiter limiter = limiter(2, Duration.ZERO, 100);

        assertThat(limiter.record("k").blocked()).isFalse();
        assertThat(limiter.record("k").blocked()).isFalse();
        FixedWindowRateLimiter.Decision third = limiter.record("k");
        assertThat(third.blocked()).isTrue();
        assertThat(third.retryAfter()).isEqualTo(WINDOW);
    }

    @Test
    void windowExpiryStartsAFreshCount() {
        FixedWindowRateLimiter limiter = limiter(2, Duration.ZERO, 100);
        limiter.tryAcquire("k");
        limiter.tryAcquire("k");
        assertThat(limiter.tryAcquire("k").blocked()).isTrue();

        clock.advance(WINDOW);

        assertThat(limiter.check("k").blocked()).isFalse();
        assertThat(limiter.tryAcquire("k").blocked()).isFalse();
        assertThat(limiter.tryAcquire("k").blocked()).isFalse();
        assertThat(limiter.tryAcquire("k").blocked()).isTrue();
    }

    @Test
    void blockDurationLongerThanTheWindowOutlivesTheWindow() {
        Duration block = Duration.ofMinutes(30);
        FixedWindowRateLimiter limiter = limiter(1, block, 100);
        limiter.record("k");

        assertThat(limiter.check("k").retryAfter()).isEqualTo(block);

        clock.advance(Duration.ofMinutes(20)); // window over, block not
        FixedWindowRateLimiter.Decision stillBlocked = limiter.check("k");
        assertThat(stillBlocked.blocked()).isTrue();
        assertThat(stillBlocked.retryAfter()).isEqualTo(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("k").blocked()).isTrue();

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.check("k").blocked()).isFalse();
    }

    @Test
    void blockDurationIsMeasuredFromTheHitThatReachedTheLimit() {
        FixedWindowRateLimiter limiter = limiter(2, Duration.ofMinutes(15), 100);
        limiter.record("k");
        clock.advance(Duration.ofMinutes(10));
        limiter.record("k");

        assertThat(limiter.check("k").retryAfter()).isEqualTo(Duration.ofMinutes(15));
        clock.advance(Duration.ofMinutes(14));
        assertThat(limiter.check("k").blocked()).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.check("k").blocked()).isFalse();
    }

    @Test
    void enforceThrowsWithTheLimiterNameAndRetryAfter() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ZERO, 100);
        assertThatNoException().isThrownBy(() -> limiter.enforce("k"));

        assertThatThrownBy(() -> limiter.enforce("k"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
                    assertThat(ex.getLimiter()).isEqualTo("test_limiter");
                    assertThat(ex.getRetryAfter()).isEqualTo(WINDOW);
                    assertThat(ex.getMessage()).contains("test_limiter");
                });
    }

    @Test
    void keysAreIndependent() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ZERO, 100);
        limiter.record("a");

        assertThat(limiter.check("a").blocked()).isTrue();
        assertThat(limiter.check("b").blocked()).isFalse();
        assertThat(limiter.name()).isEqualTo("test_limiter");
    }

    @Test
    void clearAndResetDropState() {
        FixedWindowRateLimiter limiter = limiter(1, Duration.ZERO, 100);
        limiter.record("a");
        limiter.record("b");

        limiter.clear("a");
        assertThat(limiter.check("a").blocked()).isFalse();
        assertThat(limiter.check("b").blocked()).isTrue();

        limiter.reset();
        assertThat(limiter.size()).isZero();
        assertThat(limiter.check("b").blocked()).isFalse();
    }

    @Test
    void fullMapFailsClosedForNewKeysButKeepsServingExistingOnes() {
        FixedWindowRateLimiter limiter = limiter(5, Duration.ZERO, 2);
        limiter.record("a");
        limiter.record("b");

        FixedWindowRateLimiter.Decision newcomer = limiter.record("c");
        assertThat(newcomer.blocked()).isTrue();
        assertThat(newcomer.retryAfter()).isEqualTo(WINDOW);
        assertThat(limiter.size()).isEqualTo(2);

        // Existing keys are still counted normally.
        assertThat(limiter.record("a").blocked()).isFalse();
    }

    @Test
    void fullMapSweepsExpiredEntriesBeforeRejectingANewKey() {
        FixedWindowRateLimiter limiter = limiter(5, Duration.ZERO, 2);
        limiter.record("a");
        clock.advance(Duration.ofMinutes(10));
        limiter.record("b");
        clock.advance(Duration.ofMinutes(5)); // "a"'s window is over, "b"'s is not

        assertThat(limiter.record("c").blocked()).isFalse();
        assertThat(limiter.size()).isEqualTo(2);
        assertThat(limiter.record("d").blocked()).isTrue();
    }

    @Test
    void retryAfterSecondsRoundsUpAndIsNeverBelowOne() {
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ZERO)).isEqualTo(1);
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ofMillis(1001))).isEqualTo(2);
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ofMillis(59_500))).isEqualTo(60);
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ofMinutes(15))).isEqualTo(900);
        assertThat(RateLimitExceededException.retryAfterSeconds(Duration.ofSeconds(-5))).isEqualTo(1);
    }

    @Test
    void publicConstructorUsesTheSystemClock() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("sys", 1, WINDOW, Duration.ZERO, 10);
        limiter.record("k");
        FixedWindowRateLimiter.Decision decision = limiter.check("k");
        assertThat(decision.blocked()).isTrue();
        assertThat(decision.retryAfter()).isPositive().isLessThanOrEqualTo(WINDOW);
    }

    /** A clock tests advance by hand. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
