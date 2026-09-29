package com.example.securedhello.service;

/**
 * Raised when a password-reset confirmation presents a token that is unknown,
 * already used, or expired. Mapped to a generic HTTP 400; the password is left
 * unchanged. The generic message avoids revealing which of the three
 * conditions failed.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("Invalid or expired reset token");
    }
}
