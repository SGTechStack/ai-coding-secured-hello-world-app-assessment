package com.example.auth.user;

import java.time.Instant;
import java.util.UUID;

/** Safe projection of an Account: never contains passwordHash. */
public record UserResponse(
        UUID id,
        String username,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
