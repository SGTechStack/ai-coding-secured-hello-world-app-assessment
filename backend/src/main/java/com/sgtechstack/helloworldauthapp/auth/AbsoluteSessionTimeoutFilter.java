package com.sgtechstack.helloworldauthapp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Caps how long a session may live in total, regardless of activity.
 *
 * <p>The container's idle timeout only measures the gap between requests, so a
 * session that is used periodically is renewed indefinitely. That makes a
 * stolen session cookie valid for as long as the attacker keeps exercising it —
 * there is no point at which the session simply ends. This filter adds that
 * point.
 *
 * <p>On expiry the session is invalidated and the security context cleared, and
 * the request is allowed to continue as anonymous rather than being answered
 * here. The authorization layer then refuses it through the normal entry point,
 * so the client sees the same 401 shape it would get from any other
 * unauthenticated request, and whitelisted routes keep working.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AbsoluteSessionTimeoutFilter.class);

    private final Duration maximumAge;

    public AbsoluteSessionTimeoutFilter(Duration maximumAge) {
        this.maximumAge = maximumAge;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null) {
            Duration age = Duration.between(Instant.ofEpochMilli(session.getCreationTime()), Instant.now());

            if (age.compareTo(maximumAge) >= 0) {
                log.info("Session exceeded absolute lifetime ageSeconds={} maximumSeconds={}",
                        age.toSeconds(), maximumAge.toSeconds());
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
