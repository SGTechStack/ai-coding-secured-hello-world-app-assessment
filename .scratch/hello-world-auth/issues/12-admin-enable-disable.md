# 12: Admin — enable/disable account (Story 9)

**What to build:** An admin can suspend or restore another user's access without deleting their data. The status-toggle endpoint updates a target account's `enabled` flag, and a disabled user can no longer log in. An admin targeting their own account via the toggle is rejected — an admin cannot disable themselves. A React control in the admin table drives it.

**Blocked by:** 11.

**Status:** ready-for-agent

- [ ] Status-toggle endpoint (ADMIN only) flips another account's `enabled` flag
- [ ] A disabled user can no longer log in
- [ ] An admin toggling their own account is rejected (self-action guard)
- [ ] CSRF enforced on the endpoint
- [ ] Audit log line for enable/disable (actor + target)
- [ ] React admin control toggles status and reflects the new state
- [ ] Integration tests: toggle disables/enables; disabled user login blocked; admin cannot disable self
