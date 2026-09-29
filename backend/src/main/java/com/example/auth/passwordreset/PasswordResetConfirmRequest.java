package com.example.auth.passwordreset;

import com.example.auth.user.ValidPassword;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank String token,

        /** Same policy as registration — length 12-72 AND category complexity. */
        @NotBlank @ValidPassword String newPassword) {
}
