package com.example.auth.admin;

import com.example.auth.user.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull(message = "Role must be provided") Role role) {}
