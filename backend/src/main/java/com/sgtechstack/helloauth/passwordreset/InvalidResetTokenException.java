package com.sgtechstack.helloauth.passwordreset;

import org.springframework.http.HttpStatus;

import com.sgtechstack.helloauth.web.ApiException;

class InvalidResetTokenException extends ApiException {

	InvalidResetTokenException() {
		super(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "This password reset link is invalid or has expired.");
	}

}
