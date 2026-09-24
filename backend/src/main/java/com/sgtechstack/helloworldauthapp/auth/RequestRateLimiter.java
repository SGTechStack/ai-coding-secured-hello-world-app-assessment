package com.sgtechstack.helloworldauthapp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts <em>requests</em> per caller against a named limit, as opposed to
 * {@link IpLoginThrottle}, which counts <em>failures</em>.
 *
 * <p>That distinction is why this is a separate component rather than a
 * generalisation of the existing throttle. Login has a natural notion of
 * failure to count, and counting only failures is right there: a legitimate
 * user logging in repeatedly should never be throttled. Registration and
 * password reset have no equivalent — every call succeeds from the caller's
 * point of view, and the cost being defended against is the work the call
 * performs regardless of outcome. A BCrypt hash on an unauthenticated endpoint
 * is roughly 100ms of server CPU for a rounding error of client effort, and a
 * reset request writes a row and (once real mail exists) sends a message to
 * somebody's inbox.
 *
 * <p>Bounded the same way as {@link IpLoginThrottle}, and for the same reason:
 * a limiter that grows a map per distinct source address is itself a
 * memory-exhaustion vector.
 */
@Component
public class RequestRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimiter.class);

    public static final int MAX_TRACKED_KEYS = 20_000;

    private record Counter(int count, Instant windowStart) {
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * Records one request and reports whether the caller has now exceeded the
     * limit.
     *
     * @param limitName identifies which limit is being applied, so the same
     *                  caller is counted separately per endpoint
     * @param clientKey the caller, normally a resolved client address
     * @return true if this request should be rejected
     */
    public boolean exceedsLimit(String limitName, String clientKey, int maxRequests, Duration window) {
        String key = limitName + "|" + clientKey;
        Instant now = Instant.now();

        Counter updated = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(window))) {
                return new Counter(1, now);
            }
            return new Counter(existing.count() + 1, existing.windowStart());
        });

        evictIfOversized(now);

        boolean exceeded = updated.count() > maxRequests;
        if (exceeded) {
            log.info("Rate limit exceeded limit={} count={} max={}", limitName, updated.count(), maxRequests);
        }

        return exceeded;
    }

    private void evictIfOversized(Instant now) {
        if (counters.size() <= MAX_TRACKED_KEYS) {
            return;
        }

        // Windows vary per limit, so "expired" cannot be decided here without
        // the window. Use the longest plausible window as a conservative floor:
        // anything older than a day is certainly finished.
        counters.entrySet().removeIf(entry ->
                now.isAfter(entry.getValue().windowStart().plus(Duration.ofDays(1))));

        int excess = counters.size() - MAX_TRACKED_KEYS;
        if (excess <= 0) {
            return;
        }

        log.warn("Rate limiter above {} tracked keys; dropping {} oldest", MAX_TRACKED_KEYS, excess);

        counters.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().windowStart()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(counters::remove);
    }

    /** Exposed so the eviction bound can be asserted. */
    int trackedKeyCount() {
        return counters.size();
    }

    /**
     * Discards all counters, so every caller starts from a clean window.
     *
     * <p>Public because it is also a plausible operational action — clearing
     * limits after a false-positive lockout of a shared egress address, say —
     * not only a test seam.
     */
    public void reset() {
        counters.clear();
    }
}
