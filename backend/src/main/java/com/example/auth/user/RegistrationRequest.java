package com.example.auth.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank String username,

        @NotBlank @Email String email,

        /*
         * Length ≥ 12 enforces the minimum strength policy.
         * Length ≤ 72 guards against BCrypt's silent byte truncation above 72 bytes
         * which would silently accept different passwords as identical.
         */
        @NotBlank
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String password) {
}
