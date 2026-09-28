package sg.securedhello.audit;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    private static final Logger audit = LoggerFactory.getLogger(AUDIT_LOGGER);
    private static final Logger log = LoggerFactory.getLogger(AuditEmitter.class);

    private final AuditRequestFields requestFields;

    AuditEmitter(AuditRequestFields requestFields) {
        this.requestFields = requestFields;
    }

    /** Writes {@code event}'s row with {@code context}'s keys, or a degraded row if they do not fit the event. */
    public void emit(AuditEvent event, AuditContext context) {
        write(event.name(), event.definition(), context);
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
                LoggingEventBuilder builder = constants(row, row.outcome());
                if (fields.reason() != null) {
                    builder.addKeyValue("event.reason", fields.reason().code());
                }
                fields.values().forEach((key, value) -> builder.addKeyValue(key.field(), value));
                if (row.scope() == Scope.REQUEST) {
                    requestFields.of(request).forEach(builder::addKeyValue);
                }
                builder.setMessage(row.message()).log();
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
