package com.assessment.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiting (spec.md S6, ticket 07).
 *
 * <p>All counters are in-memory and therefore neither restart-durable nor multi-instance-safe. That
 * is a <strong>recorded limitation</strong>, not an oversight (story 1.24) — the application is
 * single-instance only.
 *
 * @param accountPerMinute per-username sliding window on login
 * @param ipPerMinute per-{@code getRemoteAddr()} sliding window
 * @param resetRequestPerMinute IP-keyed, a recorded deviation from Std:124's per-account rule
 *     because an anonymous endpoint has no account to key on
 * @param resetConfirmPerMinute IP-keyed, same reason
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    int accountPerMinute, int ipPerMinute, int resetRequestPerMinute, int resetConfirmPerMinute) {}
