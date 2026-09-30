package local.builderday.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.common.logging.LogFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place security audit events are written: the {@code audit} logger (routed to the dedicated, long-retention
 * ECS audit destination), the shared ECS field set, level by outcome and request correlation. Best effort: never
 * throws, so auditing can never change a response. Callers pass only application-owned values, never submitted input.
 */
public final class SecurityAudit {
  public static final String LOGGER = "audit";
  private static final Logger log = LoggerFactory.getLogger(LOGGER);

  /** {@link #ERROR} is a system failure: ECS outcome {@code failure}, logged at ERROR. */
  public enum Outcome { SUCCESS, FAILURE, ERROR }

  /**
   * One security event. Every event shares these fields; {@code extra} carries event-specific ECS fields.
   *
   * @param reason stable lowercase reason, e.g. {@code invalid_credentials}
   * @param userId the account concerned, or {@code null} when unknown
   */
  public record Event(String action, String category, String type, Outcome outcome, String reason, UUID userId,
      Map<String, Object> extra) {
    public Event(String action, String category, String type, Outcome outcome, String reason, UUID userId) {
      this(action, category, type, outcome, reason, userId, Map.of());
    }
  }

  /**
   * The correlation fields of a request, captured on its thread so work it hands to a background thread can still be
   * audited against it. {@code trace.id} travels separately, in the MDC the executor copies.
   */
  public record RequestContext(String path, String method, String sourceIp, String sessionHash) {
    /** {@code null} stays {@code null}. */
    public static RequestContext capture(HttpServletRequest request) {
      if (request == null) return null;
      return new RequestContext(request.getRequestURI(), request.getMethod(), request.getRemoteAddr(),
          SessionIds.hashCurrent(request));
    }
  }

  private SecurityAudit() {}

  /**
   * @param request the request that caused the event, or {@code null} for scheduled work. An unreadable request is
   *     not audited at all rather than with guessed fields.
   */
  public static void record(HttpServletRequest request, Event event) {
    try {
      recordFrom(RequestContext.capture(request), event);
    } catch (RuntimeException ignored) {
      // Best effort by design, as in recordFrom.
    }
  }

  /** @param request the captured request that caused the event, or {@code null} for scheduled work */
  public static void recordFrom(RequestContext request, Event event) {
    try {
      var entry = switch (event.outcome()) {
        case SUCCESS -> log.atInfo();
        case FAILURE -> log.atWarn();
        case ERROR -> log.atError();
      };
      var fields = new LinkedHashMap<String, Object>();
      fields.put("event.action", event.action());
      fields.put("event.category", List.of(event.category()));
      fields.put("event.type", List.of(event.type()));
      fields.put("event.outcome", event.outcome() == Outcome.SUCCESS ? "success" : "failure");
      fields.put("event.reason", event.reason());
      fields.put("user.id", event.userId() == null ? null : event.userId().toString());
      if (request != null) {
        fields.put("url.path", request.path());
        fields.put("http.request.method", request.method());
        fields.put("source.ip", request.sourceIp());
        fields.put("session.hash", request.sessionHash());
      }
      fields.putAll(event.extra());
      // Absent values are omitted rather than logged as null.
      LogFields.log(entry, fields, event.action() + " " + event.reason());
    } catch (RuntimeException ignored) {
      // Best effort by design: never change the response, never fall back to unsafe logging.
    }
  }
}
