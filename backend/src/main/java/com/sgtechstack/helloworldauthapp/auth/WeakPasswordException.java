package com.sgtechstack.helloworldauthapp.auth;

/**
 * Thrown when a submitted password fails the strength policy. Never carries
 * the password itself, so it is safe to include in logs.
 */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
