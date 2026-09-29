package sg.securedhello.admin;

import jakarta.validation.constraints.NotNull;

/** The body of {@code PUT /api/admin/users/{uuid}/enabled}: {@code {"enabled": false}} disables the account. */
public record EnabledRequest(@NotNull Boolean enabled) {
}
