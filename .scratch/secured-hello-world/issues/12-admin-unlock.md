# 12: Admin: unlock a Locked Account

**What to build:** An Admin unlocks a Locked Account from the admin screen, so its holder can log in straight away instead of waiting out the lock. An Admin can't unlock their own Account, so a hijacked admin Session can't lift a lock that is protecting it. See the spec's stories 70 and 73, "Account administration service", and "API contract". Use `CONTEXT.md`'s Locked (not Disabled).

**Blocked by:** 06, 11

**Status:** resolved

- [x] `POST /api/admin/users/{id}/unlock` returns 200, clearing `locked_until` and resetting the failure counter. An unknown id returns 404.
- [x] Unlocking your own Account returns 403 `self_action_forbidden`, using the self-action guard from ticket 11.
- [x] Audit events: INFO `user-administration` for unlock, with `target.user.id` and the lock state before and after; WARN for a rejected attempt.
- [x] SPA: an unlock action on the admin screen, offered for Accounts whose `locked` flag is set, and showing a 403 clearly.
- [x] Tests cover: an Account Locked by 5 failures is unlocked and can log in at once with the correct password; the failure counter starts again from zero; self-unlock gets 403; 404 for an unknown id; a User gets 403; and the audit events.

## Comments

### Verification (2026-09-30)

**Implemented:** `AccountAdministrationController` adds `POST /api/admin/users/{id}/unlock`, returning 200 on success and 404 `not_found` for an unknown id. `AccountAdministrationService.unlock` runs the self-action guard (from ticket 11) before any mutation, yielding 403 `self_action_forbidden` for a self-unlock attempt. `Account.unlock()` clears `failedLoginAttempts` and `lockedUntil` (idempotent if already unlocked). Unlock deliberately skips the last-Admin machinery and does not end Sessions, since it never touches `enabled`/`role`. Success emits INFO `user-administration` with `target.user.id` and `state.before.locked`/`state.after.locked`; a rejected self-unlock emits WARN via the existing `SelfActionForbiddenException` handler from ticket 11. The SPA admin screen (`AdminUsersPage`) shows an "Unlock" button only for accounts with `locked: true`, wired through `admin.ts`'s `unlock()`/`toActionResult`, showing a clear message for the 403 and 404 cases.

**Deviations and decisions:** None. The "a User gets 403" criterion is covered by the existing, unmodified parameterized test in `AdminAccountListApiTest` (its `@CsvSource` already includes `POST, /api/admin/users/{id}/unlock`), confirmed still covers the new endpoint.

**Verification steps:** Backend: `./mvnw -q -o compile` clean; new `AdminAccountUnlockApiTest` (6/6) plus `AdminAccountEnableDisableApiTest`, `AdminAccountRoleChangeApiTest`, `AdminAccountListApiTest`, `LockoutApiTest` all green; full suite `./mvnw -q -o verify` — 268 tests, 0 failures/errors, coverage gate passed. Frontend: `npx vitest run` (135/135), `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm run build` all clean. The reviewer loop passed on the first round with Must-fix 0 and Human decisions 0. KB retrieval and the code-reviewer compliance gates were skipped by request; mutation testing was not run.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `61f9a1f feat(admin): Add admin unlock of locked accounts`
