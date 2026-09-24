package com.sgtechstack.helloworldauthapp.admin;

/**
 * Thrown when personal data was requested without a stated purpose.
 */
public class PurposeRequiredException extends RuntimeException {

    public PurposeRequiredException(String message) {
        super(message);
    }
}
