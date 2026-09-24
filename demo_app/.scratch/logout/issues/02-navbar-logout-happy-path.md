# 02: Navbar Log out button, happy path

**What to build:** a `logout()` API function and a "Log out" button in the app shell navbar, shown only when a session profile is cached. Clicking it posts to the logout endpoint, clears the query cache and navigates to `/login` with `replace`. See spec §Implementation Decisions › Frontend (API module, Navbar, Logout interaction, success path).

**Blocked by:** none (the network is mocked with MSW)

**Status:** done

**Spec scenarios:** Story 3 · Scenarios 1, 2, 3, 4, 5 (at the frontend app seam)

- [x] The API client supports a bodyless `POST` and an empty `204` response, without parsing JSON
- [x] The default MSW handlers include `POST /api/v1/auth/logout` → `204`
- [x] Signed in on `/`: the navbar shows a button with the accessible name "Log out", next to the theme toggle. Anonymous visitor on `/login`: no such button
- [x] Clicking it sends exactly one logout `POST` with `X-XSRF-TOKEN`, ends on `/login` with the form visible, and "Hello, John!" is gone
- [x] After logout, going to `/` redirects to `/login` (the guard asks `/me` again because the cache is cleared)
- [x] Log out, then log in as a different user: the greeting shows the new first name
- [x] Tests at the frontend app seam (`renderApp` + MSW); `npm run check` passes

## Comments

**Implementer (feat commit 503b633):**

- `apiRequest` returns `undefined` for any `204` (checked by status, not `Content-Length`). A bodyless `POST` already worked: with no `body` there is no `Content-Type` and no payload. Both are covered in `client.test.ts`.
- `logout()` in `api/auth.ts` is a plain `POST` for now. Ticket 03 adds the `rejected` → `refreshCsrfToken()` → retry-once wrapper (like `login()`) and treats `unauthorized` as success.
- The mutation lives in `AppShell`, not in the button, so ticket 03 can render the failure alert in the shell's content area below the navbar. `LogoutButton` is presentational (`onClick` only). Ticket 03 adds the pending/disabled props.
- `AppShell` reads the profile with `useQuery({ ...meQueryOptions, enabled: false })`, which subscribes to the cache without fetching. There is no explicit `/login` path check: the `/login` guard redirects away whenever a profile is cached, so the button can't show there.
- On narrow screens the label is `sr-only` (icon only), and on `sm` and up it is visible. The accessible name stays "Log out".
- Tests are in `src/components/AppShell.test.tsx`. The "no Back entry to `/`" test asserts `router.history.length`. The `/` guard would bounce a stale entry to `/login` anyway, so the URL alone can't tell `replace` from push. The "asks /me again" test asserts `> 0` `/me` calls, not an exact count: the `/` guard asks, then the `/login` guard asks again after the redirect.
- I checked by hand that removing `replace: true` or `queryClient.clear()` makes these tests fail.
- The validation gate was `npm run check` (passes). The /do-work multi-agent code-reviewer dispatcher was not run, because it writes an `artifacts/` report outside the scope of this ticket.
