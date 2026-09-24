package com.example.demo_app.passwordreset;

import com.example.demo_app.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One reset link sent to an account. Only the SHA-256 hex of the token is stored, so a database
 * leak doesn't hand out working links. A token works once ({@code usedAt}), until {@code
 * expiresAt}, and only while it is the account's newest: a new request marks older ones used.
 */
@Entity
@Table(name = "password_reset_token")
class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private UserAccount user;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PasswordResetToken() {}

  PasswordResetToken(UserAccount user, String tokenHash, Instant createdAt, Instant expiresAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  Long getId() {
    return id;
  }

  UserAccount getUser() {
    return user;
  }

  /** Unused and not yet expired at {@code now}. */
  boolean isUsableAt(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }
}
