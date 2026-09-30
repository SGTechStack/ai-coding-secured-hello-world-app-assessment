package com.example.auth.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/**
 * Captures every log event (root logger, so the additive {@code AUDIT} logger too) with its
 * key/value pairs and MDC <em>snapshotted at the moment of logging</em> -- {@code AuditLogger} puts
 * the actor into MDC only for the duration of one event, so reading MDC later would be wrong.
 * Thread-safe: the async password-reset email sender logs from a worker thread.
 */
public final class LogCapture implements AutoCloseable {

    public static final String AUDIT = "AUDIT";

    /** One captured event. */
    public record Event(
            String logger, Level level, String message, Map<String, String> keyValues, Map<String, String> mdc, String error) {

        public String kv(String key) {
            return keyValues.get(key);
        }

        public boolean isAudit() {
            return AUDIT.equals(logger);
        }

        /** Every piece of text this event could put into a log line. */
        public String everything() {
            return logger + " " + message + " " + keyValues + " " + mdc + " " + error;
        }
    }

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            Map<String, String> kv = new LinkedHashMap<>();
            List<KeyValuePair> pairs = event.getKeyValuePairs();
            if (pairs != null) {
                pairs.forEach(p -> kv.put(p.key, String.valueOf(p.value)));
            }
            IThrowableProxy throwable = event.getThrowableProxy();
            events.add(new Event(
                    event.getLoggerName(),
                    event.getLevel(),
                    event.getFormattedMessage(),
                    kv,
                    Map.copyOf(event.getMDCPropertyMap()),
                    throwable == null ? "" : throwable.getClassName() + ": " + throwable.getMessage()));
        }
    };

    private LogCapture() {
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
    }

    public static LogCapture start() {
        return new LogCapture();
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public List<Event> audit() {
        return events.stream().filter(Event::isAudit).toList();
    }

    public List<Event> audit(String message) {
        return events.stream().filter(Event::isAudit).filter(e -> e.message().equals(message)).toList();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
