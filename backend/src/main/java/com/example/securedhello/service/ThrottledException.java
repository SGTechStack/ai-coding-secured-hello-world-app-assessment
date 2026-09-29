package com.example.securedhello.service;

/**
 * Raised when login attempts from a client IP exceed the throttle threshold.
 * Mapped to HTTP 429 with the same generic body as other login failures, so
 * the throttle status is distinguishable by code but never leaks account
 * state. Independent of Account Lockout.
 */
public class ThrottledException extends RuntimeException {

    public ThrottledException() {
        super("Too many attempts");
    }
}
