# Spec: Logout from the Top Navbar

**Status:** ready-for-agent
**Builds on:** `.scratch/simple-login/spec.md`, which listed logout as out of scope.

## Problem Statement

Once signed in, a user cannot sign out. The session lasts until its 30-minute inactivity timeout runs out or the browser drops the cookie. On a shared or public computer, the next person to open the app lands on `/` and is greeted as the previous user. Nothing in the UI lets a user end the session on purpose, and the backend has no endpoint that ends it (Spring Security's logout is explicitly disabled).

## Solution

When a session is live, the top navbar that appears on every page shows a **Log out** button. Clicking it asks the server to end the session. The server invalidates the `HttpSession`, expires the session cookie and clears the CSRF token. The SPA then forgets the cached session profile and sends the user to `/login`, where they can sign in again straight away. Once logged out, the Back button, a reload or typing `/` directly all lead to `/login`, because the session no longer exists on the server. Anonymous visitors on `/login` never see the button.

## User Stories

1. As an authenticated user, I want a "Log out" button in the top navbar, so that I can end my session whenever I choose.
2. As an authenticated user, I want the Log out button in the same place on every page I can visit while signed in, so that I always know where to find it.
3. As an anonymous visitor, I want no Log out button in the navbar on `/login`, so that the UI doesn't offer an action that makes no sense for me.
4. As an authenticated user, I want the navbar to keep the brand and the theme toggle where they are now, so that adding logout doesn't rearrange the header I'm used to.
5. As an authenticated user, I want one click on Log out to end my session, so that I don't have to confirm a harmless, easily reversed action.
6. As an authenticated user, I want to land on `/login` after logging out, so that I can see I'm signed out and can sign in again.
7. As an authenticated user, I want the server to invalidate my session when I log out, so that my session cookie is useless even if someone copied it.
8. As an authenticated user, I want the session cookie cleared from my browser when I log out, so that no stale credential is left behind.
9. As a logged-out user, I want the browser Back button to take me to `/login`, not the greeting, so that the next person at the computer can't see my page.
10. As a logged-out user, I want a reload of `/` to send me to `/login`, so that the logout holds across reloads.
11. As a logged-out user, I want my first name to disappear from the screen the moment logout finishes, so that no personal data from the old session stays visible.
12. As a logged-out user, I want to log in again straight away, as the same or a different account, without reloading the page, so that switching accounts is easy.
13. As a user who logs in as a different account after logging out, I want to be greeted by the new account's first name and never the previous one, so that I know which account I'm using.
14. As an authenticated user, I want the Log out button disabled and reading "Logging out..." while the request is in flight, so that I can't send it twice and I know something is happening.
15. As an authenticated user, I want to see a clear error if logout fails because the server is down or can't be reached, so that I don't walk away thinking I'm signed out when I'm not.
16. As an authenticated user, I want to stay on the page with the Log out button re-enabled after a failed logout, so that I can try again.
17. As an authenticated user, I want the logout error to go away when I try again, so that stale errors don't linger.
18. As an authenticated user whose session already expired on the server, I want Log out to still take me to `/login` without an error, so that an expired session doesn't block me from "logging out".
19. As an authenticated user whose CSRF token has gone stale, I want logout to refresh the token and retry once on its own, so that a stale token doesn't stop me from signing out.
20. As a keyboard user, I want to reach the Log out button with Tab and press it with Enter or Space, so that I can log out without a mouse.
21. As a screen-reader user, I want the button's accessible name to be "Log out" and the failure message announced, so that I know what the control does and whether it worked.
22. As a mobile user, I want the Log out button to fit in the navbar on a narrow screen, so that I can log out on my phone.
23. As a security-conscious product owner, I want logout to be a CSRF-protected `POST`, so that another site can't log users out with a link or image tag.
24. As a security-conscious product owner, I want logout to clear the `XSRF-TOKEN` cookie, so that the SPA has to fetch a fresh CSRF token before its next `POST` and a token issued before logout is not carried over.
25. As a security-conscious product owner, I want the logout endpoint to answer with an empty `204` and never redirect, so that it follows the same "API never redirects" rule as the rest of `/api/v1/auth`.
26. As a developer, I want logout to be idempotent on the server (repeating it or calling it without a session is harmless), so that retries and double calls are always safe.
27. As a developer, I want the e2e suite to cover logout against the real backend, so that session invalidation is proven end to end and not only with mocks.

## Implementation Decisions

### Backend

- **Endpoint**: `POST /api/v1/auth/logout`, next to `login`, `me` and `csrf` under `/api/v1/auth`.
  - `204 No Content`, empty body, on success. The response expires the `JSESSIONID` cookie (`Max-Age=0`, same path and attributes as when it was set) and clears the `XSRF-TOKEN` cookie.
  - The endpoint is reachable **without** a session (`permitAll`) and returns `204` whether or not a session existed. It is idempotent, so an expired session never shows up as a 401 on logout.
  - `403` with `FORBIDDEN` / `"Forbidden"` in the standard error body when the CSRF token is missing or invalid. CSRF applies to logout like any other `POST`.
  - Other methods (e.g. `GET /api/v1/auth/logout`) must not log the user out.
- **Mechanism**: re-enable Spring Security's logout configurer, which is currently disabled in the security filter chain, instead of writing a controller method:
  - Match only `POST /api/v1/auth/logout`.
  - Invalidate the `HttpSession` and clear the `SecurityContext` (the defaults), delete the `JSESSIONID` cookie, and let the built-in CSRF logout handler clear the token from the cookie-based CSRF token repository.
  - Success handler: return an HTTP status (`204`) instead of redirecting.
  - Security headers, the JSON error handler and the prod HTTPS redirect apply to this endpoint unchanged.
- No schema changes. No change to the login, `me` or `csrf` contracts.

### Frontend

- **API module**: add a `logout()` function beside `login()` and `fetchMe()`, built on the existing API client wrapper (credentials, CSRF header, error kinds).
  - It follows the same retry rule as `login()`: a `rejected` error (a 4xx other than 401, usually a 403 for a stale CSRF token) re-primes the CSRF token and retries once.
  - `unauthorized` (401) counts as a successful logout. The backend is not expected to send it, but the client should handle it anyway.
  - `unavailable` (network or 5xx), or a failed retry, propagates to the UI.
  - The API client's `RequestOptions` must allow a `POST` with no body and must handle an empty `204` body without parsing JSON.
- **Navbar**: the existing app shell header (brand on the left, theme toggle on the right) gets a Log out control on the right, next to the theme toggle.
  - The shell decides whether to show it from the cached session profile (the `me` query). It shows the button only when a profile is cached, reads the cache without starting a fetch, and never shows the button on `/login`.
  - It is a real `<button>` with the accessible name "Log out" and an icon plus the "Log out" label. On narrow screens it may collapse to the icon alone, as long as the accessible name stays "Log out".
  - It uses the existing shadcn/ui button component and fits the current visual style (ghost/outline variant, like the theme toggle).
- **Logout interaction** (a small mutation, e.g. a TanStack Query `useMutation`):
  - While pending, the button is disabled and reads "Logging out...". There is **no** 400ms minimum: logout ends on navigation, so there is no feedback to protect from flicker.
  - On success (including the 401 case), the order matters:
    1. Clear the query cache (`queryClient.clear()`, not just the `me` query), so no data from the old session outlives it.
    2. Navigate to `/login` with `replace`, so Back doesn't return to `/` from history state.
    3. The `/login` guard then asks `/me`, gets 401 and shows the form. This extra request is expected.
  - On failure (`unavailable`, or `rejected` after the retry): stay on the page, re-enable the button, and show "Unable to log out. Please try again." with `role="alert"`, in the shell's content area directly below the navbar. The alert clears when the user clicks Log out again.
- **Re-login after logout**: no special handling. The CSRF cookie is gone after logout, so the API client's existing "prime the token if the cookie is missing" path gets a fresh token before the next login `POST`.
- **Route guards**: unchanged. Logout depends on the existing `/` guard redirecting once the cache is empty and `/me` returns 401.

### Seams (test boundaries). These reuse the existing seams, with no new ones.

1. **Backend HTTP seam**: `@SpringBootTest` + MockMvc against `POST /api/v1/auth/logout`, with the real security filter chain and H2, using the real SPA CSRF flow as the existing login API tests do.
2. **Frontend app seam**: the real router and app rendered through the existing `renderApp` helper, with the network mocked by MSW. Tests click the navbar button as a user would and assert on the URL, visible text, disabled state, alerts and outbound requests.
3. **End-to-end seam**: Playwright against the real frontend and backend, with a new Story 3 spec file of logout scenarios.

## Testing Decisions

- Good tests assert only what can be observed from outside. At the API seam that means status, body, `Set-Cookie` headers, and whether the old session cookie still works against `/me`. At the UI seam it means visible text, ARIA roles and states, the URL, and which requests were sent. Tests make no assertions on the shell's internals, hooks, the mutation object, or Spring bean wiring.
- **Backend** (new logout API test class, modelled on the existing login API test):
  - Log in, then log out with a valid CSRF token: `204`, `JSESSIONID` expired, and `XSRF-TOKEN` cleared.
  - After logout, `GET /me` with the old session returns `401`.
  - Logout without a session (anonymous, with a valid CSRF token) returns `204`.
  - Logout twice in a row returns `204` both times.
  - Logout without, or with a wrong, CSRF token returns `403` `FORBIDDEN` in the standard error body, and the session is still valid afterwards (`/me` returns `200`).
  - `GET /api/v1/auth/logout` does not end the session.
  - Security headers are present on the logout response, following the existing security headers test.
  - Use the existing cookie-based CSRF flow, not spring-security-test's `csrf()` post-processor (see the warning in the existing login API test).
  - JaCoCo must stay at 80% or more.
- **Frontend** (prior art: the existing landing page and router tests with `renderApp` and MSW):
  - Signed in on `/`, the navbar shows a "Log out" button. On `/login` as an anonymous visitor, it doesn't.
  - Clicking Log out sends exactly one `POST /api/v1/auth/logout` with the `X-XSRF-TOKEN` header, ends on `/login` with the form visible, and "Hello, John!" is gone.
  - While the request is in flight, the button is disabled and reads "Logging out...".
  - After logout, navigating to `/` redirects to `/login`. The cache is cleared, so the guard asks `/me` again instead of trusting the old profile.
  - A 401 from logout still ends on `/login` with no alert.
  - A 403 then a 204 means the token is re-primed, the request retried once, and the user ends on `/login`.
  - A network error, a 5xx, or a 403 twice means the user stays on `/`, sees the "Unable to log out. Please try again." alert, and the button is re-enabled. Clicking again clears the alert.
  - Log out, then log in as a different user from the MSW handler: the greeting shows the new first name.
  - The existing MSW default handlers gain a happy-path `POST /api/v1/auth/logout` → `204`.
  - Vitest coverage thresholds must still pass, and so must `tsc`, Prettier and the Vite build.
- **E2E**: a new Story 3 Playwright spec next to Story 1 and Story 2, reusing the `logInAsJohn` / `loginForm` support helpers. It needs one test per scenario in *Further Notes*. Server-failure scenarios use Playwright request interception, as the existing 5xx cases do.

## Out of Scope

- A user menu or dropdown, avatar, or "Signed in as …" label in the navbar. The only addition is the Log out button.
- A confirmation dialog before logout.
- Syncing logout across tabs (e.g. a `BroadcastChannel` or `storage` event). Other open tabs find out on their next navigation or reload, when the guard asks `/me`.
- "Log out of all devices" and invalidating other sessions.
- Detecting a session that expired while the user sits idle on a page, and redirecting them automatically.
- A "You've been logged out" message on `/login` after logout.
- Any change to the session timeout, the cookie attributes, or the login flow.

## Further Notes

Acceptance scenarios for the e2e suite and the review. These are Story 3: Logout, written in this spec because there's no upstream Gherkin source.

1. **Authenticated user sees the Log out button in the navbar.** Given John is logged in and on `/`, then the navbar shows a "Log out" button.
2. **Anonymous visitor does not see the Log out button.** Given no session, when the visitor opens `/login`, then there is no "Log out" button.
3. **Logging out ends the session and returns to login.** Given John is logged in on `/`, when they click "Log out", then they are on `/login`, the login form is shown, and "Hello, John!" is not visible.
4. **Logged-out session does not survive Back or reload.** Given John has just logged out, when they press Back or opens `/` directly, then they are redirected to `/login`, and the `/me` request returns `401`.
5. **User can log in again after logging out.** Given John has just logged out, when they log in again with valid credentials without reloading, then they land on `/` and see "Hello, John!".
6. **Logout failure keeps the user signed in and explains why.** Given John is logged in on `/` and the logout request fails with a 5xx or a network error, when they click "Log out", then they stay on `/`, see "Unable to log out. Please try again.", and the button is enabled again.

- The earlier login spec (`.scratch/simple-login/spec.md`) listed logout under Out of Scope. This spec takes that item on. The login spec's other exclusions still apply.
- The repo has no `CONTEXT.md` or ADRs. The terms here ("authenticated user", "anonymous visitor", "session", "landing page", "banner"/alert) follow the login spec.
