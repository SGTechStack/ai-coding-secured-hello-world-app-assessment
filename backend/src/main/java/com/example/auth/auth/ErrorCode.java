package com.example.auth.auth;

/** Stable error codes the frontend switches on; the accompanying message is for humans only. */
public enum ErrorCode {
    VALIDATION_FAILED,
    CONFLICT,
    RATE_LIMITED,
    UNAUTHENTICATED,
    INVALID_CREDENTIALS,
    INVALID_TOKEN,
    FORBIDDEN,
    CSRF_INVALID,
    SELF_ACTION,
    LAST_ADMIN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR
}
