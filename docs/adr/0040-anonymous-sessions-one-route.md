---
status: accepted
---

# ADR-040: Anonymous sessions are created on demand, by exactly one route

Only `GET /api/csrf` may create an anonymous session, and the SPA calls it only when it is about to need a token,
never on page load. The common SPA pattern is to prefetch the CSRF token when the app starts. Here that would write a
session row to the database for every visitor and every reload. The framework also creates sessions in places
nobody asks it to, and those are closed off.

## Context

- The CSRF token is session-bound (ADR-036), so a pre-login session is unavoidable. `GET /api/csrf` is therefore an
  unauthenticated request with a persistent side effect: one `SPRING_SESSION` row plus one attribute row.
- Spring Session JDBC's cleanup job deletes only rows whose expiry has passed. It never touches live rows, so the
  number of live anonymous rows is set by how fast they are created, not by the job.
- Spring Security 7.1.1 creates sessions in two other places. `CsrfFilter` loads the deferred token on every unsafe
  request, and when none exists the repository generates one and saves it, which calls `request.getSession()`. So
  any POST, PUT, PATCH or DELETE without a session creates one before its 403, on any path, because `CsrfFilter` runs
  before authorization. And `ExceptionTranslationFilter` saves the request in the session-backed request cache before
  starting authentication, so every anonymous request to a protected path creates one.

## Decision

- **Only `GET /api/csrf` creates anonymous sessions.**
- **The CSRF repository is wrapped.** A delegating wrapper around `HttpSessionCsrfTokenRepository` (which is `final`)
  skips a non-null `saveToken` when `request.getSession(false)` is null. A null `saveToken`, used by login and logout
  rotation, always passes through and never creates a session. A request whose save was skipped compares against a
  token that was never stored, so it still gets `403 CSRF_TOKEN_INVALID`.
- **The wrapper must not forward `loadDeferredToken`.** It inherits the interface default, which builds the deferred
  token over `this`. Forwarding the call would bind the deferred token to the inner repository, and the wrapper's
  `saveToken` would never run.
- **The controller's `getSession(true)` is load-bearing.** The wrapper cannot tell the filter's save from the
  controller's. `/api/csrf` still creates a session only because its controller calls `request.getSession(true)`
  *before* it resolves the token. The controller carries a comment saying so.
- **`NullRequestCache`** is set on the chain, so no request is ever saved in a session. Nothing in this design
  replays a saved request.
- **The SPA fetches the token lazily**, on first need, and re-fetches it proactively after sign-in, sign-out, factor
  verification and enrolment confirmation, because each rotates it. The single silent retry on
  `CSRF_TOKEN_INVALID` is the backstop, not the main path. Relying on the retry would produce a CSRF 403 on the first
  mutation of every session, which drowns the CSRF-rejection signal that operators are asked to watch.

## Consequences

- Removing `getSession(true)` from the controller makes `/api/csrf` stop creating sessions, and nothing else fails.
  T-SES-027 exists for exactly that.
- The anonymous credential-flow endpoints (registration, activation, both reset steps) are not exempt from CSRF, so
  each needs a `/api/csrf` round trip first.
- A per-source budget on `/api/csrf` bounds the creation rate per source. The total across sources is bounded by
  ADR-041, and every anonymous session's expiry is pinned at creation (REJ-091).
- An unauthenticated safe method with a persistent side effect sits oddly with HTTP safe-method semantics. That is
  structural under a session-bound token and is not a defect to fix.
- Tests: T-SES-026 (with no cookie, a fabricated cookie and an expired cookie, every method on every mapped route, an
  unmapped path, `/api/admin/**` and actuator create zero rows, and `GET /api/csrf` creates exactly one), T-SES-027,
  T-CSRF-008 (the wrapper inherits `loadDeferredToken`).

## Sources

- Standalone User Access Control Application Standard §3.1 Inputs / Outputs (session-bound Synchronizer Token
  Pattern), §6 Operational Runbook, Observable Signals.
- Spring Security 7.1.1 source: `CsrfFilter#doFilterInternal`, `RepositoryDeferredCsrfToken#init`,
  `HttpSessionCsrfTokenRepository#saveToken` (`final` class), `CsrfTokenRepository#loadDeferredToken` (interface
  default), `ExceptionTranslationFilter#sendStartAuthentication`, `HttpSessionRequestCache#saveRequest`.
- Spring Session 4.1.1 source: `JdbcIndexedSessionRepository` (one session row plus one row per attribute; cleanup
  deletes expired rows only).
