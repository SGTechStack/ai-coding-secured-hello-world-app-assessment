# feat: Logout from the top navbar

**Status:** Ready for review

This PR implements the Logout spec: a CSRF-protected `POST /api/v1/auth/logout` that ends the server session, and a "Log out" button in the app shell navbar that clears the client cache and returns the user to `/login`.

Closes `.scratch/logout/spec.md`
Closes `.scratch/logout/issues/01-backend-logout-endpoint.md`
Closes `.scratch/logout/issues/02-navbar-logout-happy-path.md`
Closes `.scratch/logout/issues/03-logout-pending-and-failure-handling.md`
Closes `.scratch/logout/issues/04-e2e-logout-suite.md`

## Progress

- [x] 01 Backend logout endpoint
- [x] 02 Navbar Log out button, happy path
- [x] 03 Logout pending state and failure handling
- [x] 04 End-to-end logout suite (Story 3)
- [x] Code review fixes

## What's in it

**Backend** (`backend/`: Spring Boot 3):
- `POST /api/v1/auth/logout` in `SecurityConfig.apiSecurity`, matched only for that path, invalidates the session and clears the `SecurityContext` and CSRF cookie via the framework's default handlers.
- `JSESSIONID` is expired with a custom `CookieClearingLogoutHandler` that copies name, path, domain (when set), `HttpOnly`, `Secure` and `SameSite` from `server.servlet.session.cookie`, so the cleared cookie matches the one the session issued rather than Spring's defaults.
- Review fix: the logout cookie clearer also copies the configured cookie domain, and security headers on the logout response are asserted by presence rather than by exact value.

**Frontend** (`frontend/`: React 19):
- A "Log out" button in `AppShell`'s navbar (icon-only on narrow screens), wired to a mutation that posts to `/api/v1/auth/logout`, clears the query cache, and replaces the route to `/login`.
- `logout()` retries once on a CSRF `rejected` error (refreshing the token first, like `login()`) and treats a `401` as success; a failure alert renders between the header and the page content while pending/failed.
- Review fix: the failure alert was extracted into a shared `ErrorAlert` component, reused by both the logout and login flows.

**Tests:**
- Backend: `LogoutApiTest` (MockMvc, one case per acceptance box), a real-HTTP `SessionCookieTest` case for the expired cookie's attributes, and a new `ExpiredSessionCookieTest`.
- Frontend: `AppShell.test.tsx` covers the happy path, pending state, and failure/retry cases (network error, 503, repeated 403) as an `it.each` block.
- E2E: `frontend/e2e/story-3-logout.spec.ts` adds six Playwright scenarios, including a held network abort to observe the pending-to-idle transition.
- Review fix: backend auth steps (CSRF priming, login) shared across tests via a new `SpaAuthFlow` helper, replacing duplicated setup in `LogoutApiTest`/`SessionCookieTest`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
