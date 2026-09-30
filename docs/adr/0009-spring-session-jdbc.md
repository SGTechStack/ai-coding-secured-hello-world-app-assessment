# ADR-0009: Spring Session JDBC with one concurrent session per user

## Status

Accepted

## Context

The PRD overview names Spring Session. App-Standards UAC §4 requires server-side session
revocation and a limit on concurrent sessions. The alternative, the container `HttpSession` with
an in-memory `SessionRegistry`, was rejected for two reasons:
- A session only "expired" in the registry would still be accepted by any code path that didn't
  consult the registry.
- Nothing would survive a restart or work across instances.

## Decision

- `spring-boot-starter-session-jdbc`. The cookie is `SESSION`, set HttpOnly and SameSite by
  profile (`Strict` in prod, `Lax` in dev), and Secure in prod.
  - `SessionCookieConfig` pins these attributes explicitly. Without an embedded server (a WAR
    deployment, or MockMvc tests) Boot would otherwise copy the servlet container's defaults
    (HttpOnly off, no SameSite) over Spring Session's safe defaults.
- Idle timeout 15 min (`server.servlet.session.timeout`). Absolute lifetime 8 h
  (`AbsoluteSessionTimeoutFilter`).
- `SpringSessionBackedSessionRegistry` backed by `FindByIndexNameSessionRepository`, with
  `maximumSessions(1)`. A new login ends the older session, whose next request gets
  `401 {code:"UNAUTHENTICATED","message":"Session expired"}`. This is audited as `session-end` with
  reason `concurrent_login`.
- On login the order is: concurrent-session control, then session-id change (fixation), then CSRF
  rotation.
- Revocation deletes rows, not flags. `SessionTerminationService` looks up every session of a
  principal and deletes it, **after the surrounding transaction commits**. It runs on password
  reset, admin disable, role change and delete. Logout deletes the session.
- Schema:
  - H2 (dev/test) is auto-initialised.
  - Prod sets `spring.session.jdbc.initialize-schema=never`. The `SPRING_SESSION` tables must be
    created from Spring Session's `schema-<platform>.sql` alongside the app schema.

## Consequences

- Revocation is authoritative: a deleted session can't be replayed, and there is a test for this.
- Sessions are shared by every instance using the same database. The in-memory rate limiters are
  not shared (see ADR-0005).
- Every authenticated request reads the session row, which is fine at this scale.
- Tests can't use `MockHttpSession`. They replay the real `SESSION` cookie (`ApiSession` test
  helper).
