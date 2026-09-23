package com.assessment.securedhelloworld.admin;

/**
 * Thrown when an admin action targets the acting admin's own account on an
 * endpoint that forbids self-targeting (enable/disable, role change,
 * delete), per Stories 9-11's self-action guards.
 */
public class SelfActionForbiddenException extends RuntimeException {

    public SelfActionForbiddenException(String message) {
        super(message);
    }
}
