package com.example.auth.exception;

/** Thrown when an admin targets a user id that does not exist. Maps to 404. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User not found");
    }
}
