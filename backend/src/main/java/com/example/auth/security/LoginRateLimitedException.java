package com.example.auth.security;

import java.time.Duration;
import org.springframework.security.core.AuthenticationException;

/**
 * Raised by {@link JsonUsernamePasswordAuthenticationFilter} before the {@code
 * AuthenticationManager} runs, so a throttled attempt never reaches password comparison and --
 * because it is thrown outside the provider -- publishes no authentication-failure event: it is
 * never counted as a failed login and cannot extend a lockout. The failure handler maps it to
 * {@code 429} with {@code Retry-After}.
 */
public class LoginRateLimitedException extends AuthenticationException {

    private final String limiter;
    private final transient Duration retryAfter;

    public LoginRateLimitedException(String limiter, Duration retryAfter) {
        super("Login rate limited");
        this.limiter = limiter;
        this.retryAfter = retryAfter;
    }

    public String getLimiter() {
        return limiter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
