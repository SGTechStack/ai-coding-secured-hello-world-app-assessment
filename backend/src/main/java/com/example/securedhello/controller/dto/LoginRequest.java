package com.example.securedhello.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request payload. Only the Username is accepted as the login
 * identifier; Email is never a credential.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
