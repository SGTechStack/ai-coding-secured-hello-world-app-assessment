# 05: Admin user management (bootstrap, list, enable/disable, role change, delete)

**What to build:** An initial admin account is seeded automatically on first startup, and any admin can list all registered accounts, enable/disable them, change their role, or delete them — with a single shared guard that blocks every one of those mutating actions when an admin targets their own account.

**Blocked by:** 02

**Status:** done

- [x] On startup, if no `ADMIN` user exists, one is seeded using credentials from configuration (e.g. `app.admin.username`, `app.admin.password`), with the password hashed identically to any other account; restarting does not create a duplicate seed account
- [x] The seeded admin account is flagged to require a password change on first login, so the operator-supplied bootstrap credential cannot remain in permanent use (IM8 `ac-6`)
- [x] `GET /api/admin/users` (admin only) returns each user's username, email, role, enabled status, `created_at`, and `last_login_at` — never password hashes; a non-admin caller gets 403 (IM8 `ac-1`, `as-7`). `last_login_at` is the access-review signal an admin uses to spot dormant accounts (IM8 `ac-4`) — automatic deactivation of inactive accounts is out of scope for this build and is recorded as a deviation, not silently dropped
- [x] The status-toggle endpoint updates another user's `enabled` flag (a disabled user can no longer log in); an admin targeting their own account via this endpoint is rejected
- [x] The role-change endpoint updates another user's role between `USER`/`ADMIN`; an admin targeting their own account via this endpoint is rejected
- [x] The delete endpoint removes another user's account; an admin targeting their own account via this endpoint is rejected
- [x] The self-action guard is implemented once and reused by all three mutating endpoints, not copied three times
- [x] Structured audit log lines for role change, enable, disable, and delete, each recording actor and target (IM8 `lm-4`)
- [x] Integration tests: a `USER` calling any `/api/admin/**` endpoint receives 403; admin cannot disable/demote/delete their own account

## Comments

Implemented in an isolated worktree/branch and merged (commits `a6c1b398` + merge `e072bfa3`,
with a small expected merge conflict in `frontend/src/App.tsx` against ticket 04's routes,
resolved by combining both). 5 integration tests (AdminUserManagementIntegrationTest) pass. Live
smoke test walked the full loop: bootstrap admin → forced-password-change gate blocks
`/api/admin/users` → completing a password reset clears the gate → admin lists all users with no
password hash exposed.
