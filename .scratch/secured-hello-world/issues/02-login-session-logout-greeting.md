# 02: Login, session, logout & protected greeting

**What to build:** A registered user can log in with username/password to establish a real server-side session on the secure cookie configured in ticket 01, fetch a personalized greeting that proves the session works, and log out — fully and irreversibly ending that session. Session lifetime is bounded (idle + absolute timeout), and each successful login records when it happened.

**Blocked by:** 01

**Status:** done

- [x] Correct credentials for a registered, enabled, non-locked account create a server-side session, set the secure session cookie, reset `failed_login_attempts` to 0, and record `last_login_at`
- [x] Incorrect credentials — including for a username that doesn't exist — return an identical, generic error message that never reveals whether the username exists; `failed_login_attempts` increments (enumeration resistance; IM8 `as-7`)
- [x] `GET /api/hello` returns `"Hello, <username>"` for an authenticated session and 401 for no session or an invalid/expired one
- [x] Logout invalidates the server-side session and clears the session cookie
- [x] A session cookie captured before logout is rejected as unauthenticated when replayed after logout
- [x] Sessions enforce both an idle timeout and an absolute maximum lifetime, after which the session is no longer valid (IM8 `as-11`)
- [x] Role/session checks are enforced server-side via Spring Security, never trusted from client-supplied state (IM8 `ac-1`)
- [x] Structured audit log lines for login success and login failure (IM8 `lm-4`)
- [x] Integration tests: login success, wrong password, unknown username (identical generic error either way), reused session cookie rejected after logout

## Comments

Implemented alongside ticket 01 (commit `cce8cf49`, "Implement tickets 01-02"); session
principal-name indexing added in `e3752de4` for ticket 04's later use; `last_login_at` recording
fixed in `09cfe3a4` after a live smoke test showed it was never being set. 4 integration tests
(AuthIntegrationTest) pass.
