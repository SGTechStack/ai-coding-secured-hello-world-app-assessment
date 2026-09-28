# 12: Admin — delete a user

**What to build:** An admin can permanently remove another user's account. An admin cannot delete their own account.

**Blocked by:** 09 (admin route-guard + user list).

**Status:** ready-for-agent

- [ ] Admin deleting another user's account removes it.
- [ ] An admin targeting their own account via the delete endpoint is rejected (cannot delete self).
- [ ] Endpoint is admin-only (403 for non-admin) and CSRF-protected. (IM8 ac-1, as-7)
- [ ] Delete actions are audit-logged with actor + target. (IM8 lm-4)
- [ ] Integration test: admin cannot delete their own account. (Testing Requirements, Stories 9–11)
