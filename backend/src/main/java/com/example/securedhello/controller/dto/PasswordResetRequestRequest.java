package com.example.securedhello.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Password-reset request payload. Only an Email is supplied; the response is
 * always a generic success regardless of whether it is registered.
 */
public record PasswordResetRequestRequest(
        @NotBlank @Email String email) {
}
