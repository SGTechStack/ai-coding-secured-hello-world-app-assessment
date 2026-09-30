package com.assessment.auth.password;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One historical credential (spec.md S5, ticket 04).
 *
 * <p>The newest row is the <em>current</em> hash, not the previous one. That is what makes "block
 * four values" a single ordered query: current plus three previous, the literal reading of Std:355.
 */
@Entity
@Table(name = "password_history")
public class PasswordHistory {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PasswordHistory() {}

  public PasswordHistory(UUID id, UUID userId, String passwordHash, Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
