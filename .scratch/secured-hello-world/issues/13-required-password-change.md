# 13: Required Password Change

**What to build:** The Bootstrap Admin, and any Account an Admin suspects is compromised, can do nothing except view itself, make a Password Change, or log out until the password is changed. When an Admin requires a password change, that Account's Sessions end at once. After login, the SPA takes the holder straight to Password Change. A Password Change or a completed reset clears the requirement. See the spec's stories 106–110, "Required password change", "Account administration service", "API contract", ADR 0001 (IM8 as-15 and ac-6, no grace-period disablement), and `CONTEXT.md`'s Required Password Change.

**Blocked by:** 09, 11

**Status:** resolved

- [x] A filter placed after authentication returns 403 `password_change_required` for every authenticated request except `GET /me`, `PATCH /me/password`, `POST /logout` and `GET /csrf`, while the Account's `password_change_required` is set.
- [x] `GET /me` reports `passwordChangeRequired: true` while the flag is set and `false` once it is cleared (the field itself exists since ticket 04).
- [x] `POST /api/admin/users/{id}/require-password-change` (Admin only) returns 200, sets the flag, and ends the target's Sessions. An unknown id returns 404; a User gets 403.
- [x] A successful Password Change or reset confirmation clears the flag.
- [x] Audit events (`password-change-enforcement`): flag set (INFO, with the acting Admin and target), flag cleared (INFO), and a request refused because of the flag (WARN).
- [x] SPA: when `/me` reports the flag, or a request returns 403 `password_change_required`, only the Password Change screen and logout are available. The admin screen has a "Require password change" action.
- [x] Test fixtures complete the Bootstrap Admin's required change, except in tests of this behaviour.
- [x] Tests cover: the Bootstrap Admin's first login getting 403 on `/hello` and `/admin/users` while `/me`, `/me/password`, `/logout` and `/csrf` work; a Password Change clearing the flag; an Admin requiring a change on another Account, which ends its Sessions and then blocks it the same way until it changes its password; a reset clearing the flag; a User calling the action getting 403; and the audit events.

## Comments

### Verification (2026-09-30)

**Implemented:** `RequiredPasswordChangeFilter` (a `OncePerRequestFilter` added to the API chain with `addFilterAfter(…, AuthorizationFilter.class)`) refuses every authenticated request with 403 `password_change_required` while the Account's flag is set, allowing only `GET /me`, `PATCH /me/password`, `POST /logout` and `GET /csrf`; it reads the flag from the Account on each request, not from the Session, so clearing it takes effect at once. `SecurityConfig` disables the container-wide `FilterRegistrationBean` Boot would create for the filter bean, keeping it in exactly one place. `Account.requirePasswordChange()` / the existing clear path, `AccountRequiredPasswordChange` (the before-state carrier), `AccountAdministrationService.requirePasswordChange` (`@PreAuthorize("hasRole('ADMIN')")`, row-locked, idempotent) and `AccountAdministrationController`'s `POST /api/admin/users/{id}/require-password-change` set the flag, end the target's Sessions through `SessionControl.endAll` and audit the change; 404 `not_found` for an unknown id, 403 `access_denied` for a User. `PasswordChangeService` / `PasswordResetService` (via `PasswordUpdater`) clear the flag and audit it. `PasswordChangeEnforcement` holds the one `password-change-enforcement` event shape for all three emitters. SPA: `AuthProvider` carries `passwordChangeRequired`, `client.ts` turns a 403 `password_change_required` into a re-check, `App.tsx`'s `LoggedInOnly` sends a flagged holder to `/password-change` and `VisitorOnly` now guards `/login`, `/register`, `/forgot-password` and `/reset-password`, `PasswordChangePage` explains the requirement and keeps only `LogoutButton` (extracted from `HelloPage`), and `AdminUsersPage` / `admin.ts` add the per-row "Require password change" action.

**Deviations and decisions:**

- The audited state field is `change_required`, not `password_change_required`: `LogSanitizer.isSensitiveKey` / `EcsLogFormatter` mask the value of any key containing "password", which would hide both the before and after states. `event.action` already says which flag it is. `event.reason` is `required` / `cleared` / `password_change_required` so the three events are told apart without reading the state fields.
- The filter sits *after* `AuthorizationFilter`, so an unauthenticated request still gets its 401 and an unauthorized one still gets `access_denied`. The refusal therefore never reveals that an endpoint exists.
- Require-password-change is deliberately outside the self-action guard and the last-Admin rule: `spec.md:198` scopes both to disable / role change / unlock / delete. An Admin may require the change of their own Account, and their own Sessions end with it — that is the intended consequence, now covered by a test. Rejected as outside this issue: adding `passwordChangeRequired` to `AdminAccountView` to hide or relabel the button, and a confirmation dialog on the acting Admin's own row (see `docs/agents/reviewer-decisions.md`, "Public SPA routes are Visitor-only…").
- Criterion 7 is satisfied **vacuously**, not by any change to the fixtures: no existing test fixture logs in as the Bootstrap Admin. Every other API test class starts with `DELETE FROM users` and builds the Accounts it needs, so there was no required change for a fixture to complete. `RequiredPasswordChangeApiTest` is the one class that recreates the real Bootstrap Admin and completes the change itself (`adminReady()`) when it needs a working Admin.
- Criterion 6 is now fully met, including `/register`, `/forgot-password` and `/reset-password`: they are wrapped in the existing `VisitorOnly`, so a flagged holder lands on `/` and is redirected to `/password-change`, while a logged-out holder still reaches the reset flow (which is how a completed reset clears the flag).
- The filter adds one `SELECT` per authenticated request by design — reading the Account rather than the Session is what makes clearing take effect at once and stops a Session started before the flag from outliving it. A latency check for this belongs to issue 14 (operations and metrics); nothing was done about it here.

**Accepted reviewer notes, not fixed here:**

- Wrapping `/reset-password` in `VisitorOnly` means a *logged-in* holder who clicks a reset email link loses the `#token=…` fragment from the address bar: `VisitorOnly` renders children while `/me` is still loading, so `ResetPasswordPage` mounts and its effect strips the fragment before the guard navigates away. Low severity and recoverable by re-opening the link after logging out, and nothing usable was lost — `POST /api/password-reset/confirm` is refused 403 for a logged-in flagged holder anyway. Closing it would mean `VisitorOnly` showing a loading placeholder instead of children, which would also make `/login` flash a loader.
- The logged-out reset-route test passes the token as `?token=` while the app reads it from the `#token=` fragment. The test still proves what it needs to (the route is reachable while logged out), but the parameter is inert.
- `AdminUsersPage` wraps the conditional Unlock button in a fragment purely to carry a trailing `{' '}` separator; a CSS gap would be tidier.

**Verification steps:** Backend `./mvnw -o verify` from `backend/`: 281 tests, 0 failures, 0 errors, JaCoCo coverage gate passed. Frontend from `frontend/`: `npx tsc -b`, `npm run lint`, `npm run format:check` all clean; `npx vitest run` 151 tests passed. The reviewer's first pass returned Must-fix 0 / `approved-with-notes`; this correction pass closed criterion 6's public-route gap, corrected the `SecurityConfig` filter-ordering javadoc, and added the self-require and idempotent-audit backend tests. KB retrieval and the code-reviewer compliance gates were skipped by request; mutation testing was not run.

**Checklist:** all acceptance-criteria boxes ticked.

Commits: `2d89e74 feat(auth): Enforce required password change`, `a09d492 docs: Record issue 13 reviewer decisions`
