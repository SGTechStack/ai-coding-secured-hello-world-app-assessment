package org.eds.demo.common.logging;

/**
 * Makes request-supplied text safe to write into a log line, so an attacker cannot forge entries
 * with line breaks or hide them with control characters.
 */
public final class LogSanitizer {

  /** Longest request-supplied value logged; the rest is replaced by {@link #TRUNCATION_MARKER}. */
  static final int MAX_LENGTH = 100;

  static final String TRUNCATION_MARKER = "...";

  private static final String NULL_TEXT = "null";

  private LogSanitizer() {}

  /**
   * Escapes CR, LF and other control characters as visible text (backslash-r, backslash-n, a
   * backslash-u code) and caps the length.
   */
  public static String sanitize(String value) {
    if (value == null) {
      return NULL_TEXT;
    }
    var escaped = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      if (escaped.length() >= MAX_LENGTH) {
        return escaped.append(TRUNCATION_MARKER).toString();
      }
      char c = value.charAt(i);
      switch (c) {
        case '\r' -> escaped.append("\\r");
        case '\n' -> escaped.append("\\n");
        default -> {
          if (Character.isISOControl(c)) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
        }
      }
    }
    return escaped.toString();
  }
}
