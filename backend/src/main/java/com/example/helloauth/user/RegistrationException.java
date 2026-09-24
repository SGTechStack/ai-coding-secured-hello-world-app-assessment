package com.example.helloauth.user;

/**
 * Registration validation failures. Each subtype maps to a distinct HTTP
 * status in {@code ApiExceptionHandler} — duplicates are 409 Conflict,
 * policy violations 400 Bad Request.
 */
public abstract class RegistrationException extends RuntimeException {

    protected RegistrationException(String message) {
        super(message);
    }

    /** Username is already taken by another account. */
    public static class DuplicateUsername extends RegistrationException {
        public DuplicateUsername(String username) {
            super("Username '" + username + "' is already taken.");
        }
    }

    /** Email is already registered to another account. */
    public static class DuplicateEmail extends RegistrationException {
        public DuplicateEmail(String email) {
            super("Email '" + email + "' is already registered.");
        }
    }

    /** Password fails the length policy (min length is externalized). */
    public static class PasswordTooShort extends RegistrationException {
        public PasswordTooShort(int minLength) {
            super("Password must be at least " + minLength + " characters.");
        }
    }
}
