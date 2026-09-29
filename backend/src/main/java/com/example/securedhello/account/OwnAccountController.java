package com.example.securedhello.account;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a logged-in Account holder sees about themselves. Both endpoints use only the authenticated
 * identity and never take an Account ID from the request.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class OwnAccountController {

	private final AccountRepository accounts;

	OwnAccountController(AccountRepository accounts) {
		this.accounts = accounts;
	}

	/** Plain text, never HTML, so the greeting cannot carry markup into the SPA. */
	@GetMapping(path = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
	String hello(@AuthenticationPrincipal AccountPrincipal principal) {
		return "Hello, " + principal.username();
	}

	/** The caller's own Account; a Session whose Account no longer exists is treated as logged out. */
	@GetMapping("/me")
	OwnAccount me(@AuthenticationPrincipal AccountPrincipal principal) {
		return accounts.findById(principal.accountId())
			.map(OwnAccount::of)
			.orElseThrow(() -> new InsufficientAuthenticationException("Account no longer exists"));
	}

}
