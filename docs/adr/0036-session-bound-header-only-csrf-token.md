---
status: accepted
---

# ADR-036: Session-bound synchronizer CSRF token, header only, with the same-site constraint it imposes

CSRF protection uses a synchronizer token stored in the server-side session (`HttpSessionCsrfTokenRepository`). The
SPA fetches it from `GET /api/csrf` and sends it in the `X-CSRF-TOKEN` header. The server accepts it **only** from
that header, never from a request parameter. Two common alternatives are ruled out: a cookie-based token, which is
the SPA norm and what Spring Security's `csrf.spa()` preset configures, and Spring's default fallback that also reads
a `_csrf` parameter.

## Context

- The governing standard's §3.1 requires the Synchronizer Token Pattern bound to the HTTP session, and prohibits the
  Double Submit Cookie pattern (`CookieCsrfTokenRepository`) outright. §2 Happy Path step 8 prescribes a dedicated
  token endpoint and the `X-CSRF-TOKEN` header.
- `CsrfConfigurer#spa()` in Spring Security 7.1 is built on `CookieCsrfTokenRepository`, so it is prohibited too. It
  is the likeliest wrong turn for a JavaScript frontend.
- In Spring Security 7.1.1 the default token resolution (`CsrfTokenRequestHandler#resolveCsrfTokenValue`) reads the
  header and, if it is absent, falls back to the `_csrf` request parameter. `XorCsrfTokenRequestAttributeHandler`,
  the default handler, inherits that fallback. The standard's §3.4 forbids logging CSRF tokens, and a token in a query
  string lands in access logs and `Referer` headers. ASVS 5.0 **14.2.1 (L1)** says the URL and query string must not
  carry sensitive data such as a session token.
- ASVS 5.0 **3.5.1 (L1)** accepts validated anti-forgery tokens as the CSRF control. The OWASP CSRF Prevention Cheat
  Sheet names a pre-session plus a token as the defence against login CSRF, provided the pre-session is destroyed and
  replaced at authentication.

## Decision

- **Repository:** `HttpSessionCsrfTokenRepository`, configured explicitly even though it is the default, so the
  enforced constraint is visible in code. No CSRF cookie is ever set.
- **Handler:** a wrapper around `XorCsrfTokenRequestAttributeHandler`, which keeps its per-response masking (BREACH
  protection). The class is `final` and has no switch for the fallback, so the wrapper delegates `handle` and returns
  no token when the header is absent. It only delegates resolution when the header is present, so the parameter is
  never consulted.
- **Endpoint:** `GET /api/csrf` returns an application-owned DTO, `{"headerName": "X-CSRF-TOKEN", "token": "…"}`, and
  is not cacheable. `parameterName` is left out on purpose, which removes the discovery path for the parameter.
- **Scope:** every unsafe method, including login, logout and the four anonymous credential-flow endpoints. Nothing is
  exempt. Logout on a dead session therefore returns 403, and the standard's §5 explicitly forbids fixing that by
  exempting logout.
- **Rotation:** the token rotates with the session id at login, logout, factor grant and credential change (ADR-038).
  It never rotates per request. The SPA re-fetches proactively after each rotation, and the one silent retry on
  `CSRF_TOKEN_INVALID` is the backstop, not the main path (ADR-040).

## Consequences

- **The same-site constraint.** A session-bound token only works if the session cookie reaches the API on the SPA's
  cross-origin `fetch`. With `SameSite=Strict` (ADR-058, chosen per ASVS 3.3.2 (L2)) that holds only while the SPA
  and the API are **same-site**: the same scheme and the same registrable domain. `localhost:5173` and
  `localhost:8080` are cross-origin but same-site, and so are two HTTPS subdomains of one registrable domain. Deploying the SPA and API on different registrable domains stops
  the cookie on every request, so the CSRF bootstrap fails, and sign-in with it. The topology is in ADR-059, and the
  handover document carries the constraint for deployers.
- Every `fetch` must send `credentials: 'include'`, and a JSON body plus the custom header makes every mutation a
  CORS preflight. CORS must therefore allow credentials and the `X-CSRF-TOKEN` header for the SPA origin only.
- The masked token differs on every call, so nothing may cache the response or compare tokens across calls.
- The pre-login session this token needs is the only anonymous session the application creates (ADR-040).
- The standard's §5 test for "previously redeemed CSRF tokens" and its CSRF-cookie attribute clause cannot pass as
  printed under this pattern. They are reinterpreted (REJ-009): a token from a superseded session is rejected
  (T-CSRF-007), and no CSRF cookie is ever set (T-CSRF-001).
- Tests: T-CSRF-001, T-CSRF-003, T-CSRF-004, T-CSRF-005, T-CSRF-006, T-CSRF-007. The header-only resolution needs a
  test of its own, because nothing else fails if the wrapper is removed: a valid token sent only as a `_csrf`
  parameter, in the query string or a form body, must be refused with 403 `CSRF_TOKEN_INVALID`.

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 8, §3.1 Inputs / Outputs (Synchronizer
  Token Pattern; Double Submit Cookie prohibited), §3.4 Logging Contract, §5 CSRF Protection Tests and the logout
  test.
- Spring Security 7.1.1 source: `CsrfTokenRequestHandler#resolveCsrfTokenValue` (header, then `_csrf` parameter),
  `XorCsrfTokenRequestAttributeHandler` (`final`; inherits the resolution).
- Spring Security reference, Servlet, Cross Site Request Forgery; `CsrfConfigurer#spa()` javadoc.
- OWASP ASVS 5.0: 3.5.1 (L1), 14.2.1 (L1), 3.3.2 (L2).
- OWASP Cross-Site Request Forgery Prevention Cheat Sheet, "Possible CSRF Vulnerabilities in Login Forms".
