# 11: Admin: enable/disable and role change, with safety rules

**What to build:** An Admin disables or re-enables another Account, or changes it between User and Admin, from the admin screen. The target's Sessions end at once, so the change applies immediately. The Admin can't target their own Account. No change can leave the system without an enabled Admin, even when two Admins act at the same moment. Every action is audited with the state before and after. See the spec's stories 23, 66–69, 73–75, "Account administration service", "API contract", and `CONTEXT.md`'s Disabled.

**Blocked by:** 05, 10

**Status:** resolved

- [x] `PATCH /api/admin/users/{id}/enabled` with `{enabled}` and `PATCH /api/admin/users/{id}/role` with `{role: USER|ADMIN}` both return 200. An unknown id returns 404.
- [x] Disabling an Account or changing its role ends all of its Sessions through Session control.
- [x] Re-enabling does not set the required-password-change flag (ADR 0001).
- [x] The self-action guard: an Admin disabling or changing the role of their own Account gets 403 `self_action_forbidden`.
- [x] The last-Admin rule: any disable or role change that would leave zero enabled Admins returns 409 `last_admin`. It is checked under a pessimistic row lock in the same transaction, so two concurrent demotions can't both succeed.
- [x] Audit events: INFO `user-administration` for each success, with `target.user.id` and the before and after state (`enabled`, `role`); WARN for rejected attempts (`self_action_forbidden`, `last_admin`).
- [x] SPA: enable/disable and role actions on the admin screen, which show 403 and 409 errors clearly.
- [x] Tests cover: disable ends the target's Sessions, and later logins fail with a body identical to a wrong password's `authentication_failed` body; re-enable restores login; a role change ends Sessions and the new role applies at next login; self-disable and self-demote return 403; the last-Admin rule for both disable and demote; two concurrent demotions of the last two Admins, where exactly one succeeds; 404 for an unknown id; and the audit events.

## Comments

### Verification (2026-09-30)

**Implemented:** `AccountAdministrationController` adds `PATCH /api/admin/users/{id}/enabled` and `PATCH /api/admin/users/{id}/role`, both returning 200 on success and 404 `not_found` (`AccountNotFoundException`) for an unknown id. `AccountAdministrationService.setEnabled`/`changeRole` end every Session of the target via `SessionControl.endAll` — disable ends Sessions only when transitioning to disabled (`ACCOUNT_DISABLED` reason), enable ends none, and a role change always ends Sessions (`ROLE_CHANGED` reason) regardless of direction. `Account.setEnabled` never touches `passwordChangeRequired` in either direction, per ADR 0001. The self-action guard (`actingAdminId.equals(targetId)`) runs before any lock or mutation and yields 403 `self_action_forbidden`. The last-Admin rule (`guardLastAdmin`) is enforced under a pessimistic row lock across every currently-enabled Admin row, locked in a fixed ID order (`findByRoleAndEnabledTrueOrderById`) to avoid an ABBA deadlock between two concurrent admin actions, returning 409 `last_admin`. Success emits INFO `user-administration` with `target.user.id` and `state.before.*`/`state.after.*`; rejections emit WARN `user-administration` with `event.reason` `self_action_forbidden`/`last_admin`. The SPA admin screen (`AdminUsersPage`) adds Enable/Disable and Make Admin/Make User buttons per row, reloading the list on success and showing a distinct message for 403/409/404/generic failures (`admin.ts`'s `toActionResult`).

**Deviations and decisions:** None beyond the implementer's documented design note: the last-Admin lock strategy locks every currently-enabled-Admin row (not just the target) whenever the target is itself a currently-enabled Admin, in fixed ID order — more conservative than a minimal per-target lock, but makes concurrent last-Admin safety hold by construction rather than by luck. No `SecurityConfig` change was needed (`/api/admin/**` already required `ROLE_ADMIN`, from issue 10).

**Known gaps (reviewer notes, non-blocking):** `docs/adr/0001-app-standards-override-prd.md` line 41 ("No role-read API and no role-mutation endpoints...") is now stale — issues 10 and 11 added both. Flagged as a follow-up documentation correction, not a code defect.

**Verification steps:** Backend: `mvn compile` clean, plus the three new test classes run directly — `AdminAccountEnableDisableApiTest` (7/7), `AdminAccountRoleChangeApiTest` (7/7), `AccountAdministrationServiceConcurrencyTest` (1/1, real two-thread race, deterministic across 5 repeated runs). Full suite (`./mvnw -q -o test`) green. Frontend: `npx vitest run` (98/98), `npm run typecheck`, `npm run lint`, `npm run format:check`, and `npm run build` all clean. The reviewer loop passed on the first round with Must-fix 0 and Human decisions 0. KB retrieval and the code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `33d59e4 feat(admin): Enable/disable accounts and change roles`
