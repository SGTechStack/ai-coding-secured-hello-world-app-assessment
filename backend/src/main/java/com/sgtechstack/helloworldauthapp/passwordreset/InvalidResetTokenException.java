package com.sgtechstack.helloworldauthapp.passwordreset;

/**
 * Thrown for any invalid reset token: unknown, expired, or already used.
 * Deliberately doesn't distinguish between these cases in its message —
 * there's no enumeration-resistance requirement to protect here (unlike
 * login), but there's also no reason to hand an attacker probing tokens
 * more diagnostic detail than "rejected".
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
