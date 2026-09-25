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

    private static final Logger log = LoggerFactory.getLogger(IpLoginThrottle.class);

    public static final int MAX_FAILED_ATTEMPTS_PER_IP = 10;

    /**
     * Deliberately much shorter than {@link LockoutPolicy#LOCKOUT_DURATION}.
     * This control exists to blunt a burst of rapid-fire requests from one
     * address (e.g. a spray script), not to ban that address for as long as a
     * targeted account stays locked. A short cooldown also limits collateral
     * impact on legitimate users who happen to share the address (NAT,
     * corporate proxy, CGNAT) with whoever tripped it.
     */
    public static final Duration WINDOW = Duration.ofSeconds(5);

    /**
     * Ceiling on how many source addresses are tracked at once. Reached only
     * under a flood of distinct addresses, which is precisely when the map must
     * not be allowed to keep growing.
     */
    public static final int MAX_TRACKED_ADDRESSES = 10_000;

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

        evictIfOversized();
    }

    /**
     * Keeps the map bounded.
     *
     * <p>Entries were previously removed only on a successful login from the
     * same address, so an attacker failing one login each from many distinct
     * addresses grew the map without limit — turning the control meant to blunt
     * brute force into a memory-exhaustion vector of its own.
     *
     * <p>Expired windows are swept first, which is free in the sense that they
     * carry no live state. If that is not enough, the oldest windows are
     * dropped. That trade is deliberate: forgetting throttle state for the
     * least-recently-seen addresses is recoverable, whereas exhausting the heap
     * takes the whole application down.
     */
    private void evictIfOversized() {
        if (attemptsByIp.size() <= MAX_TRACKED_ADDRESSES) {
            return;
        }

        attemptsByIp.entrySet().removeIf(entry -> windowExpired(entry.getValue()));

        int excess = attemptsByIp.size() - MAX_TRACKED_ADDRESSES;
        if (excess <= 0) {
            return;
        }

        log.warn("Throttle map above {} entries after sweeping expired windows; dropping {} oldest",
                MAX_TRACKED_ADDRESSES, excess);

        attemptsByIp.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().windowStart()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(attemptsByIp::remove);
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

    /**
     * How many source addresses are currently tracked. Exposed so the eviction
     * bound can be asserted, since unbounded growth is otherwise only visible
     * as heap exhaustion.
     */
    int trackedAddressCount() {
        return attemptsByIp.size();
    }
}
