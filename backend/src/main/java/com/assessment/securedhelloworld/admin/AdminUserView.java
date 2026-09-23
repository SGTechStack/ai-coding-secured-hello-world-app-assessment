package com.assessment.securedhelloworld.admin;

import com.assessment.securedhelloworld.user.User;

import java.time.Instant;

/**
 * Admin-facing view of a {@link User}. Deliberately omits
 * {@code passwordHash} (and any other credential material), per Story 8's
 * "never password hashes" requirement.
 */
public record AdminUserView(
        Long id,
        String username,
        String email,
        String role,
        boolean enabled,
        Instant createdAt) {

    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
