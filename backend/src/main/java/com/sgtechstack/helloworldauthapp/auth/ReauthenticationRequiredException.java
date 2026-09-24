package com.sgtechstack.helloworldauthapp.auth;

/**
 * Thrown when an irreversible action was attempted without the caller
 * re-proving the password behind their session.
 */
public class ReauthenticationRequiredException extends RuntimeException {

    public ReauthenticationRequiredException(String message) {
        super(message);
    }
}
