package com.assessment.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Session policy (spec.md S4, ticket 13).
 *
 * @param absoluteTimeout 8h, enforced by {@code AbsoluteSessionTimeoutFilter} because there is no
 *     configuration key for it. The 15m <em>idle</em> timeout is {@code server.servlet.session
 *     .timeout} and is the container's job.
 * @param maxConcurrentSessions 1, enforced through {@code SpringSessionBackedSessionRegistry} —
 *     {@code SessionRegistryImpl} is in-memory and would violate Std:409's "across requests and
 *     restarts".
 */
@ConfigurationProperties(prefix = "app.session-policy")
public record SessionPolicyProperties(Duration absoluteTimeout, int maxConcurrentSessions) {}
