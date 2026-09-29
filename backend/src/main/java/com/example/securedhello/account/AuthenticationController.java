package com.example.securedhello.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.security.SessionControl;
import com.example.securedhello.web.ProblemResponses;

/**
 * {@code POST /login} and {@code POST /logout}. Login takes JSON {@code {username, password}} (JSON
 * rather than a form post, see {@code docs/agents/reviewer-decisions.md}); success starts a fresh
 * Session and returns the own Account, and every refusal is the same 401
 * {@code authentication_failed} body.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class AuthenticationController {

	static final String AUTHENTICATION_FAILED = "authentication_failed";

	private static final String CLEAR_SITE_DATA = "Clear-Site-Data";

	/**
	 * Login input, capped before any business logic. Formats are not checked here: a malformed
	 * username simply matches no Account.
	 */
	record LoginRequest(@NotNull @Size(max = 32) String username, @NotNull @Size(max = 64) String password) {
	}

	private final AuthenticationGuard guard;

	private final SessionControl sessionControl;

	private final AuditLog auditLog;

	AuthenticationController(AuthenticationGuard guard, SessionControl sessionControl, AuditLog auditLog) {
		this.guard = guard;
		this.sessionControl = sessionControl;
		this.auditLog = auditLog;
	}

	@PostMapping("/login")
	OwnAccount login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Account account = authenticate(body, request);
		AccountPrincipal principal = AccountPrincipal.of(account);
		sessionControl.start(principal.toAuthentication(), request, response);
		auditLog.record(AuditEvent.success(AuditAction.USER_AUTHENTICATION)
			.userId(account.getId())
			.passwordAuthentication()
			.request(request));
		return OwnAccount.of(account);
	}

	/**
	 * {@code POST /logout}: ends the Session and tells the browser to clear what it holds for this
	 * origin. Keeps CSRF protection, so an expired Session gets 401/403, which the SPA treats as
	 * already logged out (ADR 0002).
	 */
	@PostMapping("/logout")
	ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
		sessionControl.end(authentication, request, response);
		auditLog.record(AuditEvent.success(AuditAction.USER_LOGOUT).userId(principal.accountId()).request(request));
		return ResponseEntity.ok().header(CLEAR_SITE_DATA, "\"cache\",\"cookies\",\"storage\"").build();
	}

	/**
	 * One body for unknown username, wrong password and Disabled Account; no identity is logged. An
	 * authenticated Session the request carried is ended.
	 */
	@ExceptionHandler(AuthenticationFailedException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	ProblemDetail authenticationFailed(HttpServletRequest request, HttpServletResponse response) {
		auditLog.record(AuditEvent.failure(AuditAction.USER_AUTHENTICATION, AUTHENTICATION_FAILED)
			.passwordAuthentication()
			.request(request)
			.sessionHashOf(request));
		sessionControl.endAfterFailedLogin(request, response);
		return ProblemResponses.problem(HttpStatus.UNAUTHORIZED, AUTHENTICATION_FAILED,
				"The username or password is incorrect.");
	}

	/**
	 * A refusal passes through; anything else is an authentication system failure (for example the
	 * database is unavailable): audited at ERROR here, then answered as the generic 500 by the global
	 * handler, which logs the exception once.
	 */
	private Account authenticate(LoginRequest body, HttpServletRequest request) {
		try {
			return guard.authenticate(body.username(), body.password());
		}
		catch (AuthenticationFailedException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			auditLog.record(AuditEvent.systemFailure(AuditAction.USER_AUTHENTICATION, "authentication_system_failure")
				.passwordAuthentication()
				.request(request)
				.sessionHashOf(request));
			throw ex;
		}
	}

}
