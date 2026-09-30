# 01: Walking skeleton: SPA ↔ API cross-origin with the security baseline

**What to build:** A Visitor opens the SPA on its own origin. It fetches a CSRF token from the API on another origin, calls `GET /api/hello`, gets 401, and quietly shows a placeholder login page. Both applications exist, and every later ticket builds on this. The API already carries the full HTTP security baseline: CORS allowlist, security headers, session cookie attributes, default-deny routing, and ProblemDetail errors that never leak internals. See the spec's "Repository and stack", "Security configuration" and "API contract" sections, and stories 40, 84–89, 112. Respect ADR 0002: the CSRF token is session-bound and the cookie repository is prohibited.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Two sibling applications exist: a Spring Boot 4 / Java 21 / Maven backend and a Vite + React + TypeScript frontend. `dev` runs the SPA on `http://localhost:3000` and the API on `http://localhost:8080`, with no dev proxy.
- [x] Flyway runs against a file-based H2 in `dev` and in-memory H2 in tests. Spring Session JDBC tables come from a migration; Hibernate never generates schema.
- [x] One injectable `Clock` bean in `Asia/Singapore` exists for all time-based decisions.
- [x] `GET /api/csrf` returns the session-bound token as JSON with no-cache headers and sets no CSRF cookie. State-changing requests without a valid `X-CSRF-TOKEN` get 403; GET works without one.
- [x] CORS allows only the configured origins, with credentials, and no wildcard or per-endpoint overrides. The `http://localhost:3000` default applies only in `dev`; any other profile fails at startup without an allowlist.
- [x] Every response carries HSTS (1 year, includeSubDomains), `Content-Security-Policy: default-src 'self'; object-src 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` and a restrictive `Permissions-Policy`.
- [x] The session cookie is HttpOnly and `SameSite=Lax`, and `Secure` in every profile except `dev`.
- [x] Any request matching no rule is denied; `GET /api/hello` returns 401 to a Visitor.
- [x] Errors are RFC 9457 ProblemDetail with a `code` field. An unexpected exception returns 500 `internal_error` with a generic `detail` and no exception message, class name, SQL or stack trace.
- [x] Oversized request bodies are rejected with 400 before any controller runs.
- [x] The H2 console filter chain exists only in `dev`.
- [x] The SPA's API client always sends `credentials: 'include'` and attaches the cached CSRF token to state-changing requests. Its global response handler treats 401 as "not logged in" without showing an error.
- [x] The SPA's `index.html` declares a CSP meta tag (`default-src 'self'; connect-src 'self' <API origin>; object-src 'none'`). The SPA renders server data only as text and never uses `dangerouslySetInnerHTML`.
- [x] The SPA serves `/.well-known/security.txt` with a `Contact` from `VITE_SECURITY_CONTACT` (`dev` default `mailto:security@example.invalid`) and an `Expires` date; a production build fails without the contact.
- [x] Integration tests (MockMvc, full context, throwaway H2) cover: headers present, CORS allow/reject, `/csrf` uncached with no cookie, CSRF missing or invalid → 403, GET without a token succeeds, `/hello` → 401, 500 body is generic, oversized body → 400, and non-`dev` startup failing without a CORS allowlist.

## Comments

### Verification (2026-09-29)

**Implemented:** A Spring Boot 4 / Java 21 / Maven backend and a Vite + React + TypeScript frontend, as siblings. In `dev` the SPA runs on :3000 and the API on :8080, with no proxy. Flyway migrations create the Spring Session JDBC tables, on file-based H2 in `dev` and in-memory H2 in tests. A single `Clock` bean uses `Asia/Singapore`. The CSRF token comes from `GET /api/csrf`, is bound to the session and sets no cookie. CORS allows only the configured origins; startup fails outside `dev` without an allowlist. Every response carries the security headers. The session cookie is HttpOnly, SameSite=Lax, and Secure outside `dev`. Unmatched requests are denied by default. Errors are RFC 9457 ProblemDetail, and a 500 returns a generic body. Oversized request bodies, including chunked ones, get a 400. The H2 console exists only in `dev`. The SPA API client sends credentials and the CSRF token, and treats a 401 as not logged in. The SPA has a CSP meta tag and serves `security.txt`.

**Verification steps:**
- Backend `./mvnw verify` passed: 55 tests, 0 failures, JaCoCo gate met. This run includes issue 02's tests, since the two share files.
- Frontend `npm run test:coverage` passed: 18 tests, 100% lines, 94.87% branches.
- Frontend `lint`, `format:check` and a production `build` passed.
- Reviewer gate skipped by request (deferred until all issues are implemented). An earlier code-reviewer report is at `artifacts/code-reviewer/01-walking-skeleton-compliance.html`.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `1ef5b1a feat: Add walking skeleton for secured hello world`
