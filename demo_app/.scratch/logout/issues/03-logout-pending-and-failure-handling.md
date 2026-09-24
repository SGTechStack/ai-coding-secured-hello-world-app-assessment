# 03: Logout pending state and failure handling

**What to build:** while logging out, the button is disabled and reads "Logging out...". A 401 counts as success. A `rejected` error re-primes CSRF and retries once. A network error, a 5xx, or a failed retry keeps the user on the page and shows the alert "Unable to log out. Please try again." (`role="alert"`), which clears on the next attempt. See spec §Implementation Decisions › Frontend (API module retry rule, Logout interaction failure path).

**Blocked by:** 02

**Status:** done

**Spec scenarios:** Story 3 · Scenario 6 (at the frontend app seam)

- [x] While the request is in flight, the button is disabled and reads "Logging out..." (no 400ms minimum)
- [x] A 401 from logout ends on `/login` with no alert
- [x] A 403 then a 204: the token is re-primed, the request retried once, and the user ends on `/login`
- [x] A network error, a 5xx, or 403 twice: the user stays on `/`, the alert is shown, and the button is re-enabled; clicking again clears the alert
- [x] Tests at the frontend app seam; `npm run check` passes

## Comments

**Implementer (feat commit d141b3f):**

- `api/auth.ts` has a private `withCsrfRetry(request)` helper, used by both `login()` and `logout()` (a `rejected` error → `refreshCsrfToken()` → one retry). `logout()` wraps it and swallows only `unauthorized`, so a 401 on either attempt counts as success.
- `LogoutButton` takes a `pending` prop (disabled, label "Logging out..."). The alert renders in `AppShell` between the header and `<main>` while `logoutMutation.isError`. A new `mutate()` resets the mutation, which is what clears the alert on the next click; there is no extra state.
- Tests are in the new `navbar Log out, pending and failure` block of `src/components/AppShell.test.tsx`. A deferred MSW reply holds the request open to observe the pending state. The failure cases (network error, 503, 403 twice) are one `it.each`, which also checks that the next click clears the alert while pending. I checked by hand that dropping the retry or the 401 swallow makes these tests fail.
- The validation gate was `npm run check` (passes). The code-reviewer sub-step was skipped, as in ticket 02; the whole PR gets reviewed at the end.
