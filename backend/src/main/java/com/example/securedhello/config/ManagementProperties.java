package com.example.securedhello.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Credentials for the Actuator management port (IM8 as-13). {@code prometheus} answers only to the
 * scraper's HTTP Basic credential, so the loopback binding is not its only protection. Outside the
 * {@code dev} profile the password is injected from the secrets manager and never committed or logged;
 * startup fails without it, as it does without the {@code source.ip_hash} key. {@code health} needs no
 * credential.
 *
 * @param prometheus the scraper's credential
 */
@Validated
@ConfigurationProperties("app.management")
public record ManagementProperties(@Valid @DefaultValue Prometheus prometheus) {

	/**
	 * @param username the scraper's username
	 * @param password the scraper's password
	 */
	public record Prometheus(@DefaultValue("prometheus") String username,
			@NotBlank(message = PASSWORD_REQUIRED) String password) {

		static final String PASSWORD_REQUIRED = "app.management.prometheus.password"
				+ " must be injected from the secrets manager";

		/** Whether a password is configured at all; startup already refuses a blank one. */
		public boolean configured() {
			return this.password != null && !this.password.isBlank();
		}

		@Override
		public String toString() {
			return "Prometheus[username=" + this.username + ", password=***MASKED***]";
		}

	}

}
