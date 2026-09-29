package com.example.auth.passwordreset;

/** Thrown when a source IP exceeds the password-reset request rate limit. */
public class ResetRequestThrottledException extends RuntimeException {

    public ResetRequestThrottledException() {
        super("Too many password reset requests");
    }
}
