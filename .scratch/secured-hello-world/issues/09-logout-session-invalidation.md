# 09: Logout and session invalidation

**What to build:** A logged-in user clicks log out and their session is **genuinely gone** — not
just forgotten by the frontend. The test that matters: a session cookie captured before logout
and replayed afterwards must be rejected as unauthenticated. This is the difference between
real logout and cosmetic logout, and it is the reason the PRD chose server-side sessions over
stateless tokens in the first place.

Covers PRD Story 4.

**Blocked by:** 07.

**Status:** ready-for-agent

**IM8 controls:** `as-11` Session Management; `as-7` Access Control Check Enforcement; `lm-4`
Audit Logging. *ASVS: V3.3 Session Termination, V4.1 Access Control, V7 Logging.*

- [ ] Logout invalidates the session **server-side**, removing it from the session store
- [ ] Logout clears the session cookie on the client
- [ ] Logout is a state-changing operation and is therefore CSRF-protected — it must not be
      triggerable by a cross-site GET
- [ ] A session cookie captured while authenticated and replayed after logout is rejected as
      unauthenticated, returning 401 from protected endpoints
- [ ] Logout is idempotent: calling it without a session, or twice, does not error
- [ ] The frontend clears its auth context, leaves any protected route, and offers login again
- [ ] Test: replaying a pre-logout session cookie against the greeting endpoint returns 401
- [ ] Test: the session is absent from the session store after logout
- [ ] Test: a cross-site attempt to trigger logout without a CSRF token fails
