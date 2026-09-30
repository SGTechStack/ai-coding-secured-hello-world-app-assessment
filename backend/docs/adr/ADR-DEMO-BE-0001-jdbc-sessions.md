---
status: proposed
---

# Store HTTP sessions in the database (Spring Session JDBC)

Sessions are stored with Spring Session JDBC in every profile, replacing the template's Redis-backed sessions in dev/qa/prod and the plain in-memory Tomcat sessions in local/test. Invalidating all sessions of an Account (password change, admin reset, disable) needs a principal-indexed store, and one JDBC store behaves identically in every profile without a separate Redis dependency.

## Considered Options

- **Keep Redis in cloud, in-memory locally** — rejected: local and cloud would behave differently for "invalidate all sessions of an Account", and local has no principal index.

## Consequences

- Remove `spring-boot-starter-session-data-redis`, `feat-redis` session use and `LocalRedisExclusionConfiguration` once no other Redis use remains.
- Session tables need Liquibase changelogs for H2 and MSSQL.
