package com.example.auth.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String token,

        // Same policy as registration: length 12–72 (BCrypt byte cap).
        @NotBlank
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String newPassword) {
}
