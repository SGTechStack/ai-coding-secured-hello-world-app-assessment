package com.example.helloworldauth.admin;

/**
 * Raised when an admin targets their OWN account with a state-changing admin
 * action (Story 9 self-action guard). An admin must not be able to disable
 * themselves and lock out their own access, so the toggle endpoint rejects a
 * target whose username equals the authenticated principal's username.
 */
public class SelfActionForbiddenException extends RuntimeException {

    public SelfActionForbiddenException(String message) {
        super(message);
    }
}
