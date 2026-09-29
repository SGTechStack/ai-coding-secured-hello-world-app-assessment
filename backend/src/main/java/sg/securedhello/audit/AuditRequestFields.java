package sg.securedhello.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerMapping;

import sg.securedhello.security.source.SourceKey;

/**
 * The fields every request-scoped audit row carries (Std §3.3), derived here from the request rather than passed by
 * call sites:
 * <ul>
 *   <li>{@code url.path}: the matched route pattern. Before a handler matches there is none, so the raw URI is used,
 *       neutralised and capped with a marker (REJ-081). The query string is never read.</li>
 *   <li>{@code http.request.method}: a known method, or {@code OTHER}, since the token is client-supplied.</li>
 *   <li>{@code source.ip_hash}: the keyed hash of the source key; the address itself is never logged (ADR-054).</li>
 *   <li>{@code session.hash}: the keyed hash of the current session's id, only if a session already exists. Nothing
 *       here creates one. A request marked {@link #SESSION_UNREAD} gets none, and its session is not looked up.</li>
 * </ul>
 */
final class AuditRequestFields {

    /**
     * Set on a request refused before its session was looked up, such as by the early rate limiter: reading the
     * session for {@code session.hash} would cost the very store lookup the refusal exists to avoid (ADR-017).
     */
    static final String SESSION_UNREAD = AuditRequestFields.class.getName() + ".sessionUnread";

    static final Set<String> KNOWN_METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS",
            "TRACE");

    static final String OTHER_METHOD = "OTHER";

    private final Function<HttpServletRequest, SourceKey> sourceKeys;
    private final LogFieldHasher hasher;
    private final int urlPathMaxLength;

    AuditRequestFields(Function<HttpServletRequest, SourceKey> sourceKeys, LogFieldHasher hasher,
            int urlPathMaxLength) {
        this.sourceKeys = sourceKeys;
        this.hasher = hasher;
        this.urlPathMaxLength = urlPathMaxLength;
    }

    /** The request fields, by ECS field name, in a stable order. */
    Map<String, Object> of(HttpServletRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("url.path", urlPath(request));
        fields.put("http.request.method", KNOWN_METHODS.contains(request.getMethod()) ? request.getMethod()
                : OTHER_METHOD);
        fields.put("source.ip_hash", hasher.sourceIpHash(sourceKeys.apply(request)));
        HttpSession session = request.getAttribute(SESSION_UNREAD) == null ? request.getSession(false) : null;
        if (session != null) {
            fields.put("session.hash", hasher.sessionHash(session.getId()));
        }
        return fields;
    }

    private String urlPath(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String pattern) {
            return AuditText.sanitise(pattern);
        }
        return AuditText.capped(request.getRequestURI(), urlPathMaxLength);
    }
}
