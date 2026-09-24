# 04: Session greeting and persistence across reload

**What to build:** An authenticated user on `/` sees "Hello, John!" (their first name). Reloading the browser keeps them signed in and greeted, because the frontend rehydrates the session from the server rather than from in-memory state. See spec §API contract (`GET /api/v1/auth/me`) and §Session state.

**Blocked by:** 01

**Status:** done

**Spec scenarios:** Story 2 · Scenarios 1, 2

- [x] `GET /api/v1/auth/me` returns `200 {"username","firstName"}` for a valid session and `401` JSON without one (MockMvc tests, including using the session cookie from a real login)
- [x] Landing page `/` renders "Hello, {firstName}!" — "Hello, John!" for `johndoe`
- [x] Login success seeds the `me` query cache so the greeting renders immediately after redirect
- [x] A fresh app load at `/` with a valid session (simulated by MSW returning `200` for `/me`) renders "Hello, John!" and stays on `/`
- [x] All gates from ticket 01 still pass

## Comments

- Implementer (04): no spec deviations. Decisions for ticket 05:
  - `GET /api/v1/auth/me` is covered by `anyRequest().authenticated()`, so `SecurityConfig` is unchanged. Like every GET, `/me` also sets `XSRF-TOKEN` (even on `401`); a test asserts this. The client still primes CSRF via `GET /csrf` (`204`, works when signed out) because a `401` from `/me` would throw in `apiRequest`. `/csrf` is kept.
  - `meQueryOptions` (`src/api/auth.ts`, key `["me"]`, `staleTime: Infinity`) is the session cache. Login seeds it with `setQueryData`, so `/` renders without asking `/me`. A fresh load fetches it once. `fetchMe` throws `ApiError` on `401` (kind `invalid-credentials`), and 05 decides how guards map `401`/`unavailable` to "no session".
  - The default MSW handler for `/me` is `401` (anonymous). Tests with a live session override it with `demoUser`.
  - `LandingPage` renders nothing until the profile is available. It has no guard or redirect yet, because that belongs to 05 (`beforeLoad` + `ensureQueryData(meQueryOptions)`).
