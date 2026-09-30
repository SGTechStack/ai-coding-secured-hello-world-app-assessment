---
status: proposed
---

# Store HTTP sessions in the database (Spring Session JDBC)

Sessions are stored with Spring Session JDBC in every profile, replacing the template's Redis-backed sessions in dev/qa/prod and the plain in-memory Tomcat sessions in local/test. Invalidating all sessions of an Account (password change, admin reset, disable) needs a principal-indexed store, and one JDBC store behaves identically in every profile without a separate Redis dependency.

## Considered Options

- **Keep Redis in cloud, in-memory locally** — rejected: local and cloud would behave differently for "invalidate all sessions of an Account", and local has no principal index.

## Consequences

- Only sessions leave Redis. `feat-redis` still backs the cache manager and the JWKS rotation lock, so the Redis data starter, `feat-redis` and `LocalRedisExclusionConfiguration` stay; the Redis session starter is replaced by the JDBC one.
- The repo has no migration tool. `local` and `test` get the session tables from Spring's embedded initializer; the deployed MSSQL schema, including the session tables, is managed outside this repo.
