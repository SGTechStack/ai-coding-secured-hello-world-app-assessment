# 02: Logging foundation and audit log

**What to build:** Every line the API writes is ECS JSON that an operator can follow by trace and correlation ID, with no sensitive data. There is one place later tickets use to emit audit events, written to a dedicated audit file. This sets up the logging every later ticket relies on. See the spec's "Structured logging" and "Audit event contract" sections, stories 90 (startup, shutdown and CSRF rejection events) and 91–99, and ADR 0001 (why business-rule rejections stay at WARN and why `session.hash` is allowed).

**Blocked by:** 01

**Status:** resolved

- [x] Console and application-log file output are ECS JSON: newline-delimited UTF-8 with RFC 3339 timestamps in UTC+8, and `service.name`, `service.version` and `service.environment` from configuration. Text and JSON are never mixed.
- [x] Micrometer Tracing adds `trace.id` and `span.id` to every request's log lines.
- [x] A correlation filter reads a sanitised `X-Correlation-ID` or generates a UUID, puts `correlation.id` in MDC, adds `user.id` once the request is authenticated (first verifiable in ticket 04), and clears its MDC fields in a `finally` block. MDC never holds a Session ID.
- [x] Every request logs one INFO line at start (method, path) and one at end (status, duration, outcome), never the client IP, query string, headers or body.
- [x] A startup event includes host name and IP, active profiles, service metadata, and non-secret key settings, with no credentials, connection strings or database names. A shutdown event is emitted too.
- [x] The global error handler logs each unexpected exception once, at ERROR, with `error_code`, `error_category` and `error_follow_up_action`, which a custom encoder maps to `error.code`, `error.category` and `error.follow_up_action`. The exception is attached so `error.type`, `error.message` and `error.stack_trace` are populated, and the exception message is sanitised before logging.
- [x] The encoder masks, as `***MASKED***`, any value whose key matches password, token, secret, csrf, session id, email or username.
- [x] CR, LF and other control characters in user-controlled values (request path, correlation header) are escaped before they reach a log line.
- [x] An Audit log module emits events through the SLF4J fluent API, using key-value metadata and static messages, to a dedicated audit logger. That logger writes to its own rolling JSON file with at least 90 days of history and never goes to the application log file.
- [x] Audit events support the contract fields: `event.action` (restricted to the spec's mapping table), `event.outcome`, `event.reason`, `user.id`, `target.user.id`, before and after state, `authentication.method`, `session.hash`, `source.ip_hash`, `url.path`, `http.request.method`, and `trace.id` (always present).
- [x] The first audit producers are wired here: `application-startup` and `application-shutdown` (INFO), and a CSRF rejection (WARN `access-control`, with `url.path` and `http.request.method`).
- [x] SQL logging is enabled only in `dev`.
- [x] A test seam captures application-log and audit-log output as parsed JSON, so tests can assert on fields.
- [x] Tests cover: every line parses as JSON with the required service fields and `trace.id`; the startup event is present and an `application-startup` audit event lands in the audit destination; a POST without a CSRF token emits a WARN `access-control` audit event; an unexpected exception is logged once at ERROR with `error.category`; a request path containing CR/LF cannot forge a log line; a masked key is written as `***MASKED***`; audit events land in the audit destination and not in the application log.

## Comments

### Verification (2026-09-29)

**Implemented:** Newline-delimited ECS JSON logging (UTC+8, service fields) on the console and in the application log file. Micrometer Tracing (Brave, no exporter) adds `trace.id`/`span.id`. A correlation filter handles a sanitised `X-Correlation-ID`, plus `user.id` via `AccountIdentified`, and clears MDC in `finally`. Each request gets INFO start and end lines. Startup and shutdown events each have a matching audit event. The global handler logs each unexpected exception once at ERROR with `error.code`/`error.category`/`error.follow_up_action`. `EcsLogFormatter` masks sensitive keys and escapes control characters. An `AuditLog` module writes to a dedicated 90-day rolling audit file through an enum-restricted `event.action`. A CSRF rejection writes a WARN `access-control` audit event. SQL logging is on only in `dev`. Log capture reads the real log files as parsed JSON.

**Deviations:** A custom `EcsLogFormatter` replaces Boot's built-in `ecs` format, because the built-in one would write two `error` objects and can't do UTC+8 or masking. Audit events go only to the audit file. Dev SQL goes through `logging.level.org.hibernate.SQL=DEBUG`, not `show-sql`. Hikari and Flyway connection loggers are set to WARN so JDBC URLs aren't logged. `source.ip_hash` takes an already-computed hash (the HMAC key arrives in issue 07).

**Verification steps:** `./mvnw verify` passed: 55 tests, 0 failures, JaCoCo 80% line/branch gate met. Reviewer and code-reviewer compliance gates were skipped by request and deferred until all issues are implemented.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `827644c feat(logging): Add ECS JSON logging and audit log`
