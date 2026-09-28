package com.example.helloworldauth.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-IP failed-login throttle (ticket 07, Story 3). Independent of
 * per-account lockout: it counts failures from ONE client IP across ANY
 * usernames within a sliding window, so an attacker cannot lock out a legitimate
 * user by spraying that user's password from a single source, and broad spraying
 * across many accounts is blunted regardless of any single account's state.
 *
 * <p>NOTE: this is a demo. The counter lives only in this process's heap, so it
 * does not survive a restart and is not shared across instances. A production
 * deployment behind a load balancer would back this with a shared store
 * (e.g. Redis) keyed by client IP, and would resolve the true client IP from a
 * trusted {@code X-Forwarded-For} chain rather than the socket address.
 */
@Component
public class IpThrottlingService {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final int maxFailures;
    private final Duration window;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    public IpThrottlingService(
            @Value("${app.security.throttle.max-failures:10}") int maxFailures,
            @Value("${app.security.throttle.window-minutes:15}") long windowMinutes) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /**
     * Checked in the login flow BEFORE credential verification. Throws
     * {@link TooManyRequestsException} (HTTP 429) once this IP has exceeded the
     * failure threshold inside the current window. A null/blank IP is not
     * throttled (nothing to key on).
     */
    public void checkAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        Window w = counters.get(ip);
        if (w != null && w.isCurrent() && w.count >= maxFailures) {
            audit.info("ip throttled ip={} failures={}", ip, w.count);
            throw new TooManyRequestsException();
        }
    }

    /**
     * Records a failed attempt from this IP. Starts a fresh window when there is
     * none or the previous one has expired.
     */
    public void recordFailure(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        counters.compute(ip, (key, existing) -> {
            if (existing == null || !existing.isCurrent()) {
                return new Window(Instant.now(), 1);
            }
            existing.count++;
            return existing;
        });
    }

    private final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start, int count) {
            this.start = start;
            this.count = count;
        }

        private boolean isCurrent() {
            return Instant.now().isBefore(start.plus(window));
        }
    }
}
