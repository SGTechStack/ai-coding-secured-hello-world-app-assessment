# 10: Sign in, hello, sign out

**What to build:** The first user-visible tracer bullet. A user with an existing account signs in in the SPA, sees "Hello, <username>" and signs out. Test fixtures create the accounts, since registration comes later. The behaviours:

- **`POST /api/login`** takes JSON credentials. Every password-axis failure returns the identical 401 `AUTHENTICATION_FAILED` with a constant `instance` (ADR-033): unknown user, wrong password, disabled or not activated. The internal reason goes to the audit stream at WARN.
- **Timing uniformity** rests on the framework's dummy `matches()`. It is verified by counting calls, not with a stopwatch (ADR-001; R-AUTH-004; REJ-056). `alwaysPerformAdditionalChecksOnUser` stays `true`, `isCredentialsNonExpired()` is hard-wired `true`, and `CompromisedPasswordChecker` is not a bean (REJ-005; REJ-019).
- **Sessions:** login rotates the session id and stamps `AUTH_INSTANT` in exactly one place, the login composite (ADR-038). Sessions expire after 15 minutes idle (observed lazily) and 8 hours absolute from the auth instant (REJ-012; REJ-046). One session per account, and the newer login wins (R-AUTH-002).
- **`GET /api/profile`** is the self-read, with the `factors` object stubbed for non-admins. **`GET /api/hello`** (`ROLE_USER`) returns `"Hello, <username>"`.
- **`POST /api/logout`** ends the session, sends `Clear-Site-Data`, and is still CSRF-protected on a dead session (T-CSRF-005).
- **Audit rows:** login success and failure (with `user.id` when the account resolves), session start, and logout.
- **SPA:** the sign-in page, the hello page, and sign-out that is terminal on 204, 401 or 403 and clears local state (REJ-051). Routing is driven by the self-read, and the envelope `code` overrides it (*belief versus authority*). Forms have labels, error association, focus on error and live regions.

**Blocked by:** 08, 09

**Status:** ready-for-agent

- [ ] Every failure cause returns a byte-identical 401 body, and `matches()` is called the same number of times whether or not the user exists.
- [ ] After login the session id differs from the anonymous one, and the old CSRF token is rejected.
- [ ] Moving the `Clock` past 15 minutes idle, or 8 hours from the auth instant, gives 401.
- [ ] A second login ends the first session.
- [ ] `GET /api/hello` returns the exact greeting. An unauthenticated call returns 401.
- [ ] Logout sends `Clear-Site-Data`. Logout on a dead session without a CSRF token gets 403 `CSRF_TOKEN_INVALID`.
- [ ] A Playwright test covers sign in → hello → sign out on both browsers.
