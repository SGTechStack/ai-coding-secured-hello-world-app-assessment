package com.example.auth.passwordreset;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Email is required")
                @Size(max = 254, message = "Email must be at most 254 characters")
                @Email(message = "Email must be a valid email address")
                String email) {}
