package com.example.hello.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Structured security audit log: one line per event as {@code event=X key=value ...}. Values
 * are sanitised against log injection (no line breaks / tabs) and truncated. Never pass
 * passwords or tokens here.
 */
@Component
public class AuditLogger {

  private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
  private static final int MAX_VALUE_LENGTH = 128;

  public void event(String event, String... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("keyValues must be key/value pairs");
    }
    StringBuilder line = new StringBuilder("event=").append(sanitize(event));
    for (int i = 0; i < keyValues.length; i += 2) {
      line.append(' ').append(sanitize(keyValues[i])).append('=').append(sanitize(keyValues[i + 1]));
    }
    AUDIT.info(line.toString());
  }

  static String sanitize(String value) {
    if (value == null || value.isEmpty()) {
      return "-";
    }
    String bounded = value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) : value;
    return bounded.replaceAll("[\\r\\n\\t ]", "_");
  }
}
