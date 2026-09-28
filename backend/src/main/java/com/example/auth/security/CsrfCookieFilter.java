package com.example.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred CSRF token to render its cookie on every request.
 *
 * <p>{@code CookieCsrfTokenRepository} loads the token lazily: unless
 * something actually calls {@link CsrfToken#getToken()}, the {@code
 * XSRF-TOKEN} cookie never gets written to the response. The SPA needs that
 * cookie set on first load (before it has ever authenticated), so this filter
 * resolves the deferred token on every request rather than only when a
 * controller happens to touch it. This is the standard pattern from Spring
 * Security's own SPA-CSRF documentation.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
