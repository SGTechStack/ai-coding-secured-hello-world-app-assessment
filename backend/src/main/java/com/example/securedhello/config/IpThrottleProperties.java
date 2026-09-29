package com.example.securedhello.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.hibernate.validator.constraints.time.DurationMin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * IP Throttle settings.
 *
 * @param threshold failed logins from one client address, within the window, that block it
 * @param window how far back failed logins are counted
 * @param blockDuration how long a blocked address stays blocked
 */
@Validated
@ConfigurationProperties("app.ip-throttle")
public record IpThrottleProperties(@Min(1) int threshold, @NotNull @DurationMin(millis = 1) Duration window,
		@NotNull @DurationMin(millis = 1) Duration blockDuration) {
}
