package local.builderday.account.administration.controller.dto;

import java.time.Instant;
import java.util.UUID;
import local.builderday.account.administration.model.ListedAccount;

/** One User list entry. No password hash, and no combined status: the three booleans are the state. */
public record ListedAccountResponse(UUID id, String username, String email, String role, boolean enabled,
    boolean deleted, boolean locked, int failedLoginAttempts, Instant lockedUntil, Instant disabledAt,
    Instant deletedAt, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {

  public static ListedAccountResponse from(ListedAccount account) {
    return new ListedAccountResponse(account.id(), account.username(), account.email(), account.role(),
        account.enabled(), account.deleted(), account.locked(), account.failedLoginAttempts(), account.lockedUntil(),
        account.disabledAt(), account.deletedAt(), account.lastLoginAt(), account.createdAt(), account.updatedAt());
  }
}
