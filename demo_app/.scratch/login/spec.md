# Spec: Login

**Status:** ready-for-agent
**Source:** `admin-instructions.pdf` §6 (Story 1: User Authentication & Login Flow — 8 scenarios; Story 2: Session Persistence & Protected Route Access — 4 scenarios)

## Problem Statement

A registered user has no way to prove who they are to the application. There is no login page, no server-side session, and nothing stopping an anonymous visitor from reaching views meant for signed-in users. When a user does try to sign in, they need clear, trustworthy feedback: what they forgot to fill in, that the request is in progress, whether their credentials were wrong, or whether the server itself is unreachable — without the application leaking which part of their credentials was incorrect.

## Solution

A `/login` page with a username/password form that validates only on submit, shows a deliberate loading state while the request is in flight, and reports failures via a banner above the form. On success, the server establishes a session (HTTP-only session cookie) and the user lands on `/`, a protected page that greets them by first name ("Hello, John!"). The session survives browser reloads. Route guards send anonymous visitors from `/` to `/login` and send signed-in users from `/login` to `/`.

## User Stories

1. As a registered user, I want a login page at `/login`, so that I have a clear place to sign in.
2. As a registered user, I want to enter my username and password and click "Log in", so that I can authenticate.
3. As a registered user, I want to be redirected to the landing page `/` after a successful login, so that I can proceed to my dashboard.
4. As a registered user, I want the server to establish a session when I log in, so that I don't need to re-enter my credentials on every request.
5. As a registered user, I want "Username is required" shown beneath the username field when I submit it blank, so that I know what to fix.
6. As a registered user, I want "Password is required" shown beneath the password field when I submit it blank, so that I know what to fix.
7. As a registered user, I want both inline errors shown together when both fields are blank, so that I can fix everything in one pass.
8. As a registered user, I want no network request sent when my form is incomplete, so that I get instant feedback and the server isn't bothered with requests that can't succeed.
9. As a registered user, I want no validation errors or banners to appear while I'm still typing before my first submit, so that I'm not nagged mid-entry.
10. As a registered user, I want the inline error under the username field to disappear as soon as I type in that field, so that the form reflects that I'm fixing it.
11. As a registered user, I want the inline error under the password field to disappear as soon as I type in that field, for the same reason.
12. As a registered user, I want the inputs and the "Log in" button disabled while my login is in flight, so that I can't double-submit or edit mid-request.
13. As a registered user, I want the button to read "Logging in..." with an animated ellipsis while in flight, so that I know something is happening.
14. As a registered user, I want the loading state to last at least 400ms even on a fast network, so that the feedback doesn't flicker unreadably.
15. As a registered user, I want a banner reading "Invalid username or password" above the form when my credentials are wrong, so that I know to try again.
16. As a security-conscious product owner, I want that failure message to be identical whether the username or the password was wrong, so that attackers can't enumerate valid usernames.
17. As a registered user, I want the form re-enabled after a failed login, so that I can correct my credentials.
18. As a registered user, I want the failure banner to disappear as soon as I type in either field, so that stale errors don't linger while I retry.
19. As a registered user, I want a distinct banner reading "Unable to connect to the server. Please try again later." when the server errors (5xx) or is unreachable, so that I know it's not my credentials' fault.
20. As a registered user, I want the form re-enabled after a server/network failure, so that I can retry.
21. As an authenticated user, I want the landing page to greet me by first name ("Hello, John!"), so that the app feels personal and I can confirm I'm signed in as the right account.
22. As an authenticated user, I want my session to persist when I refresh the browser, so that I'm not bounced back to `/login`.
23. As an anonymous visitor, I want to be redirected to `/login` when I open `/` directly, so that I'm guided to sign in instead of seeing a broken page.
24. As an authenticated user, I want to be redirected to `/` when I open `/login` directly, so that I don't see a login form I don't need.
25. As a security-conscious product owner, I want passwords stored only as salted adaptive hashes, so that a database leak doesn't expose credentials.
26. As a security-conscious product owner, I want the session cookie to be HTTP-only and rotated on login, so that XSS can't steal it and session fixation is prevented.
27. As a security-conscious product owner, I want state-changing API calls protected against CSRF, so that another site can't log a user in or act on their behalf.
28. As a developer, I want a seeded demo account (`johndoe` / `Password123!`, first name "John"), so that the feature can be demoed and tested end to end.

## Implementation Decisions

### Repository layout
- Monorepo with two apps: `backend/` (Spring Boot 3.x, Java 21, Maven) and `frontend/` (React 19 + TypeScript, Vite). Frontend dev server proxies `/api` to the backend so the browser sees one origin (session cookie and CSRF cookie work without CORS).

### Backend
- **User account module**: JPA entity for a user account (username — unique, password hash, first name). Schema created by Flyway migration; H2 in-memory database for dev/test. The demo account `johndoe` / first name `John` is seeded with a BCrypt hash of `Password123!` (hash committed in the migration, never the plaintext outside tests/docs). The seed lives in `db/seed`, which Flyway reads only in the `dev` profile and in tests, so no other environment gets the demo user.
- **Authentication**: Spring Security with `DaoAuthenticationProvider` + `BCryptPasswordEncoder`. Login is a JSON REST endpoint (not form login); on success the `SecurityContext` is saved in the `HttpSession` via the session-backed security context repository, and the existing session is replaced with a new one (session-fixation protection, `newSession`).
- **Session**: server-side `HttpSession` with a 30-minute inactivity timeout; `JSESSIONID` cookie `HttpOnly`, `SameSite=Strict`, and `Secure` by default. Only the `dev` profile (local runs and the e2e suite, over plain-HTTP localhost) turns `Secure` off; the `prod` profile also redirects plain HTTP to HTTPS.
- **CSRF**: enabled, using a cookie-based token repository readable by JS (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header). The SPA obtains the token before its first state-changing request (any `GET` under `/api/v1/auth/**` sets it).
- **API contract** (`/api/v1/auth`, JSON in and out):
  - Every error body has one shape, `{"status": number, "code": string, "message": string, "timestamp": ISO-8601 string, "path": string}`, built in one place for controller and security-filter errors alike. Below, only `code` and `message` are listed.
  - `POST /api/v1/auth/login` — body `{"username": string, "password": string}` (`Content-Type: application/json`).
    - `200` → `{"username": "johndoe", "firstName": "John"}` + session cookie.
    - `400` → `VALIDATION_FAILED`, `"Username and password are required"` when a field is blank (defence in depth; the UI never sends this).
    - `401` → `INVALID_CREDENTIALS`, `"Invalid username or password"` for unknown user OR wrong password (identical status and body apart from `timestamp`, and comparable timing).
    - `403` → `FORBIDDEN`, `"Forbidden"` when the CSRF token is missing or invalid.
  - `GET /api/v1/auth/me` — `200` → `{"username", "firstName"}` for a valid session; `401` → `UNAUTHORIZED`, `"Unauthorized"` otherwise. Never redirects (API returns JSON, not HTML).
- Unauthenticated requests to protected `/api/**` return `401` JSON (custom authentication entry point), not a redirect or HTML page.

### Frontend
- **Routing**: TanStack Router with two routes, `/login` and `/`. Route guards run in `beforeLoad` and consult the current session (via `GET /api/v1/auth/me`): `/` requires a session (else redirect `/login`); `/login` requires no session (else redirect `/`).
- **Session state**: TanStack Query caches the `me` query; login success writes the returned profile into that cache before navigating to `/`.
- **API client module**: one small fetch wrapper that sends credentials, attaches the CSRF header from the cookie, parses JSON errors, and classifies failures into `unauthorized` (401), `rejected` (any other 4xx, e.g. a 403 for a stale CSRF token), and `unavailable` (network error or 5xx).
- **Login form state machine** — validation is submit-deferred:
  - Field errors are computed only on submit. Typing in a field clears *that field's* inline error and clears any banner.
  - On submit with any blank field: set inline errors, send nothing.
  - On valid submit: enter `submitting` (inputs + button disabled, button label "Logging in..." with CSS-animated ellipsis). The UI stays in `submitting` until **both** the request settles **and** 400ms have elapsed since submit.
  - `401` → banner "Invalid username or password"; network/5xx → banner "Unable to connect to the server. Please try again later."; any other 4xx (e.g. a `403` from an expired CSRF token) → re-prime the CSRF token and retry the login once, then fall back to the "Unable to connect..." banner. Every failure re-enables the form. Success → navigate to `/`.
  - Banner renders above (outside and before) the `<form>` element with `role="alert"`; inline errors are linked to inputs via `aria-describedby` and set `aria-invalid`.
- **Landing page**: renders "Hello, {firstName}!" from the session profile.
- Styling: plain CSS (or CSS modules); responsive single-column form. No component library required.

### Seams (test boundaries)
1. **Backend HTTP seam** — `@SpringBootTest` + MockMvc against `/api/v1/auth/*` with the real security filter chain and H2. This is the single backend seam; no unit tests of individual Spring beans unless logic warrants it.
2. **Frontend app seam** — render the real router/app in Vitest + React Testing Library, mocking the network with MSW at the `fetch` boundary. Tests drive the UI as a user would (type, click) and assert on visible text, disabled state, URL, and whether a request was sent. Fake timers for the 400ms rule.
3. **End-to-end seam** — Playwright against the real frontend + backend, one test per Gherkin scenario, as final acceptance.

## Testing Decisions

- Good tests assert externally observable behaviour: HTTP status/body/cookies at the API seam; visible text, ARIA state, URL, and outbound requests at the UI seam. No assertions on component internals, hooks, or Spring bean wiring.
- Backend: `mvn verify` runs MockMvc integration tests; JaCoCo enforces ≥80% instruction coverage.
- Frontend: `vitest run --coverage` with ≥80% thresholds in `vitest.config.ts`; `tsc --noEmit`, `prettier --check`, and `vite build` must pass.
- E2E: Playwright suite mapping 1:1 to the 12 Gherkin scenarios (Story 1 S1–S8, Story 2 S1–S4); 5xx/network cases use Playwright request interception.
- Prior art: none — greenfield repo. The first ticket establishes the patterns later tickets follow.

## Out of Scope

- Logout, registration, password reset, "remember me", account lockout / rate limiting, MFA, SSO/OIDC.
- Any dashboard content beyond the greeting.
- Production deployment, HTTPS termination, and a production database (H2 only).
- Internationalisation.

## Further Notes

- Section numbering in the source PDF: Story 1 scenario 7 is titled "on Password Field Modification" but its body says "either field" — the body is authoritative: typing in **either** field dismisses the auth failure banner. Scenario 4 names only the username field; this spec applies the same rule symmetrically to the password field (story 11).
- The banner for network/5xx failure (Scenario 8) is dismissed by the same typing rule as the auth banner, for consistency.
