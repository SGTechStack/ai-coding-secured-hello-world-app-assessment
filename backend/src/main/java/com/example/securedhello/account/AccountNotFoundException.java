package com.example.securedhello.account;

/** An admin action's {@code {id}} path variable does not name an Account. */
class AccountNotFoundException extends RuntimeException {

	AccountNotFoundException() {
		super("No such Account");
	}

}
