# 10: Admin bootstrap

**What to build:** On first start, an operator gets a working admin account created from configuration, so the admin module is reachable without editing the database. It is never duplicated. The app refuses to start when it would have no admin at all. No default admin credentials exist outside `dev` and `test`. See spec §Backend modules › Admin bootstrap.

**Blocked by:** 07

**Status:** ready-for-agent

- [ ] An application runner starts after Flyway. If no `ADMIN` exists, it creates one from `app.admin.username`, `app.admin.email` and `app.admin.password`, using the same normalisation, password policy and encoder as registration.
- [ ] If an admin already exists, it does nothing, even when the properties are set. A second context on the same database doesn't create a duplicate.
- [ ] With no admin, missing properties or a password that fails the policy stop startup with a clear message that never contains the password.
- [ ] `dev` and `test` provide documented dev-only values (username `admin`, a ≥12-character non-common password). The base and `prod` configs provide none and read them from the environment. The README lists the dev admin next to `johndoe`.
- [ ] An `ADMIN_BOOTSTRAPPED` audit event is emitted.
- [ ] Tests: with no admin and the properties set, the admin can log in and `/me` shows `role: ADMIN`. A restart doesn't duplicate it. Missing properties fail the context start. A weak password fails the context start.
