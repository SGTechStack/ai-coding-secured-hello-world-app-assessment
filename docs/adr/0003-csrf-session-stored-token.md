# ADR-0003: Session-stored CSRF token fetched from `/api/auth/csrf`

## Status

Accepted

## Context

Authentication is cookie-based, so every state-changing endpoint needs CSRF protection (PRD
NFR). The common SPA pattern, a double-submit cookie (`XSRF-TOKEN` with `HttpOnly=false`, echoed
in a header), was rejected because App-Standards (UAC §3.1 and the HDR "Strictly Prohibited"
list) forbids a token that JavaScript can read from a cookie. The PRD asks for CSRF protection on state-changing endpoints but doesn't prescribe a
mechanism.

## Decision

- `HttpSessionCsrfTokenRepository`: the token lives in the server-side (Spring Session JDBC)
  session, not in a cookie. Spring Security's default `XorCsrfTokenRequestAttributeHandler` masks
  it per request (BREACH).
- The SPA fetches the token from `GET /api/auth/csrf` → `{"headerName":"X-CSRF-TOKEN","token":…}`
  (`Cache-Control: no-store`), holds it **in memory only**, and sends it as `X-CSRF-TOKEN`.
- `CsrfAuthenticationStrategy` rotates the token on login. After login and after logout the client
  drops its copy and refetches. On a `403 {code:"CSRF_INVALID"}` the client refetches and retries
  **once**.
- `permitAll()` does **not** mean CSRF-exempt, since login, register and
  password-reset all require the token. The only exemption is `/h2-console/**` under the `dev`
  profile.

## Consequences

- No CSRF secret is reachable from `document.cookie`. XSS can still call the endpoint, but it
  can't steal a long-lived token.
- The CSRF token's lifetime is the session's lifetime. When the session ends, the token ends with
  it.
- One extra request before the first state-changing call, and after each login or logout.
- Cross-origin dev setups work without cookie-domain tricks. The CSRF endpoint is covered by CORS
  like the rest of the API.
