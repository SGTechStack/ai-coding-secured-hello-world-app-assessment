package com.example.securedhello.account;

import java.util.Optional;
import java.util.UUID;

/**
 * A login was refused. The caller never learns why: unknown username, wrong password, Locked and
 * Disabled Account all look the same. Internally it names the Account this attempt made Locked, if
 * any, so the lockout can be audited with the request.
 */
class AuthenticationFailedException extends RuntimeException {

	private final UUID newlyLockedAccountId;

	AuthenticationFailedException() {
		this(null);
	}

	private AuthenticationFailedException(UUID newlyLockedAccountId) {
		super("Authentication failed");
		this.newlyLockedAccountId = newlyLockedAccountId;
	}

	/** The refusal of the wrong password that made this Account Locked. */
	static AuthenticationFailedException lockedAccount(UUID accountId) {
		return new AuthenticationFailedException(accountId);
	}

	Optional<UUID> newlyLockedAccountId() {
		return Optional.ofNullable(newlyLockedAccountId);
	}

}
