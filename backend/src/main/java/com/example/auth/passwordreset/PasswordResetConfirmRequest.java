package com.example.auth.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Reset token is required") @Size(max = 128, message = "Invalid reset token") String token,
        @NotNull(message = "New password is required") String newPassword) {}
