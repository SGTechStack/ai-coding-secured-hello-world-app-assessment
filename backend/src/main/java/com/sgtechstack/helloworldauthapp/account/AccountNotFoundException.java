package com.sgtechstack.helloworldauthapp.account;

/**
 * Thrown when an authenticated session refers to an account that no longer
 * exists — a session that outlived its subject.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
