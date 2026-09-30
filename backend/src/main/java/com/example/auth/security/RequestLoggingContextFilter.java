package com.example.auth.security;

import com.example.auth.audit.AuditLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the per-request logging context into MDC (App-Standards LOG, ADR-0011) so every log line --
 * audit or not -- carries it without each call site repeating it:
 *
 * <ul>
 *   <li>{@code correlation.id}: the caller's {@code X-Correlation-ID} if it is a safe token,
 *       otherwise a new UUID; echoed back in the response header.
 *   <li>{@code source.ip}: the client address (after the prod proxy-trust rules resolve it).
 *   <li>{@code session.hash}: a truncated SHA-256 of the requested session id -- links pre-login
 *       events to one browser session without logging the (credential-equivalent) id itself.
 *   <li>{@code user.id}: the authenticated user's {@code public_id}, if any.
 * </ul>
 * {@code trace.id}/{@code span.id} are added by Micrometer Tracing. Runs right after {@code
 * SecurityContextHolderFilter}, so the session's security context is already available.
 */
public class RequestLoggingContextFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final String CORRELATION_ID = "correlation.id";
    private static final String SOURCE_IP = "source.ip";
    private static final String SESSION_HASH = "session.hash";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || !SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(CORRELATION_HEADER, correlationId);

        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(SOURCE_IP, request.getRemoteAddr());
        String sessionId = request.getRequestedSessionId();
        if (sessionId != null) {
            MDC.put(SESSION_HASH, sessionHash(sessionId));
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            MDC.put(AuditLogger.USER_ID, user.getPublicId().toString());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID);
            MDC.remove(SOURCE_IP);
            MDC.remove(SESSION_HASH);
            MDC.remove(AuditLogger.USER_ID);
        }
    }

    static String sessionHash(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
