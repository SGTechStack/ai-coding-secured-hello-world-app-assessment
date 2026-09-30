package local.builderday.account.core.controller.dto;

import java.util.UUID;

/** The caller's own profile: only what the SPA needs to restore its Session after a reload. */
public record ProfileResponse(UUID id, String role) {}
