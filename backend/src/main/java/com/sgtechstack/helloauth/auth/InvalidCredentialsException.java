package com.sgtechstack.helloauth.auth;

import org.springframework.http.HttpStatus;

import com.sgtechstack.helloauth.web.ApiException;

/**
 * The one response for every failed login: unknown username, wrong password, locked or disabled
 * account all look identical to the client.
 */
class InvalidCredentialsException extends ApiException {

	InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.");
	}

}
