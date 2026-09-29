package com.eitri.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The password strength policy shared by registration, password reset and admin bootstrap: at least
 * 12 characters (Unicode code points) and at most 72 bytes of UTF-8. BCrypt ignores input beyond 72
 * bytes, so longer passwords are rejected rather than silently truncated.
 */
public final class PasswordPolicy {

    /** Usernames accepted at registration and login: 1 to 64 letters, digits, '_' or '-'. */
    public static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public static final int MIN_CHARACTERS = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** The client-facing message for the first rule {@code password} breaks, if any. */
    public static Optional<String> violation(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of("Password is required");
        }
        if (password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            return Optional.of("Password must be at least " + MIN_CHARACTERS + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("Password must be at most " + MAX_BYTES + " bytes");
        }
        return Optional.empty();
    }
}
