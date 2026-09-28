package com.example.helloworldauth.auth;

/**
 * Raised when a password-reset confirm token is unknown, expired, or already
 * used. The message is deliberately generic so the response never reveals which
 * of those cases applied (no leaking of token validity/expiry state).
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("invalid or expired reset token");
    }
}
