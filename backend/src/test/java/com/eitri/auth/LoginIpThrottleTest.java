package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class LoginIpThrottleTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(NOW);

    @Test
    void allowsUntilTheMaximumOfCountedFailuresIsReached() {
        LoginIpThrottle throttle = throttle(3, 100);

        for (int failure = 0; failure < 3; failure++) {
            assertThat(throttle.check("203.0.113.10").allowed()).isTrue();
            throttle.recordFailure("203.0.113.10");
        }

        LoginIpThrottle.Decision decision = throttle.check("203.0.113.10");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(WINDOW.toSeconds());
    }

    @Test
    void checkingDoesNotCountAsAFailure() {
        LoginIpThrottle throttle = throttle(1, 100);

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(throttle.check("203.0.113.10").allowed()).isTrue();
        }
    }

    @Test
    void ipAddressesAreThrottledIndependently() {
        LoginIpThrottle throttle = throttle(2, 100);
        throttle.recordFailure("203.0.113.10");
        throttle.recordFailure("203.0.113.10");

        assertThat(throttle.check("203.0.113.10").allowed()).isFalse();
        assertThat(throttle.check("198.51.100.7").allowed()).isTrue();
    }

    @Test
    void retryAfterCountsDownToWhenTheOldestCountedFailureLeavesTheWindow() {
        LoginIpThrottle throttle = throttle(2, 100);
        throttle.recordFailure("ip");
        clock.advance(Duration.ofMinutes(5));
        throttle.recordFailure("ip");

        clock.advance(Duration.ofMinutes(4).plusMillis(500));
        // Oldest failure leaves the window at 15m; 5m59.5s remain, rounded up to whole seconds.
        assertThat(throttle.check("ip").retryAfterSeconds()).isEqualTo(360);

        clock.set(NOW.plus(WINDOW).minusMillis(1));
        assertThat(throttle.check("ip").retryAfterSeconds()).isEqualTo(1);

        clock.set(NOW.plus(WINDOW));
        assertThat(throttle.check("ip").allowed()).isTrue();
    }

    @Test
    void slidingWindowLiftsOnceTheWindowHasPassedSinceTheLastCountedFailure() {
        LoginIpThrottle throttle = throttle(2, 100);
        throttle.recordFailure("ip");
        clock.advance(Duration.ofMinutes(10));
        throttle.recordFailure("ip");
        assertThat(throttle.check("ip").allowed()).isFalse();

        clock.advance(Duration.ofMinutes(5));
        // The first failure has left the window, so one more failure is allowed again.
        assertThat(throttle.check("ip").allowed()).isTrue();
        throttle.recordFailure("ip");
        assertThat(throttle.check("ip").allowed()).isFalse();

        clock.advance(WINDOW);
        assertThat(throttle.check("ip").allowed()).isTrue();
    }

    @Test
    void cacheBoundIsHeldByReclaimingExpiredEntriesFirst() {
        LoginIpThrottle throttle = throttle(1, 2);
        throttle.recordFailure("expired");
        clock.advance(WINDOW);
        throttle.recordFailure("active");

        throttle.recordFailure("new");

        assertThat(throttle.trackedAddressCount()).isEqualTo(2);
        assertThat(throttle.check("active").allowed()).isFalse();
        assertThat(throttle.check("new").allowed()).isFalse();
    }

    @Test
    void cacheBoundEvictsTheLeastRecentlyFailingAddressWhenEveryEntryIsActive() {
        LoginIpThrottle throttle = throttle(1, 2);
        throttle.recordFailure("first");
        throttle.recordFailure("second");
        throttle.recordFailure("first");

        throttle.recordFailure("third");

        assertThat(throttle.trackedAddressCount()).isEqualTo(2);
        assertThat(throttle.check("second").allowed()).isTrue();
        assertThat(throttle.check("first").allowed()).isFalse();
        assertThat(throttle.check("third").allowed()).isFalse();
    }

    @Test
    void concurrentFailuresAreAllCounted() throws Exception {
        LoginIpThrottle throttle = throttle(40, 100);
        int callers = 40;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int caller = 0; caller < callers; caller++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    throttle.recordFailure("ip");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(throttle.check("ip").allowed()).isFalse();
    }

    private LoginIpThrottle throttle(int maxFailures, int cacheBound) {
        return new LoginIpThrottle(clock, maxFailures, WINDOW, cacheBound);
    }
}
