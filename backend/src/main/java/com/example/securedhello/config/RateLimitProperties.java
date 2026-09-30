package com.example.securedhello.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limits.
 *
 * @param login login attempts per username
 * @param registration registrations per client address
 * @param resetRequestEmail reset requests per email address
 * @param resetRequestIp reset requests per client address
 * @param resetConfirm reset confirmations per client address (never per Account, ADR 0001)
 * @param clientEvents reports from the SPA per client address
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@Valid @NotNull Limit login, @Valid @NotNull Limit registration,
		@Valid @NotNull Limit resetRequestEmail, @Valid @NotNull Limit resetRequestIp,
		@Valid @NotNull Limit resetConfirm, @Valid @NotNull Limit clientEvents) {

	/**
	 * At most {@code capacity} attempts per {@code period} for one key.
	 *
	 * @param capacity attempts allowed per period
	 * @param period the period, refilled evenly
	 */
	public record Limit(@Min(1) int capacity, @NotNull Duration period) {
	}

}
