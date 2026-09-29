package com.example.auth.passwordreset;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.example.auth.config.PasswordResetProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Service;

/**
 * Per-IP rate limiter for password-reset requests. Same Caffeine mechanism as
 * the login IP throttle but a separate counter — reset-request volume and
 * failed-login volume are distinct concerns and must not conflate.
 *
 * Keyed strictly on source IP (never email), so it introduces no
 * account-existence leakage (Story 47). Every request counts (not just
 * failures), because the goal is to blunt reset-request flooding.
 *
 * Single-instance limitation: in-memory, resets on restart, not shared across
 * instances. Accepted for this assessment (documented in the spec).
 */
@Service
public class ResetRequestRateLimiter {

    private final Cache<String, AtomicLong> requestCache;
    private final long maxAttempts;

    public ResetRequestRateLimiter(PasswordResetProperties props) {
        PasswordResetProperties.RequestRateLimit cfg = props.requestRateLimit();
        this.maxAttempts = cfg.maxAttempts();
        this.requestCache = Caffeine.newBuilder()
                .expireAfterWrite(cfg.windowMinutes(), TimeUnit.MINUTES)
                .build();
    }

    public boolean isThrottled(String ip) {
        AtomicLong count = requestCache.getIfPresent(ip);
        return count != null && count.get() >= maxAttempts;
    }

    public void recordRequest(String ip) {
        requestCache.asMap().computeIfAbsent(ip, k -> new AtomicLong(0)).incrementAndGet();
    }

    /** Exposed for test setup — clears all cached counters. */
    void clearForTesting() {
        requestCache.invalidateAll();
    }
}
