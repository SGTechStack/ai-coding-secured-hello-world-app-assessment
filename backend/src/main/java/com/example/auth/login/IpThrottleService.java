package com.example.auth.login;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.example.auth.config.SecurityProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Service;

/**
 * Per-IP failed-login rate limiter backed by an in-memory Caffeine cache.
 * Keyed strictly by request.getRemoteAddr() — X-Forwarded-For is never
 * trusted to avoid spoofing (explicitly decided in the spec).
 *
 * Single-instance limitation: the counter resets on app restart and does not
 * synchronise across multiple instances. Accepted limitation for this assessment.
 */
@Service
public class IpThrottleService {

    private final Cache<String, AtomicLong> failureCache;
    private final long maxAttempts;

    public IpThrottleService(SecurityProperties props) {
        SecurityProperties.IpThrottleConfig cfg = props.ipThrottle();
        this.maxAttempts = cfg.maxAttempts();
        this.failureCache = Caffeine.newBuilder()
                .expireAfterWrite(cfg.windowMinutes(), TimeUnit.MINUTES)
                .build();
    }

    public boolean isThrottled(String ip) {
        AtomicLong count = failureCache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    /**
     * Atomically records one failure for the given IP.
     * computeIfAbsent is atomic in Caffeine's ConcurrentHashMap; AtomicLong
     * increment is thread-safe, so concurrent calls are race-free.
     */
    public void recordFailure(String ip) {
        failureCache.asMap().computeIfAbsent(ip, k -> new AtomicLong(0)).incrementAndGet();
    }

    /** Exposed for test setup — clears all cached counters. */
    void clearForTesting() {
        failureCache.invalidateAll();
    }
}
