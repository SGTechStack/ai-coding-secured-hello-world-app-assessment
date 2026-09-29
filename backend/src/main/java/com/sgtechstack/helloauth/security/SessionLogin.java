package com.sgtechstack.helloauth.security;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Turns a verified user into an authenticated server-side session, doing what Spring
 * Security's form-login filter would do for a JSON login endpoint.
 */
@Component
public class SessionLogin {

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	private final SecurityContextRepository securityContextRepository;

	private final SessionAuthenticationStrategy sessionStrategy;

	public SessionLogin(SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
		this.securityContextRepository = securityContextRepository;
		this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
				// Session fixation protection: the pre-login session ID stops working.
				new ChangeSessionIdAuthenticationStrategy(),
				// The pre-login CSRF token is replaced; the client fetches a fresh one.
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	public void establish(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = user.toAuthentication();
		this.sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = this.contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		this.contextHolder.setContext(context);
		this.securityContextRepository.saveContext(context, request, response);
	}

}
