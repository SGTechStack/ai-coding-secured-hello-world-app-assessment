package com.example.securedhello.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.credential.PasswordHistoryException;
import com.example.securedhello.credential.PasswordPolicyException;
import com.example.securedhello.web.ProblemResponses;

/**
 * {@code PATCH /me/password}: Password Change for the logged-in Account holder, identified only by
 * the Session, never by an ID in the request. Success notifies the holder and ends every Session of
 * the Account, the request's own included, so the holder logs in again with the new password. A
 * wrong current password counts toward lockout like a failed login; the attempt that locks the
 * Account is also audited as a lockout, and a Locked Account gets the same
 * {@code current_password_invalid} refusal as a wrong password. Each outcome is audited
 * as {@code password-reset} with {@code event.type: ["change"]}; a refusal carries only its code as
 * the reason, never the passwords or the broken rules. A change that satisfied a Required Password
 * Change also clears it, audited as {@code password-change-enforcement}.
 */
@RestController
@RequestMapping("${app.api.base-path}")
class PasswordChangeController {


	/** The new password's length and byte limits are Credential policy rules, not checked here. */
	record PasswordChangeRequest(@NotNull String currentPassword, @NotNull String newPassword) {
	}

	private final PasswordChangeService passwordChange;

	private final AuditLog auditLog;

	PasswordChangeController(PasswordChangeService passwordChange, AuditLog auditLog) {
		this.passwordChange = passwordChange;
		this.auditLog = auditLog;
	}

	@PatchMapping("/me/password")
	ResponseEntity<Void> change(@AuthenticationPrincipal AccountPrincipal principal,
			@Valid @RequestBody PasswordChangeRequest body) {
		// Ending the Sessions, auditing and notifying follow the commit, in AccountEventListener.
		passwordChange.change(principal.accountId(), body.currentPassword(), body.newPassword());
		return ResponseEntity.ok().build();
	}

	@ExceptionHandler(CurrentPasswordInvalidException.class)
	ProblemDetail currentPasswordInvalid(CurrentPasswordInvalidException exception,
			@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
		auditFailure(principal, "current_password_invalid", request);
		if (exception.newlyLocked()) {
			auditLog.record(AuditEvent.failure(AuditAction.ACCESS_CONTROL, AuthenticationController.ACCOUNT_LOCKED)
				.userId(principal.accountId())
				.request(request));
		}
		return ProblemResponses.problem(HttpStatus.BAD_REQUEST, "current_password_invalid",
				"The current password is incorrect.");
	}

	/** Handled here rather than globally, so the refusal is audited once, as a Password Change. */
	@ExceptionHandler(PasswordPolicyException.class)
	ProblemDetail passwordPolicy(PasswordPolicyException exception,
			@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
		auditFailure(principal, ProblemResponses.PASSWORD_POLICY, request);
		return ProblemResponses.passwordPolicy(exception.violations());
	}

	@ExceptionHandler(PasswordHistoryException.class)
	ProblemDetail passwordHistory(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
		auditFailure(principal, ProblemResponses.PASSWORD_HISTORY, request);
		return ProblemResponses.passwordHistory();
	}

	private void auditFailure(AccountPrincipal principal, String reason, HttpServletRequest request) {
		auditLog.record(AuditEvent.failure(AuditAction.PASSWORD_RESET, reason)
			.eventType(AccountEventListener.CHANGE)
			.userId(principal.accountId())
			.request(request));
	}

}
