# 22: Role change and delete with tombstone

**What to build:**

- **`PUT /api/admin/users/{uuid}/role`** changes a role between `USER` and `ADMIN`. It goes through the guard: no self-demote, and the two-admin rule applies. It ends the subject's sessions.
- **`DELETE /api/admin/users/{uuid}`** deletes the account and writes a `deleted_users` tombstone in one transaction (ADR-044). The tombstone holds `user_id`, `username`, `email_hmac`, `deleted_at` and `deleted_by_id`. It is guarded and ends sessions. Password history purges through the cascade.
- **Tombstone effects:** a tombstoned username or email is refused at registration (uniformly) and at bootstrap. The versioned HMAC key is used forward only (ADR-052).
- **Audit:** role change and delete, with actor and subject.
- **SPA:** a role control and a delete dialog with a keyboard path.

**Blocked by:** 20

**Status:** done

- [x] Demoting or deleting yourself is refused. Either action on one of exactly two enrolled admins gets the two-admin 409.
- [x] After a role change, the subject's old session is gone.
- [x] After delete, the user row and history are gone and a tombstone exists.
- [x] Re-registering the username or email fails as taken.
- [x] The tombstone insert and the delete succeed or fail together.
