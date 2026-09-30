package local.builderday.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import local.builderday.common.audit.SessionIds;
import local.builderday.common.logging.LogFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes the two Request log entries of every request to the {@code access} logger: one on arrival, one on completion
 * with the final status, duration and outcome. Runs directly after Spring Session's filter (so after the correlation ID)
 * and before the Spring Security chain, so it sees every final status, firewall and absolute-timeout rejections
 * included. It never creates, loads or refreshes a Session: it hashes the identifier the client sent. It records no
 * client IP, query string, referer, body, auth header or username. Best effort: logging never changes the response.
 */
@Component
@Order(RequestLogFilter.ORDER)
public class RequestLogFilter extends OncePerRequestFilter {
  public static final String LOGGER = "access";
  /** The request attribute the security chain sets to the authenticated User's id, for the completion line. */
  public static final String USER_ID_ATTRIBUTE = RequestLogFilter.class.getName() + ".userId";
  static final int ORDER = SessionRepositoryFilter.DEFAULT_ORDER + 1;
  static final int MAX_USER_AGENT_LENGTH = 512;
  private static final Pattern PATH_PARAMETER = Pattern.compile("(?i)(;|%3b)[^/]*");
  private static final Logger log = LoggerFactory.getLogger(LOGGER);

  private final Clock clock;
  private final SessionIds sessionIds;
  private final CookieSerializer cookieSerializer;

  RequestLogFilter(Clock clock, SessionIds sessionIds, CookieSerializer cookieSerializer) {
    this.clock = clock;
    this.sessionIds = sessionIds;
    this.cookieSerializer = cookieSerializer;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Instant start = clock.instant();
    long startNanos = System.nanoTime();
    Map<String, Object> shared = sharedFields(request);
    arrival(shared, start);
    boolean crashed = true;
    try {
      chain.doFilter(request, response);
      crashed = false;
    } finally {
      // An exception escaping the chain is a server failure, whatever status was set so far; it is logged where it is
      // handled, not here.
      completion(request, shared, startNanos, crashed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          : response.getStatus());
    }
  }

  /** The fields both lines carry; {@code null} when the request cannot be read, which skips both lines. */
  private Map<String, Object> sharedFields(HttpServletRequest request) {
    try {
      var fields = new LinkedHashMap<String, Object>();
      fields.put("event.kind", "event");
      fields.put("event.category", List.of("interface"));
      fields.put("event.action", "access");
      fields.put("interface.type", "api");
      fields.put("interface.direction", "inbound");
      fields.put("http.request.method", request.getMethod());
      fields.put("url.path", pathWithoutParameters(request.getRequestURI()));
      fields.put("user_agent.original", truncate(request.getHeader(HttpHeaders.USER_AGENT)));
      fields.put("session.hash", sessionIds.hash(requestedSessionId(request)));
      return fields;
    } catch (RuntimeException unreadable) {
      return null;
    }
  }

  private static void arrival(Map<String, Object> shared, Instant start) {
    if (shared == null) return;
    try {
      var fields = new LinkedHashMap<>(shared);
      fields.put("event.type", List.of("start"));
      fields.put("event.start", start.toString());
      LogFields.log(log.atInfo(), fields, requestLine(shared));
    } catch (RuntimeException ignored) {
      // Best effort by design: never change the response.
    }
  }

  private void completion(HttpServletRequest request, Map<String, Object> shared, long startNanos, int status) {
    if (shared == null) return;
    try {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      var fields = new LinkedHashMap<>(shared);
      fields.put("event.type", List.of("end"));
      fields.put("event.end", clock.instant().toString());
      fields.put("event.duration_ms", durationMs);
      fields.put("http.response.status_code", status);
      fields.put("event.outcome", status < 400 ? "success" : "failure");
      fields.put("user.id", request.getAttribute(USER_ID_ATTRIBUTE));
      if (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler) {
        fields.put("code.function.name", handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName());
        fields.put("http.route", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
      }
      var entry = status >= 500 ? log.atError() : status >= 400 ? log.atWarn() : log.atInfo();
      LogFields.log(entry, fields, requestLine(shared) + " " + status + " " + durationMs + "ms");
    } catch (RuntimeException ignored) {
      // Best effort by design: never change the response.
    }
  }

  private static String requestLine(Map<String, Object> shared) {
    return shared.get("http.request.method") + " " + shared.get("url.path");
  }

  /** Read from the cookie, never through the Session repository, so no Session is loaded or its lifetime extended. */
  private String requestedSessionId(HttpServletRequest request) {
    var values = cookieSerializer.readCookieValues(request);
    return values.isEmpty() ? null : values.getFirst();
  }

  /**
   * The URI path without path parameters ({@code ;…} segments, encoded or not), which could carry a raw session
   * identifier (IM8 lm-19). The arrival line is written before the firewall rejects them.
   */
  static String pathWithoutParameters(String uri) {
    return PATH_PARAMETER.matcher(uri).replaceAll("");
  }

  private static String truncate(String userAgent) {
    return userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH ? userAgent
        : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
  }
}
