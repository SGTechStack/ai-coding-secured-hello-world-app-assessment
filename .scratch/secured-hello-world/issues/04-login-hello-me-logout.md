# 04: Log in, see Hello, view own Account, log out

**What to build:** A registered Account holder logs in on the login screen and lands on the hello screen, which says "Hello, <username>". The SPA knows who they are from `GET /me`. They log out and land on the login page with a "you have logged out" message, and nothing from the Session can be replayed. Every login failure looks identical. This is the Authentication guard at its simplest; lockout, the IP Throttle and rate limits come in later tickets. See the spec's stories 14–18, 23 (tested in ticket 11), 24, 35–43, 82–83, 93 (authentication system failure at ERROR) and 104, "Authentication guard", "Session control", "Security configuration" (CSRF, logout), and ADR 0002.

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] `POST /api/login` takes form-encoded `username` and `password`, and returns 200 with the own-Account body `{id, username, email, role, passwordChangeRequired}`.
- [ ] Unknown username, wrong password and Disabled Account all return an identical 401 body with `code` `authentication_failed`. An unknown username still runs a BCrypt comparison against a fixed dummy hash, so timing matches.
- [ ] Login replaces the Session ID, and an Account has at most one Session: a new login ends the previous one. The maximum number of concurrent Sessions per Account is configurable, with a default of 1.
- [ ] An authentication system failure during login (for example, the database is unavailable) returns the generic 500 `internal_error` and emits an ERROR `user-authentication` failure audit event.
- [ ] A CSRF token issued before login is rejected after login; the SPA fetches a new one after login and after logout.
- [ ] `GET /api/hello` answers "Hello, <username>" as JSON or `text/plain`. `GET /api/me` returns only the caller's record and takes no ID from the request.
- [ ] `POST /api/logout` invalidates the Session, deletes the session cookie, and returns 200 with `Clear-Site-Data: "cache","cookies","storage"`. A session cookie replayed after logout gets 401, and a CSRF token from before logout is rejected afterwards.
- [ ] The SPA treats a 401 or 403 from logout as "already logged out".
- [ ] Audit events: `user-authentication` success (INFO, `authentication.method: password`) and failure (WARN, with no user identity before resolution, carrying `session.hash`), and `user-logout` (INFO).
- [ ] SPA: the login screen has "Confidential" labels, login always goes to the hello screen (never a return URL), the hello screen links to logout, and on load the auth context calls `/me` to decide what to show.
- [ ] Tests cover: login success; identical bodies for unknown username and wrong password (the Disabled case is tested in ticket 11, once an Account can be disabled over the API); Session ID changes at login; a second login ends the first Session; CSRF tokens rejected across login and logout; `/me` returns only the caller; logout returns 200 with `Clear-Site-Data`; the replayed cookie is rejected; an authentication system failure logged at ERROR; log lines of an authenticated request carry `user.id` in MDC; and the audit events. No log line contains a password, username, email, CSRF token or Session ID.
