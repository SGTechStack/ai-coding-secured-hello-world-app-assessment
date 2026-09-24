package com.example.demo_app.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/**
 * A registered user. The password is a BCrypt hash. The username and email are stored normalised
 * (lowercase), so lookups on them are case-insensitive through {@link #normaliseUsername} and
 * {@link #normaliseEmail}.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected UserAccount() {}

  /**
   * A new, enabled account with no failed logins. {@code username} and {@code email} must already
   * be normalised; {@link AccountRegistration} is the only caller.
   */
  UserAccount(
      String username,
      String email,
      String firstName,
      String passwordHash,
      Role role,
      Instant createdAt) {
    this.username = username;
    this.email = email;
    this.firstName = firstName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
    this.failedLoginAttempts = 0;
    this.createdAt = createdAt;
  }

  /** The stored form of a username: lowercase, so {@code JohnDoe} and {@code johndoe} match. */
  public static String normaliseUsername(String username) {
    return username.toLowerCase(Locale.ROOT);
  }

  /** The stored form of an email: trimmed and lowercase, so lookups are case-insensitive. */
  public static String normaliseEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public Long getId() {
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

  public String getFirstName() {
    return firstName;
  }

  public Role getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
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
}
