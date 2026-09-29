# 1. Session-cookie authentication over JWT

Date: 2026-09-29
Status: Accepted

## Context

The app needs an authentication mechanism for a React SPA talking to a Spring
Boot API. The two mainstream options are a server-side session carried by a
secure cookie, or a stateless JWT bearer token.

JWT is stateless and avoids server-side session storage, but true logout
requires a token-id (`jti`) blacklist checked on every request — which
reintroduces server-side state anyway — and storing the token client-side
creates XSS-driven theft risk.

## Decision

Use a **server-side session** via a secure `HttpOnly` cookie (Spring Session)
as the primary and only built-in auth mechanism. JWT is documented in the PRD
appendix as a future alternative but not implemented.

## Consequences

- Logout is a real server-side operation: invalidate the session, clear the
  cookie. No blacklist needed.
- The cookie is `HttpOnly` (not readable by JS), reducing XSS token theft.
- Requires CSRF protection because the credential is an ambient cookie
  (see the cross-origin CSRF setup).
- Introduces server-side session storage and, for multi-instance deployments,
  a shared session store — accepted as the correct trade-off for this app.
- Reversing to JWT later means adding a revocation store and client-side token
  handling: a meaningful, deliberate migration.
