# ADR 0001: Server-side session in an HttpOnly cookie, not JWT

Status: accepted · Date: 2026-09-30

## Context

The PRD names a server-side session (Spring Session) as the primary mechanism and documents JWT as a future alternative. The frontend and backend are on different origins.

## Decision

Authenticate with an opaque session id stored in a `SESSION` cookie (`HttpOnly`, `SameSite=Lax`, `Secure` outside dev). The session itself, including the Spring Security context, lives server-side. CSRF protection stays on; the SPA fetches a token from `GET /api/auth/csrf` and echoes it in `X-CSRF-TOKEN`.

## Consequences

- Logout, password reset and admin actions can truly end sessions (delete the server record) — no revocation list needed.
- The browser never sees a credential JavaScript could read; XSS cannot exfiltrate the session.
- Login must be a JSON endpoint that replicates the form-login post-processing (session id rotation, CSRF rotation, context save). This is done explicitly in `LoginService`.
- Cross-site deployments (frontend and API on different registrable domains) need `SameSite=None; Secure` (`app.security.cookie-same-site`) and HTTPS everywhere. Same-site deployments (sub-domains) work with `Lax`.
- Horizontal scaling requires a shared session store (ADR 0002).
