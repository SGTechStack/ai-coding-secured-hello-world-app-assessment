package com.example.securedhello.account;

import java.util.UUID;

/**
 * Something that happened to an Account, published by the service that made the change, inside its
 * transaction. {@link AccountEventListener} owns what follows once it commits: ending Sessions,
 * auditing the success and notifying the Account holder. A change that rolls back publishes nothing
 * that anyone acts on.
 */
sealed interface AccountEvent {

	/** An Admin enabled or disabled an Account. */
	record EnabledChanged(UUID actingAdminId, UUID targetId, boolean before, boolean after) implements AccountEvent {
	}

	/** An Admin changed an Account's role. */
	record RoleChanged(UUID actingAdminId, UUID targetId, Role before, Role after) implements AccountEvent {
	}

	/** An Admin lifted a Lock; {@code wasLocked} says whether there was one. It is never Locked after. */
	record Unlocked(UUID actingAdminId, UUID targetId, boolean wasLocked) implements AccountEvent {
	}

	/**
	 * An Admin required a password change; {@code alreadyRequired} says whether it already was. It is
	 * always required after.
	 */
	record PasswordChangeRequired(UUID actingAdminId, UUID targetId, boolean alreadyRequired)
			implements AccountEvent {
	}

	/** An Admin deleted an Account, leaving its tombstone. */
	record Deleted(UUID actingAdminId, UUID targetId) implements AccountEvent {
	}

	/** The Account holder changed their password; {@code requirementCleared} if that satisfied a requirement. */
	record PasswordChanged(UUID accountId, String email, boolean requirementCleared) implements AccountEvent {

		@Override
		public String toString() {
			return "PasswordChanged[accountId=" + accountId + "]";
		}

	}

	/** A Reset Token was redeemed; {@code requirementCleared} if the new password satisfied a requirement. */
	record PasswordResetCompleted(UUID accountId, String email, boolean requirementCleared) implements AccountEvent {

		@Override
		public String toString() {
			return "PasswordResetCompleted[accountId=" + accountId + "]";
		}

	}

	/**
	 * A Reset Token was issued. Issuance runs on a background thread after its request has completed,
	 * so the event carries the request's method and path for the audit event.
	 */
	record PasswordResetIssued(UUID accountId, String email, String resetLink, String httpMethod, String urlPath)
			implements AccountEvent {

		/** Never prints the link: it carries the Reset Token. */
		@Override
		public String toString() {
			return "PasswordResetIssued[accountId=" + accountId + "]";
		}

	}

	/**
	 * A failed login or a wrong current password locked the Account. Its audit event is written by the
	 * refusal's handler, which has the request; this only notifies the holder.
	 */
	record Locked(UUID accountId, String email) implements AccountEvent {

		@Override
		public String toString() {
			return "Locked[accountId=" + accountId + "]";
		}

	}

}
