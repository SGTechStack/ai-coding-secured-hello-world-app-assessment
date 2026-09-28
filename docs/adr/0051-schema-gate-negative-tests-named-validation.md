---
status: accepted
---

# ADR-051: `ddl-auto: validate` is demoted; the schema gate is a negative-test set with `NAMED` validation

`spring.jpa.hibernate.ddl-auto: validate` stays on, but it is treated as a typo-catcher, not as the schema's gate. The
gate is two sets of tests. One **proves that validation is running and can fail**. The other asserts, directly
against the database, every constraint validation cannot see. Hibernate's index and unique-key validation are turned
on at `NAMED`, and every index is named `ux_…` or `ix_…` so that none falls into Hibernate's `IDX` skip. A maintainer
who trusts `validate` would delete the negative tests as redundant. They are not.

## Context

- Hibernate 7.4's `AbstractSchemaValidator` checks table existence, column existence, column **type compatibility**
  and sequences. It does not check nullability, length, precision, defaults, foreign keys or check constraints. Type
  arguments are stripped before comparing. So a 48-byte `totp_key` column passes validation, which is exactly the
  truncation the 69-byte envelope width exists to prevent.
- Index and unique-key validation arrived in Hibernate 7.3 and are off by default: an unset value is `NONE`. They are
  controlled by `hibernate.tooling.schema.index_validation` and `hibernate.tooling.schema.unique_key_validation`
  (`SchemaToolingSettings`), each taking `NONE`, `NAMED` or `ALL`.
- Under `NAMED`, `validateIndexes` skips any index whose name starts with uppercase `IDX`, with a source comment
  conceding the check is weak. It walks the index's columns **positionally**, so a composite index must match the
  mapping's column order, not just its membership.
- Spring Boot's embedded-datasource fallback starts an ephemeral in-memory H2 when no datasource URL is set. Flyway
  migrates it and `validate` passes, so the whole gate can report green against a database that exists only for the
  life of the process.

## Decision

- **Settings:**

  | Setting | Value |
  | --- | --- |
  | `spring.jpa.hibernate.ddl-auto` | `validate` |
  | `hibernate.tooling.schema.index_validation` | `NAMED` |
  | `hibernate.tooling.schema.unique_key_validation` | `NAMED` |

- **Naming:** `ux_` for unique indexes, `ix_` for the rest. Case-sensitivity of an undocumented heuristic is not
  relied on, so a lowercase `idx_` prefix is not used either.
- **Negative set: validation must fail, or pass, as stated.** Ordered with the historically fragile cases first:
  1. a UUID column retyped to `VARCHAR(36)` fails (T-CFG-002);
  2. `totp_key` retyped from `VARBINARY(69)` to `BINARY(69)` fails on H2 2.x (T-CFG-003). This is held as an assertion
     about H2 2.x metadata, which the test makes true on this stack;
  3. `ux_users_username` dropped fails (T-CFG-004), proving `NAMED` is active and `ux_` escapes the skip;
  4. the columns of `ix_credential_tokens_user_id_type` reordered fails (T-CFG-005);
  5. lowercase mapped names against H2's upper-cased metadata **pass** (T-CFG-006), the classic false failure.
- **Database-invariant set, for what `validate` cannot see:** every `NOT NULL` (T-CFG-007), every foreign key
  (T-CFG-008), both `OCTET_LENGTH` width checks driven with 48- and 81-byte values and the `key_version` range
  (T-MFA-003, T-MFA-004), the token-type check (T-CRED-020), every cascade (T-ADM-021), password-history eviction
  (T-CRED-021), the tombstone blocking both identifiers (T-ADM-002), and the failure-cap columns
  `consecutive_failures_since_success` and `password_disabled_at` (T-LCK-018).
- **Every assertion runs outside the persistence context that made the change.** Inside it, the identity map can hand
  back a cascaded-away entity and the test passes for the wrong reason.
- **Precondition: the intended database was reached.** An in-memory or unset datasource URL refuses startup
  (T-CFG-031). Without that, the negative set proves validation ran, but not what it ran against.

## Considered options

- **Trust `validate` alone.** Rejected: it cannot see widths, nullability, keys or checks.
- **Leave index validation at its default.** Rejected: a dropped unique index, which is a security control here,
  would go unnoticed.
- **An `idx_` prefix.** Rejected. Hibernate's skip compares uppercase `IDX`, so lowercase would work today, but the
  whole gate would then rest on one case rule in an undocumented heuristic.
- **Replace `validate` with the tests.** Rejected. It is still the cheapest catch for a renamed or retyped column.
- **`ALL`**, the third value of the setting. Not needed: every index in the schema is explicitly named in its
  mapping, so `NAMED` already covers each one, and an unnamed index would have to be added later to make a difference.

## Consequences

- A schema change needs a matching test, or the gate silently stops covering it. That is the cost of a gate built from
  tests.
- A green build with an empty database now fails at startup instead of passing quietly.
- Spring Session's tables have no entity, so `validate` never reads them. They have their own blob-hash test
  (REJ-040).
- The exact Hibernate patch version is taken from Spring Boot's dependency coordinates and is confirmed by the first
  build's dependency tree.

## Sources

- Hibernate ORM 7.4 source, `org.hibernate.tool.schema.internal.AbstractSchemaValidator` (`validateIndexes`, the
  `IDX` skip, positional comparison) and `ConstraintValidationType` (values, `NONE` when unset); Javadoc,
  `org.hibernate.cfg.SchemaToolingSettings` (`INDEX_VALIDATION`, `UNIQUE_KEY_VALIDATION`).
- Hibernate issues HHH-5247 (validator scope) and HHH-9835 (UUID on H2 false failures).
- Spring Boot 4.1 dependency versions (Hibernate coordinates); Spring Boot reference, embedded database support.
