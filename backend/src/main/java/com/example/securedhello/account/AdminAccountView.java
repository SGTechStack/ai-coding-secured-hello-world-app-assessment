package com.example.securedhello.account;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin Account list. Never carries a password hash or Password History.
 *
 * @param locked whether the Account is Locked now; an expired lock is not
 */
record AdminAccountView(UUID id, String username, String email, Role role, boolean enabled, boolean locked,
		Instant createdAt) {

	static AdminAccountView of(Account account, Instant now) {
		return new AdminAccountView(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
				account.isEnabled(), account.isLocked(now), account.getCreatedAt());
	}

}
