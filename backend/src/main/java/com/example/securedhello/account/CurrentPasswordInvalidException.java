package com.example.securedhello.account;

/**
 * A Password Change gave a current password that does not match the Account's, or the Account is
 * Locked. Like a refused login, the two are not told apart.
 */
class CurrentPasswordInvalidException extends RuntimeException {

	private final boolean newlyLocked;

	CurrentPasswordInvalidException(boolean newlyLocked) {
		super("Current password is invalid");
		this.newlyLocked = newlyLocked;
	}

	/** Whether this wrong password made the Account Locked. */
	boolean newlyLocked() {
		return newlyLocked;
	}

}
