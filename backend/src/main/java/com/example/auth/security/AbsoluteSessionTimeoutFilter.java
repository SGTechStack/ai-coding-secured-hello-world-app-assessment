package com.example.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the App-Standards Standalone track's 8-hour absolute session
 * lifetime: a session lasting longer than that is invalidated regardless of
 * how recently it was used, which the servlet container's own idle timeout
 * ({@code server.servlet.session.timeout}) cannot express on its own (idle
 * timeout only resets on activity and would let a continuously-active
 * session live forever).
 *
 * <p>Registered before {@code SecurityContextHolderFilter} in {@code
 * SecurityConfig} so an expired session is invalidated before that filter
 * loads a (now-stale) {@code SecurityContext} from it -- the request is then
 * correctly treated as anonymous.
 *
 * <p>The expiry check itself is a pure static method so it's unit-testable
 * without a real {@link HttpSession} (whose creation time isn't otherwise
 * settable in tests); this filter is thin glue around it.
 */
public final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    public static final Duration DEFAULT_MAX_SESSION_AGE = Duration.ofHours(8);

    private final Duration maxSessionAge;

    public AbsoluteSessionTimeoutFilter(Duration maxSessionAge) {
        this.maxSessionAge = maxSessionAge;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && isExpired(session.getCreationTime(), System.currentTimeMillis(), maxSessionAge)) {
            session.invalidate();
        }
        filterChain.doFilter(request, response);
    }

    static boolean isExpired(long sessionCreationTimeMillis, long nowMillis, Duration maxAge) {
        Instant createdAt = Instant.ofEpochMilli(sessionCreationTimeMillis);
        Instant now = Instant.ofEpochMilli(nowMillis);
        return createdAt.plus(maxAge).isBefore(now);
    }
}
