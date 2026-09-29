# 11: Admin: enable/disable and role change, with safety rules

**What to build:** An Admin disables or re-enables another Account, or changes it between User and Admin, from the admin screen. The target's Sessions end at once, so the change applies immediately. The Admin can't target their own Account. No change can leave the system without an enabled Admin, even when two Admins act at the same moment. Every action is audited with the state before and after. See the spec's stories 23, 66–69, 73–75, "Account administration service", "API contract", and `CONTEXT.md`'s Disabled.

**Blocked by:** 05, 10

**Status:** ready-for-agent

- [ ] `PATCH /api/admin/users/{id}/enabled` with `{enabled}` and `PATCH /api/admin/users/{id}/role` with `{role: USER|ADMIN}` both return 200. An unknown id returns 404.
- [ ] Disabling an Account or changing its role ends all of its Sessions through Session control.
- [ ] Re-enabling does not set the required-password-change flag (ADR 0001).
- [ ] The self-action guard: an Admin disabling or changing the role of their own Account gets 403 `self_action_forbidden`.
- [ ] The last-Admin rule: any disable or role change that would leave zero enabled Admins returns 409 `last_admin`. It is checked under a pessimistic row lock in the same transaction, so two concurrent demotions can't both succeed.
- [ ] Audit events: INFO `user-administration` for each success, with `target.user.id` and the before and after state (`enabled`, `role`); WARN for rejected attempts (`self_action_forbidden`, `last_admin`).
- [ ] SPA: enable/disable and role actions on the admin screen, which show 403 and 409 errors clearly.
- [ ] Tests cover: disable ends the target's Sessions, and later logins fail with a body identical to a wrong password's `authentication_failed` body; re-enable restores login; a role change ends Sessions and the new role applies at next login; self-disable and self-demote return 403; the last-Admin rule for both disable and demote; two concurrent demotions of the last two Admins, where exactly one succeeds; 404 for an unknown id; and the audit events.
