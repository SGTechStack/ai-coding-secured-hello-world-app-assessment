package com.example.securedhello.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.security.SessionControl;
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

	static final String PASSWORD_CHANGE = "password_change";

	private static final String CHANGE = "change";

	/** The new password's length and byte limits are Credential policy rules, not checked here. */
	record PasswordChangeRequest(@NotNull String currentPassword, @NotNull String newPassword) {
	}

	private final PasswordChangeService passwordChange;

	private final SessionControl sessionControl;

	private final AuditLog auditLog;

	private final EmailService emailService;

	PasswordChangeController(PasswordChangeService passwordChange, SessionControl sessionControl, AuditLog auditLog,
			EmailService emailService) {
		this.passwordChange = passwordChange;
		this.sessionControl = sessionControl;
		this.auditLog = auditLog;
		this.emailService = emailService;
	}

	@PatchMapping("/me/password")
	ResponseEntity<Void> change(@AuthenticationPrincipal AccountPrincipal principal,
			@Valid @RequestBody PasswordChangeRequest body, HttpServletRequest request, HttpServletResponse response) {
		PasswordChangeService.CompletedPasswordChange completed = passwordChange.change(principal.accountId(),
				body.currentPassword(), body.newPassword());
		// The change has committed, so the notification never announces a change that rolled back.
		emailService.notifyPasswordChanged(completed.email());
		sessionControl.endAll(principal.accountId(), PASSWORD_CHANGE, request, response);
		auditLog.record(AuditEvent.success(AuditAction.PASSWORD_RESET)
			.eventType(CHANGE)
			.userId(principal.accountId())
			.request(request));
		if (completed.requirementCleared()) {
			auditLog.record(PasswordChangeEnforcement.cleared(principal.accountId()).request(request));
		}
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
			.eventType(CHANGE)
			.userId(principal.accountId())
			.request(request));
	}

}
