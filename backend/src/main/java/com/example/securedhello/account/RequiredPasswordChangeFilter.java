package com.example.securedhello.account;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.config.ApiProperties;
import com.example.securedhello.logging.AccountIdentified;
import com.example.securedhello.security.SessionControl;
import com.example.securedhello.web.ProblemResponses;

/**
 * Enforces a Required Password Change: while the authenticated Account's
 * {@code password_change_required} is set, every request is refused with 403
 * {@code password_change_required} except the four that let the holder see who they are, choose a new
 * password, get a CSRF token for that request, and log out — and the public client-event report, which
 * says nothing about the Account. There is no grace period (ADR 0001).
 * <p>
 * Placed after authentication and authorization in the API filter chain, so an unauthenticated or
 * unauthorized request still gets its usual 401 or 403 and this refusal never reveals that an
 * endpoint exists. The flag is read from the Account on each request rather than from the Session, so
 * clearing it takes effect at once and a Session started before it was set cannot outlive it. Every
 * refusal is audited at WARN as {@code password-change-enforcement}.
 * <p>
 * This is also the one place in the chain where "the Account still exists" is checked, the degenerate
 * case of the same rule: the Account is read fresh per request, so no Session outlives the Account it
 * belongs to. An admin delete can only sweep the Sessions the store has indexed under the Account at
 * that moment, so a Session issued mid-delete would otherwise keep the authorities it was granted —
 * including {@code ROLE_ADMIN} — with no id left for an operator to revoke. The existence check
 * therefore runs <em>above</em> the allowed matcher below, unlike the flag check, and it ends the
 * Session rather than only refusing the request.
 * <p>
 * The Account read here is handed on through {@link CurrentAccount}, so nothing behind this filter
 * looks it up or checks its existence again.
 */
@Component
public class RequiredPasswordChangeFilter extends OncePerRequestFilter {

	/** The problem {@code code} and the audited {@code event.reason} of a refusal. */
	static final String CODE = "password_change_required";

	private final AccountRepository accounts;

	private final SessionControl sessionControl;

	private final AuditLog auditLog;

	private final RequestMatcher allowed;

	RequiredPasswordChangeFilter(AccountRepository accounts, SessionControl sessionControl, AuditLog auditLog,
			ApiProperties api) {
		this.accounts = accounts;
		this.sessionControl = sessionControl;
		this.auditLog = auditLog;
		this.allowed = new OrRequestMatcher(List.of(
				PathPatternRequestMatcher.pathPattern(HttpMethod.GET, api.path("/me")),
				PathPatternRequestMatcher.pathPattern(HttpMethod.PATCH, api.path("/me/password")),
				PathPatternRequestMatcher.pathPattern(HttpMethod.POST, api.path("/logout")),
				PathPatternRequestMatcher.pathPattern(HttpMethod.GET, api.path("/csrf")),
				// Defence in depth for a caller that does send a session cookie: the report is already
				// public and carries nothing about the Account, so refusing it would widen no control and
				// would only write an enforcement event, diluting a real security signal. The SPA itself
				// sends no credentials (ADR 0002), so its reports never reach this matcher at all.
				PathPatternRequestMatcher.pathPattern(HttpMethod.POST, api.path("/client-events"))));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AccountIdentified account)) {
			chain.doFilter(request, response);
			return;
		}
		Optional<Account> current = this.accounts.findById(account.accountId());
		if (current.isEmpty()) {
			// Above the allowed matcher on purpose: a deleted Account must not keep acting on the four
			// paths a Required Password Change still permits either. Revoking, not just refusing: the
			// deleting Admin's own sweep could not reach this Session, and nothing else ever will.
			this.sessionControl.endCurrent(AccountAdministrationController.ACCOUNT_DELETED, request, response);
			throw CurrentAccount.gone();
		}
		CurrentAccount.set(request, current.get());
		if (this.allowed.matches(request) || !current.get().isPasswordChangeRequired()) {
			chain.doFilter(request, response);
			return;
		}
		this.auditLog.record(PasswordChangeEnforcement.refused(account.accountId()).request(request));
		ProblemResponses.write(response, HttpStatus.FORBIDDEN, CODE,
				"The password must be changed before anything else.");
	}

}
