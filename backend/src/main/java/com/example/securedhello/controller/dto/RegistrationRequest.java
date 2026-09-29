package com.example.securedhello.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request payload. Bean-validation constraints enforce the
 * password strength policy (length 12–128, no composition rules) and basic
 * presence/format of Username and Email; a violation yields HTTP 400 with a
 * generic validation error before the service is reached.
 */
public record RegistrationRequest(
        @NotBlank
        @Size(min = 1, max = 50)
        String username,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String password) {
}
