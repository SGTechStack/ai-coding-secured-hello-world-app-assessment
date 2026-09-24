package com.example.demo_app.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/**
 * Writes one {@code INFO} line per security-relevant event to the dedicated {@code AUDIT} logger,
 * through the SLF4J key-value API: {@code event}, {@code actor}, {@code ip}, {@code outcome}, then
 * any event-specific fields (e.g. {@code target} for admin actions). ECS JSON console logging
 * (every profile but {@code dev}) turns the pairs into top-level JSON fields; the message repeats
 * them as {@code key=value} text so the plain {@code dev} console shows them too.
 *
 * <p>Every value is neutralised: control, format and line/paragraph-separator characters become
 * {@code _}, so a user-supplied value can never start a forged line. Never pass a password, token
 * or session ID.
 */
@Component
public class AuditLog {

  /** The actor when nobody is signed in. */
  public static final String ANONYMOUS = "anonymous";

  private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

  private static final Pattern UNSAFE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

  /** Records {@code event} by {@code actor} ({@code null} for {@link #ANONYMOUS}). */
  public void record(AuditEvent event, String actor, HttpServletRequest request) {
    record(event, actor, request, Map.of());
  }

  /**
   * Records {@code event} by {@code actor} ({@code null} for {@link #ANONYMOUS}) with extra
   * event-specific {@code fields}, logged after the standard ones in iteration order.
   */
  public void record(
      AuditEvent event, String actor, HttpServletRequest request, Map<String, ?> fields) {
    Map<String, String> pairs = new LinkedHashMap<>();
    pairs.put("event", event.name());
    pairs.put("actor", actor == null ? ANONYMOUS : neutralise(actor));
    pairs.put("ip", neutralise(request.getRemoteAddr()));
    pairs.put("outcome", event.outcome().name().toLowerCase(Locale.ROOT));
    fields.forEach((key, value) -> pairs.put(key, neutralise(String.valueOf(value))));

    LoggingEventBuilder line = LOG.atInfo();
    pairs.forEach(line::addKeyValue);
    line.log(
        pairs.entrySet().stream()
            .map(pair -> pair.getKey() + "=" + pair.getValue())
            .collect(Collectors.joining(" ")));
  }

  static String neutralise(String value) {
    return value == null ? "null" : UNSAFE.matcher(value).replaceAll("_");
  }
}
