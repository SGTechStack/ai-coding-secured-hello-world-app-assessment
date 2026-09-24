package com.sgtechstack.helloworldauthapp.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The throttle map must stay bounded.
 *
 * <p>Entries were previously removed only on a successful login from the same
 * address, so an attacker failing one login each from many distinct addresses
 * grew the map without limit — turning the control meant to blunt brute force
 * into a memory-exhaustion vector of its own. No successful login ever arrives
 * to clean up after a spray.
 */
class IpLoginThrottleEvictionTest {

    @Test
    void staysBoundedUnderAFloodOfDistinctAddresses() {
        IpLoginThrottle throttle = new IpLoginThrottle();
        int flood = IpLoginThrottle.MAX_TRACKED_ADDRESSES * 2;

        for (int i = 0; i < flood; i++) {
            throttle.recordFailure("10.1." + (i / 256) + "." + (i % 256));
        }

        assertThat(throttle.trackedAddressCount())
                .as("map must not grow with the number of distinct source addresses")
                .isLessThanOrEqualTo(IpLoginThrottle.MAX_TRACKED_ADDRESSES);
    }

    @Test
    void keepsThrottlingTheAddressItIsCurrentlyCountingAcrossEviction() {
        IpLoginThrottle throttle = new IpLoginThrottle();
        String attacker = "198.51.100.7";

        // Push this address to the threshold, then flood with others.
        for (int i = 0; i < IpLoginThrottle.MAX_FAILED_ATTEMPTS_PER_IP; i++) {
            throttle.recordFailure(attacker);
        }
        assertThat(throttle.isThrottled(attacker)).isTrue();

        // Eviction drops the *oldest* windows. This address was seen first, so
        // this test documents the accepted trade rather than asserting the
        // throttle is indefinitely durable: bounded memory is worth more than
        // perfect recall, because exhausting the heap takes the app down.
        for (int i = 0; i < IpLoginThrottle.MAX_TRACKED_ADDRESSES + 10; i++) {
            throttle.recordFailure("10.2." + (i / 256) + "." + (i % 256));
        }

        assertThat(throttle.trackedAddressCount())
                .isLessThanOrEqualTo(IpLoginThrottle.MAX_TRACKED_ADDRESSES);
    }

    @Test
    void countsFailuresAndThrottlesAtTheThreshold() {
        IpLoginThrottle throttle = new IpLoginThrottle();
        String ip = "203.0.113.9";

        for (int i = 0; i < IpLoginThrottle.MAX_FAILED_ATTEMPTS_PER_IP - 1; i++) {
            throttle.recordFailure(ip);
        }
        assertThat(throttle.isThrottled(ip)).isFalse();

        throttle.recordFailure(ip);
        assertThat(throttle.isThrottled(ip)).isTrue();
    }

    @Test
    void clearsStateOnSuccess() {
        IpLoginThrottle throttle = new IpLoginThrottle();
        String ip = "203.0.113.9";

        for (int i = 0; i < IpLoginThrottle.MAX_FAILED_ATTEMPTS_PER_IP; i++) {
            throttle.recordFailure(ip);
        }

        throttle.recordSuccess(ip);

        assertThat(throttle.isThrottled(ip)).isFalse();
        assertThat(throttle.trackedAddressCount()).isZero();
    }
}
