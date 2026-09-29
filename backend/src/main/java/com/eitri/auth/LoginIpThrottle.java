package com.eitri.auth;

import com.eitri.config.LoginSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Per-source-IP throttle of failed logins, independent of account lockout: a sliding log of counted
 * failure times per address, held in a bounded in-memory cache. An address with the maximum number of
 * failures inside the window is refused until its oldest counted failure leaves the window.
 *
 * <p>Under cache pressure, addresses whose failures have all expired are reclaimed first; otherwise the
 * address whose last failure is oldest is evicted. A source controlling more addresses than the bound
 * can already spread its guesses beyond any per-IP limit, so eviction does not weaken the control.
 */
@Component
final class LoginIpThrottle {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final int cacheBound;
    // Ordered by last recorded failure (re-inserted on each one), so the eldest entry failed least recently.
    private final LinkedHashMap<String, Deque<Instant>> failures = new LinkedHashMap<>();

    @Autowired
    LoginIpThrottle(Clock clock, LoginSecurityProperties properties) {
        this(clock, properties.ipThrottleMaxFailures(), properties.ipThrottleWindow(),
                properties.ipThrottleCacheBound());
    }

    LoginIpThrottle(Clock clock, int maxFailures, Duration window, int cacheBound) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.cacheBound = cacheBound;
    }

    /** Whether a login from {@code address} may reach authentication. Does not count as a failure. */
    synchronized Decision check(String address) {
        Instant now = clock.instant();
        Deque<Instant> log = failures.get(key(address));
        if (log == null) {
            return Decision.permitted();
        }
        prune(log, now);
        if (log.size() < maxFailures) {
            return Decision.permitted();
        }
        Duration remaining = Duration.between(now, log.peekFirst().plus(window));
        long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
        return Decision.rejected(Math.max(1L, seconds));
    }

    /** Counts a login from {@code address} that reached authentication and failed. */
    synchronized void recordFailure(String address) {
        Instant now = clock.instant();
        String key = key(address);
        Deque<Instant> log = failures.remove(key);
        if (log == null) {
            makeRoom(now);
            log = new ArrayDeque<>();
        }
        failures.put(key, log);
        prune(log, now);
        log.addLast(now);
        // Only the newest maxFailures entries can ever matter for a decision.
        while (log.size() > maxFailures) {
            log.removeFirst();
        }
    }

    synchronized int trackedAddressCount() {
        return failures.size();
    }

    private void makeRoom(Instant now) {
        if (failures.size() < cacheBound) {
            return;
        }
        Iterator<Map.Entry<String, Deque<Instant>>> entries = failures.entrySet().iterator();
        while (entries.hasNext()) {
            Deque<Instant> log = entries.next().getValue();
            prune(log, now);
            if (log.isEmpty()) {
                entries.remove();
            }
        }
        if (failures.size() >= cacheBound) {
            Iterator<String> eldest = failures.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    private void prune(Deque<Instant> log, Instant now) {
        Instant windowStart = now.minus(window);
        while (!log.isEmpty() && !log.peekFirst().isAfter(windowStart)) {
            log.removeFirst();
        }
    }

    private static String key(String address) {
        return Objects.requireNonNull(address, "address");
    }

    record Decision(boolean allowed, long retryAfterSeconds) {

        static Decision permitted() {
            return new Decision(true, 0);
        }

        static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
