package com.example.securedhello.account;

import java.util.UUID;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;

/**
 * The audit trail of a Required Password Change ({@code password-change-enforcement}): an Admin
 * setting the flag, a Password Change or reset clearing it, and every request refused while it is
 * set. One place for the event shape, because two modules emit it: {@link AccountEventListener}, for
 * the committed set and clear, and {@link RequiredPasswordChangeFilter}, for refusals. Callers add
 * {@code url.path} and the request method when there is a request.
 */
final class PasswordChangeEnforcement {

	/**
	 * The audited state field. Deliberately not named after the {@code password_change_required}
	 * column: the logging foundation masks the value of any key containing "password", which would
	 * hide the before and after states. {@code event.action} already says which state this is.
	 */
	private static final String FLAG = "change_required";

	/** {@code event.reason}, so set and cleared are told apart without reading the state fields. */
	private static final String REQUIRED = "required";

	private static final String CLEARED = "cleared";

	private PasswordChangeEnforcement() {
	}

	/**
	 * An Admin required a password change on another Account (INFO). Idempotent, so the before state
	 * says whether the flag was already set.
	 */
	static AuditEvent set(UUID actingAdminId, UUID targetId, boolean alreadyRequired) {
		return AuditEvent.success(AuditAction.PASSWORD_CHANGE_ENFORCEMENT)
			.reason(REQUIRED)
			.userId(actingAdminId)
			.targetUserId(targetId)
			.change(FLAG, alreadyRequired, true);
	}

	/** A Password Change or a completed reset satisfied the requirement, so it is cleared (INFO). */
	static AuditEvent cleared(UUID accountId) {
		return AuditEvent.success(AuditAction.PASSWORD_CHANGE_ENFORCEMENT)
			.reason(CLEARED)
			.userId(accountId)
			.change(FLAG, true, false);
	}

	/** A request was refused because the Account's password must be changed first (WARN). */
	static AuditEvent refused(UUID accountId) {
		return AuditEvent.failure(AuditAction.PASSWORD_CHANGE_ENFORCEMENT, RequiredPasswordChangeFilter.CODE)
			.userId(accountId);
	}

}
