# 01: Schema and JDBC sessions

**What to build:** The app stores Account credentials state and HTTP sessions in the database on every profile. Liquibase changelogs add the new Account columns (password hash, enabled, failed-attempt counter, delay expiry, must-change-password, Temporary Password expiry) and the Spring Session tables, portable across H2 and MSSQL. Spring Session JDBC replaces the Redis and in-memory sessions (ADR-DEMO-BE-0001). A single full-context HTTP integration-test harness with a controllable clock bean exists for the later tickets.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Changelogs create the new Account columns and session tables on H2 and MSSQL; cloud profiles no longer rely on Hibernate auto-DDL for them
- [ ] Sessions are persisted in the database in `local`, `test` and cloud profiles, and can be looked up by principal
- [ ] Redis session dependency and its now-unused profile wiring are removed, or documented as still needed for something else
- [ ] A full-context HTTP integration-test harness with a controllable clock runs a smoke test against real security chains, H2, Liquibase and JDBC sessions
- [ ] Backend verify passes: `./mvnw spotless:apply`, then `./mvnw verify`
