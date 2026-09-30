package com.example.securedhello.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.example.securedhello.config.ManagementProperties;

/**
 * IM8 as-13: the Prometheus scrape credential. A configured credential admits exactly itself; with no
 * password configured the endpoint fails closed, refusing every scrape rather than opening up.
 */
class ScrapeCredentialTest {

	@Test
	void theConfiguredCredentialIsAdmittedAndNothingElse() {
		AuthenticationManager manager = ScrapeCredential
			.authenticationManager(new ManagementProperties(new ManagementProperties.Prometheus("prometheus", "synthetic-scrape-pass")));

		assertThat(manager.authenticate(token("prometheus", "synthetic-scrape-pass")).isAuthenticated()).isTrue();
		assertThatThrownBy(() -> manager.authenticate(token("prometheus", "synthetic-scrape-passx")))
			.isInstanceOf(BadCredentialsException.class);
		assertThatThrownBy(() -> manager.authenticate(token("admin", "synthetic-scrape-pass")))
			.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void withNoPasswordConfiguredEveryScrapeIsRefused() {
		AuthenticationManager manager = ScrapeCredential
			.authenticationManager(new ManagementProperties(new ManagementProperties.Prometheus("prometheus", null)));

		assertThatThrownBy(() -> manager.authenticate(token("prometheus", ""))).isInstanceOf(BadCredentialsException.class);
		assertThatThrownBy(() -> manager.authenticate(token("prometheus", "null")))
			.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void thePropertiesNeverPrintThePassword() {
		ManagementProperties properties = new ManagementProperties(
				new ManagementProperties.Prometheus("prometheus", "synthetic-scrape-pass"));

		assertThat(properties.toString()).doesNotContain("synthetic-scrape-pass");
	}

	private static UsernamePasswordAuthenticationToken token(String username, String password) {
		return UsernamePasswordAuthenticationToken.unauthenticated(username, password);
	}

}
