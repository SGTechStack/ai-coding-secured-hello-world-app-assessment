package com.example.demo_app.auth;

import jakarta.validation.constraints.NotBlank;

/** Both fields are required; the UI never sends a blank one, so this is defence in depth. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
