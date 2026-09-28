package com.example.helloworldauth.auth;

/**
 * Single generic failure for every unsuccessful login case. Carries no detail
 * that could reveal whether a username exists (enumeration resistance).
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Invalid username or password");
    }
}
