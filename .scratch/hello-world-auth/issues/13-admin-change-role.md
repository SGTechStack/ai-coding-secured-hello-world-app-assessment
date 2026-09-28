# 13: Admin — change role (Story 10)

**What to build:** An admin can grant or revoke admin privileges on another account. The role-change endpoint sets a target account's role to a valid value (USER or ADMIN). An admin targeting their own account via the endpoint is rejected — an admin cannot demote themselves. A React control in the admin table drives it.

**Blocked by:** 11.

**Status:** ready-for-agent

- [ ] Role-change endpoint (ADMIN only) sets another account's role to a valid USER/ADMIN value
- [ ] An invalid role value is rejected
- [ ] An admin changing their own role is rejected (self-action guard)
- [ ] CSRF enforced on the endpoint
- [ ] Audit log line for role change (actor + target + new role)
- [ ] React admin control changes role and reflects the new value
- [ ] Integration tests: valid role change applied; admin cannot demote self
