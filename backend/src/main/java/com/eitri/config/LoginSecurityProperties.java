package com.eitri.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Startup-validated account lockout and per-IP login throttle settings. */
@ConfigurationProperties("app.security.login")
public record LoginSecurityProperties(
        int lockoutThreshold,
        Duration lockoutDuration,
        Duration lockoutWindow,
        int ipThrottleMaxFailures,
        Duration ipThrottleWindow,
        int ipThrottleCacheBound) {

    public LoginSecurityProperties {
        requirePositive(lockoutThreshold, "lockout-threshold");
        requirePositive(lockoutDuration, "lockout-duration");
        requirePositive(lockoutWindow, "lockout-window");
        requirePositive(ipThrottleMaxFailures, "ip-throttle-max-failures");
        requirePositive(ipThrottleWindow, "ip-throttle-window");
        requirePositive(ipThrottleCacheBound, "ip-throttle-cache-bound");
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException("app.security.login." + name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, "app.security.login." + name + " must be configured");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("app.security.login." + name + " must be positive");
        }
    }
}
