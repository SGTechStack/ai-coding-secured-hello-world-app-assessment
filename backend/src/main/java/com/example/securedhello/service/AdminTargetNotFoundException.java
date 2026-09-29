package com.example.securedhello.service;

/**
 * Raised when an admin mutation targets an account id (or an actor) that does
 * not exist. Mapped to HTTP 404.
 */
public class AdminTargetNotFoundException extends RuntimeException {

    public AdminTargetNotFoundException() {
        super("Account not found");
    }
}
