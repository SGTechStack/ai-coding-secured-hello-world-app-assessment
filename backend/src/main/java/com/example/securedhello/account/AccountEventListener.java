package com.example.securedhello.account;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.notification.EmailService;
import com.example.securedhello.security.SessionControl;
import com.example.securedhello.web.ErrorCategory;

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
 * <p>
 * The side effects run in {@link TransactionSynchronization#afterCommit()}, not in a
 * {@code @TransactionalEventListener}: Spring only logs what an after-completion callback throws, so
 * a Session that could not be ended would still be reported to the caller as a success. From
 * {@code afterCommit} the failure reaches the caller, which answers 5xx. The committed change is
 * still audited when its Sessions could not be ended, and its holder is then not notified. An event
 * published with no transaction is refused, because there would be no commit to wait for.
 * <p>
 * Delivery is fire-and-forget (spec, Notifications): a notification failure is logged at ERROR
 * ({@code notification_failed}), never propagated. Otherwise a lock email that failed would turn the
 * locking attempt's generic refusal into a 500, an account-existence oracle that also loses the
 * refusal's audit events, and a committed change would be reported as a failure.
 */
@Component
class AccountEventListener {

	private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);

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

	@EventListener
	void on(AccountEvent event) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("An AccountEvent must be published inside the transaction making the change: "
					+ event.getClass().getSimpleName());
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				handle(event);
			}
		});
	}

	private void handle(AccountEvent event) {
		ServletRequestAttributes bound = (RequestContextHolder
			.getRequestAttributes() instanceof ServletRequestAttributes attributes) ? attributes : null;
		HttpServletRequest request = (bound != null) ? bound.getRequest() : null;
		HttpServletResponse response = (bound != null) ? bound.getResponse() : null;
		switch (event) {
			case AccountEvent.EnabledChanged changed -> {
				AuditEvent change = administration(changed.actingAdminId(), changed.targetId()).change("enabled",
						changed.before(), changed.after());
				if (changed.after()) {
					audit(change, request);
				}
				else {
					endAllThenAudit(changed.targetId(), ACCOUNT_DISABLED, request, response, change);
				}
			}
			case AccountEvent.RoleChanged changed -> endAllThenAudit(changed.targetId(), ROLE_CHANGED, request,
					response, administration(changed.actingAdminId(), changed.targetId()).change("role",
							changed.before(), changed.after()));
			case AccountEvent.Unlocked unlocked -> audit(administration(unlocked.actingAdminId(), unlocked.targetId())
				.change("locked", unlocked.wasLocked(), false), request);
			case AccountEvent.PasswordChangeRequired required -> endAllThenAudit(required.targetId(),
					PASSWORD_CHANGE_REQUIRED, request, response, PasswordChangeEnforcement
						.set(required.actingAdminId(), required.targetId(), required.alreadyRequired()));
			case AccountEvent.Deleted deleted -> endAllThenAudit(deleted.targetId(),
					AccountAdministrationController.ACCOUNT_DELETED, request, response,
					administration(deleted.actingAdminId(), deleted.targetId()).change("deleted", false, true));
			case AccountEvent.PasswordChanged changed -> {
				endAllThenAudit(changed.accountId(), PASSWORD_CHANGE, request, response,
						AuditEvent.success(AuditAction.PASSWORD_RESET).eventType(CHANGE).userId(changed.accountId()),
						changed.requirementCleared() ? PasswordChangeEnforcement.cleared(changed.accountId()) : null);
				notifySafely(() -> emailService.notifyPasswordChanged(changed.email()));
			}
			case AccountEvent.PasswordResetCompleted completed -> {
				endAllThenAudit(completed.accountId(), PASSWORD_RESET, request, response,
						AuditEvent.success(AuditAction.PASSWORD_RESET).userId(completed.accountId()),
						completed.requirementCleared() ? PasswordChangeEnforcement.cleared(completed.accountId())
								: null);
				notifySafely(() -> emailService.notifyPasswordResetCompleted(completed.email()));
			}
			case AccountEvent.PasswordResetIssued issued -> {
				auditLog.record(AuditEvent.success(AuditAction.PASSWORD_RESET)
					.userId(issued.accountId())
					.request(issued.httpMethod(), issued.urlPath()));
				notifySafely(() -> emailService.sendPasswordResetLink(issued.email(), issued.resetLink()));
			}
			case AccountEvent.Locked locked -> notifySafely(() -> emailService.notifyAccountLocked(locked.email()));
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

	/**
	 * Ends the Account's Sessions, then audits the committed change even if that failed, and rethrows
	 * the failure so that nothing after it (the notification) runs and the caller does not see 2xx.
	 * A {@code null} event is skipped.
	 */
	private void endAllThenAudit(UUID accountId, String reason, HttpServletRequest request,
			HttpServletResponse response, AuditEvent... events) {
		try {
			endAll(accountId, reason, request, response);
		}
		finally {
			for (AuditEvent event : events) {
				if (event != null) {
					audit(event, request);
				}
			}
		}
	}

	/**
	 * Sends one notification, logging a failure the same way the global handler logs an unexpected
	 * exception, and never rethrowing it: the change it reports has already committed and been audited.
	 */
	private static void notifySafely(Runnable notification) {
		try {
			notification.run();
		}
		catch (RuntimeException ex) {
			ErrorCategory category = ErrorCategory.of(ex);
			log.atError()
				.setCause(ex)
				.addKeyValue("error_code", "notification_failed")
				.addKeyValue("error_category", category.value())
				.addKeyValue("error_follow_up_action", category.followUpAction())
				.log("Unexpected exception while sending a notification");
		}
	}

	private void audit(AuditEvent event, HttpServletRequest request) {
		auditLog.record((request != null) ? event.request(request) : event);
	}

}
