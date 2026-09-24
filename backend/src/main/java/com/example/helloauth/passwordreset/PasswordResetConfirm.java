package com.example.helloauth.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password-reset/confirm} body — {token, newPassword}.
 * The {@code @Size} ceilings (security-review F-05) keep oversized payloads
 * off the hash-compare and BCrypt paths; minted tokens are 43-char
 * base64url, so 128 is generous headroom, and 128 matches the registration
 * password ceiling.
 */
public record PasswordResetConfirm(
    @NotBlank @Size(max = 128) String token,
    @NotBlank @Size(max = 128) String newPassword) {
}
