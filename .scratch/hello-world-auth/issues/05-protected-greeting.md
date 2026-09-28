# 05: Protected greeting (Story 5)

**What to build:** A logged-in user sees a personalized greeting confirming their authentication worked. `GET /api/hello` returns `"Hello, <username>"` for an authenticated session and 401 for no session or an invalid/expired one. The React app, once logged in, shows the greeting on its landing page; an unauthenticated visit does not.

**Blocked by:** 04.

**Status:** ready-for-agent

- [ ] `GET /api/hello` on an authenticated session returns `"Hello, <username>"`
- [ ] `GET /api/hello` with no session, or an invalid/expired one, returns 401
- [ ] React landing page shows the greeting when authenticated and gates it when not
- [ ] Integration tests: authenticated 200 with correct body, unauthenticated 401
