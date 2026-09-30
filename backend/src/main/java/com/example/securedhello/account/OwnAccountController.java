package com.example.securedhello.account;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
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

	/** Plain text, never HTML, so the greeting cannot carry markup into the SPA. */
	@GetMapping(path = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
	String hello(@AuthenticationPrincipal AccountPrincipal principal) {
		return "Hello, " + principal.username();
	}

	/**
	 * The caller's own Account, as {@link RequiredPasswordChangeFilter} read it for this request. That
	 * filter has already ended a Session whose Account no longer exists.
	 */
	@GetMapping("/me")
	OwnAccount me(HttpServletRequest request) {
		return CurrentAccount.of(request).map(OwnAccount::of).orElseThrow(CurrentAccount::gone);
	}

}
