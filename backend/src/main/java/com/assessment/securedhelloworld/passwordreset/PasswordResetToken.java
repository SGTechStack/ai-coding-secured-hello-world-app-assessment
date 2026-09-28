package com.assessment.securedhelloworld.passwordreset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single-use, short-lived password reset token, split into a
 * {@code selector} (an indexed, non-secret lookup key — like a public
 * identifier) and a {@code tokenHash} (the BCrypt hash of the secret
 * half, {@code verifier}). Splitting the token this way lets a lookup by
 * {@code selector} be a single indexed row fetch instead of a linear
 * scan comparing every issued token's hash against the presented token
 * — the latter is an O(n) BCrypt-comparison cost per confirm request
 * that grows with the number of tokens ever issued, a real DoS/latency
 * concern the selector avoids. Only the hash of {@code verifier} is ever
 * persisted; the plaintext token (handed to the user via
 * {@code EmailService}) is never stored.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String selector;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant usedAt;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(Long userId, String selector, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.selector = selector;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSelector() {
        return selector;
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

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
