package com.sgtechstack.helloauth.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the first admin, used only while no ADMIN account exists. Validated at use in
 * {@link AdminBootstrap}, because the password is legitimately absent once an admin exists.
 */
@ConfigurationProperties("app.admin")
public record AdminBootstrapProperties(String username, String email, String password) {

	@Override
	public String toString() {
		return "AdminBootstrapProperties[username=" + this.username + ", email=" + this.email
				+ ", password=<redacted>]";
	}

}
