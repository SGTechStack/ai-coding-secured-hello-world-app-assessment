package local.builderday.common.logging;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Writes structured key/value log lines that stay valid next to the request's MDC ({@code trace.id}, {@code user.id}).
 * Boot's ECS encoder refuses a key written twice, so each field is rendered once.
 */
public final class LogFields {
  private LogFields() {}

  /**
   * Logs {@code message} with the non-null {@code fields} as key/value pairs; {@code null} values are omitted. A field
   * whose MDC entry holds the same value is left to the MDC. A field whose MDC entry differs wins: that MDC entry is
   * left off this one line, and restored afterwards.
   */
  public static void log(LoggingEventBuilder entry, Map<String, ?> fields, String message) {
    var shadowed = new HashMap<String, String>();
    for (var field : fields.entrySet()) {
      if (field.getValue() == null) continue;
      String mdcValue = MDC.get(field.getKey());
      if (field.getValue().equals(mdcValue)) continue;
      entry = entry.addKeyValue(field.getKey(), field.getValue());
      if (mdcValue != null) shadowed.put(field.getKey(), mdcValue);
    }
    shadowed.keySet().forEach(MDC::remove);
    try {
      entry.log(message);
    } finally {
      shadowed.forEach(MDC::put);
    }
  }
}
