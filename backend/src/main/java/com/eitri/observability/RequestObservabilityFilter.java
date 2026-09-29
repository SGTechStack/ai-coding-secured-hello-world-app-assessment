package com.eitri.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs one safe start/end pair around every servlet request without inspecting request content. */
public final class RequestObservabilityFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);

    private final Ticker ticker;

    public RequestObservabilityFilter(Ticker ticker) {
        this.ticker = ticker;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = ticker.read();
        LOGGER.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("network"))
                .addKeyValue("event.type", List.of("start"))
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", request.getRequestURI())
                .setMessage("Request received.")
                .log();

        boolean returnedNormally = false;
        try {
            filterChain.doFilter(request, response);
            returnedNormally = true;
        } finally {
            int status = returnedNormally || response.getStatus() >= 400
                    ? response.getStatus()
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            long elapsedNanos = Math.max(0, ticker.read() - started);
            LOGGER.atInfo()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("network"))
                    .addKeyValue("event.type", List.of("end"))
                    .addKeyValue("event.outcome", status < 400 ? "success" : "failure")
                    .addKeyValue("http.request.method", request.getMethod())
                    .addKeyValue("url.path", request.getRequestURI())
                    .addKeyValue("http.response.status_code", status)
                    .addKeyValue("event.duration_ms", TimeUnit.NANOSECONDS.toMillis(elapsedNanos))
                    .setMessage("Request completed.")
                    .log();
        }
    }
}
