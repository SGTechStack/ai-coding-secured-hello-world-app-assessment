# ADR-0002: Session wiring under a custom JSON login filter

## Status

Accepted

## Context

The choice of server-side session-cookie auth over JWT is recorded in `docs/jwt-alternative.md`
and the PRD appendix. This ADR covers only the mechanics.

`SecurityConfig` replaces `http.formLogin()` with `JsonUsernamePasswordAuthenticationFilter`, so
login accepts a JSON body. `formLogin()` would normally wire the session behaviour automatically,
so the custom filter has to be given it explicitly.

## Decision

- **Login session strategy.** The login filter gets a `CompositeSessionAuthenticationStrategy`,
  applied in this order:
  1. `ConcurrentSessionControlAuthenticationStrategy` (maximum 1 session per user, backed by
     `SpringSessionBackedSessionRegistry`). A new login expires the user's older session
     (ADR-0009).
  2. `ChangeSessionIdAuthenticationStrategy`, which prevents session fixation.
  3. `CsrfAuthenticationStrategy`, which rotates the CSRF token (ADR-0003).
- **Expired sessions.** `sessionConcurrency(maximumSessions(1))` also registers
  `ConcurrentSessionFilter`. A request on an evicted session gets
  `401 {code:"UNAUTHENTICATED","message":"Session expired"}` and is audited as `session-end`
  with reason `concurrent_login`.
- **Revoking sessions.** Revocation for password reset, admin disable, role change and delete
  doesn't depend on the registry. `SessionTerminationService` deletes every Spring Session row of
  the principal after the transaction commits.
- **Cookie attributes.** The `SESSION` cookie's attributes are set explicitly in
  `config/SessionCookieConfig`: HttpOnly always, with SameSite and Secure from the profile.
  - dev: `Lax`, not Secure (plain HTTP on localhost)
  - prod: `Strict`, Secure (frontend and backend on sibling subdomains of one site)
  - Without this, Boot copies the servlet container's defaults (HttpOnly off, no SameSite)
    whenever there is no embedded server.
- **Timeouts.** Idle timeout is 15 min (`server.servlet.session.timeout`). An absolute 8-hour cap
  is enforced by `AbsoluteSessionTimeoutFilter`, which runs before `SecurityContextHolderFilter`,
  because an idle timeout can't cap a session that stays active.

## Consequences

- Any change to the security filter chain must re-verify the login strategy chain
  (`SessionAndCsrfFlowTest` covers rotation, eviction and replay).
- Users can't stay logged in on two browsers at once. This is intentional (App-Standards UAC §4).
