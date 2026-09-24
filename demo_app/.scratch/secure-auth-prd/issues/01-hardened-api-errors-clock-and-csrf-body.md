# 01: Hardened API errors, Clock seam and CSRF token in the body

**What to build:** Every API failure answers with the standard JSON error body and never leaks a stack trace, exception class or Spring's default error page. `GET /api/v1/auth/csrf` returns the CSRF token in its body as well as the cookie, so a cross-origin SPA can read it (the next ticket depends on this). A single injectable `Clock` exists so later tickets can move time in tests. See spec §Implementation Decisions › Error handling, Headers, Topology › CSRF across origins, and Backend modules › Clock seam.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `ApiError` gains an optional `fieldErrors: [{field, message}]`, left out when empty. The existing shape is otherwise unchanged, and the existing login validation errors still pass their tests.
- [ ] Malformed or unreadable JSON → `400 MALFORMED_REQUEST`. Unsupported media type → `415`. Unknown path → `404 NOT_FOUND`. Wrong method → `405 METHOD_NOT_ALLOWED`. All are JSON.
- [ ] Any unexpected exception → `500 INTERNAL_ERROR`, `"Something went wrong"`, with no stack trace or exception class in the body. The exception is logged server-side only.
- [ ] `server.error.include-message`, `include-stacktrace` and `include-binding-errors` are `never`, and the whitelabel page is off.
- [ ] Request bodies over 16 KB are rejected with a JSON error.
- [ ] `GET /api/v1/auth/csrf` → `200 {"headerName": "X-XSRF-TOKEN", "token": "<raw>"}`, and it still sets the `XSRF-TOKEN` cookie. A test proves the body token works as the header on a state-changing request.
- [ ] A `java.time.Clock` bean (system UTC) exists, and a test configuration can swap in a controllable clock.
- [ ] The prod profile test asserts HSTS (`max-age=31536000; includeSubDomains`) on an HTTPS request.
- [ ] The README documents the dependency vulnerability scans: `npm audit --audit-level=high` for the frontend and the Maven scan.
- [ ] The existing login and logout tests stay green. Frontend unit tests and handlers are updated for the new `/csrf` response shape if needed. `mvn verify` passes, including JaCoCo ≥80%.
