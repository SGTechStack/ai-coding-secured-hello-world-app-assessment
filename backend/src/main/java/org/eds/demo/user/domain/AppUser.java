package org.eds.demo.user.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eds.demo.common.BaseAuditableEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser extends BaseAuditableEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", updatable = false, nullable = false)
  @Getter(AccessLevel.NONE)
  private UUID id;

  public UserId getId() {
    return id == null ? null : new UserId(id);
  }

  @Column(name = "username", nullable = false, unique = true, length = 100)
  private String username;

  // 254 = RFC 5321 maximum email address length
  @Column(name = "email", unique = true, length = 254)
  private String email;

  /**
   * Human-readable display name from the MPDS profile lookup on OIDC login (ADR-DEMO-BE-0013).
   * Nullable — local mock users, never-enriched rows, and users whose MPDS record is not found
   * leave it unset; callers fall back to {@link #username} for display. Distinct from {@code
   * username}, which stays the stable login principal and is never rewritten by the lookup.
   */
  @Column(name = "display_name", length = 254)
  private String displayName;

  /**
   * The enterprise AAS UUID from the token, bound on first OIDC login. Nullable — local mock users
   * and pre-seeded rows that have never logged in via OIDC leave it unset. Used as the downstream
   * {@code actor.userId} for agent invocations; never the relational key.
   */
  @Column(name = "aas_uuid", unique = true)
  private UUID aasUuid;

  /** Password hash from the delegating encoder. Null until a password is set. */
  @Column(name = "password_hash")
  private String passwordHash;

  /** Admins disable an Account to suspend access without deleting it. */
  @Builder.Default
  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  /** Consecutive failed sign-ins, driving the progressive backoff. */
  @Builder.Default
  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts = 0;

  /**
   * Sign-in is refused until this instant after repeated failures. Null when no backoff applies.
   */
  @Column(name = "locked_until")
  private Instant lockedUntil;

  /** True while the holder must replace an admin-issued Temporary Password. */
  @Builder.Default
  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = false;

  /** When an unchanged Temporary Password stops working. Null when none is outstanding. */
  @Column(name = "temp_password_expires_at")
  private Instant tempPasswordExpiresAt;

  @Builder.Default
  @OneToMany(
      mappedBy = "appUser",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  private List<AppUserRole> userRoles = new ArrayList<>();

  public static AppUser create(String username, Set<Role> roles) {
    var user = AppUser.builder().username(username).build();
    roles.forEach(role -> user.userRoles.add(new AppUserRole(user, role)));
    return user;
  }

  /** Stores a hash produced by the delegating encoder; never pass a plaintext password. */
  public void updatePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  /** Stores a hashed admin-issued Temporary Password the holder must replace before it expires. */
  public void issueTemporaryPassword(String passwordHash, Instant expiresAt) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = true;
    this.tempPasswordExpiresAt = expiresAt;
  }

  /** Stores the holder's own new password hash and ends the Temporary Password state. */
  public void changePassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = false;
    this.tempPasswordExpiresAt = null;
  }

  /** True when an unchanged Temporary Password has passed its expiry at {@code now}. */
  public boolean isTemporaryPasswordExpired(Instant now) {
    return mustChangePassword
        && tempPasswordExpiresAt != null
        && !now.isBefore(tempPasswordExpiresAt);
  }

  /** A correct sign-in clears the failed-attempt counter and any backoff. */
  public void recordSuccessfulSignIn() {
    clearSignInBackoff();
  }

  /** Resets the failed-attempt counter and any delay, e.g. after an admin password reset. */
  public void clearSignInBackoff() {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
  }

  /** True while a backoff delay from earlier failures is still running at {@code now}. */
  public boolean isSignInDelayed(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  /**
   * Counts a failed sign-in. From the {@code threshold}th consecutive failure on, sets a delay of
   * {@code baseDelay} doubled once per failure past the threshold, capped at {@code maxDelay}.
   *
   * @return the delay now in force, or {@link Duration#ZERO} while under the threshold
   */
  public Duration recordFailedSignIn(
      Instant now, int threshold, Duration baseDelay, Duration maxDelay) {
    failedLoginAttempts++;
    if (failedLoginAttempts < threshold) {
      return Duration.ZERO;
    }
    var delay = baseDelay;
    for (int doublings = failedLoginAttempts - threshold; doublings > 0; doublings--) {
      if (delay.compareTo(maxDelay) >= 0) {
        break;
      }
      delay = delay.multipliedBy(2);
    }
    delay = delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    lockedUntil = now.plus(delay);
    return delay;
  }

  public void updateEmail(String email) {
    this.email = (email != null && !email.isBlank()) ? email.trim() : null;
  }

  /** Sets the display name from the MPDS lookup; blank/null values are ignored. */
  public void updateDisplayName(String displayName) {
    if (displayName != null && !displayName.isBlank()) {
      this.displayName = displayName;
    }
  }

  /** Binds the AAS UUID on first OIDC login. Idempotent — a no-op if already bound. */
  public void bindAasUuid(UUID aasUuid) {
    if (this.aasUuid == null) {
      this.aasUuid = aasUuid;
    }
  }

  public Set<Role> getRoles() {
    return userRoles.stream()
        .map(userRole -> userRole.getRole())
        .collect(Collectors.toUnmodifiableSet());
  }

  public void addRole(Role role) {
    if (userRoles.stream().noneMatch(ur -> ur.getRole() == role)) {
      userRoles.add(new AppUserRole(this, role));
    }
  }

  public void removeRole(Role role) {
    userRoles.removeIf(ur -> ur.getRole() == role);
  }

  @Override
  public String toString() {
    return String.format("id: %s, username: %s, email: %s", getId(), getUsername(), getEmail());
  }
}
