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

Every row is written once per event; no row is keyed or truncated yet (ADR-019).

| Event | `event.action` | `event.type` | `event.outcome` | Level | Severity | Request fields | Reason codes | Required keys | Optional keys | `message` |
|---|---|---|---|---|---|---|---|---|---|---|
| LOGIN_SUCCESS | `user-authentication` | `user` | success | INFO | low | yes | — | `user.id` | — | Login succeeded. |
| LOGIN_FAILURE | `user-authentication` | `user` | failure | WARN | medium | yes | `BAD_CREDENTIALS`, `UNKNOWN_USER`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED`, `CREDENTIAL_EXPIRED` | — | `user.id` | Login failed. |
| LOGOUT | `user-logout` | `end` | success | INFO | low | yes | — | `user.id` | — | Logout succeeded. |
| SESSION_START | `session-start` | `start` | success | INFO | low | yes | `LOGIN` | `user.id` | — | Session started. |
| CSRF_REJECTED | `access-control` | `denied` | failure | WARN | medium | yes | `CSRF_MISSING`, `CSRF_INVALID` | — | `user.id` | CSRF validation failed. |
| APPLICATION_STARTUP | `application-startup` | `start` | success | INFO | low | no | — | `host.name`, `host.ip`, `labels.active_profiles`, `labels.ipv6_prefix_length`, `labels.key_fingerprints`, `labels.audit_loggers` | — | Application started. |
| APPLICATION_SHUTDOWN | `application-shutdown` | `end` | success | INFO | low | no | — | — | — | Application stopping. |

## Degraded row

When a context does not fit its event, `emit` writes the event's constants with `event.outcome` `unknown`, `message` `Audit row degraded.` and one of these `event.reason` codes, and none of the context's keys or values; an ERROR on the `sg.securedhello.audit.AuditEmitter` logger raises the alert (ADR-055; R-AUD-010). The test suite fails any test that produces one unexpectedly.

| Code | Meaning |
|---|---|
| `UNKNOWN_KEY` | The context wrote a key the event does not allow. |
| `MISSING_KEY` | A required key is missing, or a request-scoped row was emitted outside a request. |
| `REASON_OUTSIDE_FAMILY` | The reason is missing or belongs to another family. |
| `EMIT_FAILED` | Building the row failed. |
