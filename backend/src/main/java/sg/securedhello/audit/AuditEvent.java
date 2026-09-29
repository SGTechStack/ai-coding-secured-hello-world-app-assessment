package sg.securedhello.audit;

import static sg.securedhello.audit.AuditKey.ACTIVE_PROFILES;
import static sg.securedhello.audit.AuditKey.AUDIT_LOGGERS;
import static sg.securedhello.audit.AuditKey.EVENTS_UNTRACKED_COUNT;
import static sg.securedhello.audit.AuditKey.HOST_IP;
import static sg.securedhello.audit.AuditKey.HOST_NAME;
import static sg.securedhello.audit.AuditKey.IPV6_PREFIX_LENGTH;
import static sg.securedhello.audit.AuditKey.KEY_FINGERPRINTS;
import static sg.securedhello.audit.AuditKey.SOURCE_DISTINCT_COUNT;
import static sg.securedhello.audit.AuditKey.TRUNCATED_ROWS;
import static sg.securedhello.audit.AuditKey.USER_DISTINCT_COUNT;
import static sg.securedhello.audit.AuditKey.USER_ID;
import static sg.securedhello.audit.AuditRowDefinition.row;

import org.slf4j.event.Level;

import sg.securedhello.audit.AuditRowDefinition.Keying;
import sg.securedhello.audit.AuditRowDefinition.Outcome;
import sg.securedhello.audit.AuditRowDefinition.Scope;
import sg.securedhello.audit.AuditRowDefinition.Severity;

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
 *   <li>If anyone can trigger the row without an account, key it (ADR-019): {@code .keyed(Keying.SOURCE)} when its
 *       key space is the source, {@code .keyed(Keying.USER)} when it needs a resolved user. Otherwise it would reopen
 *       the audit-volume arithmetic (R-AUD-031).</li>
 *   <li>Regenerate the inventory with {@code -Daudit-inventory.regenerate=true} and commit it.</li>
 * </ol>
 * Nothing else changes: the emitter validates the keys, derives the request fields and writes the row.
 */
public enum AuditEvent {

    /** Row 1: a password sign-in succeeded. Written after the session id rotated, so it carries the new hash. */
    LOGIN_SUCCESS(row("user-authentication", "Login succeeded.")
            .type("user")
            .required(USER_ID)
            .build()),

    /**
     * Row 2: a password sign-in failed. The wire answer is the uniform 401 (ADR-033); the reason is here only.
     * {@code user.id} is written when the account resolves and omitted when it does not (REJ-042; R-AUD-007).
     */
    LOGIN_FAILURE(row("user-authentication", "Login failed.")
            .type("user")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(LoginFailureReason.class)
            .optional(USER_ID)
            .build()),

    /**
     * Row 5: a request was throttled on its source key, by a budget-table row or by the session-miss budget. A tier-1
     * keyed row: one per source, reason and keying window, with its count (ADR-019).
     */
    SOURCE_THROTTLED(row("access-control", "Request throttled for its source.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(SourceThrottleReason.class)
            .keyed(Keying.SOURCE)
            .build()),

    /**
     * Row 6: a request was throttled on the value it submitted, such as a login username. Transition-keyed: written
     * once when the value's bucket runs out, not for each refusal until it is admitted again. It carries no identity
     * by construction, since the value may name no account and its bucket must not become an existence oracle.
     */
    IDENTIFIER_THROTTLED(row("access-control", "Request throttled for its submitted identifier.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(IdentifierThrottleReason.class)
            .build()),

    /** Row 7: a signed-in user signed out. */
    LOGOUT(row("user-logout", "Logout succeeded.")
            .type("end")
            .required(USER_ID)
            .build()),

    /**
     * Row 8: the session id is about to rotate at authentication. Written before the rotation, so its
     * {@code session.hash} is the pre-login one that joins the attempts before it to the sign-in (ADR-038).
     */
    SESSION_START(row("session-start", "Session started.")
            .type("start")
            .reasons(SessionStartReason.class)
            .required(USER_ID)
            .build()),

    /**
     * Row 13: a CSRF check refused an unsafe request. {@code user.id} only when the caller is signed in. A tier-1 keyed
     * row, because any unsafe request from anywhere can produce it (ADR-019).
     */
    CSRF_REJECTED(row("access-control", "CSRF validation failed.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.MEDIUM)
            .reasons(CsrfReason.class)
            .optional(USER_ID)
            .keyed(Keying.SOURCE)
            .build()),

    /** Row 43: the application is ready. Records what later correlation depends on (LOG §3.1; ADR-054; ADR-057). */
    APPLICATION_STARTUP(row("application-startup", "Application started.")
            .type("start")
            .scope(Scope.PROCESS)
            .required(HOST_NAME, HOST_IP, ACTIVE_PROFILES, IPV6_PREFIX_LENGTH, KEY_FINGERPRINTS, AUDIT_LOGGERS)
            .build()),

    /**
     * Row 46: a keying tier reached its distinct-key cap in a keying window. One per tier and window, written as the
     * window closes, with the exact tracked-key and untracked-occurrence counts and the rows truncated (ADR-019;
     * REJ-079). A per-event alert: reaching it means a campaign large enough to threaten the audit file (R-OBS-013).
     */
    KEYED_ROWS_TRUNCATED(row("access-control", "Keyed audit rows truncated.")
            .type("denied")
            .outcome(Outcome.FAILURE)
            .level(Level.WARN, Severity.HIGH)
            .scope(Scope.PROCESS)
            .reasons(TruncationReason.class)
            .required(EVENTS_UNTRACKED_COUNT, TRUNCATED_ROWS)
            .optional(SOURCE_DISTINCT_COUNT, USER_DISTINCT_COUNT)
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
