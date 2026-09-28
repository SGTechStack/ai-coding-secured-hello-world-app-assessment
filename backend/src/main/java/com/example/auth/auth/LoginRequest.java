package com.example.auth.auth;

/** Request body for {@code POST /api/auth/login}. */
public record LoginRequest(String username, String password) {}
