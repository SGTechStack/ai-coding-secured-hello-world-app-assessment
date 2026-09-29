package com.example.securedhello.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The key for {@code source.ip_hash}, the HMAC-SHA-256 of a client address in audit events. Outside
 * the {@code dev} profile it is injected from the secrets manager and never committed or logged;
 * startup fails without it. Rotating it only breaks correlation of hashes across the rotation point.
 *
 * @param key the HMAC key
 */
@Validated
@ConfigurationProperties("app.ip-hash")
public record IpHashProperties(
		@NotBlank(message = "app.ip-hash.key must be injected from the secrets manager") String key) {

	@Override
	public String toString() {
		return "IpHashProperties[key=***MASKED***]";
	}

}
