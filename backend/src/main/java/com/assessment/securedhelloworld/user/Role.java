package com.assessment.securedhelloworld.user;

/**
 * Roles a {@link User} account can hold. Role checks are always enforced
 * server-side (Spring Security), never trusted from client-supplied state.
 */
public enum Role {
    USER,
    ADMIN
}
