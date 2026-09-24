package com.example.demo_app.audit;

/**
 * The fixed audit vocabulary. {@link AuditLog} only accepts these, so no call site can invent an
 * ad-hoc event name. Each event has one outcome: a rejection or a throttle is its own event.
 */
public enum AuditEvent {
  LOGIN_SUCCESS(Outcome.SUCCESS),
  LOGIN_FAILURE(Outcome.FAILURE),
  ACCOUNT_LOCKED(Outcome.FAILURE),
  LOGIN_THROTTLED(Outcome.FAILURE),
  REQUEST_THROTTLED(Outcome.FAILURE),
  LOGOUT(Outcome.SUCCESS),
  USER_REGISTERED(Outcome.SUCCESS),
  PASSWORD_RESET_REQUESTED(Outcome.SUCCESS),
  PASSWORD_RESET_COMPLETED(Outcome.SUCCESS),
  PASSWORD_RESET_REJECTED(Outcome.FAILURE),
  USER_ENABLED(Outcome.SUCCESS),
  USER_DISABLED(Outcome.SUCCESS),
  USER_ROLE_CHANGED(Outcome.SUCCESS),
  USER_DELETED(Outcome.SUCCESS),
  ADMIN_SELF_ACTION_REJECTED(Outcome.FAILURE),
  ADMIN_BOOTSTRAPPED(Outcome.SUCCESS);

  /** Logged as the lower-case {@code outcome} field. */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  private final Outcome outcome;

  AuditEvent(Outcome outcome) {
    this.outcome = outcome;
  }

  public Outcome outcome() {
    return outcome;
  }
}
