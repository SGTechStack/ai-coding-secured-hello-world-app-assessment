# 23: Invite, admin-issued reset and unlock

**What to build:** Admins issue single-use tokens, never passwords (ADR-006).

- **`POST /api/admin/users`** (invite) creates a *pending registration* and returns the activation token once. It returns 400 `USER_EXISTS` for a taken or tombstoned identifier (R-ADM-012).
- **`POST /api/admin/users/{uuid}/password-reset`** returns a reset token once, for any account including the admin's own. No self-action rule applies. It ends the subject's sessions (ADR-037). Issuing the token clears nothing, including the lock (REJ-016; R-LCK-010). The user redeems it through the ordinary reset confirm.
- **`POST /api/admin/users/{uuid}/unlock`** takes a closed-enum reason: `USER_REQUEST`, `FALSE_POSITIVE`, `PASSWORD_RESET_COMPLETED` or `OTHER` (REJ-028). It clears the password lockout and the tier-1 factor lock, never tier 2 (REJ-072). Actor ≠ subject.
- Every token response carries `Cache-Control: no-store`, and the token is never logged (R-FE-007).
- **SPA:** an invite form, and issue-reset and unlock actions that show the token once, with a copy affordance.

**Blocked by:** 12, 15, 19, 20

**Status:** ready-for-agent

- [ ] Inviting a taken or tombstoned identifier gets `USER_EXISTS`. A fresh invite's token activates the account.
- [ ] An admin-issued reset token redeems through confirm. Issuing it doesn't unlock a locked account.
- [ ] Unlock clears the password lock and the tier-1 lock, leaves tier 2 intact, and refuses self-unlock.
- [ ] The unlock audit row carries `user.target.unlock_reason`.
- [ ] Every token response has `no-store`, and the canary scan finds no token in the logs.
