package com.assessment.auth.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The custom encoder (spec.md S11).
 *
 * <p>It exists because the native {@code logging.structured.format.*} route leaves two enforced
 * constraints undischarged, and this is the only mechanism available under that route:
 *
 * <ol>
 *   <li><strong>Boundary masking</strong> (Std_Logging:326). A last line of defence: nothing in this
 *       application deliberately logs a secret, but masking at the encoder means a future careless
 *       {@code addKeyValue} cannot leak one. Boot's ECS formatter has no masking of any kind, so
 *       without this class the constraint is simply undischarged.
 *   <li><strong>Error nesting</strong> (Std_Logging:186), as a guarantee rather than a repair — see
 *       below.
 * </ol>
 *
 * <p><strong>Deliberate deviation from Encoder:408.</strong> The recipe <em>strips</em> the error
 * keys when no throwable is present. This encoder never strips them. Three of this application's
 * security events carry an error semantic with no throwable behind them — lockout, access denial
 * and forced-change denial — and the recipe's silent drop violates Std_Logging:164: nine fields
 * across the three most important security events would vanish with no failing build.
 *
 * <p><strong>Measured behaviour of Boot 4.0.7:</strong> its ECS formatter already folds a dotted
 * key-value pair into a nested object, so {@code error.code} arrives here as
 * {@code error: {code: ...}}, and it does <em>not</em> strip error keys when no throwable is
 * present. {@link #nestErrorObject} is therefore a no-op on the current version. It is kept
 * deliberately: it costs nothing, and it makes the nesting a property of this application rather
 * than of a Boot implementation detail a patch release could change. That is also why the
 * encoder's test is a named required gate (story 1.24).
 *
 * <p>Registered in {@code logback-spring.xml} on all three appenders.
 */
public class MaskingStructuredLogEncoder extends StructuredLogEncoder {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String MASK = "***MASKED***";

  /** Exact ECS keys whose value is replaced wholesale. */
  private static final Set<String> MASKED_KEYS =
      Set.of(
          "password",
          "newPassword",
          "currentPassword",
          "password_hash",
          "passwordHash",
          "token",
          "tokenHash",
          "token_hash",
          "resetToken",
          "secret",
          "authorization",
          "cookie",
          "set-cookie",
          "session.id",
          "sessionId",
          "user.name",
          "username",
          "email");

  /** Flat ECS keys that are folded into a nested {@code error} object. */
  private static final List<String> ERROR_KEYS =
      List.of("error.code", "error.category", "error.follow_up_action");

  @Override
  public byte[] encode(ILoggingEvent event) {
    byte[] encoded = super.encode(event);
    if (encoded == null || encoded.length == 0) {
      return encoded;
    }
    String line = new String(encoded, StandardCharsets.UTF_8);
    String trimmed = line.stripTrailing();
    if (!trimmed.startsWith("{")) {
      // Not JSON (a header or a non-structured format). Leave it untouched.
      return encoded;
    }
    try {
      JsonNode parsed = MAPPER.readTree(trimmed);
      if (!(parsed instanceof ObjectNode root)) {
        return encoded;
      }
      nestErrorObject(root);
      mask(root, "");
      return (MAPPER.writeValueAsString(root) + System.lineSeparator())
          .getBytes(StandardCharsets.UTF_8);
    } catch (Exception ex) {
      // A logging encoder must never take the application down, and it must never silently swallow
      // a line either. Logback's status system surfaces this on CONSOLE (ticket 12, Std:262).
      addWarn("Failed to post-process structured log line; emitting it unchanged", ex);
      return encoded;
    }
  }

  /**
   * Folds {@code error.*} flat keys into a nested {@code error} object, creating it when absent
   * rather than stripping the keys.
   */
  private void nestErrorObject(ObjectNode root) {
    ObjectNode error = null;
    for (String flatKey : ERROR_KEYS) {
      JsonNode value = root.get(flatKey);
      if (value == null) {
        continue;
      }
      root.remove(flatKey);
      if (error == null) {
        JsonNode existing = root.get("error");
        error = existing instanceof ObjectNode node ? node : MAPPER.createObjectNode();
      }
      error.set(flatKey.substring("error.".length()), value);
    }
    if (error != null) {
      root.set("error", error);
    }
  }

  /**
   * Masks depth-first, matching on both the leaf name and the full dotted path.
   *
   * <p>The path matters: Boot's ECS formatter folds {@code user.name} into
   * {@code user: {name: ...}}, so a leaf-only match would have to blacklist the bare word
   * {@code name} — far too broad, and it would mangle unrelated fields.
   */
  private void mask(ObjectNode node, String prefix) {
    List<String> toMask = new ArrayList<>();
    for (Map.Entry<String, JsonNode> field : node.properties()) {
      String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
      JsonNode value = field.getValue();
      if (value instanceof ObjectNode child) {
        mask(child, path);
      } else if (isMasked(field.getKey(), path) && !value.isNull()) {
        // Collected rather than replaced in place: the entry view is not contractually a live,
        // writable view of the node in Jackson 3.
        toMask.add(field.getKey());
      }
    }
    for (String key : toMask) {
      node.put(key, MASK);
    }
  }

  private boolean isMasked(String leaf, String path) {
    return MASKED_KEYS.contains(leaf) || MASKED_KEYS.contains(path);
  }
}
