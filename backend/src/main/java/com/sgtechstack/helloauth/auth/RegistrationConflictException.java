package com.sgtechstack.helloauth.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.sgtechstack.helloauth.web.ApiException;

class RegistrationConflictException extends ApiException {

	private final Map<String, String> errors;

	RegistrationConflictException(Map<String, String> errors) {
		super(HttpStatus.CONFLICT, "REGISTRATION_CONFLICT", "Username or email is already registered.");
		this.errors = Map.copyOf(errors);
	}

	@Override
	public Map<String, String> errors() {
		return this.errors;
	}

}
