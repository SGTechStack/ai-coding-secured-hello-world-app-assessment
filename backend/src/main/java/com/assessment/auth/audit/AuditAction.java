package com.assessment.auth.audit;

/**
 * {@code event.action} — the operation an audit event describes.
 *
 * <p>The vocabulary cannot name every operation on its own, which is why {@link AuditReason} exists
 * as the discriminator (spec.md S11, ticket 01 Q9).
 */
public enum AuditAction {
  AUTHENTICATION,
  AUTHORIZATION,
  ACCOUNT_MANAGEMENT,
  CREDENTIAL_MANAGEMENT,
  SESSION_MANAGEMENT,
  SYSTEM
}
