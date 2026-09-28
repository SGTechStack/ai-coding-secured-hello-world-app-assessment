# 14: Admin — delete account (Story 11)

**What to build:** An admin can remove an account that should no longer exist. The delete endpoint removes a target account. An admin targeting their own account via the endpoint is rejected — an admin cannot delete themselves. A React control in the admin table drives it.

**Blocked by:** 11.

**Status:** done

- [x] Delete endpoint (ADMIN only) removes another account
- [x] An admin deleting their own account is rejected (self-action guard)
- [x] CSRF enforced on the endpoint
- [x] Audit log line for account deletion (actor + target)
- [x] React admin control deletes an account and updates the list
- [x] Integration tests: account removed; admin cannot delete self
