# 01: Hardened API errors, Clock seam and CSRF token in the body

**What to build:** Every API failure answers with the standard JSON error body and never leaks a stack trace, exception class or Spring's default error page. `GET /api/v1/auth/csrf` returns the CSRF token in its body as well as the cookie, so a cross-origin SPA can read it (the next ticket depends on this). A single injectable `Clock` exists so later tickets can move time in tests. See spec §Implementation Decisions › Error handling, Headers, Topology › CSRF across origins, and Backend modules › Clock seam.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `ApiError` gains an optional `fieldErrors: [{field, message}]`, left out when empty. The existing shape is otherwise unchanged, and the existing login validation errors still pass their tests.
- [x] Malformed or unreadable JSON → `400 MALFORMED_REQUEST`. Unsupported media type → `415`. Unknown path → `404 NOT_FOUND`. Wrong method → `405 METHOD_NOT_ALLOWED`. All are JSON.
- [x] Any unexpected exception → `500 INTERNAL_ERROR`, `"Something went wrong"`, with no stack trace or exception class in the body. The exception is logged server-side only.
- [x] `server.error.include-message`, `include-stacktrace` and `include-binding-errors` are `never`, and the whitelabel page is off.
- [x] Request bodies over 16 KB are rejected with a JSON error.
- [x] `GET /api/v1/auth/csrf` → `200 {"headerName": "X-XSRF-TOKEN", "token": "<raw>"}`, and it still sets the `XSRF-TOKEN` cookie. A test proves the body token works as the header on a state-changing request.
- [x] A `java.time.Clock` bean (system UTC) exists, and a test configuration can swap in a controllable clock.
- [x] The prod profile test asserts HSTS (`max-age=31536000; includeSubDomains`) on an HTTPS request.
- [x] The README documents the dependency vulnerability scans: `npm audit --audit-level=high` for the frontend and the Maven scan.
- [x] The existing login and logout tests stay green. Frontend unit tests and handlers are updated for the new `/csrf` response shape if needed. `mvn verify` passes, including JaCoCo ≥80%.

## Comments

- **Error rendering.** `ApiExceptionHandler` now extends `ResponseEntityExceptionHandler` and overrides `handleExceptionInternal`, so every Spring MVC exception keeps its status (and headers such as `Allow`) but gets an `ApiError` body. `ApiError.forStatus(status, path)` gives the generic code: `400 MALFORMED_REQUEST`, `500 INTERNAL_ERROR` for any 5xx or unknown status, otherwise the `HttpStatus` name and reason phrase (`NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `NOT_ACCEPTABLE`). Error responses always set `Content-Type: application/json`, so the body is JSON even for `Accept: text/html`.
- **`/error`.** `ApiErrorController` replaces Boot's `BasicErrorController` and whitelabel page for errors raised outside Spring MVC (firewall rejections, filter exceptions, `sendError`). A direct `GET /error` is a `404`. `HeaderWriterFilter` skips error dispatches, so these rare responses don't carry the security headers. Everything that goes through Spring MVC or the security handlers does.
- **Catch-all and Spring Security.** The `Exception` handler would turn an `AccessDeniedException` from method security into a `500`, so `AccessDeniedException` is rethrown to Spring Security (anonymous → `401`, authenticated → `403`). The admin tickets depend on this.
- **Body cap.** `RequestBodyLimitFilter` (16 KB) runs in the security chain before `CsrfFilter`. A declared `Content-Length` over the cap gets `413 PAYLOAD_TOO_LARGE` right away. A chunked body is counted as it is read and also ends as `413`. Exactly 16 KB is accepted.
- **CSRF body.** `GET /api/v1/auth/csrf` returns `200 {"headerName","token"}` (was `204`). The frontend still reads the cookie. Only the MSW mocks were changed, to the new shape, through a shared `csrfResponse()` in `src/test/handlers.ts`. Ticket 03 moves the SPA to the body token.
- **Clock.** `ClockConfig` provides `Clock.systemUTC()`. Tests use `@Import(TestClockConfig.class)` and autowire `MutableClock` (`advance`, `set`). It is `@Primary`, so the context holds two `Clock` beans. Inject `Clock`, never `MutableClock`, in production code. `ApiError` timestamps still use `Instant.now()`, since they are informational.
- **`fieldErrors`.** The `ApiError.FieldError` type and JSON omission are in place. The login `VALIDATION_FAILED` response is unchanged and still has no `fieldErrors`. Registration will fill them.
- **HSTS.** Spring Security's default header value is `max-age=31536000 ; includeSubDomains`, with a space before the `;`. That is valid, and the prod test asserts that exact string.
- **Scans.** The README documents `npm audit --audit-level=high` and `mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7`. Neither was run here, since both need network access.
