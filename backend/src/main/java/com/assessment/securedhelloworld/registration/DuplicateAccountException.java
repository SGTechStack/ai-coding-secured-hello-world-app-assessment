package com.assessment.securedhelloworld.registration;

/**
 * Thrown when a registration request targets a username or email that is
 * already registered. Carries no reference to the submitted password.
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String message) {
        super(message);
    }
}
