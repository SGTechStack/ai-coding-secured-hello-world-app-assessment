package com.example.securedhello.credential;

/** A new password is one of the Account's Password History, which includes the current password. */
public class PasswordHistoryException extends RuntimeException {

	PasswordHistoryException() {
		super("Password was used recently");
	}

}
