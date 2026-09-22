package com.sgtechstack.helloworldauthapp.user;

/**
 * The two account roles in the system. Role checks are always enforced
 * server-side; this state is never trusted from client-supplied input.
 */
public enum Role {
    USER,
    ADMIN
}
