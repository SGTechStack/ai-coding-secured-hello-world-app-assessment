package sg.securedhello.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.slf4j.event.KeyValuePair;

import org.springframework.boot.logging.structured.ContextPairs;
import org.springframework.boot.logging.structured.ElasticCommonSchemaProperties;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

import sg.securedhello.audit.AuditEmitter;

/**
 * The application's one log format: ECS NDJSON, one event per line, on stdout and in the audit file (LOG §3.1;
 * ADR-056). It is Boot's ECS layout with redaction at source rather than a masking decorator (REJ-001):
 * <ul>
 *   <li>MDC entries are written only if they are on {@link #MDC_FIELDS}, renamed to their ECS names. Everything else
 *       a library or a caller puts in the MDC, such as baggage correlation fields or a {@code correlation.id}, is
 *       dropped (REJ-087; REJ-088).</li>
 *   <li>A row on the {@code audit} logger never carries a throwable's {@code error.*} fields, even if one is
 *       attached, since an exception message can hold request content (ADR-055).</li>
 *   <li>On a line with a reportable throwable, the key-value pairs {@link #ERROR_CODE}, {@link #ERROR_CATEGORY} and
 *       {@link #ERROR_FOLLOW_UP_ACTION} join the throwable's {@code error} object, so the line has one {@code error}
 *       object, not two (R-AUD-001; T-AUD-002).</li>
 * </ul>
 * Key-value pairs are written as given; on audit rows they come only from {@link AuditEmitter}, which validates them.
 */
public class EcsLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent> {

    /** The MDC entries that reach a log line, by MDC key, with the ECS name each is written under. */
    public static final Map<String, String> MDC_FIELDS = Map.of("traceId", "trace.id", "spanId", "span.id");

    static final String ECS_VERSION = "8.11";

    /** {@code error.code}: the status of the error code the response carried. */
    public static final String ERROR_CODE = "error.code";

    /** {@code error.category}: a closed set, from the error code's family. */
    public static final String ERROR_CATEGORY = "error.category";

    /** {@code error.follow_up_action}: a closed set, what the caller must do next. */
    public static final String ERROR_FOLLOW_UP_ACTION = "error.follow_up_action";

    /** The pairs written inside the throwable's {@code error} object, by pair key, with their member name there. */
    private static final Map<String, String> ERROR_PAIRS = errorPairs();

    public EcsLogFormatter(Environment environment, ContextPairs contextPairs,
            ThrowableProxyConverter throwableProxyConverter,
            StructuredLoggingJsonMembersCustomizer.Builder<?> customizerBuilder) {
        super(members -> {
            members.add("@timestamp", ILoggingEvent::getInstant);
            members.add("log").usingMembers(log -> {
                log.add("level", ILoggingEvent::getLevel);
                log.add("logger", ILoggingEvent::getLoggerName);
            });
            ElasticCommonSchemaProperties.get(environment).jsonMembers(members);
            members.add("message", ILoggingEvent::getFormattedMessage);
            // process.* goes through the nested pairs, so an audit row's process.real_user.name joins the same object.
            Long pid = environment.getProperty("spring.application.pid", Long.class);
            members.add().usingPairs(contextPairs.nested(pairs -> pairs.add((ILoggingEvent event,
                    BiConsumer<String, Object> fields) -> {
                if (pid != null) {
                    fields.accept("process.pid", pid);
                }
                fields.accept("process.thread.name", event.getThreadName());
                contextFields(event, fields);
            })));
            members.add().whenNotNull(EcsLogFormatter::reportableThrowable).usingMembers(throwable -> throwable
                    .add("error").usingMembers(error -> {
                        error.add("type", EcsLogFormatter::reportableThrowable).as(IThrowableProxy::getClassName);
                        error.add("message", EcsLogFormatter::reportableThrowable).as(IThrowableProxy::getMessage);
                        error.add("stack_trace", throwableProxyConverter::convert);
                        ERROR_PAIRS.forEach((pair, member) -> error.add(member, event -> pairValue(event, pair))
                                .whenNotNull());
                    }));
            members.add("ecs").usingMembers(ecs -> ecs.add("version", ECS_VERSION));
        }, customizerBuilder.nested().build());
    }

    /** The allowed MDC entries under their ECS names, then the event's key-value pairs. */
    static void contextFields(ILoggingEvent event, BiConsumer<String, Object> pairs) {
        event.getMDCPropertyMap().forEach((key, value) -> {
            String field = MDC_FIELDS.get(key);
            if (field != null) {
                pairs.accept(field, value);
            }
        });
        if (event.getKeyValuePairs() != null) {
            boolean inErrorObject = reportableThrowable(event) != null;
            for (KeyValuePair pair : event.getKeyValuePairs()) {
                if (!(inErrorObject && ERROR_PAIRS.containsKey(pair.key))) {
                    pairs.accept(pair.key, pair.value);
                }
            }
        }
    }

    /** {@link #ERROR_PAIRS} in a fixed order, so every line writes the members in the same order. */
    private static Map<String, String> errorPairs() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put(ERROR_CODE, "code");
        pairs.put(ERROR_CATEGORY, "category");
        pairs.put(ERROR_FOLLOW_UP_ACTION, "follow_up_action");
        return Collections.unmodifiableMap(pairs);
    }

    /** The value of the event's key-value pair {@code key}, or {@code null}. */
    static Object pairValue(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() != null) {
            for (KeyValuePair pair : event.getKeyValuePairs()) {
                if (key.equals(pair.key)) {
                    return pair.value;
                }
            }
        }
        return null;
    }

    /** The event's throwable, unless the event is an audit row, which never reports one. */
    static IThrowableProxy reportableThrowable(ILoggingEvent event) {
        if (event == null || AuditEmitter.AUDIT_LOGGER.equals(event.getLoggerName())) {
            return null;
        }
        return event.getThrowableProxy();
    }
}
