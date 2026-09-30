package com.example.securedhello.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Account lockout settings.
 *
 * @param threshold consecutive wrong passwords that make an Account Locked
 * @param duration how long an Account stays Locked
 */
@Validated
@ConfigurationProperties("app.lockout")
public record LockoutProperties(@Min(1) int threshold, @NotNull Duration duration) {
}
