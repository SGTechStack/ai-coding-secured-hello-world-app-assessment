package sg.securedhello.security.csrf;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.session.shedding.AnonymousSessionShedding;
import sg.securedhello.session.shedding.NewSessionShedException;

/**
 * {@code GET /api/csrf}: the token bootstrap (ADR-036), and the only route that creates an anonymous session
 * (ADR-040). Caching headers are Spring Security's defaults, not set here. A session-less request is refused while
 * new anonymous sessions are shed (ADR-041).
 */
@RestController
public class CsrfController {

    private final AnonymousSessionShedding shedding;

    public CsrfController(AnonymousSessionShedding shedding) {
        this.shedding = shedding;
    }

    /** The token and the header to send it in. The parameter name is left out on purpose: it is never accepted. */
    public record CsrfTokenResponse(String headerName, String token) {
    }

    @GetMapping("/api/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request, CsrfToken csrfToken) {
        // Load-bearing (ADR-040; T-SES-027): the token repository saves only into an existing session, so the session
        // must exist before the deferred token is resolved below. Without this line no session, and no usable token,
        // is ever created, and nothing else fails. Shedding is decided first, so a refused request creates no row, and
        // only for a caller with no session: one that has a session is never refused (ADR-041).
        if (request.getSession(false) == null && shedding.shedsNewSession()) {
            throw new NewSessionShedException();
        }
        request.getSession(true);
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
