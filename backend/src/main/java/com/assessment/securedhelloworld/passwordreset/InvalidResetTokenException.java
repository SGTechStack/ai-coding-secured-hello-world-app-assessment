package com.assessment.securedhelloworld.passwordreset;

/**
 * Thrown when a password-reset-confirm request carries a token that is
 * unknown, expired, or already used. The message is intentionally generic
 * enough not to distinguish these cases beyond what the PRD requires.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
