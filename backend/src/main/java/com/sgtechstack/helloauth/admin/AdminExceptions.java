package com.sgtechstack.helloauth.admin;

import org.springframework.http.HttpStatus;

import com.sgtechstack.helloauth.web.ApiException;

final class AdminExceptions {

	private AdminExceptions() {
	}

	static class UserNotFoundException extends ApiException {

		UserNotFoundException() {
			super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found.");
		}

	}

	/**
	 * An admin may not disable, demote or delete their own account, so there is always at least
	 * one admin able to undo a mistake.
	 */
	static class SelfActionNotAllowedException extends ApiException {

		SelfActionNotAllowedException() {
			super(HttpStatus.CONFLICT, "SELF_ACTION_NOT_ALLOWED",
					"Administrators cannot change the status or role of, or delete, their own account.");
		}

	}

}
