package com.eitri.auth;

/** The account an operation targets does not exist. */
public final class AccountNotFoundException extends RuntimeException {

    AccountNotFoundException() {
        super("account not found");
    }
}
