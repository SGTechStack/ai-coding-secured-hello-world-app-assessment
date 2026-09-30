package com.example.securedhello.account;

/**
 * The submitted Reset Token is unknown, expired or already used. Answered as 400 {@code token_invalid}
 * with the Standard's exact wording; the body never says which of the three it was.
 */
class TokenInvalidException extends RuntimeException {

	TokenInvalidException() {
		super("Reset Token is expired, used or unknown");
	}

}
