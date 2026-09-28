# 06: Protected personalized greeting

**What to build:** A logged-in user sees a personalized greeting confirming authentication worked; unauthenticated requests to the greeting endpoint are rejected.

**Blocked by:** 03 (needs an authenticated session).

**Status:** ready-for-agent

- [ ] `GET /api/hello` with an authenticated session returns `"Hello, <username>"`.
- [ ] `GET /api/hello` with no/invalid/expired session returns 401. (IM8 as-7)
- [ ] The username in the response is contextually output-encoded / safely rendered in React. (IM8 as-3 AUTO-FIX)
- [ ] React shows the greeting on the protected view after login.
