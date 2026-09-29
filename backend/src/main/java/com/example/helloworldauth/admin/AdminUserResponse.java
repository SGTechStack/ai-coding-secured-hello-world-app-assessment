package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.Role;
import com.example.helloworldauth.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Response projection for the admin user-list endpoint. Exposes only the
 * safe-to-share fields — deliberately NOT the password hash, and never the raw
 * {@link User} entity, so the persisted secret can never leak through JSON
 * serialization.
 *
 * <p>Includes the account {@code id} so the client can address the
 * mutation endpoints ({@code PATCH /api/admin/users/{id}/enabled},
 * {@code /role}, {@code DELETE /api/admin/users/{id}}); the id is a
 * non-sensitive surrogate key, not the password hash.
 */
public record AdminUserResponse(
    UUID id,
    String username,
    String email,
    Role role,
    boolean enabled,
    Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.isEnabled(),
            user.getCreatedAt());
    }
}
