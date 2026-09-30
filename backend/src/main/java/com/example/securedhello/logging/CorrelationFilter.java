package com.example.securedhello.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation ID and logs it once at start and once at end. Runs just after
 * the tracing filter (so {@code trace.id} is already set) and before Spring Security (so CSRF
 * rejections and other early exits are logged too).
 * <p>
 * The caller's {@code X-Correlation-ID} is used after escaping control characters and capping its
 * length; otherwise a UUID is generated. MDC holds only {@code correlation.id} and, once
 * authenticated, {@code user.id} — never a Session ID — and both are cleared in {@code finally}.
 * Request lines carry method, path, status, duration and outcome, never the client IP, query
 * string, headers or body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CorrelationFilter extends OncePerRequestFilter {

	public static final String CORRELATION_HEADER = "X-Correlation-ID";

	public static final String CORRELATION_ID = "correlation.id";

	public static final String USER_ID = "user.id";

	static final int MAX_CORRELATION_ID_LENGTH = 64;

	private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		String method = LogSanitizer.escapeControl(request.getMethod());
		String path = urlPath(request);
		MDC.put(CORRELATION_ID, correlationId(request.getHeader(CORRELATION_HEADER)));
		int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		try {
			log.atInfo()
				.addKeyValue("http.request.method", method)
				.addKeyValue("url.path", path)
				.log("Request started");
			chain.doFilter(request, response);
			status = response.getStatus();
		}
		finally {
			log.atInfo()
				.addKeyValue("http.request.method", method)
				.addKeyValue("url.path", path)
				.addKeyValue("http.response.status_code", status)
				.addKeyValue("event.duration", System.nanoTime() - start)
				.addKeyValue("event.outcome", (status < 400) ? "success" : "failure")
				.log("Request completed");
			MDC.remove(CORRELATION_ID);
			MDC.remove(USER_ID);
		}
	}

	/** The request path without query string or {@code ;} path parameters, safe for a log line. */
	public static String urlPath(HttpServletRequest request) {
		return LogSanitizer.escapeControl(request.getRequestURI().replaceAll(";[^/]*", ""));
	}

	private static String correlationId(String header) {
		if (header == null || header.isBlank()) {
			return UUID.randomUUID().toString();
		}
		String escaped = LogSanitizer.escapeControl(header.strip());
		return (escaped.length() > MAX_CORRELATION_ID_LENGTH) ? escaped.substring(0, MAX_CORRELATION_ID_LENGTH)
				: escaped;
	}

}
