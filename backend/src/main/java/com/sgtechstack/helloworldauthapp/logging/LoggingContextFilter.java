package com.sgtechstack.helloworldauthapp.logging;

import com.sgtechstack.helloworldauthapp.auth.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a correlation id and the resolved client address,
 * and echoes the id back as {@code X-Request-Id}.
 *
 * <p>Without a correlation id, the audit lines this application writes are
 * individually readable and collectively useless. A failed login, the lockout
 * it triggered and the reset request that followed are three records with no
 * marker tying them to one caller's sequence of actions, so reconstructing an
 * incident means inferring causality from timestamps — which stops working the
 * moment two people are active at once.
 *
 * <p>The id is returned in a response header deliberately. A user reporting
 * "it said access denied" can quote it, which turns a vague report into an
 * exact log lookup. It is a random UUID carrying no information about the
 * session, the account or the server, so exposing it reveals nothing.
 *
 * <p>An inbound {@code X-Request-Id} is ignored rather than honoured. Accepting
 * it would let a caller stamp their own requests with somebody else's id, or
 * reuse one value forever, which is precisely the ability to corrupt the trail
 * that the id exists to establish. A deployment with a real ingress that
 * assigns trustworthy ids should read it from there instead — and that is a
 * change to make alongside {@code trusted-proxy-hops}, on the same "declare the
 * topology" reasoning.
 *
 * <p>Runs at highest precedence so the context is present for every other
 * filter's log output, including the rate-limit and request-size filters that
 * reject requests before Spring Security sees them. The {@code finally} block
 * is not optional: servlet containers pool threads, so a context left behind
 * would be inherited by the next unrelated request served on that thread and
 * attribute its log lines to the wrong caller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingContextFilter extends OncePerRequestFilter {

    /** Response header carrying the correlation id back to the caller. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ClientIpResolver clientIpResolver;

    public LoggingContextFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();

        MDC.put(LoggingContext.REQUEST_ID, requestId);
        MDC.put(LoggingContext.CLIENT_IP, clientIpResolver.resolve(request));
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(LoggingContext.REQUEST_ID);
            MDC.remove(LoggingContext.CLIENT_IP);
        }
    }
}
