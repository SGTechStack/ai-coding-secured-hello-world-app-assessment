package com.assessment.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A tombstone (spec.md S8, ticket 10).
 *
 * <p>{@code id} is the <strong>same</strong> id the live row held, so an audit line's {@code
 * target_user_id} stays resolvable after the account is gone.
 *
 * <p>There is no password hash here, ever. Tombstones are retained indefinitely and no process
 * purges them — that has no owner, deliberately.
 */
@Entity
@Table(name = "deleted_users")
public class DeletedUser {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "username", nullable = false, length = 100)
  private String username;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "role", nullable = false, length = 50)
  private String role;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "deleted_at", nullable = false)
  private Instant deletedAt;

  @Column(name = "deleted_by_user_id", nullable = false)
  private UUID deletedByUserId;

  protected DeletedUser() {}

  public DeletedUser(User source, Instant deletedAt, UUID deletedByUserId) {
    this.id = source.getId();
    this.username = source.getUsername();
    this.email = source.getEmail();
    this.role = source.getRole();
    this.enabled = source.isEnabled();
    this.deletedAt = deletedAt;
    this.deletedByUserId = deletedByUserId;
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

  public String getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public UUID getDeletedByUserId() {
    return deletedByUserId;
  }
}
