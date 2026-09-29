# 01: Walking skeleton: SPA ↔ API cross-origin with the security baseline

**What to build:** A Visitor opens the SPA on its own origin. It fetches a CSRF token from the API on another origin, calls `GET /api/hello`, gets 401, and quietly shows a placeholder login page. Both applications exist, and every later ticket builds on this. The API already carries the full HTTP security baseline: CORS allowlist, security headers, session cookie attributes, default-deny routing, and ProblemDetail errors that never leak internals. See the spec's "Repository and stack", "Security configuration" and "API contract" sections, and stories 40, 84–89, 112. Respect ADR 0002: the CSRF token is session-bound and the cookie repository is prohibited.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Two sibling applications exist: a Spring Boot 4 / Java 21 / Maven backend and a Vite + React + TypeScript frontend. `dev` runs the SPA on `http://localhost:3000` and the API on `http://localhost:8080`, with no dev proxy.
- [ ] Flyway runs against a file-based H2 in `dev` and in-memory H2 in tests. Spring Session JDBC tables come from a migration; Hibernate never generates schema.
- [ ] One injectable `Clock` bean in `Asia/Singapore` exists for all time-based decisions.
- [ ] `GET /api/csrf` returns the session-bound token as JSON with no-cache headers and sets no CSRF cookie. State-changing requests without a valid `X-CSRF-TOKEN` get 403; GET works without one.
- [ ] CORS allows only the configured origins, with credentials, and no wildcard or per-endpoint overrides. The `http://localhost:3000` default applies only in `dev`; any other profile fails at startup without an allowlist.
- [ ] Every response carries HSTS (1 year, includeSubDomains), `Content-Security-Policy: default-src 'self'; object-src 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and a restrictive `Permissions-Policy`.
- [ ] The session cookie is HttpOnly and `SameSite=Lax`, and `Secure` in every profile except `dev`.
- [ ] Any request matching no rule is denied; `GET /api/hello` returns 401 to a Visitor.
- [ ] Errors are RFC 9457 ProblemDetail with a `code` field. An unexpected exception returns 500 `internal_error` with a generic `detail` and no exception message, class name, SQL or stack trace.
- [ ] Oversized request bodies are rejected with 400 before any controller runs.
- [ ] The H2 console filter chain exists only in `dev`.
- [ ] The SPA's API client always sends `credentials: 'include'` and attaches the cached CSRF token to state-changing requests. Its global response handler treats 401 as "not logged in" without showing an error.
- [ ] The SPA's `index.html` declares a CSP meta tag (`default-src 'self'; connect-src 'self' <API origin>; object-src 'none'`). The SPA renders server data only as text and never uses `dangerouslySetInnerHTML`.
- [ ] The SPA serves `/.well-known/security.txt` with a `Contact` from `VITE_SECURITY_CONTACT` (`dev` default `mailto:security@example.invalid`) and an `Expires` date; a production build fails without the contact.
- [ ] Integration tests (MockMvc, full context, throwaway H2) cover: headers present, CORS allow/reject, `/csrf` uncached with no cookie, CSRF missing or invalid → 403, GET without a token succeeds, `/hello` → 401, 500 body is generic, oversized body → 400, and non-`dev` startup failing without a CORS allowlist.
