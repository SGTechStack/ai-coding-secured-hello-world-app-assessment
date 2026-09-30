package local.builderday.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/** Captures events from one logger so tests can assert structured fields and MDC as they would be emitted. */
public final class AuditLogCapture implements AutoCloseable {
  private final Logger logger;
  // Snapshots the MDC as the event is logged, as the file appenders do, so tests see what the line carried.
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      super.append(event);
    }
  };

  public AuditLogCapture(String loggerName) {
    logger = (Logger) LoggerFactory.getLogger(loggerName);
    appender.start();
    logger.addAppender(appender);
  }

  public List<ILoggingEvent> events() { return List.copyOf(appender.list); }

  /**
   * The fields of an event as rendered into the structured log: its MDC ({@code trace.id}, {@code user.id}) and its
   * key/value pairs. A field in both would be a key written twice, which the ECS encoder refuses, so it fails here too.
   */
  public static Map<String, Object> fields(ILoggingEvent event) {
    var fields = new HashMap<String, Object>(event.getMDCPropertyMap());
    if (event.getKeyValuePairs() != null) {
      for (var pair : event.getKeyValuePairs()) {
        if (fields.put(pair.key, pair.value) != null) throw new IllegalStateException("Duplicate field " + pair.key);
      }
    }
    return fields;
  }

  /**
   * The line as the named logger's real destination appender (e.g. {@code audit}/{@code AUDIT}, or the root logger's
   * {@code FILE}) encodes it. Boot's ECS encoder refuses a key written twice, so this throws for such a line.
   */
  public static String render(String loggerName, String appenderName, ILoggingEvent event) {
    var destination = (OutputStreamAppender<ILoggingEvent>) ((Logger) LoggerFactory.getLogger(loggerName))
        .getAppender(appenderName);
    return new String(destination.getEncoder().encode(event), StandardCharsets.UTF_8);
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
