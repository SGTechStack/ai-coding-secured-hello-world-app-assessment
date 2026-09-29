package com.example.auth.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistrationRequest(
        @NotBlank String username,

        @NotBlank @Email String email,

        /**
         * Validated by {@link PasswordPolicyValidator} via {@link ValidPassword}.
         * Enforces length 12-72 AND character-category complexity (IM8-aligned).
         * The backend is authoritative — frontend mirrors the policy for UX only.
         */
        @NotBlank @ValidPassword String password) {
}
