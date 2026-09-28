package com.example.auth.auth;

/**
 * Generic error body. Used for the anti-enumeration login failure, which must
 * be identical regardless of which field (username or password) was wrong.
 */
public record ErrorResponse(String message) {}
