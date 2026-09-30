# ADR-0008: Hibernate `ddl-auto=update` with no migration tool, and the Postgres/MySQL portability gap

## Status

Accepted

## Context

The PRD states persistence should run on H2 to start, "schema portable to
Postgres/MySQL later." Today:

- Schema is entirely Hibernate-managed via
  `spring.jpa.hibernate.ddl-auto=update` — there is no `schema.sql`,
  Flyway, or Liquibase migration anywhere in the repo.
- `pom.xml` depends only on `com.h2database:h2` (plus the H2 console
  starter) — there is no Postgres or MySQL driver dependency at all, even
  though `application-prod.properties` already parameterizes
  `spring.datasource.driver-class-name=${DATABASE_DRIVER_CLASS_NAME}` as if
  a real driver were pluggable today. As it stands, prod would still
  connect to H2 unless a driver is added at deploy time — a real gap
  between the PRD's stated portability intent and what's actually
  buildable right now.
- One concrete portability accommodation *is* already in place: the table
  is named `users`, not `user`, because `USER` is a reserved word in ANSI
  SQL / H2 — a deliberate cross-dialect choice, not an accident. The one
  custom repository query (`PasswordResetTokenRepository`) is portable
  JPQL, not native SQL, so nothing dialect-specific has leaked into the
  application layer yet.

## Decision

Accept `ddl-auto=update` for the current H2-only, dev/demo scope of this
app. Do not treat the PRD's Postgres/MySQL-portability language as already
delivered — it is a stated future direction, not a current guarantee.

## Consequences

- `ddl-auto=update` is broadly considered unsafe for a real production
  database: it offers no repeatable, reviewable, versioned schema history,
  and `update` can make destructive-adjacent guesses on column changes.
  Introducing Flyway or Liquibase migrations must happen *before*, not
  after, any real move to Postgres/MySQL — this should be treated as a
  blocking prerequisite for that migration, not a nice-to-have.
- A Postgres or MySQL driver dependency needs to be added to `pom.xml`
  before `DATABASE_DRIVER_CLASS_NAME` in `application-prod.properties` can
  actually take effect — the parameterization exists today but currently
  has nothing else to switch to.
- Future schema/entity changes should keep avoiding native SQL and
  dialect-specific column types, consistent with the one deliberate
  accommodation already made (`users` table naming), so the eventual DB
  swap stays low-risk.
