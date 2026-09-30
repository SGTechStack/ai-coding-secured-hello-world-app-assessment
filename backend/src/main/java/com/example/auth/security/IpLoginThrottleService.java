package com.example.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>It also caps how many failures one IP may aim at a single username: one
 * short of the account-lockout threshold ({@code
 * app.security.lockout.max-attempts}) within the lockout window, after which
 * the IP is blocked for at least that window. One source can therefore never
 * supply all the failures an account lockout needs, so it cannot lock a
 * legitimate user out by itself (Story 3) -- it gets throttled instead, and
 * the user can still log in from anywhere else. The cap is keyed on the
 * submitted username whether or not such an account exists, so hitting it
 * reveals nothing about account existence.
 *
 * <p>In-memory rather than persisted: state resets on restart, which is
 * acceptable for a throttle (as opposed to the account lockout, which must
 * survive restarts) and keeps this simple for a single-instance deployment.
 */
@Service
public class IpLoginThrottleService {

    private record IpState(
            int failureCount, Instant windowStart, Instant blockedUntil, Map<String, List<Instant>> failuresByUsername) {}

    private final ConcurrentHashMap<String, IpState> states = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final int maxAttemptsPerUsername;
    private final Duration perUsernameWindow;
    private final Duration perUsernameBlockDuration;

    public IpLoginThrottleService(
            @Value("${app.security.ip-throttle.max-attempts}") int maxAttempts,
            @Value("${app.security.ip-throttle.window}") Duration window,
            @Value("${app.security.ip-throttle.block-duration}") Duration blockDuration,
            @Value("${app.security.lockout.max-attempts}") int lockoutMaxAttempts,
            @Value("${app.security.lockout.window}") Duration lockoutWindow) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
        this.maxAttemptsPerUsername = Math.max(1, lockoutMaxAttempts - 1);
        this.perUsernameWindow = lockoutWindow;
        // Never shorter than the lockout window: once the block lifts, every
        // failure this IP already put on that account must have aged out of
        // the account's own counting window.
        this.perUsernameBlockDuration = blockDuration.compareTo(lockoutWindow) >= 0 ? blockDuration : lockoutWindow;
    }

    public void recordFailure(String ip, String username) {
        Instant now = Instant.now();
        String usernameKey = username == null ? "" : username;
        states.compute(ip, (key, existing) -> {
            Map<String, List<Instant>> failuresByUsername = recentFailuresByUsername(existing, now);
            List<Instant> failuresForUsername = failuresByUsername.computeIfAbsent(usernameKey, k -> new ArrayList<>());
            failuresForUsername.add(now);

            int failureCount;
            Instant windowStart;
            Instant blockedUntil;
            if (existing == null || now.isAfter(existing.windowStart().plus(window))) {
                // No prior state, or the sliding window has fully elapsed:
                // start counting fresh rather than accumulating forever.
                failureCount = 1;
                windowStart = now;
                blockedUntil = null;
            } else {
                failureCount = existing.failureCount() + 1;
                windowStart = existing.windowStart();
                blockedUntil = existing.blockedUntil();
            }

            if (failureCount >= maxAttempts) {
                blockedUntil = now.plus(blockDuration);
            }
            if (failuresForUsername.size() >= maxAttemptsPerUsername) {
                blockedUntil = later(blockedUntil, now.plus(perUsernameBlockDuration));
            }
            return new IpState(failureCount, windowStart, blockedUntil, failuresByUsername);
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

    /**
     * Copies the per-username failure times still inside the lockout window,
     * dropping everything older -- a true sliding window (unlike the overall
     * counter above), and what keeps this map bounded per IP.
     */
    private Map<String, List<Instant>> recentFailuresByUsername(IpState existing, Instant now) {
        Map<String, List<Instant>> recent = new HashMap<>();
        if (existing == null) {
            return recent;
        }
        Instant cutoff = now.minus(perUsernameWindow);
        existing.failuresByUsername().forEach((username, failures) -> {
            List<Instant> stillRecent = new ArrayList<>(
                    failures.stream().filter(failure -> failure.isAfter(cutoff)).toList());
            if (!stillRecent.isEmpty()) {
                recent.put(username, stillRecent);
            }
        });
        return recent;
    }

    private static Instant later(Instant first, Instant second) {
        return first == null || second.isAfter(first) ? second : first;
    }
}
