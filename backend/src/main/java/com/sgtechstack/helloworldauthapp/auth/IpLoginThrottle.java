package com.sgtechstack.helloworldauthapp.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-level throttle, independent of any single account's lockout state.
 * Tracks failed login attempts per source IP across all usernames tried
 * from that IP, so an attacker spraying passwords across many accounts
 * gets throttled without needing to trip any one account's lockout — and,
 * symmetrically, so failing one victim's password repeatedly from one IP
 * can't be used to lock that victim out on its own (the account-lockout
 * counter is keyed by username, this one is keyed by IP; they don't
 * interact).
 *
 * In-memory and process-local: adequate for a single-instance reference
 * app. A multi-instance deployment would need a shared store (e.g. Redis)
 * behind the same interface.
 */
@Component
public class IpLoginThrottle {

    public static final int MAX_FAILED_ATTEMPTS_PER_IP = 10;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant windowStart) {
    }

    private final Map<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    /**
     * @return true if this IP has exceeded the failure threshold within
     * the current window and should be throttled before even attempting
     * authentication.
     */
    public boolean isThrottled(String ipAddress) {
        Attempts attempts = attemptsByIp.get(ipAddress);
        if (attempts == null) {
            return false;
        }
        if (windowExpired(attempts)) {
            return false;
        }
        return attempts.count() >= MAX_FAILED_ATTEMPTS_PER_IP;
    }

    /**
     * Records a failed login attempt from this IP, starting a fresh window
     * if none is active or the previous one has expired.
     */
    public void recordFailure(String ipAddress) {
        attemptsByIp.compute(ipAddress, (ip, existing) -> {
            if (existing == null || windowExpired(existing)) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(existing.count() + 1, existing.windowStart());
        });
    }

    /**
     * Clears this IP's failure count. Called on successful login so a
     * legitimate user who mistypes a password a few times, then logs in
     * correctly, doesn't stay near the throttle threshold indefinitely.
     */
    public void recordSuccess(String ipAddress) {
        attemptsByIp.remove(ipAddress);
    }

    private boolean windowExpired(Attempts attempts) {
        return Instant.now().isAfter(attempts.windowStart().plus(WINDOW));
    }
}
