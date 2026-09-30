package org.eds.demo.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String MDC_REQUEST_ID = "request.id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Validate as UUID to prevent log injection via a crafted X-Request-Id header value
    String requestId;
    String incoming = request.getHeader(REQUEST_ID_HEADER);
    try {
      requestId =
          (incoming != null) ? UUID.fromString(incoming).toString() : UUID.randomUUID().toString();
    } catch (IllegalArgumentException e) {
      requestId = UUID.randomUUID().toString();
    }

    MDC.put(MDC_REQUEST_ID, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
