package com.example.securedhello.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password-reset confirmation payload. The new password is subject to the same
 * strength policy as registration (length 12–128).
 */
public record PasswordResetConfirmRequest(
        @NotBlank String token,

        @NotBlank
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String newPassword) {
}
