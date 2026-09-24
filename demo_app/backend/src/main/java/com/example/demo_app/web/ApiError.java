package com.example.demo_app.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * JSON body for every API error response: {@code {"status", "code", "message", "timestamp",
 * "path"}}, plus {@code "fieldErrors"} when there are any. Build it with {@link #of} or {@link
 * #forStatus} so every error, from a controller, the security filter chain or the container's
 * error dispatch, has the same shape.
 *
 * @param status the HTTP status code
 * @param code a stable, machine-readable error code (e.g. {@code INVALID_CREDENTIALS})
 * @param message a human-readable message, safe to show to the user
 * @param timestamp when the error occurred
 * @param path the request path that failed
 * @param fieldErrors per-field problems with the request body; left out of the JSON when empty
 */
public record ApiError(
    int status,
    String code,
    String message,
    Instant timestamp,
    String path,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

  /** The generic message for any server-side failure; never carries exception detail. */
  public static final String INTERNAL_ERROR_MESSAGE = "Something went wrong";

  /**
   * One problem with one request field.
   *
   * @param field the request body field name
   * @param message what is wrong with it, safe to show to the user; never echoes the value
   */
  public record FieldError(String field, String message) {}

  public ApiError {
    fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
  }

  public static ApiError of(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return of(status, code, message, request.getRequestURI());
  }

  /** An error with per-field problems, e.g. {@code 400 VALIDATION_FAILED}. */
  public static ApiError of(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest request,
      List<FieldError> fieldErrors) {
    return new ApiError(
        status.value(), code, message, Instant.now(), request.getRequestURI(), fieldErrors);
  }

  static ApiError of(HttpStatusCode status, String code, String message, String path) {
    return new ApiError(status.value(), code, message, Instant.now(), path, List.of());
  }

  /**
   * The generic error for {@code status} when nothing more specific applies: {@code 400
   * MALFORMED_REQUEST}; {@code 500 INTERNAL_ERROR} with a generic message for any server-side or
   * unknown status; otherwise the status name and reason phrase (e.g. {@code 404 NOT_FOUND},
   * {@code 405 METHOD_NOT_ALLOWED}).
   */
  public static ApiError forStatus(HttpStatusCode status, String path) {
    HttpStatus known = HttpStatus.resolve(status.value());
    if (known == null || known.is5xxServerError()) {
      return of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_ERROR_MESSAGE, path);
    }
    if (known == HttpStatus.BAD_REQUEST) {
      return of(known, "MALFORMED_REQUEST", "Malformed request", path);
    }
    return of(known, known.name(), known.getReasonPhrase(), path);
  }
}
