# Reviewer Decisions

Human decisions on reviewer `Human Decision Needed` findings. Apply a matching entry instead of asking again.

## Request body size limit for chunked / length-less bodies

- **Area:** Backend request-body limit filter (`RequestBodyLimitFilter`), issue 01 walking-skeleton.
- **KB finding:** None (KB unavailable). Reviewer finding: bodies without `Content-Length` bypassed the up-front size rejection; oversized chunked bodies reached auth/controllers, and `getReader()` was not wrapped.
- **Chosen action:** When `Content-Length` is absent, buffer up to limit+1 bytes in the filter and reject with 400 `request_too_large` before CSRF, auth, or controllers. Enforce the limit on both `getInputStream()` and `getReader()`. Cover the chunked path with a test. (Rejected: refusing all length-less bodies; accepting read-time-only enforcement.)
- **When to reuse:** Any request-size / body-limit enforcement question — enforce pre-controller regardless of transfer encoding, with `request_too_large` as the error code.

## Non-blocking reviewer notes to fold into correction passes

- **Area:** Issue 01 minor review notes.
- **KB finding:** None (KB unavailable).
- **Chosen action:** Fix issues that prevent near-term bugs (catch-all `@ExceptionHandler(Exception.class)` must re-throw Spring Security `AccessDeniedException`/`AuthenticationException`; `VITE_API_ORIGIN` must be a bare origin with no path/query/fragment). Skip cosmetic/low-risk ones (plain-text CORS 403 body; `aria-live` on placeholder loading text).
- **When to reuse:** Fold cheap, future-bug-preventing minor notes into the correction pass; leave cosmetic notes on placeholder UI or browser-unreadable responses.

## Code-reviewer gate: controls owned by a later issue

- **Area:** Code-reviewer validation gate (IM8 and similar checks), issue 01.
- **KB finding:** None (KB unavailable). IM8 flagged lm-15, lm-16, as-4, as-5, as-6, as-11, ac-6 and pm-6 as Critical/High, but each is explicitly owned by a later issue file (02, 14, 06/07, 03, 03, 05, 10/13, 16).
- **Chosen action:** Treat them as tracked deferrals, not blockers, for the current issue's gate. They count as blockers again once the gate runs for the owning issue.
- **When to reuse:** Any gate finding for a control that a later issue file assigns to itself (cite the issue file and line). A finding with no owning issue is still a blocker.

## Code-reviewer gate: ADR-accepted deviations awaiting risk-owner sign-off

- **Area:** IM8 ac-2 (MFA), ac-3 (inactive accounts), ac-4 (access review).
- **KB finding:** None (KB unavailable). Accepted deviations in `docs/adr/0001-app-standards-override-prd.md`; IM8 still wants risk-owner sign-off.
- **Chosen action:** The ADR is enough for the code gate; not a blocker. Risk-owner sign-off is a governance item tracked outside the code commits.
- **When to reuse:** Any gate finding for a control that an accepted ADR records as a deviation.

## OpenAPI spec gap (IM8 pm-6)

- **Area:** System documentation.
- **KB finding:** None (KB unavailable). No issue planned an OpenAPI spec; issue 16 covers only the README.
- **Chosen action:** Tracked as a new issue, `.scratch/secured-hello-world/issues/17-openapi-spec.md` (needs-triage). Not a blocker for earlier issues.
- **When to reuse:** Gate findings about missing API documentation before issue 17 is done.

## Chunked-body wrapper replays bytes only (no form decoding)

- **Area:** `RequestBodyLimitFilter`, issue 01 (thermo-nuclear High, raised twice).
- **KB finding:** None (KB unavailable). Decoding form fields in the filter duplicates servlet parsing that no endpoint needs.
- **Chosen action:** The buffered chunked-body wrapper only replays the body bytes (`getInputStream()` / `getReader()`). A chunked `application/x-www-form-urlencoded` post therefore loses its body form fields and fails CSRF with 403 (fail closed). The SPA sends CSRF as a header. All API endpoints, including issue 04's login, must accept JSON bodies, not form posts. Supersedes the earlier "malformed chunked form body → 400 validation" behaviour; the size limit (400 `request_too_large`) still applies.
- **When to reuse:** Any request-body wrapper or endpoint body-format question: keep wrappers byte-replay only and use JSON request bodies.

## Frontend error and performance reporting (IM8 lm-16, SPA side)

- **Area:** SPA observability.
- **KB finding:** None (KB unavailable). The SPA reports no client-side errors or performance data; no issue owned it.
- **Chosen action:** Added to issue 14's scope (`.scratch/secured-hello-world/issues/14-operations-metrics-and-dependency-scan.md`). Tracked deferral for earlier issues.
- **When to reuse:** Gate findings about SPA-side telemetry before issue 14 is done.

## Password length/byte caps return `password_policy`

- **Area:** Credential policy (`CredentialPolicy`), issue 03 registration; reused by Password Change, reset and Bootstrap Admin.
- **KB finding:** None (KB unavailable). Issue 03 said the 64-character / 72-byte caps return 400 `validation`; `spec.md` (story 2, Testing Decisions, Further Notes) and ADR 0001 say `password_policy`.
- **Chosen action:** Keep 400 `password_policy` with violations `max_length` / `max_bytes`, enforced only in the Credential policy. Issue text corrected to match the spec.
- **When to reuse:** Any password-rule error-code question: password rules (including length and byte caps) belong to the Credential policy and return `password_policy`; `validation` is for non-password field format errors.

## Non-H2 migrations: fix obvious dialect issues now, defer real-database runs

- **Area:** Flyway migrations for Postgres/MySQL, issue 03 (V2 accounts).
- **KB finding:** None (KB unavailable). Only H2 is tested; MySQL `TIMESTAMP` overflows in 2038.
- **Chosen action:** Use `DATETIME(6)` for MySQL time columns. Don't add Testcontainers/Docker in feature issues; running migrations against real Postgres/MySQL is deferred to the issue that owns deployment (or noted in the current issue if none does).
- **When to reuse:** Any later migration: fix clear dialect bugs in place; don't block a feature issue on real-database migration runs.

## Session-end audit for Sessions that expire unused

- **Area:** `SessionControl` session-end auditing, issue 05 session lifetime.
- **KB finding:** None (KB unavailable). Spring Session's JDBC cleanup silently deletes an abandoned expired Session, so no `session-end` event is written. When a later login or `endAll` removes an already-expired Session, it is recorded as `new_login` (or the caller's reason).
- **Chosen action:** When `endOldestBeyondLimit` / `endAll` remove a Session that has already passed its idle or absolute limit, record the real reason (`idle_timeout` / `absolute_timeout`). Accept that abandoned Sessions nothing touches again get no event, and note this gap in the issue. No scheduled sweep (option B rejected; could be a separate follow-up issue).
- **When to reuse:** Any audit-completeness question about Sessions or other records that a store expires in the background: record the true reason wherever the app touches them, and document the untouched-expiry gap instead of adding a background job.

## Wrong current password counts toward lockout

- **Area:** Password Change (`PATCH /api/me/password`), issue 08.
- **KB finding:** None (KB unavailable). A wrong current password was only audited, with no lockout or rate limit, so a hijacked Session could guess the password without limit.
- **Chosen action:** Count a wrong current password as a failed login attempt: same threshold, lock, and Account-locked email as login. No dedicated endpoint rate limit (options B/D rejected). Also fixed the minor notes: redirect to login even if the CSRF refresh fails after success, correct the misleading form-clearing comment, send the "Password changed" email after commit. Left the hard-coded "last 3" SPA text.
- **When to reuse:** Any endpoint that re-verifies a password inside an authenticated Session (password change, re-auth, step-up): a wrong password feeds the same lockout counter as login.

## Public SPA routes are Visitor-only, and admin-screen affordances stay out of scope

- **Area:** SPA route guards (`frontend/src/App.tsx`) and the admin Account list, issue 13 required password change.
- **KB finding:** None (KB unavailable). Reviewer finding: `/register`, `/forgot-password` and `/reset-password` were unguarded, so a holder with `password_change_required` set could open three screens beyond Password Change and logout, against criterion 6. Every request they made there was refused 403 `password_change_required`, so the effect was a dead end, not a security hole.
- **Chosen action:** Wrap `/register`, `/forgot-password` and `/reset-password` in the existing `VisitorOnly`, matching `/login`. Those endpoints are outside what any authenticated Session can usefully call, so the same guard fixes the flagged case and the pre-existing looseness at once; a logged-out holder still reaches the reset flow, which is how a reset clears the flag. Rejected: a second flag-specific guard (duplicates what `VisitorOnly` already says), and deferring to a general route-guard issue. Also rejected, as outside this issue: adding `passwordChangeRequired` to `AdminAccountView` to hide or relabel the per-row "Require password change" button, and a confirmation dialog on the acting Admin's own row — an Admin requiring the change of themselves and being bounced to login is the intended consequence of having no self-action guard (`spec.md:198`), and the first confirmation dialog should be designed once for all destructive admin actions. Kept the filter as a bean with a disabled `FilterRegistrationBean` rather than extracting a public `RequiredPasswordChangeCheck` port: the workaround is two explained lines, the port is more surface.
- **When to reuse:** Any screen whose API endpoints an authenticated Session cannot usefully call belongs in `VisitorOnly`, not a bespoke guard. Admin-screen affordances (state badges, confirmations) are their own issue unless a criterion names them; don't widen an admin API contract for a cosmetic gain.
