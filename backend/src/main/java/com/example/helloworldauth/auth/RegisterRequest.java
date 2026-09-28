package com.example.helloworldauth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration payload. Password strength policy: length >= 12 (PRD Story 1). */
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 12, max = 200, message = "Password must be at least 12 characters") String password
) {
}
