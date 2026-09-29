# 12: Admin: unlock a Locked Account

**What to build:** An Admin unlocks a Locked Account from the admin screen, so its holder can log in straight away instead of waiting out the lock. An Admin can't unlock their own Account, so a hijacked admin Session can't lift a lock that is protecting it. See the spec's stories 70 and 73, "Account administration service", and "API contract". Use `CONTEXT.md`'s Locked (not Disabled).

**Blocked by:** 06, 11

**Status:** ready-for-agent

- [ ] `POST /api/admin/users/{id}/unlock` returns 200, clearing `locked_until` and resetting the failure counter. An unknown id returns 404.
- [ ] Unlocking your own Account returns 403 `self_action_forbidden`, using the self-action guard from ticket 11.
- [ ] Audit events: INFO `user-administration` for unlock, with `target.user.id` and the lock state before and after; WARN for a rejected attempt.
- [ ] SPA: an unlock action on the admin screen, offered for Accounts whose `locked` flag is set, and showing a 403 clearly.
- [ ] Tests cover: an Account Locked by 5 failures is unlocked and can log in at once with the correct password; the failure counter starts again from zero; self-unlock gets 403; 404 for an unknown id; a User gets 403; and the audit events.
