package com.eitri.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Startup-validated limits for server-side sessions. */
@ConfigurationProperties("app.security.session")
public record SessionSecurityProperties(
        Duration idleTimeout, Duration absoluteLifetime, int maximumSessions) {

    public SessionSecurityProperties {
        idleTimeout = requirePositive(idleTimeout, "idle-timeout");
        absoluteLifetime = requirePositive(absoluteLifetime, "absolute-lifetime");
        if (maximumSessions < 1) {
            throw new IllegalArgumentException("app.security.session.maximum-sessions must be positive");
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, "app.security.session." + name + " must be configured");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("app.security.session." + name + " must be positive");
        }
        return value;
    }
}
