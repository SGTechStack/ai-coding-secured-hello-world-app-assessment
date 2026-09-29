package com.example.securedhello.service;

/**
 * Raised by the Admin Self-Action Guard when an Admin attempts to disable,
 * demote, or delete their own account (authenticated principal id == target
 * account id). Mapped to HTTP 409 Conflict.
 */
public class SelfActionException extends RuntimeException {

    public SelfActionException() {
        super("An admin cannot perform this action on their own account");
    }
}
