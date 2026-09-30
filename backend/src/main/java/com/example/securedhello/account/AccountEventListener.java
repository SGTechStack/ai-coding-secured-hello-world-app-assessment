package com.example.securedhello.account;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.security.SessionControl;

/**
 * The one owner of what follows a committed Account change. Runs after the publishing transaction
 * commits, so nothing is ended, audited as a success or sent for a change that rolled back. Always in
 * the same order: end the Account's Sessions, then audit, then notify. A holder is never told about a
 * change whose Sessions are still live, and the audit trail never misses a change that was mailed.
 * <p>
 * On a request thread the request and response are those the change was made on. Ending Sessions
 * there also ends the request's own Session when it is one of them, and the audit events carry the
 * request's method and path. With no request bound (a direct service call), the stored Sessions are
 * still ended and the events simply carry no request fields. Refusals are not events: they are
 * audited where they are handled, because they do not commit a change.
 */
@Component
class AccountEventListener {

	/** The {@code event.reason} of each {@code session-end} that an Account change causes. */
	static final String ACCOUNT_DISABLED = "account_disabled";

	static final String ROLE_CHANGED = "role_changed";

	static final String PASSWORD_CHANGE_REQUIRED = "password_change_required";

	static final String PASSWORD_CHANGE = "password_change";

	static final String PASSWORD_RESET = "password_reset";

	/** {@code event.type} of a Password Change, which is audited as {@code password-reset}. */
	static final String CHANGE = "change";

	private final SessionControl sessionControl;

	private final AuditLog auditLog;

	private final EmailService emailService;

	AccountEventListener(SessionControl sessionControl, AuditLog auditLog, EmailService emailService) {
		this.sessionControl = sessionControl;
		this.auditLog = auditLog;
		this.emailService = emailService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void on(AccountEvent event) {
		ServletRequestAttributes bound = (RequestContextHolder
			.getRequestAttributes() instanceof ServletRequestAttributes attributes) ? attributes : null;
		HttpServletRequest request = (bound != null) ? bound.getRequest() : null;
		HttpServletResponse response = (bound != null) ? bound.getResponse() : null;
		switch (event) {
			case AccountEvent.EnabledChanged changed -> {
				if (!changed.after()) {
					endAll(changed.targetId(), ACCOUNT_DISABLED, request, response);
				}
				audit(administration(changed.actingAdminId(), changed.targetId()).change("enabled", changed.before(),
						changed.after()), request);
			}
			case AccountEvent.RoleChanged changed -> {
				endAll(changed.targetId(), ROLE_CHANGED, request, response);
				audit(administration(changed.actingAdminId(), changed.targetId()).change("role", changed.before(),
						changed.after()), request);
			}
			case AccountEvent.Unlocked unlocked -> audit(administration(unlocked.actingAdminId(), unlocked.targetId())
				.change("locked", unlocked.wasLocked(), false), request);
			case AccountEvent.PasswordChangeRequired required -> {
				endAll(required.targetId(), PASSWORD_CHANGE_REQUIRED, request, response);
				audit(PasswordChangeEnforcement.set(required.actingAdminId(), required.targetId(),
						required.alreadyRequired()), request);
			}
			case AccountEvent.Deleted deleted -> {
				endAll(deleted.targetId(), AccountAdministrationController.ACCOUNT_DELETED, request, response);
				audit(administration(deleted.actingAdminId(), deleted.targetId()).change("deleted", false, true),
						request);
			}
			case AccountEvent.PasswordChanged changed -> {
				endAll(changed.accountId(), PASSWORD_CHANGE, request, response);
				audit(AuditEvent.success(AuditAction.PASSWORD_RESET).eventType(CHANGE).userId(changed.accountId()),
						request);
				if (changed.requirementCleared()) {
					audit(PasswordChangeEnforcement.cleared(changed.accountId()), request);
				}
				emailService.notifyPasswordChanged(changed.email());
			}
			case AccountEvent.PasswordResetCompleted completed -> {
				endAll(completed.accountId(), PASSWORD_RESET, request, response);
				audit(AuditEvent.success(AuditAction.PASSWORD_RESET).userId(completed.accountId()), request);
				if (completed.requirementCleared()) {
					audit(PasswordChangeEnforcement.cleared(completed.accountId()), request);
				}
				emailService.notifyPasswordResetCompleted(completed.email());
			}
			case AccountEvent.PasswordResetIssued issued -> {
				auditLog.record(AuditEvent.success(AuditAction.PASSWORD_RESET)
					.userId(issued.accountId())
					.request(issued.httpMethod(), issued.urlPath()));
				emailService.sendPasswordResetLink(issued.email(), issued.resetLink());
			}
			case AccountEvent.Locked locked -> emailService.notifyAccountLocked(locked.email());
		}
	}

	private static AuditEvent administration(UUID actingAdminId, UUID targetId) {
		return AuditEvent.success(AuditAction.USER_ADMINISTRATION).userId(actingAdminId).targetUserId(targetId);
	}

	private void endAll(UUID accountId, String reason, HttpServletRequest request, HttpServletResponse response) {
		if (request != null && response != null) {
			sessionControl.endAll(accountId, reason, request, response);
		}
		else {
			sessionControl.endAll(accountId, reason);
		}
	}

	private void audit(AuditEvent event, HttpServletRequest request) {
		auditLog.record((request != null) ? event.request(request) : event);
	}

}
