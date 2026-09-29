package com.example.securedhello.account;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Cancels an Account's pending Reset Tokens by marking them used (never deleted, spec Schema). Shared
 * by Password Change (story 48: a successful change cancels any pending token) and the password reset
 * service itself (story 55: a new request cancels the earlier one).
 */
@Component
class PasswordResetTokenCanceller {

	private final PasswordResetTokenRepository tokens;

	PasswordResetTokenCanceller(PasswordResetTokenRepository tokens) {
		this.tokens = tokens;
	}

	void cancelPending(UUID accountId, Instant now) {
		tokens.findByUserIdAndUsedAtIsNull(accountId).forEach((token) -> token.markUsed(now));
	}

}
