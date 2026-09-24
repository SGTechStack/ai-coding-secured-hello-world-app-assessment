package com.example.demo_app.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * JSON body for every API error response: {@code {"status", "code", "message", "timestamp",
 * "path"}}. Build it with {@link #of} so every error, from a controller or from the security
 * filter chain, has the same shape.
 *
 * @param status the HTTP status code
 * @param code a stable, machine-readable error code (e.g. {@code INVALID_CREDENTIALS})
 * @param message a human-readable message, safe to show to the user
 * @param timestamp when the error occurred
 * @param path the request path that failed
 */
public record ApiError(int status, String code, String message, Instant timestamp, String path) {

  public static ApiError of(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return new ApiError(status.value(), code, message, Instant.now(), request.getRequestURI());
  }
}
