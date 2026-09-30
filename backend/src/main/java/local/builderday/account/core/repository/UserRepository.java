package local.builderday.account.core.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import local.builderday.account.core.repository.entity.UserEntity;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
  Optional<UserEntity> findByUsername(String username);

  /** @param email normalized (trimmed, lowercased) email, the form it is stored in */
  Optional<UserEntity> findByEmail(String email);

  /** True while the account is under a Login lockout; false for an unknown username. */
  boolean existsByUsernameAndLockedUntilAfter(String username, Instant now);

  // Every in-place update below also increments the version (ADR 0013): bulk updates bypass JPA's optimistic check, so
  // without the increment a whole-row writer holding an older read would silently overwrite them.
  // JPQL rather than Criteria for the two Login lockout updates: each must be one atomic in-place UPDATE so concurrent
  // failures on different instances can neither lose a count nor both set a lock, and bulk updates read clearer here.

  /** Counts one failed Login attempt, unless the account is currently locked (attempts during a lock never count). */
  @Modifying
  @Query("""
      update UserEntity u set u.version = u.version + 1,
      u.failedLoginAttempts = u.failedLoginAttempts + 1, u.updatedAt = :now
      where u.username = :username and (u.lockedUntil is null or u.lockedUntil <= :now)""")
  int incrementFailedLoginAttempts(@Param("username") String username, @Param("now") Instant now);

  /**
   * A successful login: restarts the inactivity clock and the failure count. In place too, so it never writes back a
   * stale {@code locked_until} over a lock another instance has just set.
   */
  @Modifying
  @Query("""
      update UserEntity u set u.version = u.version + 1,
      u.lastLoginAt = :at, u.failedLoginAttempts = 0, u.updatedAt = :at
      where u.username = :username""")
  void recordSuccessfulLogin(@Param("username") String username, @Param("at") Instant at);

  /** Sets the lock and restarts the count once the threshold is reached; 1 when this call set the lock, else 0. */
  @Modifying
  @Query("""
      update UserEntity u set u.version = u.version + 1,
      u.failedLoginAttempts = 0, u.lockedUntil = :lockedUntil, u.updatedAt = :now
      where u.username = :username and u.failedLoginAttempts >= :threshold""")
  int lockWhenThresholdReached(@Param("username") String username, @Param("threshold") int threshold,
      @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

  /**
   * A completed Password reset: the new password hash, and the Login lockout ends. JPQL for the same reason as the
   * lockout updates above: one atomic in-place UPDATE, so it never writes back a stale lock or failure count.
   * {@code last_login_at} is deliberately untouched: a reset is not use of the account (IM8 ac-3).
   */
  @Modifying
  @Query("""
      update UserEntity u set u.version = u.version + 1,
      u.passwordHash = :passwordHash, u.failedLoginAttempts = 0, u.lockedUntil = null, u.updatedAt = :now
      where u.id = :id""")
  int resetPassword(@Param("id") UUID id, @Param("passwordHash") String passwordHash, @Param("now") Instant now);

  /** Case-insensitive match that deliberately includes soft-deleted accounts so their identifiers stay reserved. */
  boolean existsByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

  /**
   * Case-insensitive username match, including soft-deleted accounts. The duplicate check for an emailless account
   * (Admin bootstrap): an emailless account must not collide with another emailless one on a null email.
   */
  boolean existsByUsernameIgnoreCase(String username);

  /**
   * Whether any account holds {@code role}, whatever its state. Deliberately no filter on {@code enabled} or
   * {@code deleted_at}: a disabled or tombstoned Admin still counts as "an Admin has existed", so Admin bootstrap
   * never mints a replacement (ADR 0009 §1).
   */
  boolean existsByRole(String role);

  /** Not soft-deleted, and last used (last login, else creation) before {@code cutoff}. */
  static PredicateSpecification<UserEntity> inactiveSince(Instant cutoff) {
    return (root, cb) -> cb.and(
        cb.isNull(root.get("deletedAt")),
        cb.lessThan(cb.coalesce(root.<Instant>get("lastLoginAt"), root.<Instant>get("createdAt")), cutoff));
  }

  static PredicateSpecification<UserEntity> enabled() {
    return (root, cb) -> cb.isTrue(root.get("enabled"));
  }

  /** Keyset pagination: rows after {@code id} in id order, so processed rows never shift later pages. */
  static PredicateSpecification<UserEntity> idAfter(UUID id) {
    return (root, cb) -> id == null ? cb.conjunction() : cb.greaterThan(root.get("id"), id);
  }
}
