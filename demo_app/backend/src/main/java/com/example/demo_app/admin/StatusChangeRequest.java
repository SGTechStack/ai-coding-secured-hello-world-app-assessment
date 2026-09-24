package com.example.demo_app.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PATCH /api/v1/admin/users/{id}/status}.
 *
 * @param enabled {@code false} disables the account, {@code true} re-enables it; required
 */
record StatusChangeRequest(@NotNull(message = "Enabled must be true or false.") Boolean enabled) {}
