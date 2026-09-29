package com.example.securedhello.account;

/**
 * A login was refused. Deliberately carries no reason: unknown username, wrong password and
 * Disabled Account all look the same to the caller.
 */
class AuthenticationFailedException extends RuntimeException {

	AuthenticationFailedException() {
		super("Authentication failed");
	}

}
