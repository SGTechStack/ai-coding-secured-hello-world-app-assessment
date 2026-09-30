package com.example.hello.passwordreset;

import com.example.hello.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The {@code password_reset_tokens} table. Only the SHA-256 of the token is stored. */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = @Index(name = "ix_password_reset_tokens_hash", columnList = "token_hash", unique = true))
public class PasswordResetToken {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  /** JPA only. */
  protected PasswordResetToken() {}

  public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.user = Objects.requireNonNull(user);
    this.tokenHash = Objects.requireNonNull(tokenHash);
    this.expiresAt = Objects.requireNonNull(expiresAt);
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public void markUsed(Instant now) {
    this.usedAt = Objects.requireNonNull(now);
  }
}
