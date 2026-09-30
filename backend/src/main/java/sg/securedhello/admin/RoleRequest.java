package sg.securedhello.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The body of {@code PUT /api/admin/users/{uuid}/role}: {@code {"role": "ADMIN"}} promotes the account. One of the two
 * roles (ADR-042); anything else is a 400 {@code VALIDATION_FAILED}.
 */
public record RoleRequest(@NotNull @Pattern(regexp = USER + "|" + ADMIN) String role) {

    /** The role a promotion grants. */
    static final String ADMIN = "ADMIN";

    /** The role a demotion leaves. */
    static final String USER = "USER";
}
