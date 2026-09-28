package com.example.helloworldauth.admin;

/**
 * Raised when an admin action targets an account id that does not exist. Mapped
 * to 404 by the API exception handler.
 */
public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException(String message) {
        super(message);
    }
}
