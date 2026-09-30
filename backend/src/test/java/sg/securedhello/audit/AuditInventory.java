package sg.securedhello.audit;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import sg.securedhello.audit.AuditRowDefinition.Keying;
import sg.securedhello.audit.AuditRowDefinition.Scope;

/**
 * Renders the ASVS 16.1.1 (L2) log inventory from {@link AuditEvent} (R-AUD-027): the events, their fields and
 * levels, and each destination's format, retention and readers. {@link AuditInventoryDriftIT} fails {@code verify}
 * when the committed document differs from this rendering.
 */
final class AuditInventory {

    /** The committed inventory, relative to the backend module (Maven's working directory for tests). */
    static final Path MARKDOWN = Path.of("..", "docs", "audit", "log-inventory.md");

    private AuditInventory() {
    }

    static String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("""
                # Audit log inventory

                <!-- Generated from AuditEvent by AuditInventory. Do not edit: change the enum, then regenerate with
                     mvn -f backend/pom.xml verify -Daudit-inventory.regenerate=true (T-AUD-017). -->

                The ASVS 16.1.1 (L2) log inventory of the audit stream: every row the application can write, the \
                fields it carries, and where it goes, for how long, and who can read it. Rows are written only by \
                `AuditEmitter.emit(event, context)` on the `audit` logger (ADR-055).

                ## Destinations

                Every audit row goes to both destinations (ADR-056; R-AUD-037); a recovery-runner run adds a third.

                | Destination | Format | Retention | Who can read |
                |---|---|---|---|
                | Dedicated audit file `<app.audit.directory>/audit.ndjson` (default `logs/`), `audit` logger only \
                | ECS NDJSON, one event per line | Rolled daily; 90 archives kept; no total-size cap (REJ-045). \
                Retention past the host is the platform's (R-AUD-011; R-AUD-022). | Operating-system accounts that \
                can read the log directory. The application neither access-controls nor tamper-proofs the file \
                (R-AUD-012). |
                | Standard output, with every other log line | ECS NDJSON, one event per line | None in the \
                application; whatever collects stdout (R-AUD-013). | Anyone who can read the process's console, \
                journal or container log (R-AUD-012). |
                | The recovery runner's standard output: its terminal, or the journal or job log it is launched \
                under (R-AUD-034) | The runner's audit rows, as above, and its plain-text operator lines: the \
                digest, account UUIDs, database path, schema version and file time. Never a password (ADR-073; \
                T-AUD-027). The runner's rows also reach the audit file once its checks pass (ADR-072). | Whatever \
                captures the runner's launch. | Anyone who can read the operator's terminal or the job log; the \
                deployer records who (R-AUD-034). |

                ## Fields

                - **Every line:** `@timestamp`, `log.level`, `log.logger`, `message`, `process.pid`, \
                `process.thread.name`, `service.name`, `service.version`, `ecs.version`; `trace.id` and `span.id` \
                inside a request. The trace is server-generated: inbound trace context is restarted (ADR-063).
                - **Every audit row:** `event.kind` (`event`), `event.category` (`["process"]`), `event.type`, \
                `event.action`, `event.outcome`, `event.severity`; `event.reason` on rows with a reason family, \
                serialised by code (T-AUD-045).
                - **Request-scoped rows:** `url.path` (the matched route pattern; before a handler matches, the raw \
                URI with CR, LF and pipe stripped, capped at `app.audit.url-path.max-length`, default 256, with a \
                truncation marker), `http.request.method` (a known method or `OTHER`), `source.ip_hash` (keyed hash \
                of the source key), and `session.hash` (keyed hash of the session id) when a session exists \
                (ADR-054).
                - **Never written:** a client address in any form, `user.hash`, `user.name`, an email address, a \
                password, a token or its hash, a raw session id, or a throwable's `error.*` fields.

                ## Events

                The Keying column says how often a row is written (ADR-019): per event, or as a keyed row.

                | Event | `event.action` | `event.type` | `event.outcome` | Level | Severity | Request fields \
                | Keying | Reason codes | Required keys | Optional keys | `message` |
                |---|---|---|---|---|---|---|---|---|---|---|---|
                """);
        for (AuditEvent event : AuditEvent.values()) {
            AuditRowDefinition row = event.definition();
            md.append("| ").append(event.name())
                    .append(" | `").append(row.action())
                    .append("` | `").append(String.join(", ", row.type()))
                    .append("` | ").append(row.outcome().code())
                    .append(" | ").append(row.level())
                    .append(" | ").append(row.severity().code())
                    .append(" | ").append(row.scope() == Scope.REQUEST ? "yes" : "no")
                    .append(" | ").append(keying(row.keying()))
                    .append(" | ").append(codes(row.reasonFamily()))
                    .append(" | ").append(keys(row.required()))
                    .append(" | ").append(keys(row.allowed().stream().filter(key -> !row.required().contains(key))
                            .toList()))
                    .append(" | ").append(row.message())
                    .append(" |\n");
        }
        md.append("""

                ## Keyed rows and truncation

                Rows anyone can trigger without an account are keyed, so their volume does not grow with the attacker's \
                request rate or number of sources (ADR-019; REJ-078 to REJ-080; R-AUD-027 to R-AUD-029).

                - **Key tuple:** (key, event, `event.reason`, keying window). Tier 1 keys on `source.ip_hash`, tier 2 \
                on `user.id`.
                - **Window:** `app.audit.keying.window`, default 15 minutes, one window for both tiers. It opens at the \
                first keyed occurrence and closes when an occurrence or the one-minute tick finds it that old, and when \
                the application stops.
                - **When written:** once per key tuple, as the window closes, with the first occurrence's fields plus \
                `event.count` (the occurrences it stands for) and `event.start` (when the first happened). Timestamps \
                of the other occurrences are not kept.
                - **Caps:** at most `app.audit.truncation.distinct-sources` (default 20) sources in tier 1 and \
                `app.audit.truncation.distinct-users` (default 500) users in tier 2 per window. A key beyond the cap \
                is never admitted, and its occurrences are only counted.
                - **Truncation row:** `KEYED_ROWS_TRUNCATED`, once per capped tier per window, after the keyed rows: \
                `source.distinct_count` or `user.distinct_count` (keys tracked, exact), `events.untracked_count` \
                (occurrences beyond the cap, exact) and `labels.truncated_rows` (the events those occurrences \
                belonged to). The true number of distinct keys lies between the tracked count and the sum of both.
                - **Transition-keyed:** `IDENTIFIER_THROTTLED` is written per event, but only on the first refusal \
                after the submitted value's bucket last admitted a request.

                ## Degraded row

                When a context does not fit its event, `emit` writes the event's constants with `event.outcome` \
                `unknown`, `message` `%s` and one of these `event.reason` codes, and none of the context's keys or \
                values; an ERROR on the `sg.securedhello.audit.AuditEmitter` logger raises the alert (ADR-055; \
                R-AUD-010). The test suite fails any test that produces one unexpectedly.

                | Code | Meaning |
                |---|---|
                | `UNKNOWN_KEY` | The context wrote a key the event does not allow. |
                | `MISSING_KEY` | A required key is missing, or a request-scoped row was emitted outside a request. |
                | `REASON_OUTSIDE_FAMILY` | The reason is missing or belongs to another family. |
                | `EMIT_FAILED` | Building the row failed. |
                """.formatted(AuditEmitter.DEGRADED_MESSAGE));
        return md.toString();
    }

    private static String keying(Keying keying) {
        return switch (keying) {
            case NONE -> "per event";
            case SOURCE -> "tier 1: per source";
            case USER -> "tier 2: per user";
        };
    }

    private static String codes(Class<? extends AuditReason> family) {
        AuditReason[] reasons = family.getEnumConstants();
        return reasons.length == 0 ? "—"
                : Arrays.stream(reasons).map(reason -> "`" + reason.code() + "`").collect(Collectors.joining(", "));
    }

    private static String keys(Collection<AuditKey> keys) {
        return keys.isEmpty() ? "—" : keys.stream()
                .sorted(Comparator.naturalOrder())
                .map(key -> "`" + key.field() + "`")
                .collect(Collectors.joining(", "));
    }
}
