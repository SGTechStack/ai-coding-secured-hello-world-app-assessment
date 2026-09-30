package com.assessment.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A live user account (spec.md S2).
 *
 * <p><strong>Lock state is {@code lockedUntil} and nothing else.</strong> There is deliberately no
 * {@code accountNonLocked} boolean: {@code lockedUntil} is self-expiring, so an automatic lift
 * needs no write, and two sources of truth for "is this account locked" is a silent auth bypass
 * (ticket 07). {@link #isLockedAt} is the only way this question is asked.
 *
 * <p>{@code role} is a validated {@code String} holding exactly one value, not a Java enum
 * (ticket 03) — the PRD's enum loses to the standards' role model.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "username", nullable = false, unique = true, length = 100)
  private String username;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "role", nullable = false, length = 50)
  private String role;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "require_password_change", nullable = false)
  private boolean requirePasswordChange;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "disabled_at")
  private Instant disabledAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected User() {}

  public User(
      UUID id,
      String username,
      String email,
      String passwordHash,
      String role,
      boolean enabled,
      boolean requirePasswordChange,
      Instant createdAt) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = enabled;
    this.requirePasswordChange = requirePasswordChange;
    this.createdAt = createdAt;
    this.failedLoginAttempts = 0;
  }

  /**
   * The single lock predicate. Derived, never stored — an expired {@code lockedUntil} lifts the
   * lock with no write, which is the whole point of having no boolean beside it.
   */
  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isRequirePasswordChange() {
    return requirePasswordChange;
  }

  public void setRequirePasswordChange(boolean requirePasswordChange) {
    this.requirePasswordChange = requirePasswordChange;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public void setFailedLoginAttempts(int failedLoginAttempts) {
    this.failedLoginAttempts = failedLoginAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public Instant getDisabledAt() {
    return disabledAt;
  }

  public void setDisabledAt(Instant disabledAt) {
    this.disabledAt = disabledAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
