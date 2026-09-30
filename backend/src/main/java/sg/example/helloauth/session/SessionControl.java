package sg.example.helloauth.session;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Session control: the sessions stored in the database, looked up by the principal that owns
 * them. It ends every session of an Account at once, wherever it was opened, and bounds each
 * logged-in session's lifetime by an idle and an absolute timeout on the application
 * {@link Clock}. (Spring Session's own idle expiry also applies, on wall-clock time.)
 */
@Component
public class SessionControl {

    private static final String STARTED_AT = SessionControl.class.getName() + ".STARTED_AT";
    private static final String LAST_SEEN_AT = SessionControl.class.getName() + ".LAST_SEEN_AT";

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;
    private final SessionProperties properties;

    SessionControl(FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock,
            SessionProperties properties) {
        this.sessions = sessions;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Ends every session of the Account with this username, as stored on the Account. Their
     * cookies get 401 from then on.
     */
    public void endAllSessions(String username) {
        sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
    }

    /** Starts the idle and absolute timeouts of a session that has just logged in. */
    public void loggedIn(HttpSession session) {
        Instant now = clock.instant();
        session.setAttribute(STARTED_AT, now);
        session.setAttribute(LAST_SEEN_AT, now);
    }

    /**
     * Enforces the timeouts by ending an expired session. It must run before the security
     * context is loaded, so the request then carries no session: protected endpoints answer 401
     * and anonymous ones start afresh.
     */
    public Filter lifetimeFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    enforceLifetime(session);
                }
                chain.doFilter(request, response);
            }
        };
    }

    /** Lets Spring Security find a user's other sessions, so a new login can end the older one. */
    public SessionRegistry sessionRegistry() {
        return registry(sessions);
    }

    private void enforceLifetime(HttpSession session) {
        if (session.getAttribute(STARTED_AT) instanceof Instant startedAt
                && session.getAttribute(LAST_SEEN_AT) instanceof Instant lastSeenAt) {
            Instant now = clock.instant();
            if (now.isAfter(lastSeenAt.plus(properties.idleTimeout()))
                    || now.isAfter(startedAt.plus(properties.absoluteTimeout()))) {
                session.invalidate();
            } else {
                session.setAttribute(LAST_SEEN_AT, now);
            }
        }
    }

    private static <S extends Session> SessionRegistry registry(FindByIndexNameSessionRepository<S> sessions) {
        return new SpringSessionBackedSessionRegistry<>(sessions);
    }
}
