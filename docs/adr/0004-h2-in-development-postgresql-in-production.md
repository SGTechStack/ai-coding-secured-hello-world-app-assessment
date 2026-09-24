# ADR 0004 — H2 in development, PostgreSQL in production, Flyway owns the schema

- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** persistence

## Decision

- `dev` uses in-memory H2 with `ddl-auto: update` and Flyway disabled.
- `prod` uses an external PostgreSQL from environment-supplied credentials, with
  `ddl-auto: validate` and Flyway enabled.
- The schema of record is `backend/src/main/resources/db/migration/`.

## Why

Before this, the dev datasource was the *only* datasource: in-process H2, user
`sa` with a blank password, `ddl-auto: update`, no migrations. The application as
committed could not start outside dev, which meant the transport, CORS and cookie
hardening already in place were protecting a deployment that did not exist.

`ddl-auto: validate` rather than `update` in production is the substantive half.
`update` lets the running application alter the schema — an ambient write
privilege over the data model, and a silent divergence between environments, since
the schema becomes whatever the entity classes happened to say on the day each
instance started. `validate` turns drift into a startup failure instead of a data
problem discovered later.

Keeping Flyway off in dev is a deliberate asymmetry. The dev database is created
from the entity model on every boot and discarded on shutdown, so there is no
schema history to migrate, and the migrations are written for PostgreSQL.
Running them against H2 would mean constraining them to the intersection of two
dialects — giving up partial indexes, functional unique indexes and
`timestamp with time zone` — or maintaining two sets.

## Consequences

- **Dev does not exercise the migrations.** A migration can be wrong in a way dev
  cannot reveal. Two partial mitigations: `FlywayMigrationTest` asserts the
  checked-in SQL names the same tables and columns as the entity model, which
  catches the realistic mistake of adding a field and forgetting the migration;
  and `ProductionProfileTest` pins the profile's shape. Neither validates the SQL
  against a real engine. A staging environment on PostgreSQL remains outstanding
  work and is recorded as such in the threat model rather than implied to be
  covered.
- Every production datasource value is an unresolvable placeholder, so a
  deployment that forgets one fails to start rather than falling back to
  something weaker.
- TLS to the database belongs in the JDBC URL and is engine-specific. For
  PostgreSQL, `sslmode=verify-full` with a pinned root certificate —
  `require` encrypts without checking who is on the other end, which stops
  passive sniffing and not an active man-in-the-middle, and the latter is the
  threat that matters for credentials in transit.

## Related

- Threat model TM-19, TM-19b
- ADR 0007 (append-only audit log) — depends on a database-level grant this
  migration cannot make
