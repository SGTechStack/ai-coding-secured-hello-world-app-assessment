# 15: Admin: delete an Account, with tombstone

**What to build:** An Admin deletes another Account from the admin screen, after confirming. Its Sessions end, its Password History and Reset Tokens go, and a tombstone keeps its UUID, username, email, deletion time and the deleting Admin indefinitely. That username can never be registered or bootstrapped again, though the email can be reused. See the spec's stories 10–11, 71–74 and 81 (the tombstone half), "Account administration service", "Schema" (`deleted_users`), and ADR 0001 (tombstone as soft delete). Use `CONTEXT.md`'s Deleted Account.

**Blocked by:** 09 (delete removes the Account's Reset Tokens), 11 (self-action guard and last-Admin rule)

**Status:** ready-for-agent

- [ ] A Flyway migration creates `deleted_users` (the deleted Account's UUID, unique username, email, deleted-at, deleting Admin's UUID).
- [ ] `DELETE /api/admin/users/{id}` returns 204. In one transaction it writes the tombstone, removes the Account and its `password_reset_tokens` and `password_history` rows, and then ends the Account's Sessions. An unknown id returns 404.
- [ ] Deleting your own Account returns 403 `self_action_forbidden`, and deleting the last enabled Admin returns 409 `last_admin` (checked under the same row lock as ticket 11).
- [ ] Registration rejects a tombstoned username with 400 `user_exist`, but accepts a Deleted Account's email.
- [ ] The Bootstrap Admin initializer fails startup with a clear message when the configured username is tombstoned.
- [ ] Audit events: INFO `user-administration` for delete, with `target.user.id` and state; WARN for rejected attempts; `session-end` for the ended Sessions.
- [ ] SPA: a delete action on the admin screen that asks for confirmation and shows 403 and 409 errors clearly.
- [ ] Tests cover: delete writes the tombstone and ends the target's Sessions; the deleted Account's pending Reset Token no longer works; self-delete gets 403; the last-Admin delete gets 409; a tombstoned username is blocked while the email is reusable; bootstrap startup fails on a tombstoned username; 404 for an unknown id; and the audit events.
