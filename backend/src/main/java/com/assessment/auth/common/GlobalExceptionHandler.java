package com.assessment.auth.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The boundary handler (spec.md S10, story 1.3).
 *
 * <p>Log levels are prescribed: client input validation failures at WARN, business rule violations
 * at ERROR, and anything unclassified logged <em>once</em> here with its stack trace. Business
 * exceptions carrying an {@link ApiErrorCode} are logged where they are handled, so this class only
 * renders them.
 *
 * <p>No {@code detail} written here may carry an enumeration-sensitive distinction — {@code
 * Std:247} forbids it and the login/reset paths depend on it (spec.md S10).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemDetailWriter writer;

  public GlobalExceptionHandler(ProblemDetailWriter writer) {
    this.writer = writer;
  }

  @ExceptionHandler(ApiException.class)
  public ProblemDetail onApiException(ApiException ex, HttpServletRequest request) {
    if (ex.code().status().is5xxServerError()) {
      log.error("Business rule violation at {} {}", request.getMethod(), request.getRequestURI(), ex);
    } else if (ex.code().status().is4xxClientError()) {
      log.warn(
          "Rejected {} {} with code {}", request.getMethod(), request.getRequestURI(), ex.code());
    }
    return writer.problem(ex.code(), ex.getMessage());
  }

  /** Bean Validation on request bodies. Client input, therefore WARN (story 1.3). */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail onValidationFailure(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
    log.warn("Validation failed at {} {}: {}", request.getMethod(), request.getRequestURI(), detail);
    return writer.problem(ApiErrorCode.VALIDATION_FAILED, detail.isBlank() ? "Invalid request." : detail);
  }

  /** The one place an unhandled exception is logged, with its stack trace (story 1.3). */
  @ExceptionHandler(Exception.class)
  public ProblemDetail onUnhandled(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
    return writer.problem(ApiErrorCode.INTERNAL_ERROR, "An unexpected error occurred.");
  }
}
