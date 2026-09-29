package com.example.auth.admin;

import com.example.auth.user.Role;

import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull Role role) {
}
