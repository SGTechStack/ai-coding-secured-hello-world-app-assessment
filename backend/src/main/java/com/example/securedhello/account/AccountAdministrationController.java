package com.example.securedhello.account;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;

/**
 * {@code /admin/users}: Account administration for Admins. The filter chain and the service both
 * require the Admin role. Viewing the list is audited, because it exposes every Account's email.
 */
@RestController
@RequestMapping("${app.api.base-path}/admin/users")
class AccountAdministrationController {

	private final AccountAdministrationService administration;

	private final AuditLog auditLog;

	AccountAdministrationController(AccountAdministrationService administration, AuditLog auditLog) {
		this.administration = administration;
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

}
