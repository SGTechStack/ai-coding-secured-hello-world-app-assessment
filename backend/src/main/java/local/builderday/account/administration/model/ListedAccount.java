package local.builderday.account.administration.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One Account as the User list shows it: everything stored about it except the password hash.
 *
 * @param deleted the Account is a tombstone (deleted-at is set)
 * @param locked a Login lockout holds at the time the page was read
 */
public record ListedAccount(UUID id, String username, String email, String role, boolean enabled, boolean deleted,
    boolean locked, int failedLoginAttempts, Instant lockedUntil, Instant disabledAt, Instant deletedAt,
    Instant lastLoginAt, Instant createdAt, Instant updatedAt) {}
