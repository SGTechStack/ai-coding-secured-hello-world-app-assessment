package com.example.auth.passwordreset;

/**
 * Thrown when a reset token is unknown, expired, or already used. The message
 * is deliberately generic so the three cases are indistinguishable to a caller.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("Invalid or expired reset token");
    }
}
