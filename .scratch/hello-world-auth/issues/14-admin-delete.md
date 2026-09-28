# 14: Admin — delete account (Story 11)

**What to build:** An admin can remove an account that should no longer exist. The delete endpoint removes a target account. An admin targeting their own account via the endpoint is rejected — an admin cannot delete themselves. A React control in the admin table drives it.

**Blocked by:** 11.

**Status:** ready-for-agent

- [ ] Delete endpoint (ADMIN only) removes another account
- [ ] An admin deleting their own account is rejected (self-action guard)
- [ ] CSRF enforced on the endpoint
- [ ] Audit log line for account deletion (actor + target)
- [ ] React admin control deletes an account and updates the list
- [ ] Integration tests: account removed; admin cannot delete self
