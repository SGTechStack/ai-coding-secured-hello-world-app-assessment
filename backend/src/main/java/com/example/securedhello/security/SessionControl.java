package com.example.securedhello.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import com.example.securedhello.config.SessionProperties;

/**
 * Session control: starts and ends authenticated Sessions.
 * <p>
 * Starting a Session replaces the Session ID (so a planted ID is useless), replaces the CSRF token
 * (so a token issued before login is rejected afterwards, ADR 0002), stores the authentication in
 * the Session, and ends the Account's oldest other Sessions beyond the configured maximum (default
 * 1). Ending a Session removes its CSRF token and invalidates it, which also expires the session
 * cookie.
 */
@Component
public class SessionControl {

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder
		.getContextHolderStrategy();

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	private final CsrfAuthenticationStrategy csrfRotation;

	private final CsrfLogoutHandler csrfRemoval;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final int maxConcurrentPerAccount;

	SessionControl(CsrfTokenRepository csrfTokens, FindByIndexNameSessionRepository<? extends Session> sessions,
			SessionProperties properties) {
		this.csrfRotation = new CsrfAuthenticationStrategy(csrfTokens);
		this.csrfRemoval = new CsrfLogoutHandler(csrfTokens);
		this.sessions = sessions;
		this.maxConcurrentPerAccount = properties.maxConcurrentPerAccount();
	}

	/** Starts an authenticated Session for the request; its principal name is the Account's UUID. */
	public void start(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		String previousId = request.getSession().getId();
		String currentId = request.changeSessionId();
		csrfRotation.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		endOldestBeyondLimit(authentication.getName(), Set.of(previousId, currentId));
	}

	/** Ends the request's Session: no CSRF token, no authentication, session cookie expired. */
	public void end(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		csrfRemoval.logout(request, response, authentication);
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	/**
	 * The current Session is saved only when the response commits, so it is not yet stored under the
	 * principal; its IDs before and after the change are excluded, and the newest others are kept.
	 */
	private void endOldestBeyondLimit(String principalName, Set<String> currentIds) {
		Map<String, ? extends Session> existing = sessions.findByPrincipalName(principalName);
		List<Session> others = new ArrayList<>(existing.values());
		others.removeIf((session) -> currentIds.contains(session.getId()));
		others.sort(Comparator.<Session, Instant>comparing(Session::getCreationTime).reversed());
		others.stream().skip(maxConcurrentPerAccount - 1L).forEach((session) -> sessions.deleteById(session.getId()));
	}

}
