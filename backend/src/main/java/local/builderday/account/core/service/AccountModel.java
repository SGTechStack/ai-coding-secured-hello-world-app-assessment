package local.builderday.account.core.service;

import local.builderday.account.core.model.Role;
import local.builderday.account.core.model.User;
import local.builderday.account.core.repository.entity.UserEntity;

/**
 * The one bridge between a persistence {@code UserEntity} and the domain {@link User} model (ADR 0011): reads an
 * entity into a model to run a state transition, and writes the model's resulting state back onto the entity to
 * persist. Kept in {@code core.service} so every Account use case (hygiene, admin status toggle, admin deletion, Role
 * change) shares one path rather than each mapping entity state to a transition itself.
 */
public final class AccountModel {
  private AccountModel() {}

  /** The domain model of {@code entity}, carrying the state its transitions read and change, role included. */
  public static User of(UserEntity entity) {
    return new User(entity.getId(), entity.getUsername(), Role.valueOf(entity.getRole()), entity.isEnabled(),
        entity.getDisabledAt(), entity.getDeletedAt());
  }

  /** Writes {@code user}'s (post-transition) role and enabled/disabled/deleted state back onto {@code entity}. */
  public static void applyTo(User user, UserEntity entity) {
    entity.setRole(user.role().name());
    entity.setEnabled(user.enabled());
    entity.setDisabledAt(user.disabledAt());
    entity.setDeletedAt(user.deletedAt());
  }
}
