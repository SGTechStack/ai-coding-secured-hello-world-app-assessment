package com.example.helloauth.admin;

import com.example.helloauth.user.Role;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PATCH /api/admin/users/{id}/role} body — ratified shape
 * {@code {role: "USER"|"ADMIN"}}. Binding to the enum means any other value
 * is a malformed body (400) before service code ever runs.
 */
public record UpdateRoleRequest(@NotNull Role role) {
}
