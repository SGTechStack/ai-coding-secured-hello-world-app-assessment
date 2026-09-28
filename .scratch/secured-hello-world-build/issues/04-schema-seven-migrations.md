# 04: Schema: all seven migrations

**What to build:** The whole data model lands in one setup ticket. The spec fixes the migration order, and Flyway refuses out-of-order versions, so the tables can't be added feature by feature. The seven versioned, append-only migrations, in order:

1. `roles` (seeded, read-only)
2. `users`
3. `credential_tokens`
4. `password_history`
5. `totp_user_details` and `pending_totp`
6. `deleted_users`
7. the Spring Session tables, copied verbatim, with a blob-hash check and Spring Session's own initialisation off (REJ-040; ADR-030)

JPA entities are mapped and validated with `ddl-auto: validate` and `NAMED` index validation. Column decisions:
- `users` has the extra columns listed in the spec's data model, and `role` is a foreign key to `roles.name`;
- *locked* is derived from `locked_until` and never stored;
- timestamps are `TIMESTAMP(6) WITH TIME ZONE`, digests are hex `VARCHAR(64)`, and `totp_key` is `VARBINARY(69)` with named checks;
- `deleted_users` has no foreign keys;
- `ON DELETE CASCADE` is the only deletion mechanism;
- IDs are application-generated UUIDv4 (ADR-050).

H2 runs in file mode only, `LOCK_TIMEOUT=1000` is on the URL, `FILE_LOCK=NO` is prohibited, and Flyway `clean` is disabled (REJ-041).

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A fresh H2 file migrates V1–V7 and the context validates.
- [ ] ADR-051's negative-test set passes: each deliberate schema drift makes startup fail.
- [ ] A change to the Spring Session DDL fails the blob-hash check.
- [ ] Index names follow `ux_` / `ix_`.
- [ ] Deleting a user row cascades to its tokens, history and TOTP rows (REJ-035).
