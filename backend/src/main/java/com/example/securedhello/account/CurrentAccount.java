package com.example.securedhello.account;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * The authenticated request's Account, read once per request by {@link RequiredPasswordChangeFilter},
 * the one place that checks "the Account still exists" so that no Session outlives its Account. Code
 * behind that filter takes the Account from here instead of looking it up, and so never repeats the
 * existence check.
 */
final class CurrentAccount {

	private static final String ATTRIBUTE = CurrentAccount.class.getName();

	private CurrentAccount() {
	}

	static void set(HttpServletRequest request, Account account) {
		request.setAttribute(ATTRIBUTE, account);
	}

	/** The Account the filter read for this request; empty when it is not authenticated as one. */
	static Optional<Account> of(HttpServletRequest request) {
		return Optional.ofNullable((Account) request.getAttribute(ATTRIBUTE));
	}

	/**
	 * The refusal for an Account that no longer exists. The API chain's entry point turns it into the
	 * same 401 any unauthenticated request gets.
	 */
	static InsufficientAuthenticationException gone() {
		return new InsufficientAuthenticationException("Account no longer exists");
	}

}
