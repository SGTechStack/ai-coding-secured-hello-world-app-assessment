package com.example.helloauth.passwordreset;

/**
 * The reset token is unknown, expired, or already spent — one generic
 * rejection for every mode so the response can't distinguish them (the token
 * is a credential; its state is not public information). Mapped to a 400
 * {@code problem+json} in {@code ApiExceptionHandler}.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("This reset link is invalid or has expired.");
    }
}
