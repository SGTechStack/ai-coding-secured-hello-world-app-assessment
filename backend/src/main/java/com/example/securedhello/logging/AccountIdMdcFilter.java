package com.example.securedhello.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the authenticated Account's UUID to MDC as {@code user.id}. Placed in the security chain
 * after the Session's authentication is loaded; {@link CorrelationFilter} clears it.
 */
public class AccountIdMdcFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AccountIdentified account) {
			MDC.put(CorrelationFilter.USER_ID, account.accountId().toString());
		}
		chain.doFilter(request, response);
	}

}
