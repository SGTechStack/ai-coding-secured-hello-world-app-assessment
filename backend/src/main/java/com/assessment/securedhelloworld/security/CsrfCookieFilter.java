package com.assessment.securedhelloworld.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security's SPA CSRF pattern: the CSRF token is normally rendered lazily (only when a
 * view/template reads it), which means a pure-JSON API might never actually set the XSRF-TOKEN
 * cookie. Touching the token attribute on every request forces it to render, so the frontend can
 * always read the cookie and echo it back as the X-XSRF-TOKEN header on state-changing calls.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
