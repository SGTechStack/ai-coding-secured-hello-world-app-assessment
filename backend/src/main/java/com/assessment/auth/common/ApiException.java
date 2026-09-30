package com.assessment.auth.common;

/** A business failure that carries its own {@link ApiErrorCode} to the boundary (spec.md S10). */
public class ApiException extends RuntimeException {

  private final ApiErrorCode code;

  public ApiException(ApiErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ApiErrorCode code() {
    return code;
  }
}
