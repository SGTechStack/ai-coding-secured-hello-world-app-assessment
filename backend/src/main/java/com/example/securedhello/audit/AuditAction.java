package com.example.securedhello.audit;

/**
 * The only allowed {@code event.action} values (spec "Audit event contract" mapping table).
 */
public enum AuditAction {

	/** Login success or failure. */
	USER_AUTHENTICATION("user-authentication"),

	/** Logout. */
	USER_LOGOUT("user-logout"),

	/** A Session expired or was ended by the system. */
	SESSION_END("session-end"),

	/** Registration or Bootstrap Admin creation. */
	USER_PROVISIONING("user-provisioning"),

	/** Admin list, enable/disable, role change, unlock, delete, including rejected attempts. */
	USER_ADMINISTRATION("user-administration"),

	/** Required password change set, cleared, or a request refused because of it. */
	PASSWORD_CHANGE_ENFORCEMENT("password-change-enforcement"),

	/** Reset request, reset completion, and Password Change (with {@code event.type: ["change"]}). */
	PASSWORD_RESET("password-reset"),

	/** Lockout, IP Throttle, rate-limit breach, 403, CSRF rejection, input-validation failure. */
	ACCESS_CONTROL("access-control"),

	APPLICATION_STARTUP("application-startup"),

	APPLICATION_SHUTDOWN("application-shutdown");

	private final String value;

	AuditAction(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

}
