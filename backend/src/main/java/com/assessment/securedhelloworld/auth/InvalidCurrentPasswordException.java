package com.assessment.securedhelloworld.auth;

/**
 * Thrown when a password-change request's {@code currentPassword} does
 * not match the account's stored credential. Carries a generic message
 * only — never distinguishes "wrong password" from other failure modes,
 * consistent with this codebase's enumeration-resistance convention.
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
