package com.example.securedhello.service;

/**
 * Raised when registration is attempted with a Username or Email that already
 * belongs to an existing account. The message is intentionally generic and
 * safe to surface: it names the conflicting field, not any other account
 * detail.
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String message) {
        super(message);
    }
}
