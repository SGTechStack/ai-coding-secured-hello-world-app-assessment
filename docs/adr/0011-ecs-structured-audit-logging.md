# ADR-0011: ECS structured logging with a typed audit module and trace correlation

## Status

Accepted

## Context

Audit lines used to be plain text that included raw usernames, which App-Standards LOG §3.3
forbids. Levels were inconsistent. Several security events weren't logged at all: logout, 403/CSRF,
rate limiting, registration and rejected admin actions. Nothing correlated a log line with a request
or trace.

## Decision

- `logging.structured.format.console=ecs`. Every line is ECS JSON.
- Micrometer Tracing (Brave) supplies `trace.id` / `span.id`. Micrometer's MDC keys are renamed via
  `logging.structured.json.rename`. There's no exporter, so the sampling probability is 1.0 and
  costs only bookkeeping.
- `RequestLoggingContextFilter` puts these into MDC:
  - `correlation.id`: the inbound `X-Correlation-ID` if it matches `^[A-Za-z0-9-]{8,64}$`,
    otherwise a new UUID. It's echoed on the response.
  - `source.ip`
  - `session.hash`: the first 8 bytes of the SHA-256 of the session id, never the id itself.
  - `user.id`
- An MDC `TaskDecorator` carries this context onto `@Async` threads.
- `AuditLogger` is the only way to write audit events. They go to the `AUDIT` logger via the SLF4J
  fluent API:
  - Every event has `event.action`, `event.category` and `event.outcome`, and where relevant
    `event.reason`, `user.target.id` and `labels.*`.
  - Successes log at INFO; failures and rejections at WARN.
  - For events before authentication, `user.id` is omitted.
  - Fields are sanitised (control and line-separator characters removed) and capped at 64 characters.
- Users are identified only by `users.public_id` (UUID). Usernames, emails, passwords, reset
  tokens, reset links (outside the dev-only stub, ADR-0001) and raw session ids are never logged.
- The event vocabulary is listed in `.scratch/security-hardening/spec.md` ("Audit events").

## Consequences

- Logs can be sent straight to an ECS-aware SIEM. To trace a user, look up their `public_id`
  instead of searching for their username.
- To add an audit event, add a method to `AuditLogger`. Ad-hoc `log.info` calls for security events
  are a review smell.
- Audit writes are synchronous local calls, which is safe inside transactions. If `AUDIT` is ever
  routed to a network appender, move the calls out of transactions.
