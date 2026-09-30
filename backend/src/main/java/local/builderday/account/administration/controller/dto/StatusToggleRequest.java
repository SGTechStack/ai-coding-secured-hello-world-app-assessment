package local.builderday.account.administration.controller.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The admin status-toggle body: {@code {"enabled": <boolean>}}. {@code enabled} is required — absent or {@code null}
 * is a 400 {@code INVALID_REQUEST} via {@link NotNull}; a non-boolean value fails JSON binding, also a 400.
 */
public record StatusToggleRequest(@NotNull Boolean enabled) {}
