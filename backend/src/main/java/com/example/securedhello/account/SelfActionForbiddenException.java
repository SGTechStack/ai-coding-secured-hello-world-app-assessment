package com.example.securedhello.account;

import java.util.UUID;

/**
 * An Admin tried to disable, re-enable or change the role of their own Account: a hijacked admin
 * Session can't lift a lock protecting itself, and an Admin can't accidentally lock themselves out.
 */
class SelfActionForbiddenException extends RuntimeException {

	private final UUID accountId;

	SelfActionForbiddenException(UUID accountId) {
		super("An Admin cannot perform this action on their own Account");
		this.accountId = accountId;
	}

	/** The acting Admin's own Account ID, which was also the target. */
	UUID accountId() {
		return accountId;
	}

}
