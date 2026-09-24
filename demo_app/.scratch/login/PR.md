# feat: Login (login flow, session persistence, route guards)

**Status:** Ready for review

This PR implements the Login spec end to end:
- A Spring Boot auth API based on server sessions.
- A React 19 login flow with shadcn/ui and Tailwind. It validates only on submit, shows loading and failure feedback, greets the signed-in user by first name, and guards the routes.

Closes `.scratch/simple-login/spec.md`
Closes `.scratch/simple-login/issues/01-walking-skeleton-happy-path-login.md`
Closes `.scratch/simple-login/issues/02-submit-deferred-inline-validation.md`
Closes `.scratch/simple-login/issues/03-submission-feedback-loading-and-banners.md`
Closes `.scratch/simple-login/issues/04-session-greeting-and-persistence.md`
Closes `.scratch/simple-login/issues/05-route-guards.md`
Closes `.scratch/simple-login/issues/06-e2e-acceptance-suite.md`

## Progress

- [x] 01 Walking skeleton — happy-path login
- [x] 02 Submit-deferred inline validation
- [x] 03 Submission feedback — loading state and failure banners
- [x] 04 Session greeting and persistence
- [x] 05 Route guards
- [x] 06 End-to-end acceptance suite
- [x] UI redesign with shadcn/ui and Tailwind v4, merged in with the review fixes
- [x] Code review fixes

## What's in it

**Backend** (`backend/`: Spring Boot 3, Java 21, H2 with Flyway):
- **Login:** `POST /api/v1/auth/login` checks the password against a BCrypt hash. A wrong password and an unknown username get the same 401 response.
- **Current user:** `GET /api/v1/auth/me` returns the signed-in user, which is how the session survives a page reload.
- **CSRF:** `GET /api/v1/auth/csrf` issues the token, which is stored in a cookie.
- **Sessions:** login starts a brand-new session (`newSession`). The cookie is HttpOnly, `SameSite=Strict`, and `Secure` by default. Sessions time out after 30 minutes of inactivity.
- **Headers:** CSP, Referrer-Policy and Permissions-Policy are set. The prod profile redirects HTTP to HTTPS.
- **Errors:** every error has the same JSON shape: `{status, code, message, timestamp, path}`.
- **Demo user:** `johndoe` is seeded only in the dev and test profiles.

**Frontend** (`frontend/`: React 19, Vite, TanStack Router and Query, shadcn/ui):
- `/login` validates only when you submit. Typing in a field clears its inline error and any banner.
- While a login is in flight, the form stays disabled and shows "Logging in..." for at least 400ms.
- A 401 shows the invalid-credentials banner. A 5xx or network failure shows the "Unable to connect" banner.
- If a request is rejected because the CSRF token expired, the client fetches a new one and retries once.
- `/` shows "Hello, {firstName}!".
- Route guards run before each page renders: `/` needs a session, and `/login` needs no session.

## Code review

The review had two axes: the org checklists (react-review and spring-security/web/test-review) plus Fowler's code-smell list, and the spec.
- **Fixed:** S1–S10, S13–S17, P1, P4, P7 and P8. The main ones: the demo user is seeded only in dev, cookie and session hardening, security headers, one error-body shape, a reusable `FormField` component, a single `Credentials` state, honest client error kinds with the CSRF 403 retry, the banner moved outside the `<form>`, and no fixed sleeps in e2e.
- **Declined** because the spec's own decision takes precedence:
  - S11: `/login` is a verb path, but it's part of the spec's API contract.
  - S12: the backend tests boot the whole app (`@SpringBootTest`), which is the test seam the spec chose.
  - S18: the tests use H2 because the spec puts production databases out of scope.
  - P2, P3, P5, P6: the reviewer found no defect in any of them.
- **Deviations** from the spec are recorded under `## Comments` in each ticket. The spec itself was updated for the cookie flags, the error body shape and the error kinds.

## Testing

All three gates pass on this branch:
- `backend/`: `mvn verify` runs 26 MockMvc and real-HTTP tests. JaCoCo requires at least 80% coverage.
- `frontend/`: `npm run check` runs prettier, tsc, 44 Vitest + RTL + MSW tests (98.5% statements, 94.4% branches) and the vite build.
- `frontend/`: `npm run e2e` runs 12 Playwright tests, one per Gherkin scenario, against the real backend and frontend. To run it while your dev servers are up, set `BACKEND_PORT` and `FRONTEND_PORT` to free ports.

## How to run

See `README.md`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
