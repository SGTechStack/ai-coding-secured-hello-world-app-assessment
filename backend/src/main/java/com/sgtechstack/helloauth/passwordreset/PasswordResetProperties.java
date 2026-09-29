package com.sgtechstack.helloauth.passwordreset;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param tokenTtl how long a reset link stays valid (the PRD asks for 15-30 minutes)
 * @param resetUrl frontend page that completes the reset; the token is appended as a URL fragment
 * @param ipThrottle limits reset requests per client IP, against email flooding
 */
@Validated
@ConfigurationProperties("app.password-reset")
public record PasswordResetProperties(@NotNull Duration tokenTtl, @NotNull URI resetUrl,
		@Valid @NotNull IpThrottle ipThrottle) {

	public record IpThrottle(@Min(1) int maxRequests, @NotNull Duration window) {
	}

}
