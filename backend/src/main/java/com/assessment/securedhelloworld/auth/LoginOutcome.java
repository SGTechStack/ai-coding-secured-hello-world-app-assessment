package com.assessment.securedhelloworld.auth;

/**
 * Outcome of a login attempt, used as the discriminator for the
 * database-backed {@link LoginCount} aggregate (one row per outcome).
 */
public enum LoginOutcome {
    SUCCESS,
    FAILURE
}
