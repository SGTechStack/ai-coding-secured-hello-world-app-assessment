package local.builderday.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves one correlation ID per request (existing MDC value, else W3C traceparent, else a random UUID) and exposes it
 * as MDC {@code trace.id} so every log line in the request shares it. Runs before the Spring Security chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
  /** The ECS field name, so every log line carries the correlation ID exactly once, as {@code trace.id}. */
  public static final String MDC_KEY = "trace.id";
  private static final Pattern TRACEPARENT =
      Pattern.compile("^[0-9a-fA-F]{2}-[0-9a-fA-F]{32}-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}$");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    MDC.put(MDC_KEY, traceId(request));
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String traceId(HttpServletRequest request) {
    String traceId = MDC.get(MDC_KEY);
    if (traceId != null && !traceId.isBlank()) return traceId;
    String traceparent = request.getHeader("traceparent");
    if (traceparent != null && TRACEPARENT.matcher(traceparent).matches()) {
      return traceparent.substring(3, 35).toLowerCase(Locale.ROOT);
    }
    return UUID.randomUUID().toString();
  }
}
