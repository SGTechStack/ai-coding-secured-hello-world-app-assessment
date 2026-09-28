package com.example.helloauth.service;

public class ServiceExceptions {

    /** Registration conflict (username/email already taken) or validation failure. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** Generic authentication failure — deliberately does not reveal the cause (enumeration resistance). */
    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException() {
            super("Invalid username or password.");
        }
    }

    /** Request throttled at the IP level. */
    public static class ThrottledException extends RuntimeException {
        public ThrottledException() {
            super("Too many attempts. Please try again later.");
        }
    }

    /** Admin attempted a forbidden self-action. */
    public static class SelfActionException extends RuntimeException {
        public SelfActionException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
