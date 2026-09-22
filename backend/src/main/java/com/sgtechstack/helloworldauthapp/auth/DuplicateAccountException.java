package com.sgtechstack.helloworldauthapp.auth;

/**
 * Thrown when registration is attempted with a username or email that is
 * already registered.
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String message) {
        super(message);
    }
}
