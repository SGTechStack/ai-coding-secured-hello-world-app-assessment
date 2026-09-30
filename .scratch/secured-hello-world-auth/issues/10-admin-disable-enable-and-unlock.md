# 10: Admin disable/enable and unlock

**What to build:** From the admin page, an Admin can disable or re-enable another Account and unlock a Locked one. Disabling takes effect at once because it ends the target's sessions. Disabled and Locked stay independent. An Admin can never act on their own Account, and a stale target returns a clear 404. This ticket adds the self-action and not-found rules that ticket 11 reuses.

**Blocked by:** 06 (Account lockout), 09 (Bootstrap admin and the admin Account list)

**Status:** ready-for-agent

- [ ] `PATCH /admin/users/{id}/status` with `{enabled}` (ADMIN, CSRF) disables or re-enables the target.
- [ ] Disabling ends the target's active sessions immediately: the target's existing cookie then gets 401. This uses Session control.
- [ ] A Disabled Account can't log in and gets the generic `invalid credentials`.
- [ ] Re-enabling leaves any lock in place.
- [ ] `POST /admin/users/{id}/unlock` (ADMIN, CSRF) clears `locked_until` and the Failed-login counter, so the target can log in straight away.
- [ ] An Admin targeting their own Account gets 403 `self action not allowed`.
- [ ] An unknown id or a Tombstone gets 404 `not found`.
- [ ] Both endpoints reject missing or invalid CSRF tokens with 403, and a Regular user gets 403.
- [ ] Enable, disable and unlock are audited with the actor's and the target's UUIDs and the outcome. Failed admin actions are logged at WARN.
- [ ] The SPA admin page has enable/disable and unlock actions for each Account.

## Comments

**2026-09-29 — shape.** `AccountAdministration` (admin package) holds the rules that every admin action shares, and ticket 11 reuses them. A target equal to the caller gets 403 `self action not allowed`. An unknown id or a Tombstone gets 404 `not found`. Both are audited at WARN as `user-administration` failures, with `user.id` (the Admin) and `user.target.id`. The self-action rule applies to enable and unlock too, not just disable. Status and unlock return 200 with the updated Account, in the list's item shape, so the SPA can refresh a row.

**Audit fields.** Successes are INFO `user-administration` events carrying `user.id` (actor) and `user.target.id` (target). The message tells them apart: "Account disabled.", "Account enabled." or "Account unlocked.".

**Malformed id.** A path id that isn't a UUID gets 400 `validation failed`, not 404.
