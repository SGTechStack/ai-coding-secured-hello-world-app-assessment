package com.assessment.auth.password;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A password reset token (spec.md S7, ticket 11).
 *
 * <p>Only the <strong>unsalted SHA-256</strong> of the token is stored. The token is already ~190
 * bits of {@code SecureRandom} entropy and needs no adaptive hash (Std:66) — and an unsalted digest
 * is what lets the hash <em>be</em> the lookup key.
 *
 * <p>{@code usedAt} means exactly one thing: redeemed. Issuing a new token <em>deletes</em> any
 * prior unused row (Std:112), so an unused token never lingers to muddy that meaning.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  protected PasswordResetToken() {}

  public PasswordResetToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  /**
   * Expired and already-used are <em>not</em> distinguished to the caller (Std:261) — telling them
   * apart tells an attacker they guessed a real token. One predicate, one error.
   */
  public boolean isRedeemableAt(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
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

  public void markUsed(Instant usedAt) {
    this.usedAt = usedAt;
  }
}
