package com.example.auth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration input. Length/charset limits bound every field (App-Standards input validation);
 * the password rule itself lives in {@code PasswordPolicy} so registration, reset and bootstrap
 * share one definition.
 */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
                @Pattern(
                        regexp = "^[A-Za-z0-9._-]{3,64}$",
                        message = "Username must be 3-64 characters: letters, digits, '.', '_' or '-'")
                String username,
        @NotBlank(message = "Email is required")
                @Size(max = 254, message = "Email must be at most 254 characters")
                @Email(message = "Email must be a valid email address")
                String email,
        @NotNull(message = "Password is required") String password,
        @NotBlank(message = "First name is required")
                @Size(max = 100, message = "First name must be at most 100 characters")
                String firstName) {}
