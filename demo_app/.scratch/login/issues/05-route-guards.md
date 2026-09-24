# 05: Route guards for protected and login routes

**What to build:** Anonymous visitors who open `/` directly are sent to `/login`. Signed-in users who open `/login` directly are sent to `/`. Guards consult the server session (`GET /api/v1/auth/me`) so they are correct after a reload. See spec §Routing.

**Blocked by:** 04

**Status:** done

**Spec scenarios:** Story 2 · Scenarios 3, 4

- [x] Navigating to `/` with no valid session (`/me` → `401`) redirects to `/login`; the greeting never renders
- [x] Navigating to `/login` with an active session (`/me` → `200`) redirects to `/`; the login form never renders
- [x] Guards run before the route renders (TanStack Router `beforeLoad`), with no flash of the wrong page
- [x] A `/me` network/5xx failure on `/` is treated as "no session" (redirect to `/login`), not a crash
- [x] All gates from ticket 01 still pass

## Comments

- Implementer (05): no spec deviations. Notes:
  - Guards live in `src/router.tsx` (`beforeLoad` on `/` and `/login`). Both call `currentSession`, which runs `queryClient.ensureQueryData(meQueryOptions)` and maps any failure (`401`, network, `5xx`) to `null`. A cached profile, such as one seeded by login, is used without a request.
  - `/login` treats a failed `/me` as "no session" too, and shows the form rather than crashing.
  - Failures are not cached, so an anonymous visit to `/` asks `/me` twice: once in the `/` guard and again in the `/login` guard after the redirect. Caching `null` for `401` would save that request, but it would change ticket 04's `fetchMe` contract and could go stale if the user signs in from another tab.
  - Tests are in `src/router.test.tsx`. One existing test in `LandingPage.test.tsx` ("without asking /me") now resets its `/me` counter after the form appears, because the `/login` guard asks `/me` once before rendering the form.
