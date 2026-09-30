package com.example.securedhello.account;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.security.SessionControl;
import com.example.securedhello.web.ProblemResponses;

/**
 * {@code /admin/users}: Account administration for Admins. The filter chain and the service both
 * require the Admin role. Viewing the list is audited, because it exposes every Account's email.
 * Disabling an Account or changing its role ends every Session of the target Account at once, so
 * the change takes effect immediately; re-enabling does not end Sessions or clear the
 * required-password-change flag (ADR 0001). Unlocking clears the lock and failure counter without
 * touching Sessions. Deleting an Account leaves a tombstone and ends its Sessions too. The
 * self-action guard and the last-Admin rule are enforced by the service; a rejected attempt is
 * audited here at WARN as {@code user-administration}. Requiring a password change also ends the
 * target's Sessions and is audited as {@code password-change-enforcement} instead, since it
 * changes what the target may do rather than its administrative state.
 */
@RestController
@RequestMapping("${app.api.base-path}/admin/users")
class AccountAdministrationController {

	static final String ACCOUNT_DISABLED = "account_disabled";

	static final String ROLE_CHANGED = "role_changed";

	static final String PASSWORD_CHANGE_REQUIRED = "password_change_required";

	static final String ACCOUNT_DELETED = "account_deleted";

	private final AccountAdministrationService administration;

	private final SessionControl sessionControl;

	private final AuditLog auditLog;

	AccountAdministrationController(AccountAdministrationService administration, SessionControl sessionControl,
			AuditLog auditLog) {
		this.administration = administration;
		this.sessionControl = sessionControl;
		this.auditLog = auditLog;
	}

	@GetMapping
	List<AdminAccountView> list(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
		List<AdminAccountView> accounts = administration.list();
		auditLog.record(AuditEvent.success(AuditAction.USER_ADMINISTRATION)
			.eventType("access")
			.userId(principal.accountId())
			.request(request));
		return accounts;
	}

	record EnabledRequest(@NotNull Boolean enabled) {
	}

	@PatchMapping("/{id}/enabled")
	ResponseEntity<Void> setEnabled(@PathVariable UUID id, @Valid @RequestBody EnabledRequest body,
			@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request,
			HttpServletResponse response) {
		AccountEnabledChange change = administration.setEnabled(principal.accountId(), id, body.enabled());
		if (!change.after()) {
			sessionControl.endAll(id, ACCOUNT_DISABLED, request, response);
		}
		auditLog.record(AuditEvent.success(AuditAction.USER_ADMINISTRATION)
			.userId(principal.accountId())
			.targetUserId(id)
			.change("enabled", change.before(), change.after())
			.request(request));
		return ResponseEntity.ok().build();
	}

	record RoleRequest(@NotNull Role role) {
	}

	@PatchMapping("/{id}/role")
	ResponseEntity<Void> changeRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest body,
			@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request,
			HttpServletResponse response) {
		AccountRoleChange change = administration.changeRole(principal.accountId(), id, body.role());
		sessionControl.endAll(id, ROLE_CHANGED, request, response);
		auditLog.record(AuditEvent.success(AuditAction.USER_ADMINISTRATION)
			.userId(principal.accountId())
			.targetUserId(id)
			.change("role", change.before(), change.after())
			.request(request));
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{id}/unlock")
	ResponseEntity<Void> unlock(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal,
			HttpServletRequest request) {
		AccountUnlockChange change = administration.unlock(principal.accountId(), id);
		auditLog.record(AuditEvent.success(AuditAction.USER_ADMINISTRATION)
			.userId(principal.accountId())
			.targetUserId(id)
			.change("locked", change.before(), false)
			.request(request));
		return ResponseEntity.ok().build();
	}

	/**
	 * Requires the target Account to change its password and ends its Sessions at once, so a suspected
	 * attacker is logged out and the holder must choose a new password before doing anything else. An
	 * Admin may require it of their own Account; that ends their own Session too.
	 */
	@PostMapping("/{id}/require-password-change")
	ResponseEntity<Void> requirePasswordChange(@PathVariable UUID id,
			@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request,
			HttpServletResponse response) {
		AccountRequiredPasswordChange change = administration.requirePasswordChange(id);
		sessionControl.endAll(id, PASSWORD_CHANGE_REQUIRED, request, response);
		auditLog.record(PasswordChangeEnforcement.set(principal.accountId(), id, change.before()).request(request));
		return ResponseEntity.ok().build();
	}

	/**
	 * Deletes an Account. The service writes its tombstone and removes the Account with its Reset Tokens
	 * and Password History in one transaction; its Sessions are then ended, so the holder is logged out
	 * at once. The tombstone keeps the username for good, so it can never be registered again, while the
	 * email can be reused (ADR 0001).
	 */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal,
			HttpServletRequest request, HttpServletResponse response) {
		administration.delete(principal.accountId(), id);
		sessionControl.endAll(id, ACCOUNT_DELETED, request, response);
		auditLog.record(AuditEvent.success(AuditAction.USER_ADMINISTRATION)
			.userId(principal.accountId())
			.targetUserId(id)
			.change("deleted", false, true)
			.request(request));
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(AccountNotFoundException.class)
	ProblemDetail accountNotFound() {
		return ProblemResponses.problem(HttpStatus.NOT_FOUND, "not_found", "No such Account.");
	}

	/** Not the acting Admin's own Account failing a different check: they can never target themselves. */
	@ExceptionHandler(SelfActionForbiddenException.class)
	ProblemDetail selfActionForbidden(SelfActionForbiddenException exception,
			@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
		auditRejected(principal, exception.accountId(), "self_action_forbidden", request);
		return ProblemResponses.problem(HttpStatus.FORBIDDEN, "self_action_forbidden",
				"An Admin cannot perform this action on their own Account.");
	}

	@ExceptionHandler(LastAdminException.class)
	ProblemDetail lastAdmin(LastAdminException exception, @AuthenticationPrincipal AccountPrincipal principal,
			HttpServletRequest request) {
		auditRejected(principal, exception.accountId(), "last_admin", request);
		return ProblemResponses.problem(HttpStatus.CONFLICT, "last_admin", "This change would leave no enabled Admin.");
	}

	private void auditRejected(AccountPrincipal principal, UUID targetId, String reason, HttpServletRequest request) {
		auditLog.record(AuditEvent.failure(AuditAction.USER_ADMINISTRATION, reason)
			.userId(principal.accountId())
			.targetUserId(targetId)
			.request(request));
	}

}
