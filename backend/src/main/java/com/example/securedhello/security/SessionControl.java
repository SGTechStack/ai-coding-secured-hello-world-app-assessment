package com.example.securedhello.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

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

import com.example.securedhello.audit.AuditAction;
import com.example.securedhello.audit.AuditEvent;
import com.example.securedhello.audit.AuditLog;
import com.example.securedhello.config.SessionProperties;
import com.example.securedhello.logging.AccountIdentified;

/**
 * Session control: starts, checks and ends authenticated Sessions.
 * <p>
 * Starting a Session replaces the Session ID (so a planted ID is useless), replaces the CSRF token
 * (so a token issued before login is rejected afterwards, ADR 0002), stores the authentication in
 * the Session, records its start time from the {@link Clock}, and ends the Account's oldest other
 * Sessions beyond the configured maximum (default 1). Every request on a Session is checked against
 * the idle and absolute timeouts. Ending a Session removes its CSRF token and invalidates it, which
 * also expires the session cookie. Every Session the system ends, or that expires, is audited as
 * {@code session-end} with the reason.
 */
@Component
public class SessionControl {

	/** Why the system ended a Session: the {@code event.reason} of its {@code session-end} event. */
	public static final String IDLE_TIMEOUT = "idle_timeout";

	public static final String ABSOLUTE_TIMEOUT = "absolute_timeout";

	public static final String NEW_LOGIN = "new_login";

	public static final String LOGIN_FAILED = "login_failed";

	private static final String STARTED_AT = SessionControl.class.getName() + ".startedAt";

	private static final String LAST_SEEN_AT = SessionControl.class.getName() + ".lastSeenAt";

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder
		.getContextHolderStrategy();

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	private final CsrfAuthenticationStrategy csrfRotation;

	private final CsrfLogoutHandler csrfRemoval;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final SessionProperties properties;

	private final Clock clock;

	private final AuditLog auditLog;

	SessionControl(CsrfTokenRepository csrfTokens, FindByIndexNameSessionRepository<? extends Session> sessions,
			SessionProperties properties, Clock clock, AuditLog auditLog) {
		this.csrfRotation = new CsrfAuthenticationStrategy(csrfTokens);
		this.csrfRemoval = new CsrfLogoutHandler(csrfTokens);
		this.sessions = sessions;
		this.properties = properties;
		this.clock = clock;
		this.auditLog = auditLog;
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
		HttpSession session = request.getSession();
		Instant now = clock.instant();
		session.setAttribute(STARTED_AT, now);
		session.setAttribute(LAST_SEEN_AT, now);
		// Visitor Sessions keep Spring Session's short (idle) row lifetime; an authenticated one is
		// stored until its absolute limit so the idle check above it can detect and audit expiry.
		session.setMaxInactiveInterval((int) properties.absoluteTimeout().toSeconds());
		endOldestBeyondLimit(authentication.getName(), Set.of(previousId, currentId), request);
	}

	/** Ends the request's Session: no CSRF token, no authentication, session cookie expired. */
	public void end(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		csrfRemoval.logout(request, response, authentication);
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	/**
	 * Ends the authenticated Session the request carries, if any, after a failed login, so a
	 * half-trusted browser is not left logged in. A Visitor's Session (CSRF token only) is kept.
	 */
	public void endAfterFailedLogin(HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = accountAuthentication();
		if (authentication != null && request.getSession(false) != null) {
			endBySystem(authentication, LOGIN_FAILED, request, response);
		}
	}

	/**
	 * Applies the idle and absolute timeouts to the request's authenticated Session. An expired
	 * Session is ended before the request goes further, so the request continues as a Visitor's;
	 * a live one has its last use recorded.
	 */
	void enforceLifetime(HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = accountAuthentication();
		HttpSession session = request.getSession(false);
		if (authentication == null || session == null) {
			return;
		}
		Instant now = clock.instant();
		String expiry = expiryReason((Instant) session.getAttribute(STARTED_AT),
				(Instant) session.getAttribute(LAST_SEEN_AT), now);
		if (expiry != null) {
			endBySystem(authentication, expiry, request, response);
		}
		else {
			session.setAttribute(LAST_SEEN_AT, now);
		}
	}

	/**
	 * Ends every Session of the Account, including the request's own if it is one of them, and
	 * audits each as {@code session-end} with the given reason (or the timeout it had already
	 * passed). Used by Password Change, reset confirmation and admin actions.
	 */
	public void endAll(UUID accountId, String reason, HttpServletRequest request, HttpServletResponse response) {
		HttpSession current = request.getSession(false);
		String currentId = (current != null) ? current.getId() : null;
		for (Session session : sessions.findByPrincipalName(accountId.toString()).values()) {
			if (session.getId().equals(currentId)) {
				end(contextHolder.getContext().getAuthentication(), request, response);
			}
			// Also after end(): a no-op once the Session is invalidated, but it guarantees the stored row goes.
			removeStored(session, accountId, reason, request);
		}
	}

	/**
	 * Deletes a stored Session and audits it. A Session that had already passed a timeout is
	 * recorded with that timeout, not the caller's reason.
	 */
	private void removeStored(Session session, UUID accountId, String reason, HttpServletRequest request) {
		String expiry = expiryReason(session.getAttribute(STARTED_AT), session.getAttribute(LAST_SEEN_AT),
				clock.instant());
		sessions.deleteById(session.getId());
		audit(accountId, (expiry != null) ? expiry : reason, request);
	}

	private String expiryReason(Instant startedAt, Instant lastSeenAt, Instant now) {
		if (startedAt == null || reached(startedAt, properties.absoluteTimeout(), now)) {
			return ABSOLUTE_TIMEOUT;
		}
		if (lastSeenAt == null || reached(lastSeenAt, properties.idleTimeout(), now)) {
			return IDLE_TIMEOUT;
		}
		return null;
	}

	private static boolean reached(Instant from, Duration limit, Instant now) {
		return !now.isBefore(from.plus(limit));
	}

	private void endBySystem(Authentication authentication, String reason, HttpServletRequest request,
			HttpServletResponse response) {
		UUID accountId = ((AccountIdentified) authentication.getPrincipal()).accountId();
		end(authentication, request, response);
		audit(accountId, reason, request);
	}

	/** The request's authentication when it is an Account's, else null. */
	private Authentication accountAuthentication() {
		Authentication authentication = contextHolder.getContext().getAuthentication();
		return (authentication != null && authentication.getPrincipal() instanceof AccountIdentified)
				? authentication : null;
	}

	/**
	 * The current Session is saved only when the response commits, so it is not yet stored under the
	 * principal; its IDs before and after the change are excluded, and the newest others are kept.
	 */
	private void endOldestBeyondLimit(String principalName, Set<String> currentIds, HttpServletRequest request) {
		Map<String, ? extends Session> existing = sessions.findByPrincipalName(principalName);
		List<Session> others = new ArrayList<>(existing.values());
		others.removeIf((session) -> currentIds.contains(session.getId()));
		others.sort(Comparator.<Session, Instant>comparing(Session::getCreationTime).reversed());
		UUID accountId = UUID.fromString(principalName);
		others.stream()
			.skip(properties.maxConcurrentPerAccount() - 1L)
			.forEach((session) -> removeStored(session, accountId, NEW_LOGIN, request));
	}

	private void audit(UUID accountId, String reason, HttpServletRequest request) {
		auditLog.record(AuditEvent.success(AuditAction.SESSION_END).reason(reason).userId(accountId).request(request));
	}

}
