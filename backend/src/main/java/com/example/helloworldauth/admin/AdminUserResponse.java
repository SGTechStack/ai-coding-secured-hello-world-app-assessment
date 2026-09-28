package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;

import java.time.Instant;

/**
 * Response projection for the admin user-list endpoint. Exposes only the
 * safe-to-share fields — deliberately NOT the password hash, and never the raw
 * {@link User} entity, so the persisted secret can never leak through JSON
 * serialization.
 */
public record AdminUserResponse(
    String username,
    String email,
    Role role,
    boolean enabled,
    Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.isEnabled(),
            user.getCreatedAt());
    }
}
