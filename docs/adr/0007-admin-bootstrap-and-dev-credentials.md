# ADR-0007: Admin/local-user bootstrap idempotency and committed dev credentials

## Status

Accepted

## Context

Story 12 requires an admin account to be seeded on first startup, without
duplicating it on restart. `AdminBootstrapRunner` implements this as a
check-then-create:

```java
if (userRepository.existsByRole(Role.ADMIN)) {
    return;
}
User admin = new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword), "Admin");
admin.setRole(Role.ADMIN);
userRepository.save(admin);
```

- **Idempotency is a check-then-create race, not a DB constraint.** There
  is no unique constraint on `role` in `User` (only `username`/`email` are
  unique). Two instances starting concurrently against the same database
  could both observe `existsByRole(ADMIN) == false` and each insert an
  admin row. Low likelihood given this app's expected single-instance
  startup shape, but not actually race-safe. `LocalUsersSeedRunner` has the
  identical shape (`existsByUsername` → `save`) for the same reason, and is
  effectively dev-only: with no `app.local.users` config bound in prod, it
  becomes a no-op there by absence of configuration rather than an
  explicit profile check.
- **Credential source differs by profile, deliberately.** Dev binds real
  values directly in `application-dev.properties`
  (`app.admin.password=...`, plus two seeded local users' passwords). Prod
  requires `${ADMIN_USERNAME}` / `${ADMIN_PASSWORD}` / `${ADMIN_EMAIL}`
  with **no default** — startup fails fast rather than silently falling
  back to a dev-shaped credential.
- **The dev credentials are committed, plaintext, and not gitignored.**
  `.gitignore` excludes build output, `node_modules`, and IDE files, but
  nothing matches `application-dev.properties`. The admin password and the
  two seeded local users' passwords in that file (`app.admin.password`,
  `app.local.users[0].password`, `app.local.users[1].password`) are
  therefore in version control today, in plaintext.

## Decision

Accept the check-then-create race as sufficient for this app's expected
single-instance startup, rather than adding a DB-level uniqueness
constraint on `role` that would complicate future multi-admin support.

Accept the dev credentials being committed in plaintext, on the basis that
they are throwaway, non-sensitive placeholder values scoped to local
development, and that prod deliberately has no default and cannot fall
back to them.

## Consequences

- The dev credentials in `application-dev.properties` must never be reused
  for any real deployment. If this repo is ever forked into a real
  product, rotating away from committed dev secrets should happen before
  the fork's first real deployment, not as an afterthought.
- If this app ever needs to run with multiple instances starting
  concurrently against a fresh database, the admin-bootstrap race needs to
  be closed (e.g. a unique constraint plus catching the resulting
  constraint violation, or a startup lock) before that becomes a real
  deployment shape.
- Prod's fail-fast-on-missing-env-var behavior for admin credentials must
  be preserved — do not add a fallback default "for convenience," as that
  would reopen the dev-credential-in-prod risk this design avoids.
