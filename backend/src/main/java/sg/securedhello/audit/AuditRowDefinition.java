package sg.securedhello.audit;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.event.Level;

/**
 * The constants of one audit row (ADR-055): what {@link AuditEvent} holds for each member. {@code allowed} always
 * contains {@code required}; a context may write exactly the allowed keys, and must write the required ones.
 *
 * @param action       {@code event.action}, from the schema's closed vocabulary
 * @param type         {@code event.type}
 * @param outcome      {@code event.outcome}
 * @param level        the log level
 * @param severity     {@code event.severity}
 * @param message      the static {@code message}, which is the row's discriminator
 * @param scope        whether the row carries the request fields
 * @param reasonFamily the {@code event.reason} family; {@link AuditReason.None} for a row without a reason
 * @param required     the context keys the row must carry
 * @param allowed      the context keys the row may carry
 */
public record AuditRowDefinition(String action, List<String> type, Outcome outcome, Level level, Severity severity,
        String message, Scope scope, Class<? extends AuditReason> reasonFamily, Set<AuditKey> required,
        Set<AuditKey> allowed) {

    public AuditRowDefinition {
        type = List.copyOf(type);
        required = Set.copyOf(required);
        allowed = Set.copyOf(allowed);
        if (!allowed.containsAll(required)) {
            throw new IllegalArgumentException("every required key must also be allowed");
        }
    }

    /** Starts a definition with its action and static message; everything else has a default. */
    static Builder row(String action, String message) {
        return new Builder(action, message);
    }

    /** {@code event.outcome}. */
    public enum Outcome {
        SUCCESS, FAILURE, UNKNOWN;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** {@code event.severity}. */
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * {@link #REQUEST} rows carry {@code url.path}, {@code http.request.method}, {@code source.ip_hash} and, when the
     * request has a session, {@code session.hash}. {@link #PROCESS} rows describe the process and carry none of them.
     */
    public enum Scope {
        REQUEST, PROCESS
    }

    /** Defaults: {@code ["info"]}, success, INFO, low, request-scoped, no reason, no keys. */
    static final class Builder {

        private final String action;
        private final String message;
        private List<String> type = List.of("info");
        private Outcome outcome = Outcome.SUCCESS;
        private Level level = Level.INFO;
        private Severity severity = Severity.LOW;
        private Scope scope = Scope.REQUEST;
        private Class<? extends AuditReason> reasonFamily = AuditReason.None.class;
        private final Set<AuditKey> required = EnumSet.noneOf(AuditKey.class);
        private final Set<AuditKey> allowed = EnumSet.noneOf(AuditKey.class);

        private Builder(String action, String message) {
            this.action = action;
            this.message = message;
        }

        Builder type(String... values) {
            this.type = List.of(values);
            return this;
        }

        Builder outcome(Outcome value) {
            this.outcome = value;
            return this;
        }

        Builder level(Level value, Severity severityValue) {
            this.level = value;
            this.severity = severityValue;
            return this;
        }

        Builder scope(Scope value) {
            this.scope = value;
            return this;
        }

        Builder reasons(Class<? extends AuditReason> family) {
            this.reasonFamily = family;
            return this;
        }

        Builder required(AuditKey... keys) {
            required.addAll(List.of(keys));
            allowed.addAll(List.of(keys));
            return this;
        }

        Builder optional(AuditKey... keys) {
            allowed.addAll(List.of(keys));
            return this;
        }

        AuditRowDefinition build() {
            return new AuditRowDefinition(action, type, outcome, level, severity, message, scope, reasonFamily,
                    required, allowed);
        }
    }
}
