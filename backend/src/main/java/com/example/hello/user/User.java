package com.example.hello.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The {@code users} table from the PRD data model. */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
      @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    })
public class User {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, length = 32)
  private String username;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** JPA only. */
  protected User() {}

  public User(String username, String email, String passwordHash, Role role, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.username = Objects.requireNonNull(username);
    this.email = Objects.requireNonNull(email);
    this.passwordHash = Objects.requireNonNull(passwordHash);
    this.role = Objects.requireNonNull(role);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.enabled = true;
    this.failedLoginAttempts = 0;
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
    this.passwordHash = Objects.requireNonNull(passwordHash);
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = Objects.requireNonNull(role);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  /** A lock whose cooldown has elapsed (as opposed to no lock at all). */
  public boolean hasExpiredLock(Instant now) {
    return lockedUntil != null && !lockedUntil.isAfter(now);
  }

  public int recordFailedLogin() {
    return ++failedLoginAttempts;
  }

  public void lockUntil(Instant until) {
    this.lockedUntil = Objects.requireNonNull(until);
  }

  public void resetFailedLogins() {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof User other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /** Deliberately excludes the password hash. */
  @Override
  public String toString() {
    return "User{id=" + id + ", username='" + username + "', role=" + role + ", enabled=" + enabled + '}';
  }
}
