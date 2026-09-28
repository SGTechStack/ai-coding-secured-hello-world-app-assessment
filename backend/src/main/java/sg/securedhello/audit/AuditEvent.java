package sg.securedhello.audit;

import static sg.securedhello.audit.AuditKey.ACTIVE_PROFILES;
import static sg.securedhello.audit.AuditKey.AUDIT_LOGGERS;
import static sg.securedhello.audit.AuditKey.HOST_IP;
import static sg.securedhello.audit.AuditKey.HOST_NAME;
import static sg.securedhello.audit.AuditKey.IPV6_PREFIX_LENGTH;
import static sg.securedhello.audit.AuditKey.KEY_FINGERPRINTS;
import static sg.securedhello.audit.AuditRowDefinition.row;

import sg.securedhello.audit.AuditRowDefinition.Scope;

/**
 * The audit catalogue: one member per audit row, and the only list of what the audit stream can contain (ADR-055).
 * {@link AuditEmitter#emit} writes every row from its member's constants plus a typed {@link AuditContext}. The ASVS
 * 16.1.1 log inventory, {@code docs/audit/log-inventory.md}, is generated from this enum and checked for drift
 * (T-AUD-017).
 *
 * <h2>Adding an event</h2>
 * A later ticket adds a row with one constant here and, if the row carries keys, one context record:
 * <ol>
 *   <li>If the row carries a reason, write its family: an enum implementing {@link AuditReason} whose constants return
 *       pinned {@code code()} values, named in {@code AuditReason}'s {@code permits} and in the T-AUD-045 list.</li>
 *   <li>Add any new key to {@link AuditKey}, by its ECS field name.</li>
 *   <li>Write the context: a record of UUIDs, enums and numbers that implements {@link AuditContext} and writes its
 *       keys, and its reason typed as the family, so a reason from another family does not compile. For example:
 *       <pre>{@code
 * record CsrfRejection(UUID userId, CsrfReason reason) implements AuditContext {
 *     public void writeTo(AuditFields fields) {
 *         fields.put(AuditKey.USER_ID, userId).reason(reason);
 *     }
 * }}</pre></li>
 *   <li>Add the constant: {@code row(action, message)} with its type, outcome, level and severity, its reason family
 *       and its required and optional keys. Rows are request-scoped unless declared {@code PROCESS}.</li>
 *   <li>Regenerate the inventory with {@code -Daudit-inventory.regenerate=true} and commit it.</li>
 * </ol>
 * Nothing else changes: the emitter validates the keys, derives the request fields and writes the row.
 */
public enum AuditEvent {

    /** Row 43: the application is ready. Records what later correlation depends on (LOG §3.1; ADR-054; ADR-057). */
    APPLICATION_STARTUP(row("application-startup", "Application started.")
            .type("start")
            .scope(Scope.PROCESS)
            .required(HOST_NAME, HOST_IP, ACTIVE_PROFILES, IPV6_PREFIX_LENGTH, KEY_FINGERPRINTS, AUDIT_LOGGERS)
            .build()),

    /** Row 44: the application context is closing. */
    APPLICATION_SHUTDOWN(row("application-shutdown", "Application stopping.")
            .type("end")
            .scope(Scope.PROCESS)
            .build());

    private final AuditRowDefinition definition;

    AuditEvent(AuditRowDefinition definition) {
        this.definition = definition;
    }

    /** The row's constants. */
    public AuditRowDefinition definition() {
        return definition;
    }
}
