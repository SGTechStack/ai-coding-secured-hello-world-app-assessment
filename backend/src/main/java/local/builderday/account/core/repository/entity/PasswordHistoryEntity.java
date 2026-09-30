package local.builderday.account.core.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One Password history entry. Timestamps come from the application clock rather than JPA auditing, because the
 * history is ordered by them and pruned to its newest entries.
 */
@Entity
@Table(name = "password_history")
public class PasswordHistoryEntity {
  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "password_hash", nullable = false, length = 256)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PasswordHistoryEntity() {}

  public PasswordHistoryEntity(UUID id, UUID userId, String passwordHash, Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public UUID getId() { return id; }
  public String getPasswordHash() { return passwordHash; }
}
