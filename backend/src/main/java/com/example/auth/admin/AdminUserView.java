package com.example.auth.admin;

import com.example.auth.user.Role;
import com.example.auth.user.User;
import java.time.Instant;

/** Deliberately omits the password hash -- never part of the admin panel's shape. */
public record AdminUserView(Long id, String username, String email, Role role, boolean enabled, Instant createdAt) {

    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
