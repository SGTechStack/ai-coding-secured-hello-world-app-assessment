package com.example.securedhello.controller.dto;

import java.time.Instant;

import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;

/**
 * Admin-facing view of an account. Exposes only non-sensitive fields; the
 * password hash is never included.
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
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
