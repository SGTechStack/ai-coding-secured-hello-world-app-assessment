# 11: Admin — change a user's role

**What to build:** An admin can grant or revoke admin privileges by changing another user's role between USER and ADMIN. An admin cannot demote their own account.

**Blocked by:** 09 (admin route-guard + user list).

**Status:** ready-for-agent

- [ ] Admin changing another user's role to a valid role (USER/ADMIN) updates the account's role.
- [ ] An invalid role value is rejected with a validation error. (IM8 as-1)
- [ ] An admin targeting their own account via the role-change endpoint is rejected (cannot demote self).
- [ ] Endpoint is admin-only (403 for non-admin) and CSRF-protected. (IM8 ac-1, as-7)
- [ ] Role-change actions are audit-logged with actor + target. (IM8 lm-4)
- [ ] Integration test: admin cannot demote their own account. (Testing Requirements, Stories 9–11)
