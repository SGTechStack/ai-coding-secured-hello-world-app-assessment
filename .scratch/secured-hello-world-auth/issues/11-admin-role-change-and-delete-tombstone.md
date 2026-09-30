# 11: Admin role change and delete (Tombstone)

**What to build:** From the admin page, an Admin can grant or revoke admin rights on another Account and delete another Account. A role change ends the target's sessions, so a demoted Admin can't keep their old rights. Delete turns the Account into a Tombstone: it disappears from the app, can't authenticate, and its username and email stay reserved forever. See ADR-0007.

**Blocked by:** 08 (Self-service password reset), 10 (Admin disable/enable and unlock)

**Status:** ready-for-agent

- [ ] `PATCH /admin/users/{id}/role` with `{role}` (ADMIN, CSRF) switches the target between `USER` and `ADMIN`.
- [ ] A role change ends the target's sessions, so a demoted Admin's old session cookie no longer reaches `/admin/**`.
- [ ] `DELETE /admin/users/{id}` (ADMIN, CSRF) sets `deleted_at`, ends the target's sessions and removes its pending Password reset tokens.
- [ ] A Tombstone is hidden from the admin list, can't log in (generic `invalid credentials`), and returns 404 on any later admin action.
- [ ] Registering the username (in any case) or the email of a Tombstone returns `user exist`.
- [ ] A password reset request for a Tombstone's email creates no token and still returns the generic 202.
- [ ] An Admin who tries to demote or delete their own Account gets 403 `self action not allowed`. An unknown id gets 404.
- [ ] Both endpoints reject missing or invalid CSRF tokens with 403, and a Regular user gets 403.
- [ ] Role changes and deletes are audited with the actor's and the target's UUIDs and the outcome.
- [ ] The SPA admin page has role-change and delete actions for each Account.

## Comments

**2026-09-29 — shape.** Role change returns 200 with the updated Account. Delete returns 204. The role-change audit event adds `user.target.roles: [<new role>]`, and delete uses `event.type: deletion`. Delete ends sessions and removes the Account's Password reset tokens after the soft delete commits. A token left behind by a failure between those steps still can't be redeemed, because confirm only changes the password of an Account that isn't a Tombstone.

**Known gap: who deleted.** The standard's recipe (Privileged User Administration §6) says a Tombstone should also record who deleted it (`deletedById`). The spec's `users` schema has no such column, so the deleting Admin's UUID is recorded only in the audit event (`user.id` on "Account deleted."). Adding a `deleted_by` column is a schema decision for the spec owner.

**Known gap: concurrent mutual demotion.** Two Admins demoting or deleting each other at the same moment each pass the self-action check, and together they could leave no active Admin. The next startup would then create the Bootstrap admin again, unless its username or email now belongs to a Tombstone, in which case startup fails with a clear message.
