# 04: Structured logging and the audit trail

**What to build:** An operator can follow a request across log lines and investigate security events from a separate audit log that contains no personal data. This ticket adds ECS JSON logging, the correlation-ID filter, and the typed Audit logger, which is the only component allowed to log security events. It audits registration, login success and failure, and logout. Later tickets add their own events through the same component. See ADR-0009.

**Blocked by:** 01 (Walking skeleton), 02 (Logout and session lifetime)

**Status:** ready-for-agent

- [ ] The application log is ECS structured JSON on the console, with `service.name`, `service.version` and `service.environment` set.
- [ ] The `audit` logger writes ECS JSON to its own rolling file and does not also write to the application log.
- [ ] Every log line carries a trace ID (Micrometer tracing bridge, no exporter), renamed to `trace.id`. Record the key's actual output shape (nested or flat) in a comment on this ticket.
- [ ] A servlet filter puts a correlation ID into MDC, taken from `X-Correlation-ID` or generated, adds `user.id` after authentication, and clears both afterwards.
- [ ] The Audit logger has one typed method per event and writes through the SLF4J fluent API with ECS fields: `event.action`, `event.outcome`, `event.category`, `user.id`, target UUID, `source.ip_hash` and `error_code`.
- [ ] Client IPs are logged only as an HMAC-SHA256 with a server secret from configuration.
- [ ] Registration, login success, login failure and logout are audited with the right `event.action` and outcome. Successes are logged at INFO and failed logins at WARN.
- [ ] Accounts are identified only by UUID, and failed-login events carry no account identity.
- [ ] Passwords, CSRF tokens, raw session IDs, usernames, emails and raw IPs appear in no log output. A `ListAppender` test on the `audit` logger checks this.
- [ ] Line breaks are stripped from user input before it reaches any log field, and a test shows that injecting a fake log line fails.
- [ ] System failures are logged at ERROR.

## Comments

**2026-09-29 — `trace.id` output shape.** With Boot 4.1.1's ECS formatter and `logging.structured.json.rename.traceId: trace.id` (likewise `spanId: span.id`), the renamed keys come out **flat**: `"trace.id":"6abbb06b…","span.id":"6281861c…"`. Dotted MDC and key-value fields are **nested**, e.g. `"user":{"id":…}`, `"correlation":{"id":…}`, `"event":{"action":…}`, `"source":{"ip_hash":…}`. Elastic accepts both forms for the same field. `StructuredLoggingTest` pins this shape.

**Also found while building:** the ECS encoder refuses a line that carries the same key twice, for example `user.id` from both MDC and the event's own fields, and Logback then drops that line silently. Audit events therefore take the request's MDC `user.id` off their own line and name their Account themselves. `StructuredLoggingTest` fails if Logback reports an appender error.
