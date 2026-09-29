package com.example.auth.exception;

/** Thrown when a unique-constraint business rule is violated (e.g. username or email already taken). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
