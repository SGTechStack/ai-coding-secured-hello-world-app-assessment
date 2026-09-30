package com.example.securedhello.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.example.securedhello.config.ManagementProperties;

/**
 * Checks the Prometheus scraper's HTTP Basic credential on the management port (IM8 as-13). The
 * scraper is a machine, not an Account, so it has no row, no lockout and no audit trail; it is one
 * configured credential compared in constant time. With no password configured nothing is admitted.
 */
final class ScrapeCredential {

	/** The single authority a successful scrape credential carries. */
	static final String SCRAPER = "SCRAPER";

	private ScrapeCredential() {
	}

	static AuthenticationManager authenticationManager(ManagementProperties properties) {
		ManagementProperties.Prometheus expected = properties.prometheus();
		return (authentication) -> {
			if (!expected.configured()) {
				throw new BadCredentialsException("No scrape credential is configured");
			}
			String username = String.valueOf(authentication.getPrincipal());
			String password = String.valueOf(authentication.getCredentials());
			// Both halves are always compared, so the time taken says nothing about which one was wrong.
			boolean usernameMatches = matches(username, expected.username());
			boolean passwordMatches = matches(password, expected.password());
			if (!(usernameMatches & passwordMatches)) {
				throw new BadCredentialsException("Bad scrape credential");
			}
			return UsernamePasswordAuthenticationToken.authenticated(username, null,
					List.of(new SimpleGrantedAuthority(SCRAPER)));
		};
	}

	private static boolean matches(String given, String expected) {
		return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
	}

}
