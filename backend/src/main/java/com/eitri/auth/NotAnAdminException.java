package com.eitri.auth;

/** The actor of an admin action is no longer an enabled {@code ADMIN} (checked inside its transaction). */
public final class NotAnAdminException extends RuntimeException {

    NotAnAdminException() {
        super("actor is not an enabled admin");
    }
}
