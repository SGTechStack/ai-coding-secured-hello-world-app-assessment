package local.builderday.support;

import java.time.Instant;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.account.core.service.AccountModel;

/**
 * Seeds account state in tests by running the same domain transition production does (ADR 0011): build the domain
 * model, apply the transition, write the state back onto the entity. Behaviour that used to live on {@code UserEntity}
 * is now on the domain model, so tests drive it through here rather than through entity methods.
 */
public final class Accounts {
  private Accounts() {}

  /** Disables {@code entity} through the domain model (idempotent, keeps the first disable time). */
  public static UserEntity disable(UserEntity entity, Instant at) {
    var user = AccountModel.of(entity);
    user.disable(at);
    AccountModel.applyTo(user, entity);
    return entity;
  }

  /** Soft-deletes {@code entity} (a tombstone) through the domain model. */
  public static UserEntity markDeleted(UserEntity entity, Instant at) {
    var user = AccountModel.of(entity);
    user.markDeleted(at);
    AccountModel.applyTo(user, entity);
    return entity;
  }
}
