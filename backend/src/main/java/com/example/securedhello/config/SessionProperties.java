package com.example.securedhello.config;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Session control settings.
 *
 * @param maxConcurrentPerAccount most Sessions one Account may hold at once; a new login ends the
 *        oldest beyond this
 */
@Validated
@ConfigurationProperties("app.session")
public record SessionProperties(@Min(1) int maxConcurrentPerAccount) {
}
