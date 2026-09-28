package com.example.helloworldauth.auth;

/**
 * Thrown when a single client IP exceeds the failed-login throttle threshold
 * (ticket 07, Story 3). Mapped to HTTP 429 in
 * {@link com.example.helloworldauth.web.ApiExceptionHandler}. Independent of
 * per-account lockout: it blunts spraying from one source before any single
 * account can be locked out by that source.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException() {
        super("Too many requests");
    }
}
