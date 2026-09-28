package com.example.auth.auth;

import com.example.auth.user.Role;
import java.time.Instant;

public record RegisterResponse(String username, String email, Role role, boolean enabled, Instant createdAt) {}
