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

    /** Every captured row, in order. */
    public List<Map<String, Object>> rows() {
        return events().stream().map(event -> EcsJson.flatten(EcsJson.render(event).strip())).toList();
    }

    /**
     * A snapshot of the captured events. Rows can be appended from a request thread while a test reads them, and
     * logback's {@code AppenderBase.doAppend} holds the appender's lock while it appends, so copy under that lock.
     */
    private List<ILoggingEvent> events() {
        synchronized (appender) {
            return List.copyOf(appender.list);
        }
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
