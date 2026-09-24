package com.example.helloauth.passwordreset;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password-reset/request} body — {email}. The 254
 * ceiling (security-review F-05) is the RFC 5321 path maximum.
 */
public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
}
