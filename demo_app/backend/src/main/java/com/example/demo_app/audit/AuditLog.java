package com.example.demo_app.audit;

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

  /** The actor, and the {@code ip}, of an event the application itself performs at startup. */
  public static final String SYSTEM = "system";

  private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

  private static final Pattern UNSAFE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

  /** Records {@code event} by {@code actor}. */
  public void record(AuditEvent event, Actor actor) {
    record(event, actor, Map.of());
  }

  /**
   * Records {@code event} by {@code actor} with extra event-specific {@code fields}, logged after
   * the standard ones in iteration order (see {@link #withTarget}).
   */
  public void record(AuditEvent event, Actor actor, Map<String, ?> fields) {
    String username = actor.username();
    write(event, username == null ? ANONYMOUS : username, actor.ip(), fields);
  }

  /**
   * Records {@code event} performed by the application itself, outside any request (e.g. the
   * admin bootstrap): {@code actor} and {@code ip} are both {@link #SYSTEM}.
   */
  public void recordSystem(AuditEvent event, Map<String, ?> fields) {
    write(event, SYSTEM, SYSTEM, fields);
  }

  /**
   * The event-specific fields of an action on another account: {@code target} (its username) first,
   * then {@code extra} in its iteration order.
   */
  public static Map<String, Object> withTarget(String target, Map<String, ?> extra) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("target", target);
    fields.putAll(extra);
    return fields;
  }

  /** Just {@code target}, for an event with no other specific fields. */
  public static Map<String, Object> withTarget(String target) {
    return withTarget(target, Map.of());
  }

  /**
   * Logs one line: the standard pairs, then {@code fields}, all neutralised, as SLF4J key-value
   * pairs and again as the {@code key=value} message text.
   */
  private void write(AuditEvent event, String actor, String ip, Map<String, ?> fields) {
    Map<String, String> pairs = new LinkedHashMap<>();
    pairs.put("event", event.name());
    pairs.put("actor", neutralise(actor));
    pairs.put("ip", neutralise(ip));
    pairs.put("outcome", event.outcome().name().toLowerCase(Locale.ROOT));
    fields.forEach((key, value) -> pairs.put(key, neutralise(String.valueOf(value))));

    LoggingEventBuilder line = LOG.atInfo();
    pairs.forEach(line::addKeyValue);
    line.log(
        pairs.entrySet().stream()
            .map(pair -> pair.getKey() + "=" + pair.getValue())
            .collect(Collectors.joining(" ")));
  }

  /**
   * {@code value} with every control, format and line/paragraph-separator character replaced by
   * {@code _}, so it can't break the line; {@code null} becomes {@code "null"}.
   */
  static String neutralise(String value) {
    return value == null ? "null" : UNSAFE.matcher(value).replaceAll("_");
  }
}
