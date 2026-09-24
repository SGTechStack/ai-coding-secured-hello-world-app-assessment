package com.example.demo_app.web;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;

/**
 * A request the API refuses with a specific {@link ApiError}: {@code ApiExceptionHandler} renders
 * it as its status, code, message and field errors. Throw it from a controller or service instead
 * of adding a handler per error code. Its message is shown to the user, so it must never carry a
 * submitted value such as a password. No stack trace is captured: these are expected outcomes.
 */
public class ApiException extends RuntimeException {

  /** The {@code VALIDATION_FAILED} message for any request with field errors. */
  public static final String VALIDATION_FAILED_MESSAGE = "Some fields are invalid";

  private final HttpStatus status;
  private final String code;
  private final List<ApiError.FieldError> fieldErrors;

  public ApiException(
      HttpStatus status, String code, String message, List<ApiError.FieldError> fieldErrors) {
    super(message, null, false, false);
    this.status = status;
    this.code = code;
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  /** {@code 400 VALIDATION_FAILED} naming each invalid field. */
  public static ApiException validationFailed(List<ApiError.FieldError> fieldErrors) {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_FAILED_MESSAGE, fieldErrors);
  }

  /** {@code 400 VALIDATION_FAILED} for the field errors in a Bean Validation result. */
  public static ApiException validationFailed(Errors errors) {
    return validationFailed(fieldErrors(errors));
  }

  /**
   * One entry per invalid field, sorted by field name so the body is stable (Bean Validation
   * reports violations in no particular order). A field with several violations keeps the first:
   * the request DTOs give all of a field's constraints the same message, except where one
   * validator reports one problem at a time.
   */
  public static List<ApiError.FieldError> fieldErrors(Errors errors) {
    Map<String, String> byField = new LinkedHashMap<>();
    errors
        .getFieldErrors()
        .forEach(error -> byField.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return byField.entrySet().stream()
        .map(entry -> new ApiError.FieldError(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(ApiError.FieldError::field))
        .toList();
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<ApiError.FieldError> fieldErrors() {
    return fieldErrors;
  }
}
