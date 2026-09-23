package com.assessment.securedhelloworld.admin;

/**
 * Thrown when an admin action targets a user id that does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
