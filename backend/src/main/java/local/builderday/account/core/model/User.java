package local.builderday.account.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The domain model of an <b>Account</b> (the glossary term): its identity plus the state a state transition reads or
 * changes, and the transitions themselves. This is where the enable/disable/delete and Role change rules live; {@code UserEntity} is
 * the persistence form and holds no behaviour (ADR 0011). A service loads the entity, builds a {@code User} from it,
 * applies a transition, then writes the changed state back onto the entity to persist.
 */
public final class User {
  private final UUID id;
  private final String username;
  private Role role;
  private boolean enabled;
  private Instant disabledAt;
  private Instant deletedAt;

  public User(UUID id, String username, Role role, boolean enabled, Instant disabledAt, Instant deletedAt) {
    this.id = id;
    this.username = username;
    this.role = role;
    this.enabled = enabled;
    this.disabledAt = disabledAt;
    this.deletedAt = deletedAt;
  }

  public UUID id() { return id; }
  public String username() { return username; }
  public Role role() { return role; }
  public boolean enabled() { return enabled; }
  public Instant disabledAt() { return disabledAt; }
  public Instant deletedAt() { return deletedAt; }

  /** A tombstone (soft-deleted): can never authenticate again, and its username and email stay reserved. */
  public boolean deleted() { return deletedAt != null; }

  /**
   * Restores the account's ability to log in. Idempotent: enabling an enabled account changes nothing. The {@code at}
   * parameter is unused today but kept for symmetry with {@link #disable} and {@link #markDeleted}, so every
   * transition reads the same and an "enabled-at" stamp could be added later without a signature change.
   */
  @SuppressWarnings("unused")
  public void enable(Instant at) {
    this.enabled = true;
    this.disabledAt = null;
  }

  /** Blocks further logins. Idempotent: keeps the first disable time. */
  public void disable(Instant at) {
    this.enabled = false;
    if (disabledAt == null) disabledAt = at;
  }

  /**
   * A Role change: gives the account {@code newRole}. Asking for the role it already holds changes nothing.
   *
   * @return whether the role actually changed, so the caller writes, audits and ends Sessions only on a real change
   */
  public boolean changeRole(Role newRole) {
    if (role == newRole) return false;
    role = newRole;
    return true;
  }

  /** Soft-deletes the account (a tombstone): it can never log in again and its username and email stay reserved. */
  public void markDeleted(Instant at) {
    disable(at);
    this.deletedAt = at;
  }
}
