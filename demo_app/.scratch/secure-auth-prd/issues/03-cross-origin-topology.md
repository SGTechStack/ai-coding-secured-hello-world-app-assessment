# 03: Cross-origin topology

**What to build:** The SPA runs on its own origin (`http://localhost:3000`) and calls the API (`http://localhost:8080`) directly, as the PRD describes. The Vite `/api` proxy is removed, and login and logout keep working end to end. The API accepts cross-origin requests only from an explicit allow-list. The SPA keeps the CSRF token in memory, taken from the `/csrf` response body. See spec §Topology and §Frontend modules › API client.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] CORS is configured in the security filter chain from `app.cors.allowed-origins`, with exact origins only (no wildcards, no `null`). `allowCredentials: true`. Methods `GET, POST, PATCH, DELETE`. Headers `Content-Type, Accept, X-XSRF-TOKEN`. Preflight cache 1 hour.
- [ ] Dev and test default to `http://localhost:3000`. With `prod` and an empty list, startup fails with a clear message.
- [ ] A preflight from the allowed origin gets the allow headers, including credentials. A preflight from another origin gets `403` and no `Access-Control-Allow-Origin`. An actual request from another origin gets no allow-origin header.
- [ ] The security configuration's documentation records that the allow-list is what protects the `/csrf` body token.
- [ ] The SPA reads `VITE_API_BASE_URL` (default `http://localhost:8080`) and sends every request with `credentials: "include"`. The Vite dev server runs on port `3000`. `FRONTEND_PORT` and `BACKEND_PORT` still work for dev and Playwright.
- [ ] The CSRF token is held in memory only, never in web storage. It is fetched lazily before the first state-changing request and dropped after login and after logout.
- [ ] `withCsrfRetry` retries only on `403`, not on `400`, `409` or `429`.
- [ ] `ApiError` exposes `code` and `fieldErrors` from the body. The kinds are `unauthorized` (401), `throttled` (429), `rejected` (other 4xx) and `unavailable` (5xx or network). `RequestOptions.method` accepts `PATCH` and `DELETE`.
- [ ] Client unit tests cover credentials, the token lifecycle, the 403-only retry, the `429` → `throttled` mapping and the parsing of `code` and `fieldErrors`.
- [ ] The existing e2e Stories 1–3 pass cross-origin. The README's run instructions and port table are updated.
