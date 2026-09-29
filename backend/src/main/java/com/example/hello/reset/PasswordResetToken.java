package com.example.hello.reset;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant usedAt;

  protected PasswordResetToken() {}

  public PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public void consume(Instant now) {
    usedAt = now;
  }
}
