package sg.example.helloauth.support;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/**
 * Captures what one logger emits during each test with a Logback {@link ListAppender}. Register
 * it with {@code @RegisterExtension}: the {@code audit} logger for audit events, or the root
 * logger for the application log (which the {@code audit} logger doesn't write to).
 */
public final class LogCapture implements BeforeEachCallback, AfterEachCallback {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(String loggerName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
    }

    public static LogCapture audit() {
        return new LogCapture("audit");
    }

    public static LogCapture application() {
        return new LogCapture(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        appender.list.clear();
        appender.start();
        logger.addAppender(appender);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        logger.detachAppender(appender);
        appender.stop();
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** The events whose {@code event.action} is this action, in the order they were logged. */
    public List<ILoggingEvent> withAction(String action) {
        return events().stream().filter(event -> action.equals(fields(event).get("event.action"))).toList();
    }

    /** The one event with this action; fails if there is none or more than one. */
    public ILoggingEvent single(String action) {
        List<ILoggingEvent> matching = withAction(action);
        if (matching.size() != 1) {
            throw new AssertionError("Expected one '" + action + "' event but found " + matching.size()
                    + " among " + events().stream().map(LogCapture::fields).toList());
        }
        return matching.getFirst();
    }

    /** The key-value fields added through the SLF4J fluent API. */
    public static Map<String, Object> fields(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs == null ? Map.of()
                : pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    /** Everything an event would write out: message, fields, MDC and any exception text. */
    public static String everything(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        fields(event).forEach((key, value) -> text.append(' ').append(key).append('=').append(value));
        event.getMDCPropertyMap().forEach((key, value) -> text.append(' ').append(key).append('=').append(value));
        for (var cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
            text.append(' ').append(cause.getClassName()).append(": ").append(cause.getMessage());
        }
        return text.toString();
    }
}
