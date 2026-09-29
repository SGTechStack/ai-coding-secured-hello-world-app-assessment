package sg.securedhello.testsupport;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.slf4j.LoggerFactory;

import sg.securedhello.audit.AuditEmitter;

/**
 * Captures the audit rows written while it is open, each rendered through the application's ECS formatter and
 * flattened by {@link EcsJson}. Open it in a try-with-resources block around the requests under test.
 */
public final class AuditCapture implements AutoCloseable {

    private final Logger audit = (Logger) LoggerFactory.getLogger(AuditEmitter.AUDIT_LOGGER);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private AuditCapture() {
        appender.start();
        audit.addAppender(appender);
    }

    public static AuditCapture start() {
        return new AuditCapture();
    }

    /**
     * Every captured row, in order. The copy is taken under the appender's own lock ({@code AppenderBase.doAppend} is
     * synchronized on it), so a row written meanwhile from another thread cannot break the read.
     */
    public List<Map<String, Object>> rows() {
        List<ILoggingEvent> events;
        synchronized (appender) {
            events = List.copyOf(appender.list);
        }
        return events.stream().map(event -> EcsJson.flatten(EcsJson.render(event).strip())).toList();
    }

    /** The captured rows with this {@code message}, the row discriminator (ADR-055). */
    public List<Map<String, Object>> withMessage(String message) {
        return rows().stream().filter(row -> message.equals(row.get("message"))).toList();
    }

    @Override
    public void close() {
        audit.detachAppender(appender);
        appender.stop();
    }
}
