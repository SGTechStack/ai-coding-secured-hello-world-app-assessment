# Audit log inventory

<!-- Generated from AuditEvent by AuditInventory. Do not edit: change the enum, then regenerate with
     mvn -f backend/pom.xml verify -Daudit-inventory.regenerate=true (T-AUD-017). -->

The ASVS 16.1.1 (L2) log inventory of the audit stream: every row the application can write, the fields it carries, and where it goes, for how long, and who can read it. Rows are written only by `AuditEmitter.emit(event, context)` on the `audit` logger (ADR-055).

## Destinations

Every audit row goes to both destinations (ADR-056; R-AUD-037).

| Destination | Format | Retention | Who can read |
|---|---|---|---|
| Dedicated audit file `<app.audit.directory>/audit.ndjson` (default `logs/`), `audit` logger only | ECS NDJSON, one event per line | Rolled daily; 90 archives kept; no total-size cap (REJ-045). Retention past the host is the platform's (R-AUD-011; R-AUD-022). | Operating-system accounts that can read the log directory. The application neither access-controls nor tamper-proofs the file (R-AUD-012). |
| Standard output, with every other log line | ECS NDJSON, one event per line | None in the application; whatever collects stdout (R-AUD-013). | Anyone who can read the process's console, journal or container log (R-AUD-012). |

## Fields

- **Every line:** `@timestamp`, `log.level`, `log.logger`, `message`, `process.pid`, `process.thread.name`, `service.name`, `service.version`, `ecs.version`; `trace.id` and `span.id` inside a request. The trace is server-generated: inbound trace context is restarted (ADR-063).
- **Every audit row:** `event.kind` (`event`), `event.category` (`["process"]`), `event.type`, `event.action`, `event.outcome`, `event.severity`; `event.reason` on rows with a reason family, serialised by code (T-AUD-045).
- **Request-scoped rows:** `url.path` (the matched route pattern; before a handler matches, the raw URI with CR, LF and pipe stripped, capped at `app.audit.url-path.max-length`, default 256, with a truncation marker), `http.request.method` (a known method or `OTHER`), `source.ip_hash` (keyed hash of the source key), and `session.hash` (keyed hash of the session id) when a session exists (ADR-054).
- **Never written:** a client address in any form, `user.hash`, `user.name`, an email address, a password, a token or its hash, a raw session id, or a throwable's `error.*` fields.

## Events

The Keying column says how often a row is written (ADR-019): per event, or as a keyed row.

| Event | `event.action` | `event.type` | `event.outcome` | Level | Severity | Request fields | Keying | Reason codes | Required keys | Optional keys | `message` |
|---|---|---|---|---|---|---|---|---|---|---|---|
| LOGIN_SUCCESS | `user-authentication` | `user` | success | INFO | low | yes | per event | — | `user.id` | — | Login succeeded. |
| LOGIN_FAILURE | `user-authentication` | `user` | failure | WARN | medium | yes | per event | `BAD_CREDENTIALS`, `UNKNOWN_USER`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED`, `CREDENTIAL_EXPIRED`, `PASSWORD_DISABLED` | — | `user.id` | Login failed. |
| LOCKOUT_TRIGGERED | `user-authentication` | `error` | failure | WARN | high | yes | per event | `THRESHOLD_REACHED` | `user.id` | — | Account locked. |
| LOCKOUT_CLEARED | `user-authentication` | `change` | success | INFO | low | yes | per event | `AUTO_LIFT`, `PASSWORD_RESET_COMPLETED` | `user.id` | — | Account lock cleared. |
| PASSWORD_FAILURE_ALERT | `user-authentication` | `error` | failure | WARN | high | yes | per event | — | `user.id` | — | Password failures reached the alert threshold. |
| PASSWORD_DISABLED | `user-authentication` | `error` | failure | ERROR | critical | yes | per event | `FAILURE_CAP` | `user.id` | — | Password disabled. |
| SOURCE_THROTTLED | `access-control` | `denied` | failure | WARN | medium | yes | tier 1: per source | `RATE_LIMITED_SOURCE`, `RATE_LIMITED_SOURCE_MISSES`, `RATE_LIMITED_LOCKOUT_CARDINALITY`, `DISK_RESERVE_SHED` | — | — | Request throttled for its source. |
| IDENTIFIER_THROTTLED | `access-control` | `denied` | failure | WARN | medium | yes | per event | `RATE_LIMITED_IDENTIFIER` | — | — | Request throttled for its submitted identifier. |
| PASSWORD_RESET_REQUESTED | `password-reset-request` | `info` | success | INFO | low | yes | tier 1: per source | — | — | — | Password reset requested. |
| PASSWORD_RESET_COMPLETED | `password-reset` | `change` | success | INFO | low | yes | per event | — | `user.id` | — | Password reset completed. |
| LOGOUT | `user-logout` | `end` | success | INFO | low | yes | per event | — | `user.id` | — | Logout succeeded. |
| SESSION_START | `session-start` | `start` | success | INFO | low | yes | per event | `LOGIN` | `user.id` | — | Session started. |
| CSRF_REJECTED | `access-control` | `denied` | failure | WARN | medium | yes | tier 1: per source | `CSRF_MISSING`, `CSRF_INVALID` | — | `user.id` | CSRF validation failed. |
| TOTP_ENROLMENT_PROVISIONED | `totp-enrol` | `creation` | success | INFO | low | yes | per event | — | `user.id` | — | TOTP enrolment provisioned. |
| TOTP_ENROLMENT_CONFIRMED | `totp-enrol` | `change` | success | INFO | low | yes | per event | — | `user.id` | — | TOTP enrolment confirmed. |
| TOTP_ENROLMENT_FAILED | `totp-enrol` | `change` | failure | WARN | medium | yes | per event | — | `user.id` | — | TOTP enrolment confirmation failed. |
| TOTP_VERIFIED | `totp-verify` | `user` | success | INFO | low | yes | per event | — | `user.id` | — | TOTP verification succeeded. |
| TOTP_VERIFICATION_FAILED | `totp-verify` | `user` | failure | WARN | medium | yes | per event | — | `user.id` | — | TOTP verification failed. |
| TOTP_FACTOR_LOCKED | `totp-verify` | `error` | failure | WARN | high | yes | per event | — | `user.id` | — | TOTP factor locked. |
| TOTP_FACTOR_DISABLED | `totp-verify` | `error` | failure | ERROR | critical | yes | per event | — | `user.id` | — | TOTP factor disabled. |
| ADMIN_USERS_LISTED | `admin-user-list` | `access` | success | INFO | low | yes | per event | — | `user.id`, `user.target.count` | — | Administrator listed users. |
| ADMIN_USER_VIEWED | `admin-user-read` | `access` | success | INFO | low | yes | per event | — | `user.id`, `user.target.id` | — | Administrator read a user. |
| TOTP_CONTEXT_MISMATCH | `totp-decrypt` | `error` | failure | ERROR | critical | yes | per event | — | `user.id` | — | TOTP secret context mismatch. |
| APPLICATION_STARTUP | `application-startup` | `start` | success | INFO | low | no | per event | — | `host.name`, `host.ip`, `labels.active_profiles`, `labels.ipv6_prefix_length`, `labels.key_fingerprints`, `labels.audit_loggers` | — | Application started. |
| KEYED_ROWS_TRUNCATED | `access-control` | `denied` | failure | WARN | high | no | per event | `SOURCE_CAP_REACHED`, `USER_CAP_REACHED` | `events.untracked_count`, `labels.truncated_rows` | `source.distinct_count`, `user.distinct_count` | Keyed audit rows truncated. |
| SHED_EPISODE_CLEARED | `access-control` | `change` | success | INFO | low | no | per event | — | — | — | Anonymous-session shedding cleared. |
| APPLICATION_SHUTDOWN | `application-shutdown` | `end` | success | INFO | low | no | per event | — | — | — | Application stopping. |

## Keyed rows and truncation

Rows anyone can trigger without an account are keyed, so their volume does not grow with the attacker's request rate or number of sources (ADR-019; REJ-078 to REJ-080; R-AUD-027 to R-AUD-029).

- **Key tuple:** (key, event, `event.reason`, keying window). Tier 1 keys on `source.ip_hash`, tier 2 on `user.id`.
- **Window:** `app.audit.keying.window`, default 15 minutes, one window for both tiers. It opens at the first keyed occurrence and closes when an occurrence or the one-minute tick finds it that old, and when the application stops.
- **When written:** once per key tuple, as the window closes, with the first occurrence's fields plus `event.count` (the occurrences it stands for) and `event.start` (when the first happened). Timestamps of the other occurrences are not kept.
- **Caps:** at most `app.audit.truncation.distinct-sources` (default 20) sources in tier 1 and `app.audit.truncation.distinct-users` (default 500) users in tier 2 per window. A key beyond the cap is never admitted, and its occurrences are only counted.
- **Truncation row:** `KEYED_ROWS_TRUNCATED`, once per capped tier per window, after the keyed rows: `source.distinct_count` or `user.distinct_count` (keys tracked, exact), `events.untracked_count` (occurrences beyond the cap, exact) and `labels.truncated_rows` (the events those occurrences belonged to). The true number of distinct keys lies between the tracked count and the sum of both.
- **Transition-keyed:** `IDENTIFIER_THROTTLED` is written per event, but only on the first refusal after the submitted value's bucket last admitted a request.

## Degraded row

When a context does not fit its event, `emit` writes the event's constants with `event.outcome` `unknown`, `message` `Audit row degraded.` and one of these `event.reason` codes, and none of the context's keys or values; an ERROR on the `sg.securedhello.audit.AuditEmitter` logger raises the alert (ADR-055; R-AUD-010). The test suite fails any test that produces one unexpectedly.

| Code | Meaning |
|---|---|
| `UNKNOWN_KEY` | The context wrote a key the event does not allow. |
| `MISSING_KEY` | A required key is missing, or a request-scoped row was emitted outside a request. |
| `REASON_OUTSIDE_FAMILY` | The reason is missing or belongs to another family. |
| `EMIT_FAILED` | Building the row failed. |
