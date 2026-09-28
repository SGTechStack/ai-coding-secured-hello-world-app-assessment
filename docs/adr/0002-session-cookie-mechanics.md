# ADR-0002: Manual session-fixation/registration wiring under a custom JSON login filter

## Status

Accepted

## Context

The choice of server-side session-cookie auth over JWT is already recorded in
`docs/jwt-alternative.md` (and the PRD appendix) — this ADR does not
re-litigate that choice, only the mechanics of how it's implemented.

`SecurityConfig` replaces Spring Security's `http.formLogin()` with a custom
`JsonUsernamePasswordAuthenticationFilter` so the login endpoint accepts a
JSON body instead of a form post. `formLogin()` normally wires several
session-related behaviors for free; bypassing it means those behaviors had
to be reconstructed by hand:

- Session-fixation protection and `SessionRegistry` population are wired
  manually via `CompositeSessionAuthenticationStrategy(List.of(new
  SessionFixationProtectionStrategy(), new
  RegisterSessionAuthenticationStrategy(sessionRegistry)))` attached to the
  custom filter. Without this, session IDs would not rotate on login and the
  registry needed for Story 7's "invalidate all sessions on reset" would stay
  empty.
- `maximumSessions(SessionLimit.UNLIMITED)` is set not to cap concurrent
  sessions but solely to register `ConcurrentSessionFilter`, which is what
  makes `SessionInformation.expireNow()` actually take effect on a
  session's *next* request. A cap is deliberately not applied: Story 7
  requires that a password reset can invalidate *all* of a user's sessions,
  and a new login must never evict another of that same user's sessions as
  a side effect of a session limit.
- Cookie attributes (`HttpOnly`/`Secure`/`SameSite`) are set per Spring
  profile rather than hardcoded: dev uses `SameSite=Lax`, `Secure=false`
  (works over plain HTTP on localhost); prod uses `SameSite=Strict`,
  `Secure=true`, which assumes frontend and backend are deployed on sibling
  subdomains of one site (cross-origin, but same-site). The
  `server.servlet.session.cookie.*` properties only reach the session
  cookie, so `SecurityConfig.csrfTokenRepository` copies the same
  `SameSite`/`Secure` values onto the `XSRF-TOKEN` cookie, and scopes it to
  `app.csrf.cookie-domain` in prod so the SPA's subdomain can read it.
  `HttpOnly` is never set explicitly on the session cookie — it relies on
  Spring Boot's default of `true`.
- An absolute 8-hour session cap is enforced by a separate hand-written
  `AbsoluteSessionTimeoutFilter`, because the container's idle timeout
  (`server.servlet.session.timeout=15m`) only resets on activity and can't
  express a hard ceiling for a continuously-active session on its own.

## Decision

Accept the manual wiring as the cost of a JSON-based login endpoint. Rely on
Spring Boot's `HttpOnly=true` default rather than setting it explicitly.

## Consequences

- Every future change to the authentication filter chain must re-verify that
  session-fixation protection and `SessionRegistry` registration still fire
  — bypassing `formLogin()` means Spring Security no longer guarantees this
  by default, and a refactor that touches `SecurityConfig`'s filter chain
  could silently drop it.
- `HttpOnly` being implicit rather than explicit is a minor legibility risk:
  a reader has to know Spring Boot's default rather than reading it off the
  config. Consider making it explicit if this config is ever touched again.
- No concurrent-session cap is a deliberate absence, not an oversight — do
  not "fix" this by adding one without re-checking Story 7's session
  invalidation semantics first.
