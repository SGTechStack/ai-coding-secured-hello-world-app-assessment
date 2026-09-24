package com.example.helloauth.admin;

import com.example.helloauth.user.Role;
import com.example.helloauth.user.User;
import java.time.Instant;

/**
 * The ratified user-list DTO (ticket 07):
 * {@code {id, username, email, role, enabled, createdAt}}. Deliberately has
 * no password field — the hash must never leave the server, so the DTO
 * simply cannot carry one.
 */
public record AdminUserResponse(
    Long id,
    String username,
    String email,
    Role role,
    boolean enabled,
    Instant createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
            user.getId(), user.getUsername(), user.getEmail(),
            user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
