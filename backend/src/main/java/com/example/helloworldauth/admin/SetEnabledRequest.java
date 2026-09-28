package com.example.helloworldauth.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for the admin status-toggle endpoint (Story 9). Carries the
 * desired {@code enabled} state for the target account. {@code enabled} is
 * required so a malformed body is rejected with 400 rather than silently
 * defaulting.
 */
public record SetEnabledRequest(@NotNull Boolean enabled) {
}
