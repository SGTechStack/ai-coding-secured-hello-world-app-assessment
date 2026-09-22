package com.sgtechstack.helloworldauthapp.admin;

/**
 * Thrown when an admin targets their own account via a mutation endpoint
 * (status toggle, role change, delete) — none of these may be self-applied,
 * so an admin can't accidentally lock themselves out or delete their own
 * access.
 */
public class SelfActionNotAllowedException extends RuntimeException {

    public SelfActionNotAllowedException(String message) {
        super(message);
    }
}
