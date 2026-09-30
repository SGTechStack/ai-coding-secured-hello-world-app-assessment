package com.example.securedhello.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Session control settings.
 *
 * @param maxConcurrentPerAccount most Sessions one Account may hold at once; a new login ends the
 *        oldest beyond this
 * @param idleTimeout a Session unused for this long ends
 * @param absoluteTimeout a Session ends this long after login, however busy
 */
@Validated
@ConfigurationProperties("app.session")
public record SessionProperties(@Min(1) int maxConcurrentPerAccount, @NotNull Duration idleTimeout,
		@NotNull Duration absoluteTimeout) {
}
