# 05: Logout & session invalidation

**What to build:** A logged-in user can log out; the server-side session is fully invalidated and the cookie cleared, and a captured pre-logout cookie is rejected if replayed.

**Blocked by:** 03 (needs an active session to end).

**Status:** ready-for-agent

- [ ] Logout invalidates the server-side session and clears the session cookie.
- [ ] A session cookie captured before logout is rejected as unauthenticated when replayed after logout.
- [ ] Logout is a CSRF-protected state-changing endpoint. (Security Requirements)
- [ ] Integration test: a reused session cookie is rejected after logout. (Testing Requirements, Story 4)
