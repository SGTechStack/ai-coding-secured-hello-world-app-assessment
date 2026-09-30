package com.assessment.auth.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mints a correlation id server-side on every request (spec.md S4, filter 1).
 *
 * <p>Ordered at {@code SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1} (i.e. {@code -101}) so it
 * runs <em>before</em> Spring Security's {@code -100} (MDC:79). The constant lives on {@code
 * SecurityFilterProperties} in Boot 4; it was on {@code SecurityProperties} in Boot 3. A {@code @Component} filter would default to {@code Integer.MAX_VALUE} and
 * run after it (MDC:75) — and failed logins, lockouts and 403s are this application's entire audit
 * surface, so that ordering is not acceptable here.
 *
 * <p><strong>The inbound {@code X-Correlation-ID} header is never read</strong> (ticket 09, ratified
 * by ticket 12). With no gateway to strip it, an attacker could set their correlation id to collide
 * with a victim's and poison the audit trail — a sharper problem than the CRLF log-forging
 * obligation Std_Logging:276 would also trigger.
 *
 * <p><strong>The id is deliberately not placed in MDC.</strong> Ticket 12 ruled {@code
 * correlation.id} appears nowhere in this application: Std:132 makes the field conditional on a
 * workflow one {@code trace.id} cannot span, and the password reset — the only candidate — declined
 * it because every derivable key is a prohibited value. Log-side correlation is therefore carried by
 * Micrometer's {@code trace.id}/{@code span.id}, which this filter's position guarantees are already
 * established when the security chain rejects a request. The minted id is exposed as a request
 * attribute for off-MVC sites that need a per-request handle without touching the log schema.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String ATTRIBUTE = "com.assessment.auth.correlationId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    request.setAttribute(ATTRIBUTE, UUID.randomUUID().toString());
    chain.doFilter(request, response);
  }
}
