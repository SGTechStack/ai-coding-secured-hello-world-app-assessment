# 02: Logout and session lifetime

**What to build:** A Regular user can log out, which ends the session on the server and leaves no auth state in the SPA. Sessions have a bounded lifetime: 15 minutes idle and 8 hours absolute. A user has at most one session, and a new login ends the older one. Sessions survive an API restart. This ticket also adds Session control's "end all sessions for Account X" operation, which tickets 08, 10 and 11 use, and the one Playwright end-to-end test.

**Blocked by:** 01 (Walking skeleton)

**Status:** ready-for-agent

- [ ] `POST /logout` (CSRF) returns 200, invalidates the server session and clears the session cookie.
- [ ] A session cookie captured before logout gets 401 on `GET /hello` after logout.
- [ ] `Clear-Site-Data: "cache","cookies","storage"` is sent on logout over HTTPS only, using Spring's writer as-is. A test checks this on a secure request.
- [ ] A session idle for more than 15 minutes gets 401. This is tested with the controllable `Clock`, never with `sleep`.
- [ ] A session older than 8 hours gets 401 even if it has been active throughout. This is tested with the controllable `Clock`.
- [ ] A second login ends the first session (`maximumSessions(1)` with the Spring-Session-backed session registry on the indexed JDBC repository).
- [ ] Session state lives in the database, so it survives an API restart.
- [ ] Session control provides a single operation that ends every session for a given Account.
- [ ] The CSRF token changes at login and logout. A token from before login is rejected after login, and the SPA fetches a new token after each.
- [ ] On logout the SPA clears its own in-memory state. A logout on an already-expired session quietly returns the user to login.
- [ ] The API client has a global 401 handler: clear state, redirect to login, no error screen.
- [ ] The SPA home page has a logout button.
- [ ] One Playwright test runs against the real frontend and API on separate origins: register → log in → hello → log out → a replayed session cookie is rejected.
