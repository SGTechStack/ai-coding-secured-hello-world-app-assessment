package com.example.securedhello.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable thresholds for the two brute-force defences.
 *
 * <ul>
 *   <li>Account Lockout: {@code lockout.maxAttempts} consecutive failures lock
 *       an account for {@code lockout.cooldown}.</li>
 *   <li>IP Throttling: more than {@code throttle.maxAttempts} failures from one
 *       IP within {@code throttle.window} throttles that IP.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Lockout lockout, Throttle throttle) {

    public SecurityProperties {
        if (lockout == null) {
            lockout = new Lockout(5, Duration.ofMinutes(15));
        }
        if (throttle == null) {
            throttle = new Throttle(20, Duration.ofMinutes(10));
        }
    }

    public record Lockout(int maxAttempts, Duration cooldown) {
        public Lockout {
            if (maxAttempts <= 0) {
                maxAttempts = 5;
            }
            if (cooldown == null) {
                cooldown = Duration.ofMinutes(15);
            }
        }
    }

    public record Throttle(int maxAttempts, Duration window) {
        public Throttle {
            if (maxAttempts <= 0) {
                maxAttempts = 20;
            }
            if (window == null) {
                window = Duration.ofMinutes(10);
            }
        }
    }
}
