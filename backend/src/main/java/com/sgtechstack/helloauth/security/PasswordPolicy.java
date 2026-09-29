package com.sgtechstack.helloauth.security;

import java.nio.charset.StandardCharsets;

/**
 * The single definition of an acceptable password.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 12;

	/** BCrypt only uses the first 72 bytes; longer input would be silently truncated. */
	public static final int MAX_BYTES = 72;

	public static final String DESCRIPTION = "must be at least " + MIN_LENGTH + " characters and at most "
			+ MAX_BYTES + " bytes";

	private PasswordPolicy() {
	}

	public static boolean isAcceptable(String password) {
		return password != null && password.codePointCount(0, password.length()) >= MIN_LENGTH
				&& !exceedsMaxBytes(password);
	}

	public static boolean exceedsMaxBytes(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}

}
