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

## Triage decision (2026-09-30)

**A 403 from `GET /api/csrf` is not a session end.** The endpoint is `permitAll` and on `RequiredPasswordChangeFilter`'s allowlist, so it can never answer `password_change_required`; a 403 there says nothing about the Session (CORS, a deny rule, a proxy). Treating it as a logout would be the silent logout criterion 3 forbids. It still rejects with the existing `Error`, as does every other non-401 failure.

**The thrown `Error` stays for non-401 failures only; a 401 no longer rejects to any public caller.** The bootstrap (`fetchCsrfToken`, private) notifies `sessionEndedListeners` once per fetch on a 401 (concurrent callers share the one fetch), then rejects with a private `CsrfSessionEnded` carrying the problem body. `apiRequest` catches it on both the first send and the stale-token retry and returns `{ ok: false, status: 401, problem }` without notifying again, so callers see the same result as for a 401 anywhere else. Public `refreshCsrfToken()` swallows it and resolves (now `Promise<void>`; no caller used the value), so `login()`, `logout()` and `changePassword()` do not reject. `ensureCsrfToken()` still rejects on a 401, since it has no token to return; only `apiRequest` uses it. The retry path uses the private fetch rather than `refreshCsrfToken()`, because a resolved refresh followed by `send()` would fetch `/csrf` again and notify twice.

- Rejected resolving the bootstrap to `null` on a 401: `send()` would then need to fake a `Response` to feed the normal 401 branch.
- Rejected catching the rejection at each call site in `auth.ts`: criterion 2 asks for one change in the shared bootstrap.
- The start-up `GET /me` (`sessionEndedOn401: false`) is a safe method and never touches the bootstrap, so the bootstrap's unconditional notification does not affect it.

