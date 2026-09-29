package com.example.auth.exception;

/**
 * Thrown when an admin targets their own account for a state-changing admin
 * action (disable, role change, delete). Maps to 409 Conflict, kept distinct
 * from the authorization 403 so the two cases are not conflated.
 */
public class SelfActionException extends RuntimeException {

    public SelfActionException(String message) {
        super(message);
    }
}
