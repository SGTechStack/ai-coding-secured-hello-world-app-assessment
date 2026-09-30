package com.example.authapp.web;

import com.example.authapp.domain.Role;
import com.example.authapp.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request/response bodies. Records carrying secrets override toString() so they can't leak into logs. */
public final class Dtos {

    private Dtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may only contain letters, digits, '.', '_' and '-'")
            String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + "]";
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]";
        }
    }

    public record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetConfirmRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 200) String newPassword) {

        @Override
        public String toString() {
            return "ResetConfirmRequest[]";
        }
    }

    public record EnabledRequest(@NotNull Boolean enabled) {}

    public record RoleRequest(@NotNull Role role) {}

    public record MeResponse(String username, Role role) {
        public static MeResponse of(User u) {
            return new MeResponse(u.getUsername(), u.getRole());
        }
    }

    /** Admin view of a user. Deliberately has no password hash or lockout internals. */
    public record UserResponse(Long id, String username, String email, Role role, boolean enabled, Instant createdAt) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt());
        }
    }

    public record MessageResponse(String message) {}
}
