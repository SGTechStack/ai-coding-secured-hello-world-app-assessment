package com.example.demo_app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders controller exceptions as {@link ApiError} bodies. Security failures raised before a
 * controller runs are rendered by the security filter chain's JSON handler, with the same shape.
 */
@RestControllerAdvice
class ApiExceptionHandler {

  /**
   * Every authentication failure (unknown user, wrong password, disabled account) gets the same
   * status and body so the response cannot be used to enumerate usernames; {@code
   * DaoAuthenticationProvider} already equalises timing for unknown users.
   */
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  ApiError invalidCredentials(HttpServletRequest request) {
    return ApiError.of(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password", request);
  }

  /** A blank or missing login field. The UI never sends one, so this is defence in depth. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError invalidRequest(HttpServletRequest request) {
    return ApiError.of(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "Username and password are required",
        request);
  }
}
