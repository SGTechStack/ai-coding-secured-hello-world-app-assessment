package com.assessment.auth.audit;

/**
 * {@code event.reason} — the closed vocabulary that discriminates operations sharing an {@link
 * AuditAction} (spec.md S11).
 *
 * <p>Closed by contract: {@code event.reason} may never take a value outside this enum, and story
 * 1.22 makes that a test.
 */
public enum AuditReason {
  // authentication
  LOGIN_SUCCESS,
  LOGIN_FAILURE,
  LOGOUT,
  ACCOUNT_LOCKED,
  SESSION_EXPIRED,
  CONCURRENT_SESSION_EXPIRED,
  RATE_LIMIT_EXCEEDED,
  // authorization
  ACCESS_DENIED,
  FORCED_PASSWORD_CHANGE_DENIED,
  // credential management
  PASSWORD_CHANGED,
  PASSWORD_RESET_REQUESTED,
  PASSWORD_RESET_COMPLETED,
  PASSWORD_RESET_ISSUED_BY_ADMIN,
  PASSWORD_POLICY_REJECTED,
  // account management
  REGISTRATION_SUCCESS,
  REGISTRATION_REJECTED,
  ACCOUNT_CREATED_BY_ADMIN,
  ACCOUNT_ENABLED,
  ACCOUNT_DISABLED,
  ACCOUNT_ROLE_CHANGED,
  ACCOUNT_UNLOCKED,
  ACCOUNT_DELETED,
  SELF_READ,
  // system
  APPLICATION_STARTED,
  APPLICATION_STOPPED,
  ADMIN_BOOTSTRAP_SEEDED,
  NOTIFICATION_SENT
}
