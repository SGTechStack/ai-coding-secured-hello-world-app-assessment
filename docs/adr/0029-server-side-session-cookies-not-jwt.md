---
status: accepted
---

# ADR-029: Server-side session cookies, not JWT

The API authenticates the browser with a server-side session identified by an `HttpOnly` cookie. It does not issue
JSON Web Tokens, as bearer headers or as cookies. A maintainer who knows the common SPA default will expect JWT here,
and the PRD itself documents JWT as the alternative, so this records why it was not built.

## Context

- The PRD names a server-side session in a secure `HttpOnly` cookie (Spring Session) as the primary mechanism. It
  documents JWT in its appendix "JWT Alternative" as not built, and puts a JWT implementation out of scope.
- The governing standard (Standalone User Access Control Application Standard §4, Authentication and Security Design)
  makes server-side sessions with cookie-managed session IDs an `[Enforced Constraint]`.
- The application needs server-side termination on its hot paths: logout, credential change, admin disable, role
  change, deletion, lockout and the authenticator cap (ADR-035, ADR-037). Authorities also change while a user is
  signed in, and a role downgrade must take effect at once.

## Considered options

- **Server-side session cookie (chosen).** The server holds the state. Termination deletes one row, and the next
  request fails.
- **Stateless JWT access token plus refresh token.** The PRD appendix's own analysis applies. A JWT stays valid until
  it expires whatever "logout" does. True logout needs a revocation store keyed by the token's `jti` claim
  (RFC 7519 §4.1.7) and checked on every authenticated request. That brings back the server-side state JWT was meant
  to remove, and adds client-side token storage risk. OWASP ASVS 5.0 **7.4.1 (L1)** says the same: applications using
  self-contained tokens need a list of terminated tokens, a per-user not-before time, or a per-user signing key to
  stop a terminated token being used.
- **JWT in an `HttpOnly` cookie.** This removes the storage risk, but not the revocation store. It is also still a
  cookie credential, so it keeps every CSRF obligation a session cookie has and gains nothing from being a JWT.

## Decision

Server-side sessions in Spring Session JDBC (ADR-030), identified by an `HttpOnly`, `SameSite=Strict` cookie
(ADR-058). No JWT is issued anywhere in the application.

## Consequences

- Every termination rule in this ADR set is a row deletion, and every one is testable by replaying a captured
  cookie (T-SES-003, T-SES-012 onwards).
- The cookie is an ambient credential, so CSRF protection is mandatory on every state-changing request (ADR-036).
  A bearer header would not need it.
- The session store is a runtime dependency on every request, and it is a resource an unauthenticated caller can
  grow (ADR-040, ADR-041).
- Reopening trigger: a second, non-browser client (a mobile app or a service caller) that cannot hold a cookie. That
  is a new authentication pathway and needs its own decision. It does not justify replacing this one.

## Sources

- PRD, Overview ("Primary auth mechanism", "Alternative auth mechanism"), Out of Scope, and Appendix: JWT
  Alternative.
- Standalone User Access Control Application Standard §4, Authentication and Security Design.
- OWASP ASVS 5.0, V7.4 Session Termination, 7.4.1 (L1).
- RFC 7519 (JSON Web Token), §4.1.7 `jti`.
