# 10: Logout + session invalidation

**What to build:** `POST /api/auth/logout` truly ends the session — the server-side row is deleted so a cookie captured before logout is rejected (401) when replayed. The SPA gets a logout action that calls the endpoint (with CSRF token), clears local auth state, re-bootstraps CSRF (logout rotates/clears the token), and routes to login.

**Blocked by:** 09: Auth spine — register → login → hello

**Status:** implemented

- [x] `POST /api/auth/logout` with valid session → 200, session invalidated server-side, cookie cleared
- [x] Replaying the pre-logout cookie → 401 (integration test asserts this)
- [x] Logout requires the CSRF token (state-changing endpoint)
- [x] SPA logout button ends the session, refreshes the CSRF token, and lands on the login page

Implementation notes: logout lives in the filter chain (`http.logout()` on the
main `SecurityFilterChain`, per the ticket-01 research sketch) — not a
controller. `logoutUrl("/api/auth/logout")` matches POST only because CSRF is
enabled; `SecurityContextLogoutHandler` clears the context and invalidates the
session, which deletes the `SPRING_SESSION` row (a replayed cookie resolves to
anonymous → 401). `CsrfLogoutHandler` is added explicitly — the configurer
auto-wires one when CSRF is on, so the response carries a duplicate clear,
which is idempotent — plus `.deleteCookies("SESSION")` as belt-and-suspenders
on top of Spring Session's own expiry cookie, and a 200 success handler.
Anonymous logout is idempotent 200 (the endpoint reveals nothing).

New trap worth noting: the `SESSION` cookie carries `Base64(session_id)` —
`SPRING_SESSION.SESSION_ID` stores it *decoded*; row assertions in tests must
Base64-decode the cookie value first. Also `CsrfLogoutHandler` moved to
`org.springframework.security.web.csrf` in Security 7 (was
`web.authentication.logout`), and MockMvc's `getCookie()` skips null-valued
cookies — logout emits several clearing cookies for the same name, so assert
on raw `Set-Cookie` headers.

Frontend: `api.logout()` POSTs the endpoint (apiFetch attaches `X-XSRF-TOKEN`
from the cookie) then re-bootstraps CSRF — the server cleared the token.
`AuthProvider.signOut` calls it and drops local auth state in a `finally`, so
the `RequireAuth` guard routes to `/login` (no explicit navigate needed).
`HelloPage` gained a Sign-out button (outline variant, footer slot).

## Comments

**Verification (do-work-min, 2026-09-15):**

- Implemented: chain-level `POST /api/auth/logout` — session invalidated
  server-side (SPRING_SESSION row deleted), `SESSION` + `XSRF-TOKEN` clearing
  cookies emitted, CSRF required, anonymous call idempotent.
- Test seams: primary HTTP seam via `MockMvc` + `@SpringBootTest` on H2, CSRF
  on with the real `GET /api/auth/csrf` → `X-XSRF-TOKEN` flow; fixtures seeded
  via `UserRepository`; server-side invalidation asserted against
  `SPRING_SESSION` via `JdbcTemplate` (cookie value Base64-decoded to match
  `SESSION_ID`).
- Verification steps: `mvn test` 54/54 green (new `LogoutTests` 5/5: 200 +
  row deleted + cookie cleared, replayed-cookie 401 on `/me` and `/hello`,
  tokenless POST 403 with session surviving, CSRF token cleared, anonymous
  idempotent). `npm run build` clean; `oxlint` 0 errors (2 benign pre-existing
  fast-refresh warnings). Live curl on embedded Tomcat (port 8081 — 8080 was
  held by a stale pre-existing process, left untouched): register 201, login
  200, `/api/hello` 200, logout without token 403, logout with token 200 with
  clearing `Set-Cookie`s for `SESSION` and `XSRF-TOKEN`, replayed cookie →
  401 on both `/me` and `/hello`.
- Reviewer loop: clean on first pass (Must-fix=0).
- Final gate: semgrep/spring-security/spring-web PASS, thermo-nuclear WARN
  (test-fixture duplication; redundant-but-intentional logout handlers;
  uncaught signOut rejection — all accepted nits) → aggregate **PASS**; report
  `artifacts/code-reviewer/10-logout-session-invalidation-compliance.html`.
- Mutation gate skipped (config-only change, near-zero mutable logic — same
  reasoning as ticket 08).
- Not yet (later tickets): lockout counters/IP throttle listeners (11), reset
  flow (12), admin (13), prod-profile hardening (14).
