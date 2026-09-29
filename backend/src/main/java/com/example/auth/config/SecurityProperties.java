package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable thresholds for account lockout and per-IP throttling.
 * Bound from app.security.* in application.yml.
 * Using small values in test @TestPropertySource overrides keeps the
 * Clock seam practical for integration tests.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(LockoutConfig lockout, IpThrottleConfig ipThrottle) {

    public record LockoutConfig(int maxAttempts, int durationMinutes) {}

    public record IpThrottleConfig(int maxAttempts, int windowMinutes) {}
}
