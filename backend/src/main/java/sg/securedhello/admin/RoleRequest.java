package sg.securedhello.admin;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT /api/admin/users/{uuid}/role}: {@code {"role": "ADMIN"}} promotes the account. One of the two
 * roles (ADR-042); anything else is a 400 {@code VALIDATION_FAILED}.
 */
public record RoleRequest(@NotNull Role role) {
}
