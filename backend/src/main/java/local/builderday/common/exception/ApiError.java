package local.builderday.common.exception;

import org.springframework.http.HttpStatus;

/** The API error vocabulary of ADR 0001: each stable {@code code} with its status and fixed, safe detail. */
public enum ApiError {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request validation failed."),
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied."),
  // Missing or invalid CSRF token; the detail never says which.
  CSRF_TOKEN_REJECTED(HttpStatus.FORBIDDEN, "Request could not be verified."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource was not found."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Request method is not supported."),
  // Admin Account actions (toggle, deletion, role change): an Admin may use the endpoint, just not on this target —
  // hence 409, not 403.
  ADMIN_CANNOT_TARGET_SELF(HttpStatus.CONFLICT, "You cannot perform this action on your own account."),
  ACCOUNT_DELETED(HttpStatus.CONFLICT, "This account is deleted and cannot be changed."),
  // A whole-row Account save met a concurrent write (ADR 0013); the client refreshes and decides again.
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "This account changed while you were acting on it. Try again."),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported."),
  AUTHENTICATION_UNAVAILABLE(HttpStatus.TOO_MANY_REQUESTS, "Authentication temporarily unavailable."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
  // Registration amendment.
  REGISTRATION_REJECTED(HttpStatus.BAD_REQUEST, "Registration rejected."),
  REGISTRATION_UNAVAILABLE(HttpStatus.TOO_MANY_REQUESTS,
      "Registration is temporarily unavailable. Please try again later."),
  // Password reset amendment (ADR 0001, ADR 0004): field violations travel in the same errors extension as
  // registration's.
  PASSWORD_RESET_REJECTED(HttpStatus.BAD_REQUEST, "Password reset rejected."),
  // Unknown, expired, used or malformed token, or an account no longer eligible; the detail never says which.
  PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired."),
  // Over a per-IP Password reset rate limit; never used for the silent per-account email cap.
  PASSWORD_RESET_UNAVAILABLE(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");

  private final HttpStatus status;
  private final String detail;

  ApiError(HttpStatus status, String detail) {
    this.status = status;
    this.detail = detail;
  }

  public HttpStatus status() {
    return status;
  }

  public String detail() {
    return detail;
  }
}
