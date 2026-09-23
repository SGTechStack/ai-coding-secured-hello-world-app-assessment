# 05: Logout + protected /api/hello (Stories 4-5)

**What to build:** A logged-in user can log out, fully ending their
session so a captured cookie can't be replayed; a logged-in user can hit a
protected endpoint that greets them by name, while an unauthenticated
request to the same endpoint is rejected.

**Blocked by:** 03 (Login + session + generic errors)

**Status:** ready-for-agent

- [ ] `POST /api/logout` invalidates the server-side session and clears the
      session cookie
- [ ] A session cookie captured before logout is rejected as
      unauthenticated when replayed after logout
- [ ] `GET /api/hello` returns `"Hello, <username>"` for an authenticated
      session
- [ ] `GET /api/hello` returns 401 for no session or an invalid/expired one
- [ ] Integration tests: logout invalidates the session; a reused
      pre-logout cookie is rejected; `/api/hello` succeeds when
      authenticated and returns 401 when not
