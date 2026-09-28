package sg.securedhello.session;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds a session's lifetime whatever traffic it sees. It sits after {@code SecurityContextHolderFilter} and before
 * {@code CsrfFilter} (ADR-038), so an expired session posting a mutation is not first answered with a CSRF refusal.
 *
 * <p>Anonymous branch (no {@link SessionAttributes#AUTH_INSTANT}): the session expires at its creation plus the idle
 * window W, never later (REJ-091; R-SES-009). Spring Session extends the expiry on every access, so each request
 * shortens the interval to what is left of W. Once nothing is left the session is invalidated. A non-positive
 * interval is never set, because the cleanup job never matches a row that carries one.
 *
 * <p>The times are the session's own, stamped by Spring Session, because its expiry arithmetic uses them. The
 * authenticated branch, eight hours from {@code AUTH_INSTANT}, lands with sign-in (ticket 10).
 */
public class AbsoluteLifetimeFilter extends OncePerRequestFilter {

    private final Duration window;

    /** @param window W, the session repository's resolved idle interval */
    public AbsoluteLifetimeFilter(Duration window) {
        this.window = window;
    }

    /** W: how long an anonymous session lives from its creation. */
    Duration window() {
        return window;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionAttributes.AUTH_INSTANT) == null) {
            pinAnonymous(session);
        }
        chain.doFilter(request, response);
    }

    private void pinAnonymous(HttpSession session) {
        long leftMillis = session.getCreationTime() + window.toMillis() - session.getLastAccessedTime();
        long leftSeconds = leftMillis / 1000;
        if (leftSeconds <= 0) {
            session.invalidate();
        } else {
            session.setMaxInactiveInterval((int) leftSeconds);
        }
    }
}
