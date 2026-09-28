---
status: accepted
---

# ADR-042: Two roles, one per user: YAML is the truth, and a seeded read-only `roles` table enforces the foreign key

The application has exactly two roles, `USER` and `ADMIN`, and each user holds exactly one. The role set is declared
in configuration (`app.security.roles`). A `roles` lookup table holds the same two rows, seeded once by a versioned
migration and never written at runtime, and `users.role` is a foreign key to it. A refresh-phase validator refuses to
start if the configuration and the table disagree. There are no privileges, no role hierarchy and no role
synchroniser. The PRD models the role as an enum column, and a role hierarchy is the framework reflex, so both are
the likely "simplifications". Each is rejected below.

## Context

- PRD Story 10 lets an admin change another user's role at any time. The PRD's data model gives the role as a plain
  `USER`/`ADMIN` value on the user.
- The governing standard's Decision Logic says the role definition (configuration file or source) controls which
  roles exist, and that roles must not be created, modified or deleted through API endpoints. Its §4 Enforced
  Constraint says user account records **and role definitions** are persisted in a relational database. Its §5 Role
  and Authorization Tests require duplicate role definitions to fail fast at startup.
- The prescribed recipes carry a privilege layer, a `roles`↔`privileges` join table, a role hierarchy and a startup
  synchroniser (`SsoUserStartupConfiguration`). The synchroniser's `syncDBUsersBasedOnDefinedUsers()` calls
  `userRepository.deleteAll()` and re-seeds from properties, gated by a flag rather than a profile, so one property
  in the wrong file wipes the user table. It also hooks `ApplicationReadyEvent`, which Spring Boot documents as firing
  after the runners, later than the standard's "before serving traffic".
- The admin authorization rules are built from a hand-constructed `AuthorityAuthorizationManager` (ADR-026). In
  Spring Security 7.1.x that class defaults to `NullRoleHierarchy`, and it does not pick up a `RoleHierarchy` bean
  the way the DSL's `hasRole()` does; a hierarchy reaches it only through `setRoleHierarchy`.

## Decision

- **Two roles, one per user.** `USER` and `ADMIN`. Granted authorities are `ROLE_USER` and `ROLE_ADMIN`.
- **Configuration is the source of truth.** `app.security.roles` declares the set.
- **The `roles` table enforces it.** `name VARCHAR(20)` is the primary key, so `users.role` carries the readable value
  and rendering a role needs no join. The two rows are plain `INSERT`s in the first versioned migration. Flyway's
  run-once guarantee is the idempotency; a repeatable migration re-runs on checksum change, not on data drift, so it
  would be no more self-healing.
- **The foreign key is the point.** Without it the table is decorative. With it, the §4 constraint is met literally:
  a role value that is not a defined role cannot be stored.
- **No write path to role definitions exists.** No endpoint, repository method or runner writes `roles`. The
  standard's immutability requirement is met because there is nothing to guard, not by a guard. (The corpus's
  `ImmutableSecurityHandler` is inert here: its `SecurityResource` type is never defined, and
  `@RepositoryEventHandler` fires only for Spring Data REST resources, which this application does not export.)
- **One refresh-phase validator** refuses startup when:
  - a role name is duplicated in `app.security.roles` (T-ADM-018), rather than collapsing it silently;
  - the configuration and the table disagree in either direction (T-ADM-011).

  It runs in the same refresh-phase bean as the bootstrap validation (ADR-047), so a mismatch aborts before the web
  server binds its port.
- **No hierarchy, and that is asserted.** The context contains no `RoleHierarchy` bean (T-ADM-017). Because the
  hand-built admin rules would ignore such a bean, a hierarchy added later would be silently half-applied. The test
  makes it fail loudly instead.
- **Restart is the only reload path.** The configuration binds once at refresh.

## Considered options

- **An enum column, as in the PRD.** Rejected. It does not persist the role *definitions* anywhere, which the §4
  Enforced Constraint requires, and it leaves the database accepting a value the configuration does not define.
  The deviation from the PRD's data model is registered (R-DATA-003).
- **A lookup table without a foreign key.** Rejected. A reviewer spots a decorative table immediately, and it enforces
  nothing.
- **Privileges, a join table and a hierarchy, as in the recipes.** Rejected. With two roles and one role per user
  there is nothing for them to express, and the hierarchy would be ignored by the admin rules anyway (see Context).
- **The recipes' startup synchroniser.** Rejected. It can delete every user, it is gated by a flag rather than a
  profile, and it runs too late.
- **Configuration and table both as sources, reconciled at runtime.** Rejected. Two writable sources drift. One source
  plus a startup comparison makes drift a startup failure.

## Consequences

- Adding a role means a new migration, a configuration change and a restart, together. The validator fails the
  restart if one of the three is missed.
- A future hierarchy must call `setRoleHierarchy` on every hand-built authorization manager, or the admin rules stop
  honouring it. T-ADM-017 fails first, which forces that decision into the open.
- `roles` is the only seeded data in the system. There are no development seed accounts of any kind, which satisfies
  the standard's §2 Happy Path step 3 by construction.
- PRD Story 10's role change is permitted; the reading of the standard's §3.5 role-assignment clause that allows it
  is recorded in the register (REJ-025).

## Sources

- PRD Story 10; PRD data model.
- Standalone User Access Control Application Standard §2 Happy Path steps 1–3 and Decision Logic; §3.5 Data Access
  Control; §4 Data Persistence (Enforced Constraint); §5 Role and Authorization Tests; §6 Manual vs Self-Healing.
- Spring Boot 4.1 reference, "SpringApplication", Application Events and Listeners (event order: `ApplicationReadyEvent`
  after runners).
- Spring Security 7.1.x `AuthorityAuthorizationManager` (`setRoleHierarchy`, default `NullRoleHierarchy`).
