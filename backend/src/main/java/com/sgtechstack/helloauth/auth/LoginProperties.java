package com.sgtechstack.helloauth.auth;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.login")
public record LoginProperties(@Valid @NotNull Lockout lockout, @Valid @NotNull IpThrottle ipThrottle) {

	/**
	 * @param maxAttempts consecutive failures that lock the account
	 * @param window a failure streak is forgotten once this long has passed since its last failure
	 * @param duration how long the account stays locked
	 */
	public record Lockout(@Min(1) int maxAttempts, @NotNull Duration window, @NotNull Duration duration) {
	}

	/**
	 * @param maxFailures failed logins allowed per client IP, across all usernames, per window
	 * @param window fixed window length
	 */
	public record IpThrottle(@Min(1) int maxFailures, @NotNull Duration window) {
	}

}
