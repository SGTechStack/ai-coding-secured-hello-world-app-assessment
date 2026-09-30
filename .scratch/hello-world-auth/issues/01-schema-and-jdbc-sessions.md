# 01: Schema and JDBC sessions

**What to build:** The app stores Account credentials state and HTTP sessions in the database on every profile. The Account entity gains the new columns (password hash, enabled, failed-attempt counter, delay expiry, must-change-password, Temporary Password expiry), and Liquibase is removed (no migration tool for now). Spring Session JDBC replaces the Redis and in-memory sessions (ADR-DEMO-BE-0001). A single full-context HTTP integration-test harness with a controllable clock bean exists for the later tickets.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] Liquibase and its properties are removed; `local` and `test` keep `ddl-auto` and Spring's embedded session-schema setup
- [ ] The Account entity maps the new columns with defaults that keep existing inserts valid (tested through the HTTP seam)
- [x] Sessions are persisted in the database in `local`, `test` and cloud profiles, and can be looked up by principal
- [x] Redis session dependency is removed; Redis stays for the cache manager and JWKS lock (`feat-redis`)
- [ ] A full-context HTTP integration-test harness with a controllable clock runs a smoke test against real security chains, H2 and JDBC sessions
- [ ] Backend verify passes: `./mvnw spotless:apply`, then `./mvnw verify`
