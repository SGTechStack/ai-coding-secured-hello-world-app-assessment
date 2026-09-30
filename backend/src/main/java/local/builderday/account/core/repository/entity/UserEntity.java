package local.builderday.account.core.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {
  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String username;

  /** Normalized (trimmed, lowercased) email; nullable only for accounts provisioned before registration existed. */
  @Column(unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 256)
  private String passwordHash;

  @Column(nullable = false, length = 32)
  private String role = "USER";

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Last successful login; null until the first login, when inactivity counts from {@code created_at}. */
  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "disabled_at")
  private Instant disabledAt;

  /** Consecutive failed Login attempts since the last success or lock; changed in place by the repository. */
  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  /** End of the current Login lockout; the account is locked while this is in the future. */
  @Column(name = "locked_until")
  private Instant lockedUntil;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Optimistic lock (ADR 0013): a whole-row save built from a stale read fails instead of overwriting. A wrapper, so a
   * newly constructed Account (null) is inserted rather than merged. Every in-place JPQL update also increments it.
   */
  @Version
  @Column(nullable = false)
  private Integer version;

  protected UserEntity() {}

  public UserEntity(UUID id, String username, String email, String passwordHash, String role, boolean enabled) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.email = email;
    this.role = role;
    this.enabled = enabled;
  }

  public UUID getId() { return id; }
  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getRole() { return role; }
  public boolean isEnabled() { return enabled; }
  public Instant getDeletedAt() { return deletedAt; }
  public Instant getLastLoginAt() { return lastLoginAt; }
  public Instant getDisabledAt() { return disabledAt; }
  public int getFailedLoginAttempts() { return failedLoginAttempts; }
  public Instant getLockedUntil() { return lockedUntil; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  // Persistence-only setters (ADR 0011): they assign state with no rules. The enable/disable/delete and Role change
  // rules live on the domain User model; a service applies a transition there, then writes the resulting state back
  // through these.
  public void setRole(String role) { this.role = role; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
