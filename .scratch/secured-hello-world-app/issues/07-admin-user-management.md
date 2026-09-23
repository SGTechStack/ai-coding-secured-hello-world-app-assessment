# 07: Admin user management (Stories 8-11)

**What to build:** An admin can list all users, enable/disable another
user's account, change another user's role, and delete another user's
account — with every mutation blocked against an admin targeting
themselves, and every endpoint blocked for non-admins.

**Blocked by:** 03 (Login + session + generic errors)

**Status:** ready-for-agent

- [ ] `GET /api/admin/users` lists username, email, role, enabled status,
      and created-at for every user — never password hashes
- [ ] Any `/api/admin/**` endpoint called by an authenticated non-admin
      (`USER`) responds 403
- [ ] Enable/disable endpoint: admin can toggle another user's `enabled`
      flag; a disabled user can no longer log in
- [ ] Enable/disable endpoint rejects an admin targeting their own account
- [ ] Role-change endpoint: admin can change another user's role between
      `USER` and `ADMIN`
- [ ] Role-change endpoint rejects an admin targeting their own account
- [ ] Delete endpoint: admin can delete another user's account
- [ ] Delete endpoint rejects an admin targeting their own account
- [ ] Audit log line emitted for role change / enable / disable / delete
      (actor + target, no sensitive data)
- [ ] Integration tests: list returns expected fields and omits password
      hashes; non-admin gets 403 on each admin endpoint; enable/disable
      works cross-account and is rejected on self; role change works
      cross-account and is rejected on self; delete works cross-account and
      is rejected on self
