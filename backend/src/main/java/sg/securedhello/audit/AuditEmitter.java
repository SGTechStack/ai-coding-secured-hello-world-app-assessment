package sg.securedhello.audit;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sg.securedhello.audit.AuditRowDefinition.Keying;
import sg.securedhello.audit.AuditRowDefinition.Outcome;
import sg.securedhello.audit.AuditRowDefinition.Scope;

/**
 * The single writer on the {@code audit} logger (ADR-055). Every audit row is {@code emit(event, context)}: the
 * event's constants, the context's validated keys and, on request-scoped rows, the fields derived from the current
 * request. There is no throwable parameter and no {@code setCause}, so no exception message can reach the audit
 * stream (T-AUD-016).
 *
 * <p>{@code emit} never throws. A context with a key the event does not allow, a missing key, a reason from outside
 * the event's family, or any failure while building the row produces a <em>degraded row</em> instead: the event's
 * constants with {@code event.outcome} {@code unknown} and a {@link Degradation} reason, and none of the context's
 * keys or values. An ERROR on this class's own logger raises the alert (R-AUD-010). Rows are written after commit,
 * so throwing could undo nothing and would turn a committed operation into a 500. The test suite fails any test that
 * produces a degraded row unless it expects one, which is what makes the path unreachable.
 *
 * <p>To add an event, see {@link AuditEvent}.
 */
public class AuditEmitter {

    /** The audit logger's name; its rows go to the dedicated audit file and to stdout (ADR-056). */
    public static final String AUDIT_LOGGER = "audit";

    /** The static message of every degraded row and the start of its alert, which the test suite looks for. */
    public static final String DEGRADED_MESSAGE = "Audit row degraded.";

    /**
     * The request attribute a filter sets when it refuses a request before the session store was consulted. The row
     * then carries no {@code session.hash}, and writing it costs no session lookup (ADR-017; T-RL-021).
     */
    public static final String SESSION_UNREAD_ATTRIBUTE = AuditRequestFields.SESSION_UNREAD;

    private static final Logger audit = LoggerFactory.getLogger(AUDIT_LOGGER);
    private static final Logger log = LoggerFactory.getLogger(AuditEmitter.class);

    private final AuditRequestFields requestFields;
    private final AuditKeying keying;

    /**
     * @param keyingWindow    the keying window, {@code app.audit.keying.window}
     * @param distinctSources tier 1's cap per window, {@code app.audit.truncation.distinct-sources}
     * @param distinctUsers   tier 2's cap per window, {@code app.audit.truncation.distinct-users}
     */
    AuditEmitter(AuditRequestFields requestFields, Clock clock, Duration keyingWindow, int distinctSources,
            int distinctUsers) {
        this.requestFields = requestFields;
        this.keying = new AuditKeying(clock, keyingWindow, distinctSources, distinctUsers, new AuditKeying.Sink() {
            @Override
            public void keyed(AuditRowDefinition row, Map<String, Object> fields) {
                log(row, fields);
            }

            @Override
            public void truncated(TruncationContext context) {
                emit(AuditEvent.KEYED_ROWS_TRUNCATED, context);
            }
        });
    }

    /**
     * Writes {@code event}'s row with {@code context}'s keys, or a degraded row if they do not fit the event. A keyed
     * row is recorded instead, and written when its keying window closes (ADR-019).
     */
    public void emit(AuditEvent event, AuditContext context) {
        write(event.name(), event.definition(), context);
    }

    /**
     * Writes every keyed row recorded so far and the truncation rows, and starts a new keying window. Called as the
     * application stops, so no recorded occurrence is lost; tests call it to read keyed rows without waiting a window.
     */
    public void closeKeyingWindow() {
        keying.close();
    }

    /** Closes the keying window if it has run its length; the periodic tick, for a window no occurrence closes. */
    void closeKeyingWindowIfDue() {
        keying.closeIfDue();
    }

    /** {@link #emit} for any definition, so the emitter can be exercised with rows the catalogue does not hold. */
    void write(String name, AuditRowDefinition row, AuditContext context) {
        Degradation problem;
        try {
            HttpServletRequest request = currentRequest();
            AuditFields fields = new AuditFields();
            context.writeTo(fields);
            Optional<Degradation> invalid = EmitterKeyValidator.check(row, fields, request != null);
            if (invalid.isEmpty()) {
                Map<String, Object> values = new LinkedHashMap<>();
                if (fields.reason() != null) {
                    values.put("event.reason", fields.reason().code());
                }
                fields.values().forEach((key, value) -> values.put(key.field(), value));
                if (row.scope() == Scope.REQUEST) {
                    values.putAll(requestFields.of(request));
                }
                if (row.keying() == Keying.NONE) {
                    log(row, values);
                } else {
                    keying.record(name, row, values);
                }
                return;
            }
            problem = invalid.get();
        } catch (RuntimeException failure) {
            problem = Degradation.EMIT_FAILED;
        }
        constants(row, Outcome.UNKNOWN).addKeyValue("event.reason", problem.code()).setMessage(DEGRADED_MESSAGE).log();
        log.atError().setMessage(DEGRADED_MESSAGE + " event={} degradation={}").addArgument(name)
                .addArgument(problem.code()).log();
    }

    /** Writes {@code row} with {@code fields} after its constants. */
    private static void log(AuditRowDefinition row, Map<String, Object> fields) {
        LoggingEventBuilder builder = constants(row, row.outcome());
        fields.forEach(builder::addKeyValue);
        builder.setMessage(row.message()).log();
    }

    /** The Tier B constants every row carries, degraded or not. */
    private static LoggingEventBuilder constants(AuditRowDefinition row, Outcome outcome) {
        return audit.atLevel(row.level())
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("process"))
                .addKeyValue("event.type", row.type())
                .addKeyValue("event.action", row.action())
                .addKeyValue("event.outcome", outcome.code())
                .addKeyValue("event.severity", row.severity().code());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
