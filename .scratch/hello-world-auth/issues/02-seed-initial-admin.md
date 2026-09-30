# 02: Seed the initial admin

**What to build:** On first startup an operator gets an `ADMIN` Account without touching the database. Credentials come from configuration: AWS Secrets Manager in cloud profiles, a `local`-only property locally. The password is hashed like any other. A restart never creates a duplicate, and cloud startup fails if no admin exists and no password is configured. There is no default password anywhere.

**Blocked by:** 01 Schema and JDBC sessions

**Status:** ready-for-agent

- [ ] With no `ADMIN` Account, startup creates one with a hashed password, enabled, and no forced password change
- [ ] With an `ADMIN` Account present, restart creates no duplicate
- [ ] Cloud profile with no admin and no configured password fails startup with a clear message
- [ ] No default or hard-coded password exists in code or committed config; secrets stay out of the repo
- [ ] The seed's password is never logged
- [ ] Integration tests cover seeds-once, no-duplicate and fails-without-password
