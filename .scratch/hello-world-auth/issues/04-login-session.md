# 04: Login and session cookie (Story 2)

**What to build:** A registered user logs in from the React app with username and password and gets an authenticated session. On correct credentials for an enabled, non-locked account, the server creates a server-side session, sets the secure session cookie, and resets `failed_login_attempts` to 0. Incorrect credentials are rejected with a single generic error that does not reveal whether the username exists, and `failed_login_attempts` increments. A currently-locked account (`locked_until` in the future) is rejected even with the correct password until the lockout expires. A React login form drives it.

**Blocked by:** 03.

**Status:** done

- [x] Correct credentials on an enabled, non-locked account → session created, cookie set, `failed_login_attempts` reset to 0
- [x] Wrong credentials → generic error identical whether the username exists or not; `failed_login_attempts` increments
- [x] Correct password against a currently-locked account → rejected until `locked_until` passes
- [x] Session cookie carries the inherited HttpOnly/SameSite/Secure(prod) attributes; session-fixation protection active
- [x] CSRF enforced on the login endpoint
- [x] Audit log lines for login success and login failure (no password)
- [x] React login form submits and reflects success/failure
- [x] Integration tests: success, wrong password, unknown username (identical generic error), account locked
