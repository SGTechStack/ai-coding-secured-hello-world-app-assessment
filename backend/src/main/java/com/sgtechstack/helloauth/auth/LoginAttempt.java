package com.sgtechstack.helloauth.auth;

import java.util.UUID;

import com.sgtechstack.helloauth.user.Role;

/**
 * Outcome of starting a login attempt against the account store, before the password is checked.
 */
sealed interface LoginAttempt {

	record UnknownAccount() implements LoginAttempt {
	}

	record Locked(String username) implements LoginAttempt {
	}

	/** The attempt has been counted; the caller must now verify the password. */
	record Permitted(UUID id, String username, String passwordHash, Role role, boolean enabled)
			implements LoginAttempt {

		@Override
		public String toString() {
			return "Permitted[id=" + this.id + ", username=" + this.username + "]";
		}

	}

}
