package com.example.auth.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small, bounded, in-memory fixed-window counter keyed by an arbitrary string (an IP, a username,
 * an IP+username pair, ...). Once a key reaches {@code maxHits} within one {@code window}, it is
 * blocked for {@code blockDuration} (or, when that is {@link Duration#ZERO}, until the window
 * ends).
 *
 * <p>In-memory and per-instance by design (see ADR-0005): state resets on restart and is not
 * shared between instances. To stop an attacker from growing the map without limit (e.g. by
 * rotating source addresses), expired entries are swept whenever the map reaches {@code maxKeys};
 * if it is still full after the sweep, the new key is treated as blocked (fail closed) rather than
 * evicting a live entry an attacker could otherwise reset on demand.
 */
public class FixedWindowRateLimiter {

    /** Outcome of a check: whether the key is blocked, and for how long if so. */
    public record Decision(boolean blocked, Duration retryAfter) {

        static final Decision ALLOWED = new Decision(false, Duration.ZERO);
    }

    private record Entry(int hits, Instant windowStart, Instant blockedUntil) {

        boolean isExpired(Instant now, Duration window) {
            boolean windowOver = !now.isBefore(windowStart.plus(window));
            boolean blockOver = blockedUntil == null || !now.isBefore(blockedUntil);
            return windowOver && blockOver;
        }
    }

    private final String name;
    private final int maxHits;
    private final Duration window;
    private final Duration blockDuration;
    private final int maxKeys;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(String name, int maxHits, Duration window, Duration blockDuration, int maxKeys) {
        this(name, maxHits, window, blockDuration, maxKeys, Clock.systemUTC());
    }

    FixedWindowRateLimiter(
            String name, int maxHits, Duration window, Duration blockDuration, int maxKeys, Clock clock) {
        this.name = name;
        this.maxHits = maxHits;
        this.window = window;
        this.blockDuration = blockDuration;
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    public String name() {
        return name;
    }

    /** Checks whether {@code key} is currently blocked, without counting a hit. */
    public Decision check(String key) {
        Instant now = clock.instant();
        Entry entry = entries.get(key);
        if (entry == null || entry.isExpired(now, window)) {
            return Decision.ALLOWED;
        }
        return decisionFor(entry, now);
    }

    /**
     * Counts one hit against {@code key} and returns the resulting decision. The hit that reaches
     * {@code maxHits} is itself still allowed; the block applies from the next check onwards.
     */
    public Decision record(String key) {
        Instant now = clock.instant();
        if (!entries.containsKey(key) && entries.size() >= maxKeys) {
            sweep(now);
            if (entries.size() >= maxKeys) {
                return new Decision(true, window);
            }
        }
        Entry updated = entries.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now, window)) {
                existing = new Entry(0, now, null);
            }
            int hits = existing.hits() + 1;
            Instant blockedUntil = existing.blockedUntil();
            if (hits >= maxHits && blockedUntil == null) {
                blockedUntil = blockDuration.isZero()
                        ? existing.windowStart().plus(window)
                        : now.plus(blockDuration);
            }
            return new Entry(hits, existing.windowStart(), blockedUntil);
        });
        return updated.hits() > maxHits ? decisionFor(updated, now) : Decision.ALLOWED;
    }

    /** Records a hit and reports whether this request should be rejected. */
    public Decision tryAcquire(String key) {
        Decision current = check(key);
        if (current.blocked()) {
            return current;
        }
        return record(key);
    }

    /** {@link #tryAcquire} that throws {@link RateLimitExceededException} when the request is rejected. */
    public void enforce(String key) {
        Decision decision = tryAcquire(key);
        if (decision.blocked()) {
            throw new RateLimitExceededException(name, decision.retryAfter());
        }
    }

    public void clear(String key) {
        entries.remove(key);
    }

    /** Test-only: drops all state so tests sharing one Spring context don't leak into each other. */
    public void reset() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private Decision decisionFor(Entry entry, Instant now) {
        if (entry.blockedUntil() != null && now.isBefore(entry.blockedUntil())) {
            return new Decision(true, Duration.between(now, entry.blockedUntil()));
        }
        return Decision.ALLOWED;
    }

    private void sweep(Instant now) {
        entries.entrySet().removeIf(e -> e.getValue().isExpired(now, window));
    }
}
