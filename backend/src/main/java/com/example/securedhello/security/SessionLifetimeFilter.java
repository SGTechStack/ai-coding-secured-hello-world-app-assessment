package com.example.securedhello.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the Session idle and absolute timeouts ({@link SessionControl#enforceLifetime}) once the
 * Session's authentication is loaded and before CSRF and authorization, so a request on an expired
 * Session is treated as a Visitor's.
 */
class SessionLifetimeFilter extends OncePerRequestFilter {

	private final SessionControl sessionControl;

	SessionLifetimeFilter(SessionControl sessionControl) {
		this.sessionControl = sessionControl;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		sessionControl.enforceLifetime(request, response);
		chain.doFilter(request, response);
	}

}
