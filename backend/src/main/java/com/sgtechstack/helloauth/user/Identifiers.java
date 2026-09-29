package com.sgtechstack.helloauth.user;

import java.util.Locale;

/**
 * Canonical forms for login identifiers, so "Alice" and "alice" can never be two accounts.
 */
public final class Identifiers {

	/** 3-32 characters: letters, digits, dot, underscore, hyphen. */
	public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]{3,32}$";

	private Identifiers() {
	}

	public static String normalizeUsername(String username) {
		return username.strip().toLowerCase(Locale.ROOT);
	}

	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
