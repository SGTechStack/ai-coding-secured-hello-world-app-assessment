package com.example.securedhello.account;

import java.io.IOException;
import java.util.List;

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
 * endpoint exists. The flag is read from the Account on each request rather than from the Session,
 * so clearing it takes effect at once and a Session started before it was set cannot outlive it.
 * Every refusal is audited at WARN as {@code password-change-enforcement}.
 */
@Component
public class RequiredPasswordChangeFilter extends OncePerRequestFilter {

	/** The problem {@code code} and the audited {@code event.reason} of a refusal. */
	static final String CODE = "password_change_required";

	private final AccountRepository accounts;

	private final AuditLog auditLog;

	private final RequestMatcher allowed;

	RequiredPasswordChangeFilter(AccountRepository accounts, AuditLog auditLog, ApiProperties api) {
		this.accounts = accounts;
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
		if (authentication == null || !(authentication.getPrincipal() instanceof AccountIdentified account)
				|| this.allowed.matches(request)) {
			chain.doFilter(request, response);
			return;
		}
		if (!isRequired(account)) {
			chain.doFilter(request, response);
			return;
		}
		this.auditLog.record(PasswordChangeEnforcement.refused(account.accountId()).request(request));
		ProblemResponses.write(response, HttpStatus.FORBIDDEN, CODE,
				"The password must be changed before anything else.");
	}

	/** A Session whose Account no longer exists is left to the endpoint, which treats it as logged out. */
	private boolean isRequired(AccountIdentified account) {
		return this.accounts.findById(account.accountId()).filter(Account::isPasswordChangeRequired).isPresent();
	}

}
