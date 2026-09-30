package com.example.auth.auth;

/**
 * The one error body every endpoint and security handler returns: a stable machine-readable
 * {@code code} (see {@link ErrorCode}) plus a fixed human-readable {@code message}. Messages never
 * echo request input and never carry stack traces or exception text.
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message);
    }
}
