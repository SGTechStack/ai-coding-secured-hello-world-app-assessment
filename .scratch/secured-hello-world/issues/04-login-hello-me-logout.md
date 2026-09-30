# 04: Log in, see Hello, view own Account, log out

**What to build:** A registered Account holder logs in on the login screen and lands on the hello screen, which says "Hello, <username>". The SPA knows who they are from `GET /me`. They log out and land on the login page with a "you have logged out" message, and nothing from the Session can be replayed. Every login failure looks identical. This is the Authentication guard at its simplest; lockout, the IP Throttle and rate limits come in later tickets. See the spec's stories 14–18, 23 (tested in ticket 11), 24, 35–43, 82–83, 93 (authentication system failure at ERROR) and 104, "Authentication guard", "Session control", "Security configuration" (CSRF, logout), and ADR 0002.

**Blocked by:** 03

**Status:** resolved

- [x] `POST /api/login` takes JSON `{username, password}` (see `docs/agents/reviewer-decisions.md`), and returns 200 with the own-Account body `{id, username, email, role, passwordChangeRequired}`.
- [x] Unknown username, wrong password and Disabled Account all return an identical 401 body with `code` `authentication_failed`. An unknown username still runs a BCrypt comparison against a fixed dummy hash, so timing matches.
- [x] Login replaces the Session ID, and an Account has at most one Session: a new login ends the previous one. The maximum number of concurrent Sessions per Account is configurable, with a default of 1.
- [x] An authentication system failure during login (for example, the database is unavailable) returns the generic 500 `internal_error` and emits an ERROR `user-authentication` failure audit event.
- [x] A CSRF token issued before login is rejected after login; the SPA fetches a new one after login and after logout.
- [x] `GET /api/hello` answers "Hello, <username>" as JSON or `text/plain`. `GET /api/me` returns only the caller's record and takes no ID from the request.
- [x] `POST /api/logout` invalidates the Session, deletes the session cookie, and returns 200 with `Clear-Site-Data: "cache","cookies","storage"`. A session cookie replayed after logout gets 401, and a CSRF token from before logout is rejected afterwards.
- [x] The SPA treats a 401 or 403 from logout as "already logged out".
- [x] Audit events: `user-authentication` success (INFO, `authentication.method: password`) and failure (WARN, with no user identity before resolution, carrying `session.hash`), and `user-logout` (INFO).
- [x] SPA: the login screen has "Confidential" labels, login always goes to the hello screen (never a return URL), the hello screen links to logout, and on load the auth context calls `/me` to decide what to show.
- [x] Tests cover: login success; identical bodies for unknown username and wrong password (the Disabled case is tested in ticket 11, once an Account can be disabled over the API); Session ID changes at login; a second login ends the first Session; CSRF tokens rejected across login and logout; `/me` returns only the caller; logout returns 200 with `Clear-Site-Data`; the replayed cookie is rejected; an authentication system failure logged at ERROR; log lines of an authenticated request carry `user.id` in MDC; and the audit events. No log line contains a password, username, email, CSRF token or Session ID.

## Comments

### Verification (2026-09-29)

**Implemented:** `POST /api/login` (JSON `{username, password}`) returns 200 with the own-Account body. Unknown username, wrong password and Disabled Account return one identical 401 `authentication_failed`; unknown usernames and passwords over 72 bytes still cost one BCrypt comparison. Username over 32 or password over 64 characters returns 400 `validation`. Login changes the Session ID and CSRF token and ends older Sessions beyond `app.session.max-concurrent-per-account` (default 1). A database failure during login returns 500 `internal_error` with an ERROR `user-authentication` audit event. `GET /api/hello` and `GET /api/me` use only the authenticated principal. `POST /api/logout` invalidates the Session, sends `Clear-Site-Data` and records `user-logout`. SPA: an auth context calls `/me` on load; the login screen has "Confidential" labels and always goes to the hello screen; the hello screen links to logout; a 403 `csrf_invalid` on a state-changing call fetches a new token and retries once; login maps only `authentication_failed` / `validation` to "incorrect username or password".

**Deviations and decisions:** Login takes JSON, not form-encoded, per the existing decision in `docs/agents/reviewer-decisions.md`; the first criterion was reworded to match (the spec still says form-encoded). The Disabled-Account test is left to issue 11, as this issue says. `session-end` audit events and ending the carried Session on a failed login belong to issue 05. The startup `/me` 401 no longer triggers the "Session ended" redirect, so a Visitor can open `/register` directly.

**Verification steps:** `./mvnw verify` passed: 124 tests, 0 failures, 97% instruction / 87% branch coverage. Frontend typecheck, lint, format check and 64 tests passed (98.5% line coverage). Reviewer loop clean after one correction pass (a stale CSRF token made login show "incorrect password" after the Session ended server-side; fixed with a single token refresh and retry). KB retrieval and code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `799c9ff feat(auth): Add login, hello screen, /me and logout`
