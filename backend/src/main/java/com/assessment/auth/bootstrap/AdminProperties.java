package com.assessment.auth.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The initial administrator (spec.md S9).
 *
 * <p>{@code password} binds from {@code APP_ADMIN_PASSWORD} with an empty default <em>only</em> so
 * that {@code AdminBootstrapRunner} can fail startup with an explicit message. It never falls back
 * to a value.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String password, String email) {}
