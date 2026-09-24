# ADR 0003 — Session, revocation and throttle state are single-instance

- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** deployment topology

## Decision

Sessions, the `SessionRegistry` that backs session revocation, the per-IP login
throttle and the request-rate limiter are all JVM-local. The application is
supported as a single instance.

## Why

Making any of them shared means an external store — Spring Session with Redis or
JDBC, and a distributed counter. That is real infrastructure for an application
whose PRD scopes out containerisation and deployment entirely, and the correct
choice depends on the platform that does not exist yet.

The important part is that this is recorded as a limitation rather than an
assumption nobody wrote down, because three separate security controls quietly
depend on it.

## Consequences

Running a second instance does not degrade gracefully; it breaks specific
controls:

- **Session continuity.** A request routed to the other instance is
  unauthenticated. Visible immediately, so this one is self-correcting.
- **Session revocation.** `SessionRevoker` reaches only the instance handling the
  call. A disabled, demoted or deleted user keeps a working session on every other
  instance until it expires. This is the dangerous one: it fails silently, and it
  is the fix for TM-04.
- **Login throttling and rate limits.** Each instance holds its own counters, so
  the effective threshold is multiplied by the instance count.

Before scaling out, Spring Session and a shared rate-limit store are prerequisites,
not optimisations.

## Related

- Threat model TM-25, TM-09, TM-10
- `IpLoginThrottle` Javadoc, `AdminUserManagementService` Javadoc
