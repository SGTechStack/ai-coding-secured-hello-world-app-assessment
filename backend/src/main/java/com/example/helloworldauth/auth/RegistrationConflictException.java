package com.example.helloworldauth.auth;

/** Thrown when a registration conflicts with an existing username or email. */
public class RegistrationConflictException extends RuntimeException {
    public RegistrationConflictException(String message) {
        super(message);
    }
}
