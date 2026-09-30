package com.assessment.securedhelloworld.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-level login throttling (PRD Story 3): independent of any single account's lockout state, so
 * an attacker cannot lock a legitimate user out merely by failing that user's password from one
 * source. Deliberately in-memory only (no new dependency, no DB table) — a sliding window of
 * failure timestamps per source IP, purged lazily on each check.
 * <p>
 * This is a single-instance, in-memory implementation; a multi-instance deployment would need a
 * shared store (e.g. Redis) for this to hold across nodes, which is out of scope for this ticket.
 */
@Service
public class IpLoginThrottleService {

    private final ConcurrentHashMap<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long windowMinutes;

    public IpLoginThrottleService(@Value("${app.security.ip-throttle.max-failures}") int maxFailures,
                                   @Value("${app.security.ip-throttle.window-minutes}") long windowMinutes) {
        this.maxFailures = maxFailures;
        this.windowMinutes = windowMinutes;
    }

    /** True once the given IP has accumulated {@code max-failures} or more failures within the window. */
    public boolean isThrottled(String ip) {
        Deque<Instant> timestamps = failuresByIp.get(ip);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            purgeExpired(timestamps);
            return timestamps.size() >= maxFailures;
        }
    }

    /** Records a failed login attempt from {@code ip}, regardless of the target username's validity or lock state. */
    public void recordFailure(String ip) {
        Deque<Instant> timestamps = failuresByIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            purgeExpired(timestamps);
            timestamps.addLast(Instant.now());
        }
    }

    private void purgeExpired(Deque<Instant> timestamps) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }
    }
}
