package com.example.securedhello.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Reset Token: only its SHA-256 hash is stored, never the token itself. Rows are never deleted;
 * cancelling a pending token marks it used instead (spec Schema).
 */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private String tokenHash;

	private Instant expiresAt;

	private Instant usedAt;

	protected PasswordResetToken() {
	}

	static PasswordResetToken issue(UUID userId, String tokenHash, Instant expiresAt) {
		PasswordResetToken token = new PasswordResetToken();
		token.userId = userId;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		return token;
	}

	UUID getUserId() {
		return userId;
	}

	/** Whether the token can still be redeemed: not already used, and not past its expiry. */
	boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	/** Redeems or cancels the token; a token is marked used exactly once, never deleted. */
	void markUsed(Instant now) {
		usedAt = now;
	}

}
