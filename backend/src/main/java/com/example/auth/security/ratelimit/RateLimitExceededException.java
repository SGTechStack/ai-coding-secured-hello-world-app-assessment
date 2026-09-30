package com.example.auth.security.ratelimit;

import java.time.Duration;

/**
 * Thrown by {@link FixedWindowRateLimiter#enforce(String)}; {@code GlobalExceptionHandler} turns it
 * into {@code 429} with a {@code Retry-After} header and a {@code RATE_LIMITED} body.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String limiter;
    private final Duration retryAfter;

    public RateLimitExceededException(String limiter, Duration retryAfter) {
        super("Rate limit exceeded: " + limiter);
        this.limiter = limiter;
        this.retryAfter = retryAfter;
    }

    public String getLimiter() {
        return limiter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    /** {@code Retry-After} in whole seconds, rounded up and never below 1. */
    public static long retryAfterSeconds(Duration retryAfter) {
        long seconds = (retryAfter.toMillis() + 999) / 1000;
        return Math.max(1, seconds);
    }
}
