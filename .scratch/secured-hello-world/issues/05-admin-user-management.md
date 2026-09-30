# 05: Admin user management (bootstrap, list, enable/disable, role change, delete)

**What to build:** An initial admin account is seeded automatically on first startup, and any admin can list all registered accounts, enable/disable them, change their role, or delete them — with a single shared guard that blocks every one of those mutating actions when an admin targets their own account.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] On startup, if no `ADMIN` user exists, one is seeded using credentials from configuration (e.g. `app.admin.username`, `app.admin.password`), with the password hashed identically to any other account; restarting does not create a duplicate seed account
- [ ] The seeded admin account is flagged to require a password change on first login, so the operator-supplied bootstrap credential cannot remain in permanent use (IM8 `ac-6`)
- [ ] `GET /api/admin/users` (admin only) returns each user's username, email, role, enabled status, `created_at`, and `last_login_at` — never password hashes; a non-admin caller gets 403 (IM8 `ac-1`, `as-7`). `last_login_at` is the access-review signal an admin uses to spot dormant accounts (IM8 `ac-4`) — automatic deactivation of inactive accounts is out of scope for this build and is recorded as a deviation, not silently dropped
- [ ] The status-toggle endpoint updates another user's `enabled` flag (a disabled user can no longer log in); an admin targeting their own account via this endpoint is rejected
- [ ] The role-change endpoint updates another user's role between `USER`/`ADMIN`; an admin targeting their own account via this endpoint is rejected
- [ ] The delete endpoint removes another user's account; an admin targeting their own account via this endpoint is rejected
- [ ] The self-action guard is implemented once and reused by all three mutating endpoints, not copied three times
- [ ] Structured audit log lines for role change, enable, disable, and delete, each recording actor and target (IM8 `lm-4`)
- [ ] Integration tests: a `USER` calling any `/api/admin/**` endpoint receives 403; admin cannot disable/demote/delete their own account
