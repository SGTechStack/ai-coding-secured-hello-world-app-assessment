package sg.securedhello.security.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * The session-bound token store (ADR-036) that never creates a session (ADR-040). {@code CsrfFilter} generates and
 * saves a token for any unsafe request that has none, and the session repository's save would create a session to
 * hold it. So a token is saved only into a session that already exists. The request whose save was skipped still
 * compares against a token that was never stored, and is refused.
 *
 * <p>A {@code null} save (rotation at login and logout) always passes through; it never creates a session either.
 *
 * <p>{@link #loadDeferredToken} is deliberately not overridden: the interface default builds the deferred token over
 * {@code this}, so the filter's save runs through {@link #saveToken}. Forwarding it would bind the save to the
 * wrapped repository and bypass the check.
 */
public final class SessionOnlyCsrfTokenRepository implements CsrfTokenRepository {

    private final HttpSessionCsrfTokenRepository delegate = new HttpSessionCsrfTokenRepository();

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return delegate.generateToken(request);
    }

    @Override
    public void saveToken(@Nullable CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token != null && request.getSession(false) == null) {
            return;
        }
        delegate.saveToken(token, request, response);
    }

    @Override
    public @Nullable CsrfToken loadToken(HttpServletRequest request) {
        return delegate.loadToken(request);
    }
}
