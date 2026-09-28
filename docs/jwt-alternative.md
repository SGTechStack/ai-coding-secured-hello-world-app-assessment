# JWT Alternative (Documented, Not Implemented)

This document expands on `assessment-prd.md`'s "Appendix: JWT Alternative". It
describes how the auth backend could be reworked to issue JWT bearer tokens
instead of the session-cookie model actually implemented (`SecurityConfig`,
`AuthController`, `SessionRepository`). Nothing in this document is built —
it exists purely as a design reference for future extension, per the PRD's
"Out of Scope" list.

## Why session cookies were chosen instead

The implemented design uses server-side sessions with an `HttpOnly`,
`Secure` (prod), `SameSite` (`Lax` in dev, `Strict` in prod) cookie plus a double-submit CSRF
token, coordinated across the frontend/backend origin split via CORS with
credentials enabled. See `assessment-prd.md`'s appendix for the trade-off
summary: JWT avoids server-side session storage but reintroduces
statefulness anyway once logout/revocation is required, while adding
token-storage risk on the client. Session cookies were judged the simpler,
safer default for this spec.

## What a JWT-based design would look like

**Token issuance.** On successful login, issue a short-lived access token
(e.g. 15 minutes, signed with RS256 or HS256) instead of creating a
`Session` row. The token's claims would carry `sub` (username), `role`, and
a unique `jti`. The client sends it back via `Authorization: Bearer <token>`
on every request — not a cookie, so no CSRF token is needed for this part of
the flow.

**Client-side storage.** Store the access token in memory (a JS variable),
not `localStorage` or `sessionStorage`, to reduce the blast radius of an XSS
vulnerability reading persisted tokens. This means the token is lost on page
reload, which is what the refresh flow below is for.

**Refresh tokens.** Issue a longer-lived refresh token (e.g. 7 days) in an
`HttpOnly`, `Secure` cookie, scoped to a dedicated `/api/auth/refresh`
endpoint path. The client calls that endpoint on startup (and on 401) to
mint a new access token. Refresh tokens should rotate on each use — old
refresh tokens are invalidated after being redeemed once — to limit replay
if one leaks.

**Revocation requires a blacklist.** JWTs are stateless and, absent
revocation, remain valid until expiry even after a user "logs out." True
logout needs a `jti` blacklist (a table or cache keyed by `jti`, with a TTL
matching the token's remaining lifetime) checked on every authenticated
request. This is the statefulness JWT was supposed to avoid — it comes back
in a different shape. The current session-repository approach
(`SessionRepository`) already does this job directly, which is part of why
it was chosen as primary.

**Password reset / account lockout interaction.** The existing
`PasswordResetService` invalidates all of a user's active sessions on
successful reset (Story 8). Under JWT, the equivalent would be blacklisting
every outstanding `jti` for that user, or bumping a per-user "token version"
claim and rejecting any token whose version doesn't match the current
value — the latter avoids needing to enumerate outstanding tokens.

**Algorithm and validation.** The decoder must reject unsigned tokens
(`alg: none`) and pin a specific algorithm; it must validate `iss`, `exp`,
and an `aud` claim scoped to this service specifically, to avoid a token
issued for a different service being accepted here.

**CORS/CSRF shift.** Bearer tokens in an `Authorization` header are not an
ambient credential the browser attaches automatically, so CSRF stops being a
concern for the API calls themselves. The primary risks shift instead to
XSS-driven token theft (mitigated by in-memory-only storage) and to CORS
misconfiguration exposing the token endpoints to origins that shouldn't have
access.

## Migration sketch, if this were ever built

1. Add a `jti` blacklist store (table or cache) and a token-issuing service
   alongside `SessionRepository`, or replace it outright.
2. Replace the session cookie in `SecurityConfig`'s filter chain with a
   bearer-token authentication filter (`OncePerRequestFilter`) that resolves
   `Authorization: Bearer` into an `Authentication`.
3. Add `/api/auth/refresh`, gated by the refresh cookie, rotating on each
   use.
4. Update `PasswordResetService`'s session-invalidation step to blacklist
   the affected user's outstanding `jti`s (or bump a token version).
5. Update the frontend's `apiClient.ts` to attach the in-memory access token
   as a header instead of relying on `credentials: 'include'`, and to call
   `/api/auth/refresh` on 401.
6. Drop the CSRF token plumbing (`csrf.ts`, the `X-XSRF-TOKEN` header) for
   the bearer-token endpoints, since it no longer protects anything there.
