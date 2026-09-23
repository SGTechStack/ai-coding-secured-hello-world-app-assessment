package com.assessment.securedhelloworld.auth;

/**
 * Thrown for any login failure that must be reported to the client with a
 * single generic message: wrong password, unknown username, disabled
 * account, or an account currently inside its lockout window. Never
 * carries a message that would let a caller distinguish these cases.
 */
public class AuthenticationFailedException extends RuntimeException {

    public static final String GENERIC_MESSAGE = "Invalid username or password";

    public AuthenticationFailedException() {
        super(GENERIC_MESSAGE);
    }
}
