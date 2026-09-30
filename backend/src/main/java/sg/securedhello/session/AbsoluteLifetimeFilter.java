package sg.securedhello.session;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.filter.OncePerRequestFilter;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.error.ErrorCode;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.user.SignedInUser;

/**
 * Bounds a session's lifetime whatever traffic it sees. It sits after {@code CorsFilter} and the header writer, so its
 * 401 carries the SPA's CORS headers and the security headers, and before {@code CsrfFilter} (ADR-038), so an expired
 * session posting a mutation is answered 401, not first refused for CSRF.
 *
 * <p>Authenticated branch ({@link SessionAttributes#AUTH_INSTANT} present): once the {@code Clock} reaches the auth
 * instant plus the absolute lifetime, the session is invalidated and the request answered 401
 * {@code AUTHENTICATION_FAILED}, whatever its route. Row 9 is written first, while the session still exists, so it
 * carries that session's hash (R-AUD-003). The lifetime runs from sign-in, not from the session's creation,
 * which {@code changeSessionId()} preserves (T-SES-006).
 *
 * <p>Anonymous branch (no {@code AUTH_INSTANT}): the session expires at its creation plus the idle window W, never
 * later (REJ-091; R-SES-009). Spring Session extends the expiry on every access, so each request shortens the interval
 * to what is left of W. Once nothing is left the session is invalidated. A non-positive interval is never set,
 * because the cleanup job never matches a row that carries one. These times are the session's own, stamped by Spring
 * Session, because its expiry arithmetic uses them.
 */
public class AbsoluteLifetimeFilter extends OncePerRequestFilter {

    private final Duration window;
    private final Duration absolute;
    private final Clock clock;
    private final ProblemDetailWriter writer;
    private final AuditEmitter audit;

    /**
     * @param window   W, the session repository's resolved idle interval
     * @param absolute a signed-in session's lifetime from its {@code AUTH_INSTANT}
     * @param clock    the application clock, which stamped {@code AUTH_INSTANT}
     * @param writer   writes the 401
     * @param audit    writes row 9
     */
    public AbsoluteLifetimeFilter(Duration window, Duration absolute, Clock clock, ProblemDetailWriter writer,
            AuditEmitter audit) {
        this.window = window;
        this.absolute = absolute;
        this.clock = clock;
        this.writer = writer;
        this.audit = audit;
    }

    /** W: how long an anonymous session lives from its creation. */
    Duration window() {
        return window;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute(SessionAttributes.AUTH_INSTANT) instanceof Instant authInstant) {
                if (!clock.instant().isBefore(authInstant.plus(absolute))) {
                    auditExpiry();
                    session.invalidate();
                    writer.write(request, response, ErrorCode.AUTHENTICATION_FAILED);
                    return;
                }
            } else {
                pinAnonymous(session);
            }
        }
        chain.doFilter(request, response);
    }

    /** Row 9, for the signed-in principal the session holds; only password sign-in stamps the auth instant. */
    private void auditExpiry() {
        SignedInUser.current().ifPresent(user ->
                audit.emit(AuditEvent.SESSION_EXPIRED, AccountContext.sessionExpired(user.id())));
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
