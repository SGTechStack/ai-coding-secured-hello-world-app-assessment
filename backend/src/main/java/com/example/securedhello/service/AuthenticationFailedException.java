package com.example.securedhello.service;

/**
 * Raised for every login failure mode — unknown Username, wrong password,
 * disabled or locked account — with a single generic message. Using one
 * exception type with one message preserves Enumeration Resistance: the
 * response never reveals whether the Username exists.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid username or password");
    }
}
