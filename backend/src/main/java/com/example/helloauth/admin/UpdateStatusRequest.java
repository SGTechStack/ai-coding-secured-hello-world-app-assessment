package com.example.helloauth.admin;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH /api/admin/users/{id}/status} body — ratified shape {@code {enabled: bool}}. */
public record UpdateStatusRequest(@NotNull Boolean enabled) {
}
