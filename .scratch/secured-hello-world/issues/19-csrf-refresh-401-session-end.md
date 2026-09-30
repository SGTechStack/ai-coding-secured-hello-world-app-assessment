# 19: Treat a 401 from `GET /api/csrf` as a session end, not a thrown error

**What to build:** When the CSRF token bootstrap gets a 401, the SPA routes it through the same session-ended path as every other 401, so the holder lands on the login screen instead of receiving a rejected promise.

`refreshCsrfToken` in `frontend/src/api/client.ts` throws a bare `Error` on any non-ok response from `GET /api/csrf`. Every other 401 in the SPA goes through `sessionEndedListeners` / `onSessionEnded` and produces a clean redirect to login. So when `/api/csrf` itself 401s, a state-changing request with no cached token fails with an unhandled rejection rather than a redirect.

Raised by the issue 15 reviewer. `GET /api/csrf` could not 401 before issue 15: the endpoint is `permitAll` and the request was anonymous. Issue 15 hoisted an Account-existence check above `RequiredPasswordChangeFilter`'s `allowed` matcher (see `docs/agents/reviewer-decisions.md`, "A Session must not outlive its Account"), which is a deliberate placement — the check must hold for every request — and `/api/csrf` is one of the five paths that matcher lets through. A cookie-bearing request from a deleted Account now 401s there.

The window is narrow and no criterion covers it: the token is cached at login, a page reload issues `GET /me` first (which 401s, revokes the Session and redirects correctly), and `endCurrent` fires on the *first* request that reaches the filter, so `/api/csrf` answers 200 from then on. Reaching the throw requires the stale Session's very first request to be the `/csrf` fetch inside `send()` with no cached token. The defect is general rather than delete-specific: any future per-request revocation check reaches the same path. Per the issue-18 precedent, it gets a tracked owner rather than a patch to the single instance.

**Blocked by:** none (15 is done; this is a follow-up)

**Status:** needs-triage

- [ ] `refreshCsrfToken` notifies `sessionEndedListeners` on a 401 from `GET /api/csrf` before it rejects, so the holder gets the standard redirect to login rather than an unhandled rejection.
- [ ] The change is made once in the shared bootstrap, and every caller of `refreshCsrfToken` — including `apiRequest`'s retry path and `logout()` — behaves the same way as for a 401 from any other endpoint.
- [ ] A non-401 failure from `GET /api/csrf` still surfaces as an error; this does not turn every bootstrap failure into a silent logout.
- [ ] Triage decides whether a 403 from `GET /api/csrf` deserves the same treatment, and whether the thrown `Error` should remain at all once listeners have fired.
- [ ] Tests cover: a 401 from `GET /api/csrf` with no cached token drives the session-ended path exactly once and does not reject to the caller; a 500 still rejects.
