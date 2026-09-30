package local.builderday.account.administration.service;

import java.time.Instant;
import local.builderday.account.administration.model.ListedAccount;
import local.builderday.account.core.repository.entity.UserEntity;

/** Maps a {@code UserEntity} to the {@link ListedAccount} row the administration slice exposes (list and toggle). */
final class ListedAccounts {
  private ListedAccounts() {}

  static ListedAccount of(UserEntity user, Instant now) {
    return new ListedAccount(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(),
        user.getDeletedAt() != null, user.getLockedUntil() != null && user.getLockedUntil().isAfter(now),
        user.getFailedLoginAttempts(), user.getLockedUntil(), user.getDisabledAt(), user.getDeletedAt(),
        user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
  }
}
