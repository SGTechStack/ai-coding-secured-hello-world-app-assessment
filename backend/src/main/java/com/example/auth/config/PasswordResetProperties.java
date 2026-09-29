package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password-reset configuration bound from app.reset.* — token lifetime and the
 * per-IP request rate limit. The rate limit is keyed on source IP only (never
 * email), so it cannot leak account existence (Story 47).
 */
@ConfigurationProperties(prefix = "app.reset")
public record PasswordResetProperties(int tokenTtlMinutes, RequestRateLimit requestRateLimit) {

    public record RequestRateLimit(int maxAttempts, int windowMinutes) {}
}
