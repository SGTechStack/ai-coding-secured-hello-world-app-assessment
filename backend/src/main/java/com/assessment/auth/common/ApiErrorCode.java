package com.assessment.auth.common;

import org.springframework.http.HttpStatus;

/**
 * The machine-readable {@code code} extension property on every error body (spec.md S10).
 *
 * <p>The SPA's axios interceptor switches on this before deciding whether a 403 means "log out" or
 * "show in-app", which is the reason the field exists at all (spec.md S10, Std:438).
 */
public enum ApiErrorCode {

  /** Raised by the tier-0 {@code PasswordChangeFilter}. */
  PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
  /** Raised by Spring Security's {@code accessDeniedHandler}. */
  ACCESS_DENIED(HttpStatus.FORBIDDEN),
  /** Raised by {@code SelfActionGuard}. */
  SELF_ACTION_NOT_ALLOWED(HttpStatus.FORBIDDEN),
  /** Raised by the service layer when a change would leave no enabled USER_MANAGER. */
  LAST_USER_MANAGER(HttpStatus.CONFLICT),
  /** Raised by the service layer on self-service password change. */
  CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST),

  /** Bean Validation and explicit policy rejections. */
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  /** Username or email already held by a live user or burned by a tombstone. */
  IDENTIFIER_UNAVAILABLE(HttpStatus.BAD_REQUEST),
  /**
   * Reset token expired or already used — deliberately merged, because distinguishing them tells an
   * attacker they guessed a real token (spec.md S7, Std:261).
   */
  RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST),
  /** All three rate-limit counters (spec.md S6). */
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  /** Target user does not exist. */
  USER_NOT_FOUND(HttpStatus.NOT_FOUND),
  /** Fallback for anything the boundary did not classify. */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ApiErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
