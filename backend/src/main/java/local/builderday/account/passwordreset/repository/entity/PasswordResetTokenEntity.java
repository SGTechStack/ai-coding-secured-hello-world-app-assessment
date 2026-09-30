package local.builderday.account.passwordreset.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** A Password reset token, stored only as its SHA-256 hash. The account is referenced by id, never by entity. */
@Entity
@Table(name = "password_reset_tokens")
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetTokenEntity {
  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Lowercase hex SHA-256 of the token. */
  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Set once, when a reset completes with this token. */
  @Column(name = "used_at")
  private Instant usedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PasswordResetTokenEntity() {}

  public PasswordResetTokenEntity(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUsedAt() { return usedAt; }
}
