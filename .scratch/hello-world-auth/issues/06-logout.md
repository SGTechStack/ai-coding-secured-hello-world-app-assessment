# 06: Logout and session invalidation (Story 4)

**What to build:** A logged-in user can log out and have the session fully ended so it cannot be reused. Logout invalidates the server-side session and clears the session cookie. A session cookie captured before logout and replayed afterward is rejected as unauthenticated. A React logout control drives it.

**Blocked by:** 04.

**Status:** done

- [x] Logout endpoint invalidates the server-side session and clears the cookie
- [x] A pre-logout cookie replayed after logout is rejected as unauthenticated
- [x] CSRF enforced on the logout endpoint
- [x] Audit log line on logout (actor)
- [x] React logout control ends the session and returns the user to the unauthenticated view
- [x] Integration test: reused session cookie rejected after logout
