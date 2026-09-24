package com.example.helloauth.auth;

import com.example.helloauth.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Per-IP sliding-window throttles — two independent Caffeine caches sharing
 * the same window and {@code maxEntries} bound:
 *
 * <ul>
 *   <li><b>Failure bucket</b> ({@link #isThrottled}/{@link #recordFailure}) —
 *       the outer layer of the two-layer brute-force defense (ticket 11,
 *       ratified in ticket 03's research). Counts failed logins only, and is
 *       checked inside {@link LoginService} before anything that could move
 *       account state.</li>
 *   <li><b>Anonymous-request bucket</b> ({@link #isAnonThrottled}/
 *       {@link #recordAnonRequest}) — a blanket budget for the
 *       unauthenticated mutation endpoints ({@code register},
 *       {@code password-reset/request}) added for security-review F-04.
 *       Every hit counts, success or failure: the endpoint itself is the
 *       resource being protected (user rows + BCrypt work, reset-token
 *       rows). Enforced by the controllers, which check first, record, then
 *       call the service — the same gate-first ordering as the login path.</li>
 * </ul>
 *
 * <p>Backing-store properties shared by both caches:
 *
 * <ul>
 *   <li><b>Semantics live in the value, not the cache.</b> Each bucket is a
 *       {@code (count, lastEvent)} pair evaluated against the injected
 *       {@link Clock}. Caffeine's own expiry ({@code expireAfterAccess}) is
 *       memory hygiene only — its ticker is never consulted for correctness,
 *       so tests advance the {@code Clock} and never touch the cache.</li>
 *   <li><b>No reset on success.</b> A bucket decays purely by sliding-window
 *       expiry — a legitimate user's success on a shared egress IP must not
 *       launder an attacker's accumulated failures (co-tenancy bypass). For
 *       the anonymous bucket, "no reset" is literal: the request is the
 *       countable event, whatever its outcome.</li>
 * </ul>
 *
 * The key is {@code HttpServletRequest.getRemoteAddr()} — never
 * {@code X-Forwarded-For}, which a client can rotate freely when no stripping
 * proxy fronts the app (documented deployment assumption:
 * {@code server.forward-headers-strategy} when proxied).
 */
@Service
public class IpThrottleService {

    private final Cache<String, SlidingWindowBucket> failureBuckets;
    private final Cache<String, SlidingWindowBucket> anonBuckets;
    private final Clock clock;
    private final AppProperties properties;

    public IpThrottleService(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.properties = properties;
        this.failureBuckets = newBucketCache(properties);
        this.anonBuckets = newBucketCache(properties);
    }

    private static Cache<String, SlidingWindowBucket> newBucketCache(
            AppProperties properties) {
        return Caffeine.newBuilder()
            .maximumSize(properties.getIpThrottle().getMaxEntries())
            .expireAfterAccess(properties.getIpThrottle().getWindow().multipliedBy(2))
            .build();
    }

    /** {@code true} when the IP has accumulated ≥ max-failures inside its window. */
    public boolean isThrottled(String ip) {
        SlidingWindowBucket bucket = failureBuckets.getIfPresent(ip);
        return bucket != null && bucket.isThrottled(
            clock.instant(), window(), maxFailures());
    }

    /** Records one failed attempt for the IP, starting a fresh window after decay. */
    public void recordFailure(String ip) {
        record(failureBuckets, ip);
    }

    /**
     * {@code true} when the IP has hit ≥ anon-max-requests anonymous
     * mutations inside its window. Independent of {@link #isThrottled}:
     * failed logins don't burn the request budget and register/reset hits
     * don't feed the login-failure count.
     */
    public boolean isAnonThrottled(String ip) {
        SlidingWindowBucket bucket = anonBuckets.getIfPresent(ip);
        return bucket != null && bucket.isThrottled(
            clock.instant(), window(), anonMaxRequests());
    }

    /**
     * Records one anonymous-mutation hit for the IP — every call counts,
     * whatever the outcome.
     */
    public void recordAnonRequest(String ip) {
        record(anonBuckets, ip);
    }

    /** Test/maintenance hook — drops every bucket in both caches. */
    public void clear() {
        failureBuckets.invalidateAll();
        anonBuckets.invalidateAll();
    }

    private void record(Cache<String, SlidingWindowBucket> cache, String ip) {
        // asMap().compute keeps the read-modify-write atomic per key.
        cache.asMap().compute(ip, (key, existing) -> {
            SlidingWindowBucket bucket =
                existing != null ? existing : new SlidingWindowBucket();
            bucket.record(clock.instant(), window());
            return bucket;
        });
    }

    private Duration window() {
        return properties.getIpThrottle().getWindow();
    }

    private int maxFailures() {
        return properties.getIpThrottle().getMaxFailures();
    }

    private int anonMaxRequests() {
        return properties.getIpThrottle().getAnonMaxRequests();
    }

    /**
     * Sliding window anchored at the most recent event: each new event
     * re-anchors, so a sustained burst stays hot and a quiet period of one
     * full window decays the count to zero. Serves both caches — the
     * count-and-anchor semantics are identical whether the event is a
     * failed login or an anonymous request.
     */
    static final class SlidingWindowBucket {

        private int events;
        private Instant lastEventAt;

        void record(Instant now, Duration window) {
            if (lastEventAt != null && lastEventAt.plus(window).isBefore(now)) {
                events = 0;
            }
            events++;
            lastEventAt = now;
        }

        boolean isThrottled(Instant now, Duration window, int limit) {
            return lastEventAt != null
                && !lastEventAt.plus(window).isBefore(now)
                && events >= limit;
        }
    }
}
