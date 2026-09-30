package sg.example.helloauth.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the request's correlation ID on every log line written while handling it: the caller's
 * {@code X-Correlation-ID}, sanitised, or a new UUID. Runs just after tracing starts the trace,
 * before anything else can log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-ID";
    static final String MDC_KEY = "correlation.id";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, correlationId(request))) {
            chain.doFilter(request, response);
        }
    }

    private static String correlationId(HttpServletRequest request) {
        String supplied = LogSanitizer.strip(request.getHeader(HEADER));
        if (!StringUtils.hasText(supplied)) {
            return UUID.randomUUID().toString();
        }
        return supplied.length() > MAX_LENGTH ? supplied.substring(0, MAX_LENGTH) : supplied;
    }
}
