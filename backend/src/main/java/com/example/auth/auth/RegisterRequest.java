package com.example.auth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Email is required") @Email(message = "Email must be a valid email address") String email,
        @NotBlank(message = "Password is required") String password,
        @NotBlank(message = "First name is required") String firstName) {}
