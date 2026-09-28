package com.example.helloworldauth.admin;

import com.example.helloworldauth.user.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the admin role-change endpoint (Story 10). Carries the
 * desired {@link Role} for the target account. {@code role} is required and is
 * typed as the {@link Role} enum, so only {@code USER} or {@code ADMIN} are
 * accepted: any other value fails JSON deserialization and is rejected with 400
 * (see {@code ApiExceptionHandler#onUnreadable}), and a missing value fails
 * {@code @NotNull} validation and is likewise rejected with 400.
 */
public record ChangeRoleRequest(@NotNull Role role) {
}
