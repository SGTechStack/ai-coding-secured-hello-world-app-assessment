package sg.securedhello.security.ratelimit;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.filter.OncePerRequestFilter;

import sg.securedhello.audit.AccountContext;
import sg.securedhello.audit.AuditEmitter;
import sg.securedhello.audit.AuditEvent;
import sg.securedhello.audit.InvalidSessionReason;
import sg.securedhello.audit.SourceThrottleReason;
import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.security.ratelimit.AuthRateLimiter.Refusal;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.security.source.SourceKeyResolver;
import sg.securedhello.session.FirstSessionCookieResolver;

/**
 * The early per-source filter, first in the security chain, ahead of {@code SecurityContextHolderFilter} (ADR-038).
 * It has no principal and needs none. For each request it derives the source key once ({@link SourceKeyResolver}),
 * then, before anything can cost a session lookup or a password hash:
 * <ol>
 *   <li>refuses a request that presents a session cookie if its source has spent its session-miss budget
 *       (ADR-017), so the refusal itself costs no lookup and creates no session;</li>
 *   <li>takes a token from every {@link RateLimit.Axis#SOURCE} row whose route the request matches, and refuses the
 *       request if one is spent (ADR-010). A refused login never reaches the provider, so no failure event fires and
 *       no account counter moves.</li>
 * </ol>
 * On the way out it charges the source one miss if the request presented a session id that did not resolve. The
 * session repository caches the lookup {@code SessionManagementFilter} already made, so this costs no second query
 * (T-RL-021). The same observation writes row 11, {@code UNKNOWN_OR_EXPIRED}, and a request that presented more than
 * one session cookie writes row 11, {@code DUPLICATE_SESSION_COOKIE} (R-SES-007; T-SES-029; T-AUD-038). Both are
 * tier-1 keyed rows, and neither is written for a refused request, which never reached the lookup (T-RL-023).
 *
 * <p>A refusal is 429 {@code TOO_MANY_REQUESTS} with an integer {@code Retry-After}, written before
 * {@code CorsFilter} runs, so it adds the SPA's CORS response headers itself; otherwise the SPA could not read it. It
 * writes the tier-1 keyed row 5 (ADR-019).
 */
public final class SourceRateLimitFilter extends OncePerRequestFilter {

    /** Adds CORS response headers to a refusal for an allowed origin, and nothing for any other request. */
    @FunctionalInterface
    public interface RefusalHeaders {
        void apply(HttpServletRequest request, HttpServletResponse response) throws IOException;
    }

    private final AuthRateLimiter limiter;
    private final SourceKeyResolver sourceKeys;
    private final HttpSessionIdResolver sessionIds;
    private final AuditEmitter audit;
    private final ProblemDetailWriter writer;
    private final RefusalHeaders refusalHeaders;
    private final Map<RateLimit, RequestMatcher> sourceRows;

    public SourceRateLimitFilter(AuthRateLimiter limiter, SourceKeyResolver sourceKeys,
            HttpSessionIdResolver sessionIds, AuditEmitter audit, ProblemDetailWriter writer,
            RefusalHeaders refusalHeaders) {
        this.limiter = limiter;
        this.sourceKeys = sourceKeys;
        this.sessionIds = sessionIds;
        this.audit = audit;
        this.writer = writer;
        this.refusalHeaders = refusalHeaders;
        this.sourceRows = Arrays.stream(RateLimit.values())
                .filter(limit -> limit.axis() == RateLimit.Axis.SOURCE)
                .collect(Collectors.toMap(Function.identity(),
                        limit -> PathPatternRequestMatcher.withDefaults().matcher(limit.method(), limit.path()),
                        (first, second) -> first, () -> new EnumMap<>(RateLimit.class)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SourceKey source = sourceKeys.resolve(request);
        // The resolver reads cookies only; the repository lookup happens later, and only if this request is admitted.
        boolean presentsSession = !sessionIds.resolveSessionIds(request).isEmpty();
        if (presentsSession) {
            Optional<Refusal> refusal = limiter.missRefusal(source);
            if (refusal.isPresent()) {
                refuse(request, response, refusal.get(), SourceThrottleReason.RATE_LIMITED_SOURCE_MISSES);
                return;
            }
        }
        for (RateLimit limit : matching(request)) {
            Optional<Refusal> refusal = limiter.tryConsume(limit, source.value());
            if (refusal.isPresent()) {
                refuse(request, response, refusal.get(), SourceThrottleReason.RATE_LIMITED_SOURCE);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (presentsSession) {
                observeSession(request, source);
            }
        }
    }

    /** Charges a miss and writes row 11 for a session id that did not resolve, and row 11 for duplicate cookies. */
    private void observeSession(HttpServletRequest request, SourceKey source) {
        if (FirstSessionCookieResolver.presentedDuplicates(request)) {
            audit.emit(AuditEvent.SESSION_INVALID,
                    AccountContext.invalidSession(InvalidSessionReason.DUPLICATE_SESSION_COOKIE));
        }
        if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
            limiter.recordMiss(source);
            audit.emit(AuditEvent.SESSION_INVALID,
                    AccountContext.invalidSession(InvalidSessionReason.UNKNOWN_OR_EXPIRED));
        }
    }

    private List<RateLimit> matching(HttpServletRequest request) {
        return sourceRows.entrySet().stream().filter(row -> row.getValue().matches(request)).map(Map.Entry::getKey)
                .toList();
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, Refusal refusal,
            SourceThrottleReason reason) throws IOException {
        // Refused before the session store was consulted; the audit row must not consult it either.
        request.setAttribute(AuditEmitter.SESSION_UNREAD_ATTRIBUTE, Boolean.TRUE);
        audit.emit(AuditEvent.SOURCE_THROTTLED, AccountContext.sourceThrottled(reason));
        refusalHeaders.apply(request, response);
        TooManyRequests.write(writer, request, response, refusal);
    }
}
