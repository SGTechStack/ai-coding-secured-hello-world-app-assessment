package com.example.helloauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Authorization to change one account's password: single-use, short-lived, and stored only as a
 * hash. The plaintext exists in the email and nowhere else.
 */
@Entity
@Table(
        name = "password_reset_tokens",
        indexes = @Index(name = "ix_password_reset_tokens_token_hash", columnList = "token_hash"))
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_password_reset_tokens_user"))
    private Account account;

    /**
     * SHA-256 of the plaintext token, hex encoded.
     *
     * <p>Deliberately not BCrypt. The token is 256 bits of randomness, so there is nothing for a
     * slow hash to defend — slow hashing exists to make guessing low-entropy human passwords
     * expensive. It also has to be looked up by value, which a salted hash makes impossible.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null until redeemed. Set once, which is what makes the token single-use. */
    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetToken() {
        // for JPA
    }

    public PasswordResetToken(Account account, String tokenHash, Instant expiresAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRedeemable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
