# 03: Cross-origin topology

**What to build:** The SPA runs on its own origin (`http://localhost:3000`) and calls the API (`http://localhost:8080`) directly, as the PRD describes. The Vite `/api` proxy is removed, and login and logout keep working end to end. The API accepts cross-origin requests only from an explicit allow-list. The SPA keeps the CSRF token in memory, taken from the `/csrf` response body. See spec §Topology and §Frontend modules › API client.

**Blocked by:** 01

**Status:** resolved

- [x] CORS is configured in the security filter chain from `app.cors.allowed-origins`, with exact origins only (no wildcards, no `null`). `allowCredentials: true`. Methods `GET, POST, PATCH, DELETE`. Headers `Content-Type, Accept, X-XSRF-TOKEN`. Preflight cache 1 hour.
- [x] Dev and test default to `http://localhost:3000`. With `prod` and an empty list, startup fails with a clear message.
- [x] A preflight from the allowed origin gets the allow headers, including credentials. A preflight from another origin gets `403` and no `Access-Control-Allow-Origin`. An actual request from another origin gets no allow-origin header.
- [x] The security configuration's documentation records that the allow-list is what protects the `/csrf` body token.
- [x] The SPA reads `VITE_API_BASE_URL` (default `http://localhost:8080`) and sends every request with `credentials: "include"`. The Vite dev server runs on port `3000`. `FRONTEND_PORT` and `BACKEND_PORT` still work for dev and Playwright.
- [x] The CSRF token is held in memory only, never in web storage. It is fetched lazily before the first state-changing request and dropped after login and after logout.
- [x] `withCsrfRetry` retries only on `403`, not on `400`, `409` or `429`.
- [x] `ApiError` exposes `code` and `fieldErrors` from the body. The kinds are `unauthorized` (401), `throttled` (429), `rejected` (other 4xx) and `unavailable` (5xx or network). `RequestOptions.method` accepts `PATCH` and `DELETE`.
- [x] Client unit tests cover credentials, the token lifecycle, the 403-only retry, the `429` → `throttled` mapping and the parsing of `code` and `fieldErrors`.
- [x] The existing e2e Stories 1–3 pass cross-origin. The README's run instructions and port table are updated.

## Comments

- **Config.** `CorsProperties` (record, `app.cors.allowed-origins`, package `security`) validates in its constructor: the list must be non-empty and each entry an exact `http(s)://host[:port]` origin (no `*`, `null`, path, trailing slash, query, fragment or userinfo). Boot creates the record even when the property is absent, so any profile without a list fails at startup with `app.cors.allowed-origins must list at least one origin ...` (`CorsStartupTest` boots `prod` to prove it). Defaults live in `application-dev.yml` and `src/test/resources/application-test.yml` only. `ProdProfileTest` now sets `app.cors.allowed-origins`; any later prod-context test must too. Env override: `APP_CORS_ALLOWED_ORIGINS` (comma-separated).
- **Filter.** `http.cors(withDefaults())` picks up the `CorsConfigurationSource` bean in `SecurityConfig`, so CORS runs before CSRF and authentication. A refused preflight (other origin, method or header) is Spring's plain-text `403 Invalid CORS request`, not an `ApiError`: browsers never expose that body, so it was left as is. An actual request from another origin is also refused with that `403` (no allow-origin either way). No `exposedHeaders` are set: ticket 06 must add `Retry-After` to `CorsConfiguration.setExposedHeaders` if the SPA is to read it.
- **Tests.** CORS is tested through MockMvc over the real filter chain (`CorsApiTest`, `Origin` + `Access-Control-Request-*` headers); the spec's "real HTTP for CORS preflight" wasn't needed, since `CorsFilter` is part of the chain MockMvc drives.
- **Client.** `API_BASE_URL` is exported from `api/client.ts` (trailing slashes trimmed). The CSRF token is a module-level `Promise<string>`, so concurrent state-changing requests share one `/csrf` fetch, and a failed fetch is not cached. `dropCsrfToken()` replaces `refreshCsrfToken()`: `login()` and `logout()` call it on success (logout also on `401`); ticket 09 must call it after a successful reset confirm. `withCsrfRetry` now checks `error.status === 403`. `ApiError`'s constructor takes `(kind, message, { status, code, fieldErrors })`; malformed `fieldErrors` entries are dropped.
- **Tests (frontend).** MSW handlers must use absolute URLs: `api(path)` from `src/test/handlers.ts`. `csrfResponse()` no longer sets a cookie, to prove the body token is used. `setup.ts` calls `dropCsrfToken()` after each test instead of clearing cookies. New `src/api/auth.test.ts` covers the token lifecycle and the 403-only retry. `src/vite-env.d.ts` types `VITE_API_BASE_URL`.
- **Dev/e2e.** Vite dev server: port `FRONTEND_PORT ?? 3000`, `strictPort`. Playwright passes `APP_CORS_ALLOWED_ORIGINS=http://localhost:${FRONTEND_PORT}` to the backend and `VITE_API_BASE_URL=http://localhost:${BACKEND_PORT}` to Vite. Faked API replies in e2e (`route.fulfill`) must carry `corsHeaders(route)` from `e2e/support.ts`, or the browser hides them and the test only exercises a network failure. The suite passed on the default ports and on `BACKEND_PORT=18080 FRONTEND_PORT=13000` with `CI=1`.
