package com.example.auth.auth;

import org.springframework.http.HttpStatus;

/**
 * A request-level failure that should be reported as a specific HTTP status
 * with a generic {@link ErrorResponse} body, handled centrally by {@link
 * GlobalExceptionHandler}. One exception type (rather than one class per
 * failure reason) keeps that handler -- and this pattern -- reusable as later
 * stages (admin user management, password reset) grow their own request
 * validation.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
