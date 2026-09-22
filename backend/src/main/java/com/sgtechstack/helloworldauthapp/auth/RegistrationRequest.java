package com.sgtechstack.helloworldauthapp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/auth/register}. Password strength beyond
 * minimum length (see {@link PasswordPolicy}) is enforced in the service
 * layer, not here, so the specific policy can evolve without touching the
 * wire contract.
 */
public record RegistrationRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
