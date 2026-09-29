package com.example.securedhello.account;

import java.util.UUID;

/** The own-Account body of {@code POST /login} and {@code GET /me}. Never carries credential material. */
record OwnAccount(UUID id, String username, String email, Role role, boolean passwordChangeRequired) {

	static OwnAccount of(Account account) {
		return new OwnAccount(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
				account.isPasswordChangeRequired());
	}

}
