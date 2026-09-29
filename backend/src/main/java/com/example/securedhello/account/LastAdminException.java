package com.example.securedhello.account;

import java.util.UUID;

/** A disable or role change that would leave zero enabled Admins, even briefly. */
class LastAdminException extends RuntimeException {

	private final UUID accountId;

	LastAdminException(UUID accountId) {
		super("This change would leave no enabled Admin");
		this.accountId = accountId;
	}

	/** The Account the rejected change targeted. */
	UUID accountId() {
		return accountId;
	}

}
