package com.example.helloauth.auth;

/**
 * {@code {username, role}} — the ratified response shape for
 * {@code GET /api/auth/me} and the login/register success bodies.
 */
public record PrincipalResponse(String username, String role) {
}
