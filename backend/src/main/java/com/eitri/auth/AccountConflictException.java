package com.eitri.auth;

/** A new account's username or email is already held by another account (compared lower-case). */
public final class AccountConflictException extends RuntimeException {

    public enum Field {
        USERNAME,
        EMAIL
    }

    private final Field field;

    AccountConflictException(Field field) {
        super(field + " already in use");
        this.field = field;
    }

    public Field field() {
        return field;
    }
}
