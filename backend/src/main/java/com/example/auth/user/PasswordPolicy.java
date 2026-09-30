package com.example.auth.user;

import java.nio.charset.StandardCharsets;

/**
 * The one password rule set (spec D11, NIST SP 800-63B): at least 12 characters and at most 72
 * UTF-8 bytes -- BCrypt's input limit, beyond which Spring Security's encoder refuses the value
 * outright. No composition rules (a documented deviation from IM8 as-5, see ADR-0012).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;
    public static final String MESSAGE =
            "Password must be at least " + MIN_LENGTH + " characters and at most " + MAX_BYTES + " bytes long";

    private PasswordPolicy() {}

    public static boolean isAcceptable(String password) {
        return password != null
                && password.codePointCount(0, password.length()) >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    /** For callers that must never accept a longer-than-BCrypt value, e.g. the login filter. */
    public static boolean exceedsMaxBytes(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
