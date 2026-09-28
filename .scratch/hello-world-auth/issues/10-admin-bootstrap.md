# 10: Admin bootstrap seeding (Story 12)

**What to build:** An operator deploying the app for the first time gets a way into the admin module without editing the database. On startup, if no ADMIN user exists, one is seeded from configuration (`app.admin.username`, `app.admin.password`), with the password hashed identically to any other account. If an ADMIN already exists, restarting the app creates no duplicate seed account.

**Blocked by:** 02.

**Status:** ready-for-agent

- [ ] On startup with no ADMIN present, one ADMIN is seeded from `app.admin.username` / `app.admin.password`
- [ ] The seeded admin's password is BCrypt-hashed like any other account (never stored plaintext)
- [ ] On restart with an ADMIN already present, no duplicate seed account is created
- [ ] Audit/startup log line noting a seed occurred (no password)
- [ ] Integration tests: seeds when absent; idempotent when an ADMIN exists
