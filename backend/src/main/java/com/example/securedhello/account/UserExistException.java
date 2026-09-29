package com.example.securedhello.account;

/** The username or the email is already taken; which one is never disclosed. */
class UserExistException extends RuntimeException {

	UserExistException() {
		super("Username or email already registered");
	}

}
