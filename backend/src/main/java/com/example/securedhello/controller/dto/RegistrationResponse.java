package com.example.securedhello.controller.dto;

import java.time.Instant;

import com.example.securedhello.entity.Role;
import com.example.securedhello.entity.User;

/**
 * Registration response payload. Deliberately excludes the password and its
 * hash; only non-sensitive account facts are exposed.
 */
public record RegistrationResponse(
        Long id,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt) {

    public static RegistrationResponse from(User user) {
        return new RegistrationResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
