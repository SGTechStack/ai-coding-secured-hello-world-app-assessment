package com.example.helloworldauth.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password reset confirm payload (Story 7). Carries the plaintext reset token
 * and the new password. The new password is re-validated against the same
 * strength policy as registration (length &gt;= 12, PRD Story 1).
 */
public record PasswordResetConfirmRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 12, max = 200, message = "Password must be at least 12 characters") String newPassword
) {
}
