package com.sgtechstack.helloworldauthapp.admin;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
