# ADR-0009: Container `HttpSession` + `SessionRegistry`, not Spring Session

## Status

Accepted

## Context

The PRD's overview names Spring Session as the session mechanism. The
implementation instead uses the servlet container's built-in `HttpSession`,
tracked by Spring Security's `SessionRegistry` (see `SecurityConfig`) so
that a password reset or admin action can force-expire a user's existing
sessions via `SessionInformation.expireNow()`.

Spring Session's actual value is externalizing session state to a shared
store (Redis, JDBC, Hazelcast, ...) so sessions survive an app restart and
are visible across multiple app instances. This app runs as a single
instance with H2 in-memory storage — there is no second instance for
sessions to be shared with, and a restart already resets the database, so
persisting sessions across restarts independently of that would be
inconsistent.

## Decision

Keep the container `HttpSession` + `SessionRegistry` approach. Do not
introduce Spring Session's `spring-session-core` / a session-store
dependency for a single-instance deployment where it would add
configuration and an external dependency (Redis, etc.) without changing
any observable behavior.

## Consequences

- Functionally equivalent to Spring Session for this deployment shape:
  `SessionRegistry`-driven forced expiry (Story 7, and the admin actions in
  `AdminUserController`) works identically either way.
- If this app is ever horizontally scaled (more than one instance behind a
  load balancer) or needs sessions to survive a restart, this decision must
  be revisited first — `SessionRegistry` is process-local, so a session
  registered on one instance is invisible to the others.
- The PRD's "Spring Session" wording should be read as a future direction
  for that scaling scenario, not a requirement already met by a
  single-instance deployment.
