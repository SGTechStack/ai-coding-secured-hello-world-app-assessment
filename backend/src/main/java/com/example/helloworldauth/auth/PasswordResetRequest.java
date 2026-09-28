package com.example.helloworldauth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Password reset request payload (Story 6). Only the email is needed. */
public record PasswordResetRequest(
    @NotBlank @Email String email
) {
}
