package com.example.auth.auth;

import com.example.auth.user.Role;

/** Response body for {@code GET /api/auth/me}: {@code {username, email, role}}. */
public record MeResponse(String username, String email, Role role) {}
