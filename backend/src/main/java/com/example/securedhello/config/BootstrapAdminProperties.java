package com.example.securedhello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Bootstrap Admin's credentials and email, used only when no Account holds the Admin role.
 * Outside the {@code dev} profile every value is required and is injected from the secrets manager
 * (for example {@code APP_BOOTSTRAPADMIN_PASSWORD}); it is never committed or logged. In {@code dev}
 * a missing value falls back to a local default. The initializer, not binding, decides what is
 * missing, because the rule depends on the profile.
 *
 * @param username the Bootstrap Admin's username
 * @param password the Bootstrap Admin's initial password; it must be changed at first login
 * @param email the Bootstrap Admin's email
 */
@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String email) {

	@Override
	public String toString() {
		return "BootstrapAdminProperties[***MASKED***]";
	}

}
