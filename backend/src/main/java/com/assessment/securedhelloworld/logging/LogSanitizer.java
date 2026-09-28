package com.assessment.securedhelloworld.logging;

/**
 * Strips characters that would let untrusted input forge or inject
 * extra log lines (CRLF and other control characters) before it is
 * interpolated into a log message. Applied at call sites that log
 * user-controlled values (usernames, client IP headers) which have not
 * already been constrained by input validation.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * Returns {@code value} with all control characters (including
     * {@code \r} and {@code \n}) removed, or {@code "null"} if
     * {@code value} is {@code null}.
     */
    public static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c != 0x7f) {
                sanitized.append(c);
            }
        }
        return sanitized.toString();
    }
}
