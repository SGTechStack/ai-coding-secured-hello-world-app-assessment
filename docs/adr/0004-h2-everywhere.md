# ADR 0004 — H2 everywhere, with Flyway owning the schema

- **Status:** Accepted
- **Date:** 2026-09-24
- **Supersedes:** the PostgreSQL-in-production decision previously recorded here
- **Context:** persistence

## Decision

H2 is the only database, in every profile.

- `dev` — in-memory H2, discarded on shutdown.
- `prod` — file-based H2, URL and credentials from the environment with no defaults.
- Both — Flyway owns the schema (`backend/src/main/resources/db/migration/`) and
  Hibernate runs `ddl-auto: validate`.

PostgreSQL, its driver and `flyway-database-postgresql` are removed. H2 support
ships inside `flyway-core`, so no engine module is needed.

## Why

Product decision: one engine, no second thing to install, configure or keep in
step.

The previous version of this ADR had PostgreSQL in production and H2 in dev, and
paid for it with an asymmetry that was the largest residual on the whole
deployment finding: **the migrations were never executed.** Dev built its schema
from the entity model with `ddl-auto: update` and had Flyway switched off, because
the migration used PostgreSQL-only syntax. So a migration could be wrong in a way
no local run and no test could reveal, and the first thing to find out would have
been a deployment.

Standardising removes that, and it is the substantive gain rather than a
consolation:

- Flyway now runs in dev and in every `@SpringBootTest` in the suite. The
  migration that would run in production is the one CI has been running all along.
- `ddl-auto: validate` applies in dev too, so adding an entity field without a
  migration fails on the next test run instead of surviving to a deployment.
- `FlywayMigrationTest` is no longer the only line of defence. It still compares
  the SQL against the entity model by reflection, which catches the same mistake
  earlier and with a clearer message, but it is now a convenience rather than a
  substitute for execution.

## What it costs

H2 is an embedded database, and using it as the production store gives up things
an external engine provides. Recorded plainly, because "one engine" is a
simplification with a bill attached:

- **No separate server.** The database lives in the application's process. It
  cannot be scaled, tuned, restarted or failed over independently.
- **No concurrent writers beyond one JVM.** A second instance cannot open the same
  file. This compounds rather than causes the single-instance limitation already
  recorded in ADR 0003 — but note it makes that limitation a hard one. Do **not**
  reach for `AUTO_SERVER=TRUE` to work around it: it would open an
  unauthenticated H2 TCP listener on the host.
- **No online backup or point-in-time recovery.** Backing up means copying the
  file, and a consistent copy of a file being written to needs the application
  stopped or a filesystem snapshot.
- **File format stability is not guaranteed across major H2 versions**, so an H2
  upgrade can mean an export-and-reimport rather than a restart.
- **Encryption at rest and file permissions become the whole story.** With an
  external engine there was a connection to protect and a `sslmode=verify-full`
  decision to get right; here there is no connection, and the risk moves to a
  file on disk holding every password hash and email address. Neither disk
  encryption nor restrictive permissions can be enforced from `application.yml`.

None of these is a defect in the code. They are the reasons a real deployment
would want PostgreSQL, and they should be revisited if this stops being an
assessment application.

## Dialect consequences

The migration was written for PostgreSQL and used two features H2 does not have.
The substitutions are not cosmetic in one case, which is why both are recorded:

| PostgreSQL | H2 | Consequence |
| --- | --- | --- |
| `CREATE UNIQUE INDEX ... ON users (lower(username))` | `username_lower varchar GENERATED ALWAYS AS (LOWER(username))` plus a unique index on it | Same guarantee. H2 rejects expression indexes, so the expression moves into a generated column. Verified by `H2SchemaDialectTest`, because a substitute control that does not fire is worse than an absent one. |
| `CREATE INDEX ... ON users (role) WHERE enabled` | `CREATE INDEX ... ON users (role, enabled)` | Performance only. A slightly larger index serving the same enabled-admin count. |

The generated columns are deliberately **not** on the `User` entity. Hibernate's
schema validation checks that every column the entities expect is present and does
not object to columns it has never heard of, so the constraint is enforced by the
database while staying invisible to the domain model.

Enums are mapped to `varchar` via
`hibernate.type.preferred_enum_jdbc_type: VARCHAR`. Left to itself, Hibernate
expects H2's native `ENUM ('ADMIN','USER')` column type, which bakes the value list
into the schema — adding a `Role` or `AuditAction` constant would then need a
migration to alter the column. `varchar` keeps the vocabulary in Java, where the
enum already lives.

## Consequences

- Every production datasource value is an unresolvable placeholder, so a
  deployment that forgets one fails to start. This matters *more* than it did with
  an external engine: the difference between a persistent deployment and one that
  silently discards every write is now `file:` versus `mem:`, a single word.
  `ProductionProfileTest` asserts the URL comes from the environment and contains
  no `mem:`.
- `app.admin.password` still has no default outside dev, so the fixed dev
  credential in ADR 0008 cannot be inherited by a deployment.
- The audit-table grant in ADR 0007 is still a deployment step. H2 supports the
  necessary `GRANT`, but its model is additive rather than per-operation
  revocable, so the application must connect as a role that was never granted
  `UPDATE` or `DELETE` on that table.

## Related

- Threat model TM-19, TM-19b
- ADR 0003 (single-instance state) — H2 makes that limitation hard rather than
  merely unaddressed
- ADR 0007 (append-only audit log)
- ADR 0008 (fixed dev admin password)
