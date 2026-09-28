package sg.securedhello.session;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

/**
 * Resolves at most one session id, the first session cookie's (R-SES-007). The framework's resolver returns every
 * one and the session filter looks each up in turn, so many cookies would cost as many store queries, and a cookie
 * planted behind a dead one could still select the session.
 */
final class FirstSessionCookieResolver implements HttpSessionIdResolver {

    private final CookieHttpSessionIdResolver delegate = new CookieHttpSessionIdResolver();

    FirstSessionCookieResolver(CookieSerializer cookieSerializer) {
        delegate.setCookieSerializer(cookieSerializer);
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        List<String> ids = delegate.resolveSessionIds(request);
        return ids.isEmpty() ? ids : List.of(ids.getFirst());
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        delegate.setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        delegate.expireSession(request, response);
    }
}
