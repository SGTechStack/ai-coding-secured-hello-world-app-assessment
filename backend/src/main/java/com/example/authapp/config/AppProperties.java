package com.example.authapp.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:3000") List<String> allowedOrigins,
        @DefaultValue Admin admin,
        @DefaultValue Lockout lockout,
        @DefaultValue IpThrottle ipThrottle,
        @DefaultValue PasswordReset passwordReset) {

    public record Admin(
            @DefaultValue("admin") String username,
            @DefaultValue("admin@example.com") String email,
            String password) {

        /** Never expose the seed password through toString(). */
        @Override
        public String toString() {
            return "Admin[username=" + username + ", email=" + email + "]";
        }
    }

    public record Lockout(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("15") long cooldownMinutes) {}

    public record IpThrottle(
            @DefaultValue("10") int maxFailures,
            @DefaultValue("15") long windowMinutes) {}

    public record PasswordReset(@DefaultValue("30") long tokenTtlMinutes) {}
}
