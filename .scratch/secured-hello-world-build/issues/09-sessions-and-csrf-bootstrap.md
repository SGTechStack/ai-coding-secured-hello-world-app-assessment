# 09: Sessions and CSRF bootstrap

**What to build:** Spring Session JDBC sessions and synchronizer-token CSRF, working from the SPA.

- **`GET /api/csrf`** returns `{headerName, token}`. It is the only route that creates an anonymous session, whose expiry is pinned at creation plus the idle interval (ADR-040; REJ-091).
- **The cookie** is `HttpOnly`, `SameSite=Strict`, `Path=/`, with no `Domain` and no `Max-Age`. It is named `__Host-SESSION` outside `dev` and `SESSION` in `dev` (ADR-058). An explicit `CookieSerializer` honours only the first session cookie (R-SES-007; REJ-083).
- **Session attributes** are deserialised through an allowlist filter (R-SES-003).
- **CSRF** is resolved from the `X-CSRF-TOKEN` header only, on every unsafe method, with no CSRF cookie (ADR-036; R-SES-004). A missing, wrong or superseded token gets 403 `CSRF_TOKEN_INVALID` and an audit row.
- **Filter order:** the source rate-limiter slot, then `SecurityContextHolderFilter`, then the absolute-lifetime filter, then `CsrfFilter`. `NullRequestCache` is set.
- **CORS** allows the SPA origin with credentials.
- **The SPA** bootstraps the token, sends it on unsafe requests, re-fetches it after each rotation, and does one silent re-bootstrap and retry on `CSRF_TOKEN_INVALID`. It retries only requests that got no status (REJ-052).

**Blocked by:** 04, 05

**Status:** ready-for-agent

- [ ] `GET /api/csrf` sets exactly one cookie with the required attributes, checked in the raw `Set-Cookie` on a real port.
- [ ] No other anonymous route creates a session.
- [ ] A POST with no header, a wrong token, or a valid token sent only as `_csrf` in the query or a form body gets 403 `CSRF_TOKEN_INVALID` (T-CSRF-009).
- [ ] With duplicate session cookies, only the first is honoured.
- [ ] Deserialising a disallowed attribute class is refused.
- [ ] In Vitest, the SPA recovers from one `CSRF_TOKEN_INVALID` with a single re-bootstrap, and gives up on a second.
