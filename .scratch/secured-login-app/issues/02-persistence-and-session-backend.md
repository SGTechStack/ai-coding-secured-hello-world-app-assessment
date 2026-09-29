# 02 — Persistence and session backend

Type: grilling
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

## Question

What relational database and what session-persistence backend does this application use, and how is the datasource configured?

Answer `Q6` (relational database) and `Q7` (session persistence backend and datasource configuration) of the standard's question set.

Constraints already on the table:

- PRD: Spring Data JPA over **H2 in the dev profile**, with a schema "portable to Postgres/MySQL later" (`prd/assessment-prd.md:11`). Portability is a stated requirement, so column types and DDL generation strategy matter.
- PRD: primary auth is a **server-side session via Spring Session** and a secure HttpOnly cookie (`prd/assessment-prd.md:13`).
- Sessions must be invalidated on logout (Story 4) and, for *all* of a user's sessions, on password reset (Story 7). Whether the session store supports find-by-principal is therefore not a detail — it decides whether Story 7's acceptance criterion is implementable at all.

Decide: H2-only or H2-dev-plus-a-real-profile; Spring Session JDBC versus in-memory versus another backend; schema management (JPA `ddl-auto` versus Flyway/Liquibase migrations) given the portability requirement; and whether the session store shares the application datasource.

This ticket gates lockout persistence (07), session policy (13), the test plan (14) and the tech baseline (15).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Half of `Q7` is already decided: topology is **single instance**, but sessions are persisted to a **JDBC** store anyway, because the standard's Required Runtime Configuration mandates a session-state persistence backend regardless of topology (`Standalone_User_Access_Control_Application_Standard.md:604`) and because it is what makes the PRD's "reset invalidates all sessions" criterion (`prd/assessment-prd.md:79`) assertable. Record that rather than re-arguing it; what remains open here is `Q6` (which relational database) and the concrete datasource/Spring Session JDBC configuration.

## Answer

Resolved by grilling, two rounds, all twelve questions accepted as recommended. `Q6` and the open half of `Q7` are answered below; the closed half (JDBC session store despite single-instance topology) was pre-decided by [01](01-context-topology-and-api-surface.md) and is restated, not re-argued.

### `Q6` — Relational database

- **PostgreSQL is the declared target database. H2 is confined to the `dev` and `test` profiles.**
- `Q6` marks H2 *"(dev/test only)"* and recommends PostgreSQL for production (`Standalone_User_Access_Control_Application_Standard_Questions.md:151-163`), so ticking H2 alone would answer the question in a way the standard explicitly disallows. Answering PostgreSQL honours both the standard and the PRD's H2 dev profile (`prd/assessment-prd.md:12`) without inventing hosting infrastructure the PRD excludes: it costs one `postgresql` runtime dependency and one profile stanza.
- This converts the PRD's "schema portable to Postgres/MySQL later" from an aspiration into a **claim this plan must discharge** — see [How the portability claim is discharged](#how-the-portability-claim-is-discharged). MySQL is *not* a declared target; portability to it is a property of the DDL strategy, not a tested claim.

### `Q7` — Session persistence backend and datasource configuration

- **Spring Session JDBC with the shared application datasource.** `Q7`'s recommended option (`Questions.md:171`); its separate-datasource alternative is reserved for "very high write loads", and a dedicated session database would contradict the single-instance, no-infrastructure posture 01 fixed.
- Restated from 01, not re-decided: sessions persist to JDBC **regardless of topology**, because `Standalone_User_Access_Control_Application_Standard.md:604` mandates a session-state persistence backend and because only a `FindByIndexNameSessionRepository` makes Story 7's "invalidate all sessions" (`prd/assessment-prd.md:79`) implementable. Confirmed downstream: the reset recipe injects exactly that type (`Standalone_Privileged_User_Administration_and_Password_Reset.md:205,381`). The in-memory `MapSessionRepository` does not implement it, so `Q7`'s "In-memory" option was never available here.

### Schema management

- **Liquibase, with `spring.jpa.hibernate.ddl-auto: validate`.**
- Chosen over Flyway because Liquibase is the tool the **binding question set itself names**: `Q3`'s admin-bootstrap options are written as Liquibase changesets (`Questions.md:65,69,74,83`), which [16](16-admin-bootstrap-and-seeding.md) inherits. Its DB-agnostic changelog format *is* the portability mechanism; Flyway's SQL-first model would mean maintaining per-vendor scripts, the exact drift the `Q6` answer is meant to prevent.
- `ddl-auto: update` is ruled out: it lets Hibernate silently paper over the vendor differences the PRD asks us to prove we have handled.
- **Supporting evidence, weighed honestly:** `Common_Automatic_Database_Role_Synchronization_and_Deleted_Role_Backups.md:275` states "Do not rely on Hibernate's `ddl-auto` in production. Always manage the creation of the `roles` and `deleted_roles` tables through versioned database migration scripts." That recipe is **out of scope** per the map, so this is evidence of intent, not a binding clause. The decision rests on `Q3` and on portability, and would hold without it.

### H2 shape per profile

- **`dev`: file-based** (`jdbc:h2:file:./data/app`). **`test`: in-memory.** **Both with `MODE=PostgreSQL`.**
- The split is not cosmetic. Under an in-memory dev database, Story 12's second acceptance criterion — *if a privileged user already exists, do not create a duplicate* — **has no observable branch**, because every boot starts empty. File-based dev exercises it; in-memory test keeps the suite hermetic.
- `MODE=PostgreSQL` makes H2 fail on the vendor differences PostgreSQL would fail on, so portability drift surfaces in dev rather than in CI.

### Column types

- **Primary keys: `UUID`, application-generated**, declared in Liquibase as `java.sql.Types.UUID` so Liquibase resolves the vendor type rather than the changelog hardcoding one. The PRD hedges "UUID/long" (`prd/assessment-prd.md:131`); UUID is chosen because the recipes being copied already assume it (`Standalone_Privileged_User_Administration_and_Password_Reset.md:395`), so `long` would mean rewriting every recipe signature, and because it avoids a guessable enumeration sequence in the `/users/{userId}` paths 01 made resource-oriented. UUID is also the most vendor-divergent type there is (native `uuid` in PostgreSQL, `UUID` in H2, `BINARY(16)`/`CHAR(36)` in MySQL) — which is precisely why it is declared abstractly rather than literally.
- **Timestamps: `TIMESTAMP WITH TIME ZONE`, mapped to `java.time.Instant`, everything stored UTC.** Applies to `users.created_at`, `users.locked_until`, `password_reset_tokens.expires_at`, `password_reset_tokens.used_at` and anything 04 and 10 add.
- Rationale, and a collision avoided: [05](05-logging-standards-applicability.md) pinned *logs* to Singapore Time UTC+8 via the `TZ` environment variable (`Structured_Logging_Application_Standard.md:167`). With zone-naive columns, that same JVM default would silently decide what a stored timestamp means. Zone-aware columns confine the UTC+8 pin to log rendering, where 05 put it, and make lockout-expiry and token-expiry comparisons zone-free.

### IP-throttle counter store

[07](07-lockout-and-ip-throttling.md) is blocked on this ticket because, in its own words, "the counter store and the session/datasource decision are the same decision for the IP limiter." Settled here:

- **Account-lockout state is durable and transactional**, in the `users` columns the PRD's data model already fixes (`failed_login_attempts`, `locked_until`).
- **IP-throttle state is in-memory** (Caffeine or Bucket4j — library choice is 07's). A JDBC write per login *attempt* is a self-inflicted write amplification on exactly the endpoint under attack.
- **Two consequences 07 and 14 inherit, stated rather than discovered later:** throttle state does **not** survive a restart, and it is **not** multi-instance-safe. Both are honest under the single-instance topology 01 fixed; if topology is ever revisited, this is one of the things that breaks.
- 07 still owns `Q16`'s threshold, window, response code and filter-chain position.

### Who owns the `SPRING_SESSION` DDL

- **Liquibase owns every table, including Spring Session's. `spring.session.jdbc.initialize-schema: never`.** The session changesets `sqlFile` the **packaged** `org/springframework/session/jdbc/schema-h2.sql` and `schema-postgresql.sql` from the classpath, under `dbms` guards.
- **The recipe's literal configuration cannot be copied.** `Standalone_Privileged_User_Administration_and_Password_Reset.md:378` sets `initialize-schema: always` (its own comment already hedges: *"use 'never' and apply the schema manually in production"*). `always` re-runs plain `CREATE TABLE` on every boot — harmless against a fresh in-memory H2, and **broken on the second boot of the file-based dev H2** chosen above. This is a concrete defect in copying the recipe as written, not a stylistic preference.
- Referencing the packaged script rather than hand-copying it matters for a specific reason: that script carries the `PRINCIPAL_NAME` index that find-by-principal relies on, so a hand-copy that dropped it would break Story 7 in a way `ddl-auto: validate` does not catch. Liquibase checksums the file, so a Spring Session upgrade that changes the DDL **fails loudly at startup** instead of diverging silently.
- The split-ownership alternative (`initialize-schema: embedded`, Boot's default) was rejected: it covers H2 and does nothing for the PostgreSQL target, leaving that path with no schema owner at all.

### Framework-internal `@Scheduled` task — a correction to an existing ruling

Choosing Spring Session JDBC ships a **scheduled expired-session cleanup**. Boot exposes it as `spring.session.jdbc.cleanup-cron` (default: every minute), a property that exists only because the repository schedules that sweep.

**Evidential status:** this repo has no `pom.xml` yet, so this rests on the property's existence and Spring Session's documented behaviour, **not on inspecting the jar**. It must be confirmed at build time.

It matters because [15](15-tech-baseline-and-module-structure.md) records, from 05, that `@Scheduled` requires no dependency at all, so anything scheduled "would activate all 1,254 lines" of `Recipes/Logging_Batch_And_Scheduled_Jobs.md` — and that this is "a scope change, not an implementation detail". 05 ruled that recipe **confirmed vacuous**.

- **Decision: keep the cleanup task, and narrow 05's ruling rather than reverse it.** The recipe's subject is logging emitted *from* batch and scheduled jobs we author; we neither write this sweep nor log from it. The revised wording is: **vacuous for authored jobs; one framework-internal scheduled task exists and is not in the recipe's scope.**
- Disabling cleanup (`cleanup-cron: "-"`) to preserve literal vacuity was rejected — it trades a documentation problem for an unbounded `SPRING_SESSION` table, which under the file-based dev H2 above now actually persists.
- **05 and the map's binding-standards note are amended accordingly.**

### How the portability claim is discharged

- **A Testcontainers PostgreSQL run of the security-critical integration suite**, recorded here as a constraint [14](14-test-and-validation-plan.md) must satisfy — not as a test design, which remains 14's.
- **The tension, named so 14 need not re-litigate it:** the PRD excludes containerization. That exclusion is read here as covering **deployment packaging**, not test infrastructure; Docker-for-tests is materially different from shipping a container image. This is a judgement call, recorded as one.
- The weaker alternative — H2 `MODE=PostgreSQL` plus DB-agnostic Liquibase, portability argued from construction and never executed against real PostgreSQL — was rejected because the `Q6` and Liquibase answers were both taken *in order to* make portability testable. Stopping there pays the cost and declines the benefit.
- **A related recommendation handed to 14, not decided here:** a fixed `Clock` bean. 14's brief asks how lockout-cooldown and token-expiry are tested "without sleeping"; the UTC `Instant` decision above makes an injectable `Clock` the natural answer.

### Changelog organisation and table inventory

- **`db/changelog/db.changelog-master.yaml` includes one versioned file per logical change** (`001-users.yaml`, `002-roles.yaml`, …), **each downstream ticket owning its own file.** `/do-work` generates these, so the convention is locked here or every slice invents its own. It is the only layout that is parallel-safe: 03, 04, 10 and 16 each add schema, and a single growing changelog guarantees merge conflicts between them.
- **Table inventory — names and ownership only; columns belong to the owning ticket:**

| Table | Owning ticket |
| --- | --- |
| `users` | PRD data model; 03 (`role` scalar), 04, 07, 10 extend it |
| `roles` | [03](03-role-model-reconciliation.md) — read-only, seeded from config |
| `password_reset_tokens` | PRD data model; [11](11-password-reset-flow.md) |
| `password_history` | [04](04-password-policy-and-history.md) |
| tombstone store | [10](10-account-lifecycle-and-delete-semantics.md) — **slot reserved, shape open** (`deleted_users` table versus columns on `users`) |
| `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` | this ticket, via the packaged script |

### Datasource configuration — where it is settled

The standard's Required Runtime Configuration lists "Datasource configuration (database connection, connection pool, credentials)" (`Standalone_User_Access_Control_Application_Standard.md:632`), and [15](15-tech-baseline-and-module-structure.md)'s brief already claims configuration and secrets. Split on the seam rather than duplicated:

- **This ticket fixes the property shape**: `spring.datasource.*` per profile, the two profile stanzas (`dev` file-H2, `prod` PostgreSQL), HikariCP pool sizing, `spring.jpa.hibernate.ddl-auto: validate`, `spring.session.jdbc.initialize-schema: never`, `spring.session.jdbc.cleanup-cron`.
- **15 owns where the values come from** — the secrets mechanism, including the PostgreSQL password alongside `app.admin.password`.

### Dependencies this ticket adds

`spring-boot-starter-data-jpa`, `spring-session-jdbc`, `liquibase-core`, `com.h2database:h2`, `org.postgresql:postgresql`, an in-memory rate-limit cache (07 picks the library), and — for 14's portability run — Testcontainers with its PostgreSQL module. Version pins are [15](15-tech-baseline-and-module-structure.md)'s, constrained by the Spring Boot 4.0+ floor 05 established.

### Tickets amended by this resolution

- **[05](05-logging-standards-applicability.md)** — batch-recipe vacuity narrowed (see above).
- **[07](07-lockout-and-ip-throttling.md)** — counter store settled; unblocked.
- **[13](13-session-policy.md)** — session store and datasource settled; unblocked.
- **[14](14-test-and-validation-plan.md)** — Testcontainers PostgreSQL constraint, fixed `Clock` recommendation, session-DDL checksum behaviour.
- **[15](15-tech-baseline-and-module-structure.md)** — dependency additions, datasource property shape, secrets seam; unblocked (01 and 02 both resolved).
- **[16](16-admin-bootstrap-and-seeding.md)** — Liquibase changeset is the prescribed home for the seeded admin; file-based dev H2 makes Story 12's idempotence observable.
- **[04](04-password-policy-and-history.md)**, **[10](10-account-lifecycle-and-delete-semantics.md)** — changelog file convention and reserved table slots.
