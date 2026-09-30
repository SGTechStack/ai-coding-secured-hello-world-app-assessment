package com.assessment.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account lockout (spec.md S6, ticket 07).
 *
 * @param maxAttempts 5 <em>consecutive</em> failures. There is deliberately <strong>no time
 *     window</strong>: the counter decays only on a successful login.
 * @param duration 20 minutes, written to {@code locked_until}. Self-expiring, so the automatic lift
 *     needs no write and no sweep — which matters because {@code @Scheduled} is banned.
 */
@ConfigurationProperties(prefix = "app.lockout")
public record LockoutProperties(int maxAttempts, Duration duration) {}
