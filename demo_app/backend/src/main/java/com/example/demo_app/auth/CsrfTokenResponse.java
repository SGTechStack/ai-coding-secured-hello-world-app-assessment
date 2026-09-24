package com.example.demo_app.auth;

/**
 * Body of {@code GET /api/v1/auth/csrf}.
 *
 * @param headerName the header to send the token in on state-changing requests
 * @param token the raw CSRF token
 */
record CsrfTokenResponse(String headerName, String token) {}
