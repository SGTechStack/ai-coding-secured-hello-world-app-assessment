package com.example.helloauth.web;

import com.example.helloauth.domain.Role;
import com.example.helloauth.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class Dtos {

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(String username, String role, boolean mustChangePassword) {
    }

    public record MessageResponse(String message) {
    }

    public record PasswordResetRequest(
            @NotBlank @Email String email) {
    }

    public record PasswordResetConfirm(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 200) String newPassword) {
    }

    public record RoleChangeRequest(
            @NotBlank String role) {
    }

    public record StatusChangeRequest(
            boolean enabled) {
    }

    /** Admin-facing user view. Never includes the password hash (Story 8). */
    public record AdminUserView(
            UUID id,
            String username,
            String email,
            Role role,
            boolean enabled,
            Instant createdAt) {

        public static AdminUserView from(User u) {
            return new AdminUserView(u.getId(), u.getUsername(), u.getEmail(),
                    u.getRole(), u.isEnabled(), u.getCreatedAt());
        }
    }
}
