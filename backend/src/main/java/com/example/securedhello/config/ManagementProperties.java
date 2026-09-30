package com.example.securedhello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credentials for the Actuator management port (IM8 as-13). {@code prometheus} answers only to the
 * scraper's HTTP Basic credential, so the loopback binding is not its only protection. Outside the
 * {@code dev} profile the password is injected from the secrets manager and never committed or logged.
 * With no password configured the endpoint fails closed: every scrape is refused, and the application
 * still starts, because metrics are not worth an outage.
 *
 * @param prometheus the scraper's credential
 */
@ConfigurationProperties("app.management")
public record ManagementProperties(@DefaultValue Prometheus prometheus) {

	/**
	 * @param username the scraper's username
	 * @param password the scraper's password; blank refuses every scrape
	 */
	public record Prometheus(@DefaultValue("prometheus") String username, String password) {

		/** Whether a password is configured at all. */
		public boolean configured() {
			return this.password != null && !this.password.isBlank();
		}

		@Override
		public String toString() {
			return "Prometheus[username=" + this.username + ", password=***MASKED***]";
		}

	}

}
