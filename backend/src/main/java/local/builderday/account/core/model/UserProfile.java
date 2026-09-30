package local.builderday.account.core.model;

import java.util.UUID;

/**
 * The safe identity details returned to an authenticated browser session; {@code role} is the Account's stored
 * role.
 */
public record UserProfile(UUID id, String username, String role) {}
