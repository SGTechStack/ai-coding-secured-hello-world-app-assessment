package com.example.securedhello.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.securedhello.config.SecurityProperties;

/**
 * In-memory, per-application-instance IP throttle (documented single-instance
 * limitation — no shared store). Tracks recent login-failure timestamps per
 * client IP; an IP is throttled once its failures within the configured window
 * exceed the threshold. Independent of Account Lockout: it keys on IP, not
 * account, so an attacker failing many usernames from one source is throttled
 * without locking any legitimate user's account.
 *
 * <p>Time is read through the injected {@link Clock} so the sliding window is
 * deterministic in tests.
 */
@Service
public class IpThrottleService {

    private final Clock clock;
    private final SecurityProperties properties;
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    public IpThrottleService(Clock clock, SecurityProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** Records a login failure originating from {@code clientIp}. */
    public void recordFailure(String clientIp) {
        Deque<Instant> failures = failuresByIp.computeIfAbsent(clientIp, ip -> new ArrayDeque<>());
        synchronized (failures) {
            failures.addLast(clock.instant());
            evictExpired(failures);
        }
    }

    /**
     * @return true if {@code clientIp} has exceeded the failure threshold
     *         within the sliding window.
     */
    public boolean isThrottled(String clientIp) {
        Deque<Instant> failures = failuresByIp.get(clientIp);
        if (failures == null) {
            return false;
        }
        synchronized (failures) {
            evictExpired(failures);
            return failures.size() > properties.throttle().maxAttempts();
        }
    }

    /** Clears all throttle state. Used by tests for isolation. */
    public void reset() {
        failuresByIp.clear();
    }

    private void evictExpired(Deque<Instant> failures) {
        Instant cutoff = clock.instant().minus(properties.throttle().window());
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.pollFirst();
        }
    }
}
