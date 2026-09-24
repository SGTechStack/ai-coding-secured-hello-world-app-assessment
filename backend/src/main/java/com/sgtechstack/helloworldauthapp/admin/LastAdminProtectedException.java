package com.sgtechstack.helloworldauthapp.admin;

/**
 * Thrown when a mutation would leave the system with no administrator who can
 * sign in.
 */
public class LastAdminProtectedException extends RuntimeException {

    public LastAdminProtectedException(String message) {
        super(message);
    }
}
