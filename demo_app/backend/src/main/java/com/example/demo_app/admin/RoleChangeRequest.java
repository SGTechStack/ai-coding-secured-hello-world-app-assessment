package com.example.demo_app.admin;

import com.example.demo_app.user.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code PATCH /api/v1/admin/users/{id}/role}. The role is a string checked against the
 * exact role names, rather than bound to {@link Role} by Jackson, so an unknown role is a {@code
 * 400 VALIDATION_FAILED} naming the field instead of an unreadable body.
 *
 * @param role {@code USER} or {@code ADMIN}
 */
record RoleChangeRequest(
    @NotNull(message = ROLE_RULE) @Pattern(regexp = "USER|ADMIN", message = ROLE_RULE)
        String role) {

  static final String ROLE_RULE = "Role must be USER or ADMIN.";

  /** The requested role; only call once the request is valid. */
  Role toRole() {
    return Role.valueOf(role);
  }
}
