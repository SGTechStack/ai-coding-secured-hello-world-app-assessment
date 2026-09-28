package sg.securedhello.security.csrf;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/csrf}: the token bootstrap (ADR-036), and the only route that creates an anonymous session
 * (ADR-040). Caching headers are Spring Security's defaults, not set here.
 */
@RestController
public class CsrfController {

    /** The token and the header to send it in. The parameter name is left out on purpose: it is never accepted. */
    public record CsrfTokenResponse(String headerName, String token) {
    }

    @GetMapping("/api/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request, CsrfToken csrfToken) {
        // Load-bearing (ADR-040; T-SES-027): the token repository saves only into an existing session, so the session
        // must exist before the deferred token is resolved below. Without this line no session, and no usable token,
        // is ever created, and nothing else fails.
        request.getSession(true);
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
