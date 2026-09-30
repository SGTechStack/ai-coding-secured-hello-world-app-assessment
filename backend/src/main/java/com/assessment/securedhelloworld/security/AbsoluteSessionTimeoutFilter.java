package com.assessment.securedhelloworld.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Spring Session's own timeout is an IDLE timeout (resets on every request). A session that
 * stays continuously active never expires under that alone, so this filter adds the independent
 * ABSOLUTE ceiling (IM8 as-11) by invalidating a session once it's older than
 * {@code app.security.session.absolute-timeout-minutes}, regardless of activity. Runs before
 * Spring Security loads the SecurityContext from the session, so an expired-by-age session is
 * indistinguishable from no session at all to everything downstream.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    public static final String CREATED_AT_ATTRIBUTE = "SESSION_ABSOLUTE_CREATED_AT";

    private final Duration absoluteTimeout;

    public AbsoluteSessionTimeoutFilter(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Instant createdAt = (Instant) session.getAttribute(CREATED_AT_ATTRIBUTE);
            if (createdAt != null && Instant.now().isAfter(createdAt.plus(absoluteTimeout))) {
                session.invalidate();
            }
        }
        filterChain.doFilter(request, response);
    }
}
