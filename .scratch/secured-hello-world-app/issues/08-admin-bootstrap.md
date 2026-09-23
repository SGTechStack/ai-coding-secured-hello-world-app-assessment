# 08: Admin bootstrap seeding (Story 12)

**What to build:** The application seeds an initial admin account
automatically on startup when none exists, using configured credentials,
so there's a way into the admin module without manual database edits — and
restarting never creates a duplicate.

**Blocked by:** 01 (Backend project scaffold + data model)

**Status:** ready-for-agent

- [ ] On startup, if no `ADMIN` user exists in the database, one is seeded
      using `app.admin.username` / `app.admin.password` (or equivalent
      configuration keys)
- [ ] The seeded admin's password is hashed identically to any other
      account (`BCryptPasswordEncoder`)
- [ ] On a subsequent startup where an `ADMIN` user already exists, no
      duplicate seed account is created
- [ ] Integration test (or startup-level test): fresh database seeds an
      admin; a database that already has an admin does not gain a second
      one on re-run
