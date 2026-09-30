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
 * planted behind a dead one could still select the session. A request that presented more than one is marked, and
 * {@link #presentedDuplicates} reads the mark, so its audit row (row 11, {@code DUPLICATE_SESSION_COOKIE}) is written
 * once by whoever audits the request, not on every resolution.
 */
public final class FirstSessionCookieResolver implements HttpSessionIdResolver {

    /** Set on a request that presented more than one session cookie. */
    private static final String DUPLICATES = FirstSessionCookieResolver.class.getName() + ".duplicates";

    private final CookieHttpSessionIdResolver delegate = new CookieHttpSessionIdResolver();

    FirstSessionCookieResolver(CookieSerializer cookieSerializer) {
        delegate.setCookieSerializer(cookieSerializer);
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        List<String> ids = delegate.resolveSessionIds(request);
        if (ids.size() > 1) {
            request.setAttribute(DUPLICATES, Boolean.TRUE);
        }
        return ids.isEmpty() ? ids : List.of(ids.getFirst());
    }

    /** Whether {@code request} presented more than one session cookie, once its session ids have been resolved. */
    public static boolean presentedDuplicates(HttpServletRequest request) {
        return request.getAttribute(DUPLICATES) != null;
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
