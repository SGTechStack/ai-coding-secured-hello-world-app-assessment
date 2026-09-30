package com.example.auth.auth;

import org.springframework.http.HttpStatus;

/**
 * A request-level failure reported as a specific HTTP status with an {@link ErrorResponse} body,
 * handled centrally by {@link GlobalExceptionHandler}. One exception type (rather than one class
 * per failure reason) keeps that handler small; the {@link ErrorCode} carries the distinction.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
