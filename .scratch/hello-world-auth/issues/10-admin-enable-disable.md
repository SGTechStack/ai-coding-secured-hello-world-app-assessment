# 10: Admin — enable/disable a user

**What to build:** An admin can suspend or restore another user's access by toggling their `enabled` flag, without deleting data. An admin cannot disable their own account.

**Blocked by:** 09 (admin route-guard + user list), 03 (login enforces enabled).

**Status:** ready-for-agent

- [ ] Admin toggling another user's status updates the `enabled` flag; a disabled user can no longer log in.
- [ ] An admin targeting their own account via the status-toggle endpoint is rejected (cannot disable self).
- [ ] Endpoint is admin-only (403 for non-admin) and CSRF-protected. (IM8 ac-1, as-7)
- [ ] Enable/disable actions are audit-logged with actor + target. (IM8 lm-4)
- [ ] Integration test: admin cannot disable their own account. (Testing Requirements, Stories 9–11)
