package com.example.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-IP login-failure throttle: {@code app.security.ip-throttle.max-attempts}
 * failures from one IP within a {@code app.security.ip-throttle.window}
 * sliding window blocks that IP from attempting any further logins (any
 * username) for {@code app.security.ip-throttle.block-duration}. Separate
 * from {@link LoginAttemptListener}'s per-account lockout: this defends
 * against an attacker spraying many different usernames from one IP, a
 * pattern per-account lockout alone can't catch since no single account ever
 * crosses its own threshold.
 *
 * <p>In-memory rather than persisted: state resets on restart, which is
 * acceptable for a throttle (as opposed to the account lockout, which must
 * survive restarts) and keeps this simple for a single-instance deployment.
 */
@Service
public class IpLoginThrottleService {

    private record IpState(int failureCount, Instant windowStart, Instant blockedUntil) {}

    private final ConcurrentHashMap<String, IpState> states = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;

    public IpLoginThrottleService(
            @Value("${app.security.ip-throttle.max-attempts}") int maxAttempts,
            @Value("${app.security.ip-throttle.window}") Duration window,
            @Value("${app.security.ip-throttle.block-duration}") Duration blockDuration) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
    }

    public void recordFailure(String ip) {
        Instant now = Instant.now();
        states.compute(ip, (key, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plus(window))) {
                // No prior state, or the sliding window has fully elapsed:
                // start counting fresh rather than accumulating forever.
                return new IpState(1, now, null);
            }

            int failureCount = existing.failureCount() + 1;
            Instant blockedUntil = failureCount >= maxAttempts ? now.plus(blockDuration) : existing.blockedUntil();
            return new IpState(failureCount, existing.windowStart(), blockedUntil);
        });
    }

    public boolean isThrottled(String ip) {
        IpState state = states.get(ip);
        return state != null && state.blockedUntil() != null && state.blockedUntil().isAfter(Instant.now());
    }

    /**
     * Test-only: clears all per-IP state. Without this, this singleton's
     * state would otherwise leak between tests that share one MockMvc IP
     * (127.0.0.1) and one cached Spring context.
     */
    public void reset() {
        states.clear();
    }
}
