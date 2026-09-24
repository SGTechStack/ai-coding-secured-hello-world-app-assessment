package com.example.helloauth.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/login} body — ratified shape {username, password}.
 * Same {@code @Size} ceilings as {@link RegisterRequest} (security-review
 * F-05): violations are 400s at the DTO boundary, before credential
 * verification can be touched by an oversized payload.
 */
public record LoginRequest(
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Size(max = 128) String password) {
}
