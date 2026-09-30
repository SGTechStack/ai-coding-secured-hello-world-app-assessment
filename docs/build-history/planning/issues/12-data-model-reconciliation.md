# 12 — Reconcile the data model and the Flyway migration set

Type: grilling
Status: resolved
Blocked by: 07, 08, 09, 10, 11, 23

## Question

What is the final schema, once every decision made upstream on this map is folded in?

This is a consolidation ticket by design: it runs late, and its job is to make the union of earlier
decisions coherent rather than to invent anything.

## Starting point and known deltas

The PRD gives two tables: `users` and `password_reset_tokens`. Decisions elsewhere on this map add
to that. Expected deltas, to be confirmed against how those tickets actually resolved:

- **`users.id` becomes a UUID.** The logging standard mandates a UUID `user.id` and bans cleartext
  usernames and emails in logs, which settles the PRD's "UUID/long" choice. Decide the JPA mapping
  and the H2 column type, keeping it portable.
- **Password history** — a new table (standard default: 3 retained). Decide whether history rows are
  a separate table or a bounded column, and the eviction mechanism.
- **Email verification tokens** — either a new table or a discriminator on `password_reset_tokens`,
  per "Decide the credential flows".
- **Soft-delete tombstones** — per "Decide the admin module". Decide whether this is a flag on
  `users` or a separate tombstone table, and how uniqueness constraints on username and email
  interact with soft-deleted rows. This is the subtle one: a `UNIQUE` constraint on `username` plus
  soft-deleted rows means the tombstone *automatically* blocks reuse, which is the behaviour the
  standard wants — but it also means a partial unique index is wrong here. Get it right.
- **Session tables** — Spring Session JDBC's own schema. Decide whether Flyway owns it (copied from
  the Spring Session distribution and version-pinned) or Spring Session initialises it. Owning it in
  Flyway is more honest about what is in the database; it also means tracking upstream changes.
- **Verification/enabled state** — whatever "unverified vs disabled" resolved to.
- **Last-activity tracking** — only if something still needs it once hygiene jobs are out of scope.
  If nothing does, leave it out rather than adding a column nobody reads.
- **`failed_login_attempts` and `locked_until`** — confirm against the lockout decision, including
  whether a rolling window needs a "first failure at" timestamp the PRD does not have.

## What to decide

- Final DDL for every table, written vendor-neutral so it ports to Postgres and MySQL: no H2-specific
  types or functions, explicit constraint names, portable timestamp type. Pick `TIMESTAMP` semantics
  deliberately — with or without time zone — because that is the single most common portability break.
- Index set, driven by actual query paths: login by username, reset by token hash, admin list.
- Migration file layout and naming, and the rule that migrations are append-only once written.
- Whether `ddl-auto: validate` actually passes against the hand-written DDL, and what to do about
  the entity/schema mismatches that always surface (Hibernate's expectations vs hand-written types).
- Seed and test data strategy, using synthetic values only, per the standard's test data guidance.
- JPA mapping decisions that bite later: enum persistence as `STRING` not `ORDINAL`, optimistic
  locking on `users` if concurrent admin edits matter, and lazy-loading choices.

## Done when

Every table and column is specified with portable types, the tombstone-versus-uniqueness interaction
is resolved, session-table ownership is decided, and the migration set is listed in order.

## Inherited from ticket 11 — schema consequences, and one portability trap

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
settled the semantics this ticket was waiting on. Deltas:

- **A `roles` lookup table exists after all, and it is load-bearing.** Two rows (`USER`, `ADMIN`) seeded
  idempotently by Flyway, with `users.role` carrying a **foreign key** to it. The FK is the whole point — YAML
  remains the source of truth and a startup validator fails if the two disagree, but the table makes §4's
  Enforced Constraint ("role definitions are persisted in a relational database") literally true instead of
  argued around. Without the FK the table is decorative and a reviewer says so. No join table, no privileges
  table, no `deleted_roles`. *Consolidated into the register (ticket 33): R-DATA-003. Amend the table by ID, not this list.*
- **Tombstone shape:** `uuid`, `username` (plaintext), `emailHmac`, `deletedAt`, `deletedById`. Retention
  indefinite, so no purge column and no purge job. The storage-shape choice this ticket reserved — flag on
  `users` versus separate table — is still yours, but note that a separate table is now the simpler fit: the
  tombstone's email column holds an HMAC while the live table's holds an address, so they are not the same type
  of data and a shared column would have to hold both.
- **The tombstone-versus-uniqueness interaction, with a trap.** Both username **and** email must be blocked
  against tombstones, so the reuse check spans two tables and cannot be expressed as a single unique constraint.
  And the case-insensitivity half **cannot** be done with an index on this stack: H2 does not support expression
  indexes, so `CREATE UNIQUE INDEX ... ON users(LOWER(email))` fails, and H2's `VARCHAR_IGNORECASE` is
  proprietary and would silently change behaviour on Postgres — a direct violation of this ticket's
  vendor-neutral rule. **The portable answer is a stored canonical value with the constraint on that**,
  canonicalised in application code (NFC, trim, lowercase) by the single implementation ticket 11 mandates.
  Decide whether the canonical form is a separate column or replaces the stored value.
- **New columns:** `credentialIssuedAt` on `users`, set whenever a forced-change credential is issued
  (bootstrap seed, admin create, admin reset, re-enable) and read on the login path for the lazy 30-day expiry.
  This is what lets the 30-day deadline exist with no scheduled job.
- **The `uuid` column is the public identifier**, confirming ticket 03's cross-reference. Ticket 11 fixed the
  wire contract to expose `uuid` and never the primary key, so `/api/admin/users/{uuid}` is the path shape and
  the audit trail and the API share one identifier vocabulary.
- **Username format** `[a-z0-9._-]{3,32}`, lowercase, `@` rejected — a constraint, not just validation, since
  it is what keeps the plaintext tombstone username from being a readable address.
- **No unlock-reason column.** Q15's "mandatory reason" lives in the audit event as a closed enum, so this
  ticket's table set is unchanged by it. Do not add one.
- **Index driver:** ticket 11's admin list is paginated with a hard cap of 100 and a sort allowlist of
  `username`, `createdAt`, `enabled`, `role` — that allowlist is the index set the list query actually needs.
- **Optimistic locking question, answered in part:** concurrent admin edits *do* matter, but ticket 11 solved
  its own case with a pessimistic row lock over the ADMIN set rather than a version column. Decide whether
  anything else needs `@Version`, knowing the security-critical path does not rely on it.

---

## Amendment from ticket 09 (lockout and dual rate limiting)

**Lockout columns on the user table — three, not the corpus's two, and no boolean.**

| Column | Type | Notes |
|---|---|---|
| `failed_login_attempts` | `int not null default 0` | PRD's data model |
| `locked_until` | `timestamp null` | PRD's data model; `null` means not locked |
| `last_failed_at` | `timestamp null` | **new** — the 20-minute observation window is unimplementable without it |

**There is no `account_non_locked` column.** `UserDetails.isAccountNonLocked()` is derived as
`locked_until == null || locked_until <= now`. Two stored sources of truth for one fact would drift, and
derivation is what makes the 20-minute automatic lift free — no scheduler, no cron, no hygiene job. This
deviates from the admin recipe's `UserAccount` entity, which stores `accountNonLocked` as a `Boolean`; note
that the corpus prescribes **two mutually incompatible column sets in the same folder** (`failedAttempts` +
`lockedUntil` in the login recipe, `failedLoginAttempts` + `accountNonLocked` in the admin recipe), so the
naming follows the PRD, which at least agrees with itself. Ticket 11's generic-update lock guard is amended
accordingly.

**Two further constraints on the migration set.**

- **H2 `LOCK_TIMEOUT` is pinned on the JDBC URL** (`;LOCK_TIMEOUT=…`) rather than inherited. The default is
  **1000 ms**, low enough that contention on a single user row produces exceptions rather than waits — and
  contention on one user row is exactly what an attacker manufactures. `LOCK_MODE` defaults to 3 (row-level
  locking for writes), which is what makes the `SELECT … FOR UPDATE` counter path work.
- **Lock ordering is a schema-adjacent convention: user rows before session rows, always.** Three tickets now
  take row locks (11, 09, and 08's lockout-triggered session invalidation).

**Session table sizing note.** Ticket 09 raised `/csrf` to 30/min per source, so live anonymous
`SPRING_SESSION` rows are roughly **450 per source** against the 15-minute idle window rather than ticket 08's
~150. The JDBC cleanup cron deletes only already-expired rows, so this is a live-row ceiling set by the
limiter, not by the cron.

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**Three schema requirements for the MFA tables, one of which corrects a figure ticket 19 got wrong.**

**1. The ciphertext column is 69 bytes, not the 48 ticket 19 states.** `AesGcmBytesEncryptor` uses a **16-byte IV**,
not the 12 ticket 19 assumed — verified in its own javadoc, which notes the deviation from NIST SP 800-38D's
recommended 96-bit IV and keeps 16 for consistency with the rest of the crypto module. With ticket 23's context
prefix inside the plaintext:

```
┌────────────┬──────────────────────────────────────────────┬───────────┐
│  IV        │  AES-256-GCM ciphertext of the plaintext      │  tag      │
│  16 bytes  │  37 bytes                                     │  16 bytes │
└────────────┴──────────────────────────────────────────────┴───────────┘
                          plaintext, 37 bytes:
              ┌──────────────┬──────────────┬──────────────┐
              │ userUuid     │ keyVersion   │ secret       │
              │ 16 raw bytes │ 1 byte       │ 20 raw bytes │
              └──────────────┴──────────────┴──────────────┘
                     total stored: 16 + 37 + 16 = 69 bytes
```

Three widths must be pinned in the migration or this recurs: the UUID as **16 raw bytes**, not its 36-character
string form; the secret as **20 raw bytes**, not its 32-character Base32 form; the version fixed at one byte. Getting
either of the first two wrong yields 81 or 105. The column is `byte[]`/VARBINARY per STD §4.2 and Recipe 5, so base64
character counts do not apply — nobody should size a `VARCHAR(92)`. **Both tables must size identically**, because
Recipe 10 copies the blob verbatim with no re-encryption. Recipe 5 declares `@Column(name = "TOTP_KEY")` with no
length, which on H2 yields `varbinary(255)` — adequate but accidental; declare it. *Consolidated into the ADR routing (ticket 34): ADR-028 (attached amendment). Amend by ID, not this list.*

**2. `PENDING_TOTP` gains `expires_at`.** STD §4.2's Pending TOTP table has three columns and no clock, so a
provisioned-but-unconfirmed secret lives forever. Fifteen-minute TTL, enforced at confirmation with
`422 FACTOR_ENROLMENT_REQUIRED`. Disposal is deliberate and needs recording so its absence is not read as an
oversight: an expired row is overwritten by the next provisioning and otherwise lingers as encrypted dead weight,
which is acceptable with no scheduler, consistent with the account-hygiene jobs the map already defers. *Consolidated into the register (ticket 33): R-MFA-004. Amend the table by ID, not this list.*

**3. `TOTP_USER_DETAILS` needs four columns the recipes mutate or omit.** `failed_attempts`, `last_failed_at` and
`locked_until` are **absent from Recipe 5's entity** yet Recipe 12 calls getters and setters for all three, so it
cannot compile as printed; the SQL names exist only on `PIN_USER_DETAILS` and we borrow them. `lockedAt` is renamed
**`locked_until`** to match ticket 09's derived predicate. And ticket 23 adds **`cumulative_failures`**, monotonic,
for its tier-2 cap — the corpus has no equivalent. Plus a **key-version** column, which STD §4.2 lacks and without
which STD L253's yearly-rotation MUST is unimplementable.

**4. Naming, confirming ticket 22's reading:** take `TOTP_USER_DETAILS` and `PENDING_TOTP`, drop the `OTP`-prefixed
class names, drop `ssoId`. Also drop Lombok `@Builder` on these entities or add `@Builder.Default`:
`lastUsedCounter`'s `-1L` field initialiser is **silently defeated by `@Builder`**, yielding `0L`, which breaks replay
rejection on the first verification after enrolment.

**5. `PIN_USER_DETAILS` is not created at all.** PIN is out of scope; its table, entity and recipes are not
implemented. *Consolidated into the register (ticket 33): R-MFA-007. Amend the table by ID, not this list.*

---

## Answer

**Seven tables, the UUID as every primary key, Hibernate's default type mappings taken rather than
pinned, `ON DELETE CASCADE` as the single deletion mechanism — and the two rules this ticket was
handed both restated, because neither survived verification: "vendor-neutral DDL" is now a closed
six-entry seam register that is asserted and never executed, and `ddl-auto: validate` is demoted from
the schema's gate to a typo-catcher, because it checks far less than three upstream tickets assumed.**

The consolidation went as planned in one respect: nothing upstream had to be reopened on its own
terms. It went badly in another: three of the instructions this ticket inherited were unrunnable,
wrong, or weaker than their authors believed, and one constraint the map has already paid for twice
turned out to be present a third time and unnoticed.

---

### 1. The vendor-neutral rule, restated

The rule as written is unachievable. H2 2.x, PostgreSQL and MySQL 8 cannot be given one DDL text:
there are **six** points of divergence, enumerated in the Seam register below. So the deliverable
changes shape — it is no longer a claim of neutrality but a **closed register of every point where
portability is inexpressible**, written in the same spirit as
[ticket 20](20-deployment-origin-topology.md)'s treatment of production.

That parallel is the honest framing and must be stated plainly: **Postgres and MySQL are out of
scope, so no line of this DDL will ever execute against either.** Every portability claim here is
asserted by review, never by a run — identical epistemic standing to ticket 20's prod-only cookie
attributes. A register a reviewer can check is worth more than an adjective nobody can test.

Two things follow. Type choices take **Hibernate's dialect defaults** rather than pinning them with
`hibernate.type.preferred_*_jdbc_type`, because a global type pin is invisible from the schema and
silently retypes every mapping in the application, including any arriving from a library. And each
register entry names the concrete alternative spelling, so the register doubles as the porting
instruction [ticket 25](25-operational-handover-document.md) inherits.

### 2. What gates the schema — because `validate` does not

Hibernate's [`AbstractSchemaValidator`](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/tool/schema/internal/AbstractSchemaValidator.java)
checks table existence, column existence, column **type compatibility**, and sequences. It does not
check nullability, length, precision, defaults, foreign keys, or check constraints.
`ColumnDefinitions.hasMatchingType` strips type arguments before comparing, and the `hasMatchingLength`
helper in the same class is never called. Historically the scope was narrower still — column existence
and data types only ([HHH-5247](https://hibernate.atlassian.net/browse/HHH-5247)).

**The consequence that matters: a 48-byte `totp_key` column passes validation silently** — the exact
truncation [ticket 23](23-totp-enrolment-stepup-and-reset-flows.md) derived 69 bytes to prevent,
against three wrong figures already in circulation (48 from ticket 19, plus 81 and 105 from
mis-encoding the UUID or the secret).

So the configuration is:

| Setting | Value | Why |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | catches a renamed or retyped column, nothing more |
| `hibernate.tooling.schema.index_validation` | `NAMED` | new in Hibernate 7.3; **default `NONE`** |
| `hibernate.tooling.schema.unique_key_validation` | `NAMED` | same |

Hibernate is **7.4.5.Final** under Boot 4.1, per
[Boot's own dependency coordinates](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html),
so both settings exist. This map produces no code and therefore has no POM, so `mvn dependency:tree`
confirmation of the exact patch is a **first-commit check handed to `/do-work`**, not a fact this
ticket can assert.

**`NAMED` has a skip that can silently disable the whole gate.** From the 7.4 source:

```java
else if ( validationType == ConstraintValidationType.NAMED ) {
    if ( rawName.startsWith( "IDX" ) ) {
        // this is not a great check as the user could very well
        // have explicitly chosen a name that starts with this as well,
        // but...
        return;
    }
}
```

Hibernate's own comment concedes it is weak. The test is **case-sensitive on uppercase `IDX`**, so a
lowercase `idx_` prefix would in fact still be validated — which is precisely why the naming
convention is **`ux_` / `ix_`**: depending on the case-sensitivity of an undocumented heuristic is
the same silent-disable one rename away. Two further specifics from that method: a missing index
throws `Missing index named X on table Y`, and the comparison walks `index.getSelectables()`
**positionally** against `indexInformation.getIndexedColumns()`, so **composite index column order
must match the mapping**, not merely its membership.

The schema's real gate is a **negative-test set that proves validation is running**, ordered so the
historically fragile cases come first:

1. Retype a `UUID` column to `VARCHAR(36)` → validation must **fail**. First, because UUID-on-H2 is
   the epicentre of `validate` false failures ([HHH-9835](https://hibernate.atlassian.net/browse/HHH-9835)),
   and every primary and foreign key here is now a UUID.
2. Retype `totp_key` from `VARBINARY(69)` to `BINARY(69)` → must **fail**. `Dialect#equivalentTypes`
   tolerates only `VARBINARY`/`LONGVARBINARY` via `SqlTypes.isVarbinaryType`, excluding `BINARY`, and
   `BINARY` additionally zero-pads. Held as an **assertion about H2 2.x metadata**, not a fact about
   the dialect source: H2 1.x reported `varbinary` for everything including `binary(16)` (HHH-9835),
   and 2.x separated the types — so the test is what makes the claim true on our stack.
3. Drop `ux_users_username` → must **fail**. This is the assertion that `NAMED` is active and that the
   `ux_` prefix escapes the `IDX` skip.
4. Reorder the columns of `ix_credential_tokens_user_id_type` → must **fail**, per the positional walk.
5. Lowercase mapped names against H2's upper-cased metadata → must **pass**. The classic false
   failure, tested rather than assumed. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-002, T-CFG-003, T-CFG-004, T-CFG-005, T-CFG-006. Amend the table by ID, not this list.*

And a second set for everything `validate` structurally cannot see: each `NOT NULL`; both
`OCTET_LENGTH` checks, driven with 48-byte and 81-byte values so the two historically wrong widths
are the literal test inputs; the `key_version` range check; the `type` check constraint against an
unknown value; every foreign key; every cascade; the password-history eviction; and the tombstone
blocking both identifiers. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-007, T-MFA-003, T-MFA-004, T-CRED-020, T-CFG-008, T-ADM-021, T-CRED-021, T-ADM-002. Amend the table by ID, not this list.*

**Every assertion in both sets runs outside the persistence context that made the change.** Within
one, the identity map can hand back a cascaded-away entity and the test passes for the wrong reason.

### 3. Identity — the UUID is the sole primary key

**Ticket 03's prescribed mapping does not run on our only runtime.** It specifies
`@Column(nullable = false, unique = true, columnDefinition = "UUID", insertable = false, updatable = false)`
with a database default of `gen_random_uuid()`. Three faults:

- **`gen_random_uuid()` is not an H2 function.** H2's is `RANDOM_UUID()`; `GEN_RANDOM_UUID` is
  registered only inside H2's PostgreSQL compatibility mode
  ([`FunctionsPostgreSQL`](https://github.com/h2database/h2database/blob/master/h2/src/main/org/h2/mode/FunctionsPostgreSQL.java)),
  and native support remains an open upstream request (h2database#3899). It is a core PostgreSQL
  function from PG 13 onward, which is where the instruction came from.
- `columnDefinition = "UUID"` has no MySQL equivalent, so it cannot be one string for three databases.
- `insertable = false` means Hibernate never writes the column and needs a generation annotation to
  read it back, adding a moving part for nothing.

So: **the UUID is the primary key, named `id`, application-generated via `@UuidGenerator`, with no
database default anywhere.** Ticket 03's "a `uuid` column distinct from the primary key" and ticket
11's sequential PK beside it are both amended away. Four arguments:

1. Ticket 11 built a wire-contract rule ("the public identifier is the `uuid`, never the primary key —
   returning the sequential PK would publish row counts") plus an API-wide test to enforce it.
   **With one identifier the rule is vacuously true rather than enforced**, and there is no second
   value to return by mistake.
2. It deletes a column.
3. **Identity-column syntax is itself a three-way portability break** — `GENERATED BY DEFAULT AS
   IDENTITY` against `AUTO_INCREMENT` — so removing the bigint removes a seam entry rather than
   adding one.
4. `@UuidGenerator` is a **before-execution** generator, so the value is assigned during `persist()`
   and readable without a flush. Registration can therefore insert the user and its `ACTIVATION`
   token in one transaction with no flush ordering at all. A `BIGINT IDENTITY` PK has the opposite
   property: the id is unknown until the insert is flushed. *(High confidence from the documentation;
   not executed. A `/do-work` integration test covers it.)* *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-022, T-ADM-008. Amend the table by ID, not this list.*

Ticket 23's ciphertext context prefix never raises an ordering question, because enrolment is
admin-only and post-login, so the user row is always already persistent.

**UUIDv7 is rejected deliberately, not overlooked.** Once the UUID is the PK, index locality makes v7
attractive and Hibernate now supports it. But a v7 UUID embeds a millisecond creation timestamp and
is sortable, so a v7 **public** identifier discloses account creation time and is partially
predictable — attacking the unpredictability that justified putting a UUID on the wire in ticket 11.
`@UuidGenerator` defaults to random (v4), so this is the rejection of a future optimisation a
reviewer will suggest, not a setting to change.

### 4. Types, widths, and the arithmetic behind them

| Concern | Decision | Note |
|---|---|---|
| Identifiers | native `UUID` | Hibernate's default for `java.util.UUID` where the dialect has the type. Seam 1. |
| Instants | `TIMESTAMP(6) WITH TIME ZONE` | Hibernate 7's default for `Instant` on H2 and PG, both `TimeZoneSupport.NATIVE`. **Zero type settings.** Seam 2. |
| 32-byte digests | lowercase hex in `VARCHAR(64)` | `token_hash`, `email_hmac`. One DDL text on all three, so **no** seam entry. |
| TOTP ciphertext | `VARBINARY(69)` + `OCTET_LENGTH` check | Seam 3. |
| Credential | `VARCHAR(255)` | |
| Enum values | `VARCHAR` + named `CHECK … IN (…)` | `@Enumerated(EnumType.STRING)`. |
| Booleans | `BOOLEAN` | Seam 4. |

**Instants, and why no settings.** The alternative was plain `TIMESTAMP(6)` with
`preferred_instant_jdbc_type=TIMESTAMP` and `jdbc.time_zone=UTC`. It was rejected because MySQL is a
seam under *both* options — its `TIMESTAMP` carries the 2038 ceiling, so the MySQL line is
`datetime(6)` either way — so the settings buy nothing and cost two globals on which correctness
silently depends. Drop `jdbc.time_zone=UTC` from a properties file and a developer in SGT starts
writing local times into columns that assert nothing about zone. Under the chosen option the offset
travels with the value, H2 compares by UTC, and the DDL is self-documenting.

**Digest columns: hex, and `VARCHAR` not `CHAR`.** `VARBINARY(32)` would have added two more Postgres
`bytea` lines to the register for no gain. `CHAR(64)` was rejected on two counts: PostgreSQL's
`char(n)` is blank-padded `bpchar` whose trailing-space handling in comparison is an
SQL-standard-mandated exception PG's own documentation flags, which is a subtle equality hazard on a
lookup key; and Hibernate maps `String` to `varchar`, so `CHAR` risks a `validate` type mismatch too.
**Lowercase is part of the contract**, not a convention, because an equality lookup that misses on
case fails open on the reset path.

**The credential column, and the reason it is 255.** `{bcrypt}` (8) plus a 60-character hash is 68
today; Argon2id through `DelegatingPasswordEncoder` lands near 108; PBKDF2's encoding is longer. The
argument is **not** truncation: MySQL has shipped `STRICT_TRANS_TABLES` in its default `sql_mode`
since 5.7, so an over-long value errors, and silent truncation needs someone to have deliberately
unset it. The argument that does not depend on a server setting is that **a `DelegatingPasswordEncoder`
upgrade is a code change that ships with no migration**, so the column must be sized for an encoder
nobody has chosen. `VARCHAR` is length-prefixed, so the headroom costs nothing until used. Record the
arithmetic in the migration comment, because a bare 255 reads as cargo cult.

**The TOTP width becomes a database invariant.** `VARBINARY(69)` bounds only the upper end, so both
MFA tables carry named check constraints — `ck_totp_user_details_totp_key_len`,
`ck_pending_totp_totp_key_len` — asserting `OCTET_LENGTH(totp_key) = 69`, plus
`ck_*_key_version_range` on `key_version BETWEEN 0 AND 255`, because the version is copied into a
**one-byte** field inside the encrypted plaintext and a larger value would truncate there silently.
`OCTET_LENGTH` is documented in H2, PG and MySQL, so the checks cost no seam entry.

There is a contrast inside one file worth recording, because it is the register's justification in
miniature: **the `OCTET_LENGTH` check rests on a documented H2 function, while `LONGVARBINARY` in the
session migration rests on an undocumented legacy alias.** Two different epistemic standings, same
schema.

The checks are named even though **nothing validates check constraints** — `validate` ignores them
entirely. The names exist for the error message and for the negative tests, which is sufficient
reason under this ticket's explicit-constraint-names rule.

### 5. Canonicalisation — replacement, and two different failure modes

**The canonical value replaces the stored value.** One `username` column and one `email` column, each
holding the canonical form, no second column anywhere. Ticket 11 already half-decided this
("canonicalise in application code, **store the canonical value, constrain that**") while this
ticket's framing left the branch open. A separate column would mean every read has to know which one
it is looking at, and the only thing it buys — the user's original capitalisation — has no consumer:
the PRD uses email solely for password reset, and ticket 11 already accepted lowercasing the whole
address as a deviation from RFC 5321 §2.4, which does make the local part formally case-sensitive.
The indexes can then be named for the columns they are actually on, which is what stops
`ux_users_email` from silently sitting on a raw address and reopening the case-variant hole ticket 11
closed.

**The two identifiers behave differently under canonicalisation, and that is a decision, not a
symmetry.** Ticket 11's username charset is `[a-z0-9._-]{3,32}`, lowercase, `@` rejected — so
canonicalising a *valid* username is a no-op, and the canonicaliser there is a validator in disguise.
Therefore:

- **Username: reject.** If a submitted username changes under canonicalisation, it is invalid.
  Silent transformation would mean the account's name is not the one the user typed, and it is the
  **plaintext** username that lands in the tombstone forever.
- **Email: transform.** Already correct and already ADR'd — but the ADR must name the **failure
  mode**, not only the rule: a user at a case-sensitive provider cannot receive reset mail, and reset
  is email's only consumer, so it is the only thing that can break. *Consolidated into the register (ticket 33): R-CRED-014. Amend the table by ID, not this list.*

### 6. The tables

`V1__roles.sql`

```sql
CREATE TABLE roles (
    name VARCHAR(20) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (name)
);

INSERT INTO roles (name) VALUES ('USER');
INSERT INTO roles (name) VALUES ('ADMIN');
```

`name` is the primary key, so `users.role` carries the readable value and rendering a role never needs
a join — the foreign key ticket 11 called load-bearing does its job in the most visible possible
form. No surrogate id, no `privileges` table, no join table, no `role_hierarchy`, no `deleted_roles`.

`V2__users.sql`

```sql
CREATE TABLE users (
    id                    UUID         NOT NULL,
    username              VARCHAR(32)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    activated_at          TIMESTAMP(6) WITH TIME ZONE,
    force_password_change BOOLEAN      NOT NULL DEFAULT FALSE,
    credential_issued_at  TIMESTAMP(6) WITH TIME ZONE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    last_failed_at        TIMESTAMP(6) WITH TIME ZONE,
    locked_until          TIMESTAMP(6) WITH TIME ZONE,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT fk_users_role   FOREIGN KEY (role) REFERENCES roles (name)
);

CREATE UNIQUE INDEX ux_users_username ON users (username);
CREATE UNIQUE INDEX ux_users_email    ON users (email);
```

Widths are inherited, not invented: `username` 32 from ticket 11's `{3,32}`, `email` 254 from ticket
10's RFC 5321 path limit. The 64-byte local-part cap ticket 10 offered is **declined** — it is a
validation rule, and expressing it in DDL would need an expression check H2 cannot index against and
Postgres spells differently, for a constraint the Jakarta `@Email` validator already carries.

Three derived predicates are **not columns**, each for a reason already paid for:
`isAccountNonLocked()` is `locked_until == null || locked_until <= now` (ticket 09, ADR'd);
`isEnabled()` is `enabled && activated_at != null` (ticket 10); TOTP enrolment is row existence
(§8 below). There is **no `account_non_locked`**, **no unlock-reason column** (a closed audit enum
instead), **no last-activity column**, and **no `@Version`** anywhere. *Consolidated into the register (ticket 33): R-ADM-011. Amend the table by ID, not this list.*

`V3__credential_tokens.sql`

```sql
CREATE TABLE credential_tokens (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_credential_tokens      PRIMARY KEY (id),
    CONSTRAINT fk_credential_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_credential_tokens_type CHECK (type IN ('ACTIVATION', 'PASSWORD_RESET'))
);

CREATE UNIQUE INDEX ux_credential_tokens_token_hash ON credential_tokens (token_hash);
CREATE INDEX        ix_credential_tokens_user_id_type ON credential_tokens (user_id, type);
```

`used_at` is a nullable timestamp, not a boolean — ticket 10's decided mechanism, and what its
single-statement conditional consume predicates on. The unique index is on `token_hash` **alone**,
not `(type, token_hash)`: the hash is domain-separated as `SHA-256(type_label || ":" || token)`, so a
cross-type collision is cryptographically unavailable, and the `type` term in the consume predicate
is defence in depth in the query rather than in the index. **One DDL comment is owed here and is not
decorative: this table stores only a hash.** It is the column most likely to acquire a "for
debugging" plaintext sibling later, and it is the one place in the schema where that would be
catastrophic rather than untidy.

`V4__password_history.sql`

```sql
CREATE TABLE password_history (
    id            UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_password_history      PRIMARY KEY (id),
    CONSTRAINT fk_password_history_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_password_history_user_id ON password_history (user_id);
```

`V5__totp.sql`

```sql
CREATE TABLE totp_user_details (
    user_id             UUID          NOT NULL,
    totp_key            VARBINARY(69) NOT NULL,
    key_version         SMALLINT      NOT NULL,
    last_used_counter   BIGINT        NOT NULL DEFAULT -1,
    failed_attempts     INT           NOT NULL DEFAULT 0,
    cumulative_failures INT           NOT NULL DEFAULT 0,
    last_failed_at      TIMESTAMP(6) WITH TIME ZONE,
    locked_until        TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_totp_user_details      PRIMARY KEY (user_id),
    CONSTRAINT fk_totp_user_details_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_totp_user_details_totp_key_len     CHECK (OCTET_LENGTH(totp_key) = 69),
    CONSTRAINT ck_totp_user_details_key_version_range CHECK (key_version BETWEEN 0 AND 255)
);

CREATE TABLE pending_totp (
    user_id     UUID          NOT NULL,
    totp_key    VARBINARY(69) NOT NULL,
    key_version SMALLINT      NOT NULL,
    expires_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_pending_totp      PRIMARY KEY (user_id),
    CONSTRAINT fk_pending_totp_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_pending_totp_totp_key_len           CHECK (OCTET_LENGTH(totp_key) = 69),
    CONSTRAINT ck_pending_totp_key_version_range      CHECK (key_version BETWEEN 0 AND 255)
);
```

`last_used_counter` carries a **database default of `-1`**, not only a field initialiser: ticket 22
found that Lombok `@Builder` silently defeats the `-1L` initialiser without `@Builder.Default`,
yielding `0L` and breaking replay rejection on the first verification after enrolment. Two mechanisms
for one invariant is correct here precisely because one of them has already been observed to fail.

`enrolled_at` and `pending_totp.created_at` are **omitted**: nothing reads them, and this ticket's own
instruction is not to add a column nobody reads. The `expires_at` TTL is 15 minutes, per ticket 23,
enforced at confirmation with `422 FACTOR_ENROLMENT_REQUIRED`; an expired row is overwritten by the
next provisioning and otherwise lingers as encrypted dead weight, consistent with the deferred
hygiene jobs. **`PIN_USER_DETAILS` is not created.** Both tables are sized identically because Recipe *Consolidated into the register (ticket 33): R-MFA-004, R-MFA-007. Amend the table by ID, not this list.*
10 copies the blob verbatim with no re-encryption, which is why they share one migration — splitting
them is how the two widths drift.

`V6__deleted_users.sql`

```sql
CREATE TABLE deleted_users (
    user_id       UUID         NOT NULL,
    username      VARCHAR(32)  NOT NULL,
    email_hmac    VARCHAR(64)  NOT NULL,
    deleted_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_by_id UUID         NOT NULL,
    CONSTRAINT pk_deleted_users PRIMARY KEY (user_id)
);

CREATE UNIQUE INDEX ux_deleted_users_username   ON deleted_users (username);
CREATE UNIQUE INDEX ux_deleted_users_email_hmac ON deleted_users (email_hmac);
```

Ticket 11's field `uuid` is **renamed `user_id`**, since under §3 it is the deleted user's own primary
key and the name should say so. Both indexes are **unique**, because blocking reuse is the table's
entire purpose and a duplicate tombstone would mean two accounts had claimed one identifier.
Retention is indefinite, so there is no purge column and no purge job.

**Neither `user_id` nor `deleted_by_id` is a foreign key, and that is forced rather than chosen.**
Ticket 11 line 277 reads "removed from the **active table** with a tombstone retained", and its reuse
check spans two repositories — `users.existsByUsername(...) || deletedUsers.existsByUsername(...)` —
so the `users` row is genuinely deleted. A foreign key on `user_id` would therefore be violated the
moment it was written; and a foreign key on `deleted_by_id` would break, or cascade away an
accountability record, as soon as the deleting administrator was themselves deleted. Both need the
comment, or a later reviewer files the missing constraints as a defect.

### 7. Deletion, cascade, and one thing that does not cascade

**`ON DELETE CASCADE` is the single deletion mechanism** for all four child tables —
`password_history`, `credential_tokens`, `totp_user_details`, `pending_totp` — with
`@OnDelete(action = OnDeleteAction.CASCADE)` on the mappings so Hibernate does not duplicate the
work, and **no JPA cascade or orphan removal**. Forgetting one child table in application code is a
silent orphan, and for `credential_tokens` a surviving row is a **live redemption path for a deleted
account**, which is the worst failure available in this table set. A cascade is also the version a
compliance reviewer can read straight off the schema.

Three consequences, each of which was previously either implicit or written the long way:

- **Password history purging is forced by referential integrity, not merely chosen on retention
  grounds.** A foreign key to `users` must either block the delete or cascade. The retention argument
  still holds and is worth recording — history rows are BCrypt hashes, offline-attackable, and
  because ticket 11's tombstone blocks both identifiers forever, a deleted account can never return,
  so those rows can never serve the control they exist for again. NIST SP 800-63B-4 does not require
  password history at all; it is a legacy organisational control kept here only because §3.5 and §6
  mandate it and the map's conflict rule gives the standard the control's behaviour. Retaining
  attackable hashes past their only possible use is indefensible on both privacy (PDPA retention
  limitation) and security grounds. But the **mechanism is the cascade**, and there is deliberately
  no second explicit delete — one guarantee, one path.
- **The tombstone copies no `password_hash`.** Now structural rather than remembered, which matters
  because `history-length: 3` means current-plus-two-priors, so the live hash is duplicated as history
  entry zero and is the plausible thing to copy by accident.
- **Ticket 10's `used_at` stamp on admin delete is redundant** and must be removed, not merely
  noted — its five invalidation triggers become four. The rows vanish with the user, which is
  strictly stronger than marking them unusable.

**Sessions do not cascade.** `SPRING_SESSION` has no foreign key to `users` — `PRINCIPAL_NAME` is
just a `VARCHAR(100)` — so deleting a user does not remove their sessions. The session kill stays the
explicit `SpringSessionBackedSessionRegistry` call ticket 08 decided, on every one of ticket 11's
revocation paths. Stated because "we cascade the children" invites the assumption that sessions are
children.

**Eviction of history rows at password-set time is free**, and that is the whole mechanism: the reuse
check already loads every retained row to run `matches()` against each, so eviction is a `deleteAll`
on the in-memory tail — no extra query, and no `LIMIT` inside an `IN` subquery, which MySQL 8.0 and
8.4 still reject with `ER_NOT_SUPPORTED_YET` and which is the obvious unportable reach here. Order by
`created_at` descending. A tie needs two password changes for one user inside one microsecond, which
BCrypt cost 12 alone forbids at 300–400 ms per hash, and a tie's consequence is evicting one of two
equally-old entries, which is harmless — so **no sequence column**.

### 8. The two-admin invariant: enrolment is derived, and the lock set is uniform

**TOTP enrolment state is derived from `totp_user_details` row existence. There is no
`totp_enrolled` flag on `users`.** A flag would be two stored sources of truth for one fact, which is
exactly what ticket 09 refused for `account_non_locked` and then ADR'd. Ticket 19 handed this over as
a shape mismatch — `im8-review` greps for an MFA flag on the User entity while the standard normalises
the state into a table — and that resolves as a **documented false negative**, consistent with the two
other counts on which the map already records that tool as unable to read this design. *Consolidated into the register (ticket 33): R-MFA-005. Amend the table by ID, not this list.*

**The guard takes `FOR UPDATE` on both `users` and `totp_user_details`, on all four mutating paths,
in that order.** Ticket 11's endpoint table already puts the invariant on all four —
`PUT /enabled`, `PUT /role`, `DELETE /users/{uuid}`, `DELETE /users/{uuid}/totp` — so delete was never
outside the guard. What changes is that the read set is now two tables. Per-path lock sets are how
you get a guard correct on three paths and wrong on the fourth, and the fourth is whichever is added
later, which is the argument ticket 11 used to make this one central guard rather than four copies.
The delete path proves it: **the cascade removes a `totp_user_details` row, so deletion changes the
enrolled-admin count through a table the delete statement never names.** Uniform locking also makes
the ordering convention `users` → `totp_user_details` → session rows a property of one component
rather than a rule four call sites must remember. H2 refuses `FOR UPDATE` on aggregates, so both
selects fetch rows and count in Java — ticket 11's idiom.

**The protection is against decrements, not phantoms, and that must be written down.** An unenrolled
admin has no `totp_user_details` row, and H2 has no gap or predicate locking, so the lock set cannot
cover a concurrent insert. The invariant survives because **an insert can only raise the count**:
every mutation that can lower it — un-enrol, delete, demote, disable — acts on rows that exist and
therefore get locked. A later reader who assumes the guard is phantom-safe will build something on a
property it does not have. *Consolidated into the register (ticket 33): R-ADM-015. Amend the table by ID, not this list.*

### 9. Indexes, and one deliberate omission

Beyond the primary keys: `ux_users_username`, `ux_users_email`,
`ux_credential_tokens_token_hash`, `ix_credential_tokens_user_id_type`,
`ux_deleted_users_username`, `ux_deleted_users_email_hmac`, `ix_password_history_user_id`. The `ux_`
and `ix_` prefixes are chosen to clear the `IDX` skip. The 1:1 MFA tables need no additional unique
index, because the shared primary key **is** the uniqueness constraint — which is also what makes
"enrolled = a row exists" cheap.

The MFA tables are keyed on `user_id`, **not** username as the corpus's `findByUsername` does: the
user is already loaded on every path that touches these rows, and keying on username would copy an
identifier into two more tables for nothing.

**Ticket 11's sort allowlist — `username`, `createdAt`, `enabled`, `role` — is deliberately
unindexed** beyond the two unique indexes that already exist, bounded by the page cap of 100. This is
the one decision in the ticket that is wrong at real user volume and right at ours, so it carries a *Consolidated into the register (ticket 33): R-DATA-010. Amend the table by ID, not this list.*
reopening trigger rather than a justification.

### 10. The cross-table reuse race: accepted, and why the structural fix is declined

With the `users` row genuinely deleted, "not in `users` and not in `deleted_users`" is guarded by two
independent unique indexes, **neither of which can see the other**. A registration that reads
`deleted_users` before an admin's delete commits and inserts into `users` after it does ends with the
same username in both tables — a reuse of a permanently blocked identifier, with no constraint
violated. Structurally the same bug ticket 11 found in the two-admin count and fixed with a lock.

**Accepted**, with the trigger recorded. But the reason for declining the structural fix is not scale,
and the distinction matters because "too much work" would not survive review:

A **reservations table** — one row per identifier ever claimed, with the unique constraint there —
does close it, and elegantly: registration stops reading `deleted_users` at all and simply attempts a
reservation that already exists, so the interleaving has nowhere to happen, and both tables'
uniqueness becomes a consequence rather than a coincidence. The problem is what the row holds for
email. Plaintext retains deleted users' addresses and defeats ticket 11's HMAC decision outright.
HMAC moves **live** registration's uniqueness check onto the non-rotatable tombstone key, so a
missing or wrong key stops being a silent fail-open on reuse blocking for deleted accounts and
becomes a fail-open on **every registration in the system**. That trades a race requiring a delete
and a registration of the same identifier to interleave for a new dependency on the one key that can
never rotate.

**And a partial fix is worse than none here.** A reservations table could be built for usernames
alone with no new key dependency, since the tombstone already stores the username in plaintext. So
the structural fix is available for exactly the half with the smaller harm and unavailable for the
half with the real harm — the recovery-channel hijack ticket 11 named, where an unblocked address
lets a new account claim a deleted user's reset channel. Closing the cheap half while leaving the
expensive one open, and calling it a fix, is the outcome to avoid. *Consolidated into the register (ticket 33): R-DATA-011. Amend the table by ID, not this list.*

### 11. The tombstone HMAC key can never rotate — the constraint is known; the justification was missing

Because `deleted_users` holds no plaintext address, the HMAC can never be recomputed, so the key that
produced existing tombstones can never be rotated. **This half was already known and is not a finding
of this ticket.** Ticket 11 handed it to ticket 24 explicitly, and ticket 24 — resolved concurrently
with this one — carries it as "the **second** entry in the category the ticket already opened with the
password pepper", correctly noting the asymmetry that the pepper was declined and therefore never
exists while this key exists and is permanently frozen. Its scoping instruction, that the rotation
policy "must scope itself to keys that can rotate", stands.

**What was missing is why the exception is acceptable.** A scoping sentence records that the key
cannot rotate; it does not survive an IM8 crypto-controls review, which expects a rotation capability
and reads an unjustified exception as an omission. This ticket supplies the argument, plus the
alternative a reviewer will propose, plus the trigger that would end the exception. Recorded in
[ticket 24](24-secrets-and-configuration-handling.md) as a **named, justified exception**.

**This section originally also rejected forward-only key versioning, and that rejection is withdrawn —
see the amendment from ticket 24 at the end of this ticket.** Ticket 24 resolved concurrently and
reached the opposite conclusion on a better argument. The schema consequence turns out to be nil, for
a reason worth having: versioning an HMAC used for **equality search** needs no version column. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*

The exception's substance, which is also its defence: **this key is a blinding key for a
pseudonymised reuse index, not a confidentiality key protecting a secret at rest.** Its compromise
discloses only that a given address once held an account, and only to someone who already holds the
tombstone table. Rotation exists to bound the damage of key compromise over time; here that damage is
one bit per address. This matches accepted blind-index practice, where rotation requires re-deriving
from plaintext deliberately no longer held.

Three requirements travel with the exception:

- **It MUST be distinct from ticket 19's AES-GCM TOTP key.** One key, one purpose — a shared key
  would drag the TOTP key, which rotates yearly by design, into the same non-rotatability.
- **Because it can never rotate, its compromise is unbounded in time**, so its handling —
  environment-supplied, never logged, never in a properties file, absence fail-fast at refresh —
  carries more weight than for keys that can rotate.
- **Deriving both from one root via HKDF does not restore rotatability**, and should be pre-empted
  before someone proposes it: the email subkey would have to stay pinned to root v1 forever, which
  means keeping root v1 alive forever.

Honest limit, inherited from ticket 11 and unchanged: a keyed HMAC is pseudonymisation, not
anonymisation.

### 12. Session tables — copied, and the copy enforced

`V7__spring_session.sql` is **`spring-session-jdbc` 4.1.1's `schema-h2.sql`, verbatim**, behind a
header comment naming the tag and the blob SHA
**`be6e515720a5434a898bcb6d186f42d7b4766006`** (904 bytes), with
`spring.session.jdbc.initialize-schema=never`.

Retyping upstream's schema was rejected. A verbatim copy brings
`SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)` free — **ticket 08's load-bearing index is in
the shipped script and can only be lost by hand-rolling** — and preserves the `CHAR(36)` and
epoch-millis `BIGINT` shapes that `JdbcIndexedSessionRepository`'s hardcoded SQL depends on. Two
further facts pin it: `cleanup-cron` (default `0 * * * * *`) issues only
`DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?`, so attribute rows are reclaimed **exclusively**
by `ON DELETE CASCADE` on `SPRING_SESSION_ATTRIBUTES_FK` and weakening that FK is a silent leak; and
the repository's Javadoc prescribes a `SPRING_SESSION_ATTRIBUTES_IX1` that **no** shipped script
creates and the composite primary key already covers — follow the scripts, not the Javadoc. The
schema is byte-identical to 3.x, so there is no upstream-drift risk.

**The cost, recorded as a seam entry:** `ATTRIBUTE_BYTES LONGVARBINARY` uses a keyword that appears
nowhere in H2 2.x's documentation — zero hits across its data types, grammar and migration-to-v2
pages. It parses, and Spring Session 4.1.1's own build exercises this script against H2 2.4.240, but
a reviewer cannot verify it from H2's docs. The risk is proportionate: if a future H2 drops the alias
the migration fails loudly at startup, which is the right kind of failure.

**"Verbatim" is enforced, not claimed**, because a hand-save is enough to break it silently:
PowerShell 5.1's `-Encoding utf8` prepends a BOM, and a BOM makes the file no longer that blob. So:

- **One copy only.** The upstream bytes live in the migration and nowhere else. A test strips the
  header — mechanically, defined as *the leading run of `--` lines plus one blank line* — asserts the
  remainder begins `CREATE TABLE SPRING_SESSION` so a malformed strip fails **as a strip failure
  rather than as a hash mismatch**, then computes the git blob hash of the remainder and asserts
  `be6e5157…`. A test resource holding a second copy was rejected: same brittleness in the strip, one
  more thing that can drift, and an unanswerable question about which copy is authoritative.
- **The hash is computable in about six lines with no Git and no network**:
  `SHA1("blob " + length + "\0" + bytes)`.
- **`.gitattributes` and the hash catch different things, and the division of labour must be stated
  correctly.** `text eol=lf` prevents the CRLF failure mode and prevents it **silently** — a Windows
  hand-save with CRLF is normalised on commit and checkout, so the file stays correct and nothing
  fails. **The SHA assertion is what catches the BOM, and it fails loudly.** Git has no attribute
  that strips or rejects a UTF-8 BOM. Both are needed.
- `eol=lf` is kept over `-text` deliberately: `-text` would make a CRLF save fail the test instead of
  being quietly repaired, which is stricter but not better. And `eol=lf` is also what keeps the
  working-tree bytes equal to the blob on Windows, which is what the test reads — with a bare `text`
  attribute and `core.autocrlf=true`, a **correct** repository would fail the test on a Windows
  checkout. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-001. Amend the table by ID, not this list.*

Since no JPA entity maps these tables, **`validate` never looks at them at all.** The functional gate
is an integration test that persists a session carrying a `FactorGrantedAuthority` and reads it back
by principal name — which exercises IX3, the `LONGVARBINARY` column and the JDK serialisation path in
one assertion. Sizing note carried forward from tickets 08, 09 and 10: at `/csrf` 30/min against the
15-minute idle window a single source holds roughly **450 live anonymous rows**, a ceiling set by the
limiter and not by the cron, which deletes only already-expired rows. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-005. Amend the table by ID, not this list.*

### 13. The migration set, seed data, and when append-only starts

Seven files, split by concern, in dependency order:

| | File | Note |
|---|---|---|
| 1 | `V1__roles.sql` | table plus the two-row seed; first because `users.role` cannot precede its FK target |
| 2 | `V2__users.sql` | |
| 3 | `V3__credential_tokens.sql` | |
| 4 | `V4__password_history.sql` | |
| 5 | `V5__totp.sql` | both MFA tables together, so the two 69-byte widths cannot drift |
| 6 | `V6__deleted_users.sql` | |
| 7 | `V7__spring_session.sql` | **last**, because it is the one file that is not ours |

Split by concern rather than one baseline because migration filenames are the schema's table of
contents for a reviewer. `spring_session` is last because it has no foreign key to anything, so its
position is free, and parking it at the end keeps upstream's bytes in a file nobody has a reason to
renumber.

**Seeding:** a versioned migration with plain `INSERT`s for the two `roles` rows. Ticket 11's
"idempotently" is satisfied by Flyway's own once-only guarantee — a repeatable `R__` migration
re-runs on **checksum** change, not on data drift, so it would not be self-healing either. The
startup validator comparing YAML against the table is the real drift detector, and the foreign key is
the enforcement; the seed does not also need to be defensive. `roles` is the **only** seeded data in
the system: ticket 11 ruled out dev seed accounts of any kind, and the admin bootstrap stays in a
runner because a BCrypt hash inside a versioned migration is checksum-frozen and committing a hash of
a real credential is the as-8 exposure being avoided.

**Test data** via Java builders in test fixtures with synthetic values. No SQL seed files, no
test-only migrations — one schema, no second path that can diverge from it.

**Append-only starts at the first commit pushed to the shared branch** — a concrete trigger, not a
judgement call. Until then the baseline is editable, because Flyway checksum immutability would
otherwise force repair migrations for typos in a schema that has never been deployed anywhere.
Flyway's `clean` has been disabled by default since v9; **leave it disabled and add no escape hatch**,
which would later read as an approved procedure.

### 14. Affirmed while in the schema

**No table in this set stores a secret in a recoverable form.** Passwords are BCrypt-12 behind
`DelegatingPasswordEncoder`; the tombstone email is a keyed HMAC; the TOTP secret is an AES-GCM
envelope under an environment-supplied key; reset and activation tokens are stored only as
domain-separated hashes. Consistent with ASVS 5.0's stored-secret expectations and IM8's crypto
controls. The assertion worth putting in the DDL is the `credential_tokens` one in §6, because that is
the column most likely to grow a plaintext sibling later.

---

## Seam register

Six entries. Every one is **asserted by review and never executed**, because no Postgres or MySQL
runtime is in scope. This is the porting document ticket 25 inherits. *Consolidated into the register (ticket 33): R-DATA-004. Amend the table by ID, not this list.*

| # | Our DDL (H2) | PostgreSQL | MySQL 8 | Nature |
|---|---|---|---|---|
| 1 | `UUID` | `uuid` | **`BINARY(16)`** | No UUID type in MySQL. Hibernate reaches 16 via `UUIDJavaType`'s default binary length, not via anything we write. *Consolidated into the register (ticket 33): R-DATA-004. Amend the table by ID, not this list.* |
| 2 | `TIMESTAMP(6) WITH TIME ZONE` | `timestamp(6) with time zone` | **`datetime(6)`** | MySQL has no zoned type, and its `TIMESTAMP` carries the 2038 ceiling, so `datetime` is forced. |
| 3 | `VARBINARY(69)` | **`bytea`** — width inexpressible | `VARBINARY(69)` | Postgres has no parameterised binary type. The `OCTET_LENGTH` check becomes the **only** width guarantee there. *Consolidated into the register (ticket 33): R-DATA-005. Amend the table by ID, not this list.* |
| 4 | `BOOLEAN` | `boolean` | **`bit`** | Not a syntax break — `BOOLEAN` is an accepted alias for `TINYINT(1)` and parses. The break is at **validation**: metadata reports `tinyint` while Hibernate's `MySQLDialect` deliberately emits `bit` (HHH-6935). *Consolidated into the register (ticket 33): R-DATA-006. Amend the table by ID, not this list.* |
| 5 | `ATTRIBUTE_BYTES LONGVARBINARY` | `BYTEA` | `BLOB`, plus `ENGINE=InnoDB ROW_FORMAT=DYNAMIC` on both tables | Upstream's own per-vendor divergence. Compounded by `LONGVARBINARY` being undocumented in H2 2.x. *Consolidated into the register (ticket 33): R-DATA-007. Amend the table by ID, not this list.* |
| 6 | case- and accent-**sensitive** unique indexes | same | **`utf8mb4_0900_ai_ci`**: case- and accent-**insensitive** | The register's justification in one row — see below. *Consolidated into the register (ticket 33): R-DATA-008. Amend the table by ID, not this list.* |

**Entry 6 is the sharpest and the only one invisible in the DDL text.** MySQL's default collation
changes the *semantics* of every unique index on a character column — `username`, `email`,
`token_hash`, `roles.name`, `deleted_users.username` — from identical-looking DDL. Mostly benign: hex
is lowercase by contract, role names are uppercase ASCII, and ticket 11's username charset is
`[a-z0-9._-]` so case cannot vary. **The corner that is not benign is accent-insensitivity on the
canonical email.** Under `ai_ci`, `josé@…` and `jose@…` collide; on H2 they do not; and **NFC
normalisation does not fold accents** — ticket 11 chose NFC deliberately and ruled out dot-folding
and `+tag` stripping on the principle that one provider's conventions must not be imposed on all of
them. So on MySQL the uniqueness rule would be **stricter than the canonicalisation contract states**,
and a registration that succeeds on H2 would be rejected. This contradicts a principle ticket 11
chose, which is why it is raised as an amendment there and not only logged here. *Consolidated into the register (ticket 33): R-DATA-009. Amend the table by ID, not this list.*

## Reopening triggers

| Trigger | Reopens |
|---|---|
| Real user volume | The unindexed sort allowlist (§9) — right at our scale, wrong at any real one. *Consolidated into the register (ticket 33): R-DATA-010. Amend the table by ID, not this list.* |
| More than one administrator deleting accounts concurrently, or self-service deletion | The reservations table (§10). *Consolidated into the register (ticket 33): R-DATA-011. Amend the table by ID, not this list.* |
| Tombstone retention ever becomes **bounded** | Old HMAC key versions become genuinely **retirable** at the end of the retention window — the only thing that converts ticket 24's forward-only versioning into full rotation, and the exact moment the rows holding old versions stop existing. *Consolidated into the register (ticket 33): R-CFG-003. Amend the table by ID, not this list.* |
| Any actual PostgreSQL or MySQL runtime | The entire seam register moves from **asserted** to **executed**, and entry 6 becomes a functional difference rather than a note. *Consolidated into the register (ticket 33): R-DATA-012. Amend the table by ID, not this list.* |

## Findings

1. **Ticket 03's `uuid` mapping does not run on H2** — `gen_random_uuid()` is PostgreSQL's, available
   in H2 only under `MODE=PostgreSQL`; `columnDefinition = "UUID"` has no MySQL spelling;
   `insertable = false` needs read-back. Prescribed and cross-referenced to this ticket, never
   executed.
2. **`ddl-auto: validate` checks far less than three tickets assumed** — no nullability, length,
   defaults, foreign keys or check constraints. A 48-byte `totp_key` passes.
3. **Hibernate's `NAMED` index validation silently skips names beginning uppercase `IDX`**, with a
   source comment conceding the check is weak. A plausible naming convention disables the gate
   entirely.
4. **`LONGVARBINARY` is undocumented in H2 2.x** — absent from data types, grammar and
   migration-to-v2 — yet ships inside Spring Session's H2 schema. One file, two epistemic standings,
   since the `OCTET_LENGTH` check beside it rests on a documented function.
5. **MySQL's default collation folds accents that NFC deliberately does not**, contradicting ticket
   11's canonicalisation principle.
6. **The tombstone HMAC key's rotation exception had a constraint but no justification.** The
   non-rotatability itself was already recorded by tickets 11 and 24 — 24 calls it the *second* entry
   in the category ticket 07 opened with the pepper — so this is not a discovery. What was absent is
   the argument that makes the exception defensible to an IM8 review, the key-version alternative
   considered and rejected, the HKDF pre-emption, and a reopening trigger.
7. **The cross-table reuse rule is enforced by no constraint**, and the two unique indexes cannot see
   each other.
8. **Ticket 11's `deleted_by_id` cannot be a foreign key**, because the deleting admin may themselves
   be deleted — and neither can the tombstone's own `user_id`, because the `users` row is gone.
9. **Ticket 10's `used_at` stamp on admin delete is redundant** under cascade; its five invalidation
   triggers become four.
10. **Account deletion changes the enrolled-admin count through a table its statement never names**,
    via the cascade — so the two-admin guard's lock set had to become uniform across all four paths.

## ADRs owed (17)

1. UUID as sole primary key, application-generated, **v4 not v7** with the disclosure reason. *Consolidated into the ADR routing (ticket 34): ADR-050. Amend by ID, not this list.*
2. Vendor-neutrality restated as a closed six-entry seam register, asserted and never executed. *Consolidated into the ADR routing (ticket 34): REJ-030. Amend by ID, not this list.*
3. `validate` demoted to a typo-catcher; the schema's gate is a negative-test set with
   `index_validation`/`unique_key_validation` at `NAMED` and a `ux_`/`ix_` convention chosen to clear
   the `IDX` skip. *Consolidated into the ADR routing (ticket 34): ADR-051. Amend by ID, not this list.*
4. `TIMESTAMP(6) WITH TIME ZONE` with **zero** Hibernate type settings, over plain `TIMESTAMP` plus
   two globals. *Consolidated into the ADR routing (ticket 34): REJ-031. Amend by ID, not this list.*
5. Digest columns as lowercase hex `VARCHAR(64)`, not `VARBINARY(32)` and not `CHAR(64)`. *Consolidated into the ADR routing (ticket 34): REJ-032. Amend by ID, not this list.*
6. `OCTET_LENGTH` and `key_version` range checks as the compensating control for validate's width
   blindness. *Consolidated into the ADR routing (ticket 34): REJ-033. Amend by ID, not this list.*
7. Canonical value replaces the stored value; **username canonicalisation is rejection, email is
   transformation**, with the reset-delivery failure mode named. *Consolidated into the ADR routing (ticket 34): ADR-045. Amend by ID, not this list.*
8. `ON DELETE CASCADE` as the single deletion mechanism; `deleted_by_id` and the tombstone `user_id`
   deliberately unconstrained. *Consolidated into the ADR routing (ticket 34): REJ-034. Amend by ID, not this list.*
9. Password history purged by cascade, with the retention argument and NIST's silence recorded. *Consolidated into the ADR routing (ticket 34): REJ-035. Amend by ID, not this list.*
10. Cross-table reuse race accepted; reservations table declined on the key-dependency argument and
    on the partial-fix argument. *Consolidated into the ADR routing (ticket 34): REJ-036. Amend by ID, not this list.*
11. Tombstone HMAC key: the justification for its rotation exception — blinding key, not confidentiality
    key — with the asymmetry against ticket 07's pepper, the distinctness requirement, and the HKDF
    pre-emption. **Forward-only versioning adopted per ticket 24**, and the finding that it needs no
    version column because an HMAC used for equality search is not one used for decryption. *Consolidated into the ADR routing (ticket 34): ADR-052. Amend by ID, not this list.*
12. Shared primary keys on the 1:1 MFA tables; enrolment derived from row existence, not a flag. *Consolidated into the ADR routing (ticket 34): ADR-053. Amend by ID, not this list.*
13. Sort allowlist deliberately unindexed, with its reopening trigger. *Consolidated into the ADR routing (ticket 34): REJ-037. Amend by ID, not this list.*
14. No `@Version` anywhere; **non-security admin edits are last-write-wins, accepted** — recorded so a
    later reviewer sees a decision and not an oversight. *Consolidated into the ADR routing (ticket 34): REJ-038. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-DATA-013. Amend the table by ID, not this list.*
15. No last-activity column. *Consolidated into the ADR routing (ticket 34): REJ-039. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-011. Amend the table by ID, not this list.*
16. Session DDL copied verbatim, single copy, enforced by a blob-hash test, with the `eol=lf` /
    SHA division of labour stated. *Consolidated into the ADR routing (ticket 34): REJ-040. Amend by ID, not this list.*
17. Migrations split by concern; append-only from the first commit pushed to the shared branch;
    Flyway `clean` stays disabled with no escape hatch. *Consolidated into the ADR routing (ticket 34): REJ-041. Amend by ID, not this list.*

Glossary terms for `CONTEXT.md`: **Seam register**, **Blinding key**.

## Amendments raised

- **[03](03-logging-schema-extraction.md):** the prescribed `uuid` mapping is unrunnable on H2. The
  UUID becomes the sole primary key, named `id`, application-generated; `user.id` and
  `user.target.id` both map to it. "A `uuid` column distinct from the primary key" is withdrawn. *Consolidated into the ADR routing (ticket 34): spec §Data model; register. Amend by ID, not this list.*
- **[07](07-password-policy-and-hashing.md):** cross-reference the tombstone HMAC key, so the pepper
  rejection and the HMAC exception read as one constraint rather than two coincidences — and so the
  **asymmetry** is visible: a pepper protects a credential, where unbounded-in-time compromise is
  intolerable, while the HMAC protects a one-bit fact, where the same constraint is accepted. Ticket
  24 already pairs the two; this adds the reason they resolve differently. *Consolidated into the ADR routing (ticket 34): ADR-004 (attached amendment). Amend by ID, not this list.*
- **[10](10-credential-flows.md):** **delete** the admin-soft-delete `used_at` stamp — the cascade is
  stronger. Five invalidation triggers become four. Recorded as an amendment, not a note, because a
  note gets implemented anyway.
- **[11](11-admin-module-role-model-and-bootstrap.md):** tombstone field `uuid` renamed `user_id`;
  neither it nor `deleted_by_id` can be a foreign key; the sequential PK is withdrawn and the
  never-return-the-PK rule becomes vacuous rather than enforced; the guard's lock set extends to
  `totp_user_details` on all four paths, **protecting against decrements and not phantoms**; and
  **seam entry 6 contradicts the canonicalisation principle this ticket chose** — NFC's deliberate
  refusal to fold accents is overridden by MySQL's default collation.
- **[19](19-mfa-scope-conflict.md):** the "MFA flag on the User entity versus normalised table" shape
  mismatch is answered — derived from row existence, with `im8-review`'s grep recorded as a
  documented false negative. *Consolidated into the register (ticket 33): R-MFA-005. Amend the table by ID, not this list.*
- **[23](23-totp-enrolment-stepup-and-reset-flows.md):** `PENDING_TOTP.expires_at` adopted at 15
  minutes; `created_at` dropped from that table as unread; `key_version` typed `SMALLINT` with a
  `0..255` range check, because the in-plaintext copy is one byte; `last_used_counter` carries a
  **database** default of `-1` as well as the field initialiser, since `@Builder` has already been
  observed to defeat the latter. *Consolidated into the ADR routing (ticket 34): spec §Data model. Amend by ID, not this list.*
- **[24](24-secrets-and-configuration-handling.md):** resolved concurrently with this ticket, so this
  is additive rather than corrective. It already fixes the key's property name, encoding and 32-byte
  length, its fail-fast absence behaviour, its distinctness from the TOTP key, and the fact that it
  cannot rotate. What this ticket adds is the **justification** for that exception — blinding key
  rather than confidentiality key — plus the key-version alternative considered and rejected, the
  HKDF pre-emption, and the bounded-retention reopening trigger. Its 32-byte key pairs consistently
  with this ticket's `email_hmac VARCHAR(64)`, hex of a 32-byte HMAC-SHA256 output. *Consolidated into the ADR routing (ticket 34): ADR-052 (attached amendment). Amend by ID, not this list.*

## Handoffs

- **[13](13-audit-event-catalogue.md):** `user.id` and `user.target.id` both resolve to `users.id`;
  there is no unlock-reason column, so the closed enum lives entirely in the event.
- **[16](16-test-plan.md):** the five-item negative-test set and the DB-invariant set are yours to
  fold in, including the fresh-transaction rule, the 48-and-81-byte inputs, and the session
  blob-hash test.
- **[17](17-deferral-register-and-adrs.md):** 17 ADRs, two glossary terms, the six-entry seam
  register and the four reopening triggers.
- **[25](25-operational-handover-document.md):** the seam register **is** the porting document; and
  the HMAC key's compromise is unbounded in time, which belongs beside the break-glass material.
- **`/do-work`:** confirm Hibernate `7.4.5.Final` by `mvn dependency:tree` on the first commit — this
  map has no POM, so the version is cited from Boot's coordinates page and not verified locally. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-002, T-MFA-003, T-BLD-001. Amend the table by ID, not this list.*

---

## Amendment from ticket 24 (secrets and configuration handling) — resolved concurrently, and it disagrees

Tickets 12 and 24 were worked in parallel and reached different conclusions on one point. Ticket 24 is right, and the
correction is recorded here rather than left as two standing answers. Two further inputs from it land on §2's gate.

**1. Forward-only key versioning is adopted; §11's rejection of it is withdrawn.** Ticket 24 corrected ticket 11's
"cannot rotate, at all" to **forward-only**: versions accumulate under the idiom tickets 19 and 23 built and can never
retire, because we hold no plaintext to re-derive old tombstones. §11 dismissed this as "add a key, not rotation… to buy
nothing IM8 asks for". That was wrong on the benefit, which is concrete: accumulating versions gives **a leaked key a
forward response where previously there was none**, and moves **ASVS 11.2.2 (L2) from a clean failure to a partial**.
Declining a control that converts a failure into a partial, on the grounds that it is not the whole control, is the
wrong trade. Ticket 24 also records why the alternative that *would* satisfy 11.2.2 outright — deterministic AEAD —
fails anyway: ASVS Appendix C's approved AEAD list contains no SIV of any kind, and it would mean BouncyCastle or Tink
on the one path ticket 19 deliberately kept inside Spring Security after CVE-2026-47842.

**2. The schema consequence is nil, and the reason generalises.** Forward-only versioning of `email_hmac` needs **no
key-version column on `deleted_users`**, because an HMAC used for **equality search** is not an HMAC used for
decryption. The `key_version` column on `totp_user_details` and `pending_totp` is load-bearing precisely because you
cannot decrypt without knowing which key to use. The tombstone check has no such need: it computes the candidate's HMAC
under **every live version** and looks each up against `ux_deleted_users_email_hmac`, never needing to know which
version produced a stored row. A version column there would be write-only information, which §6's own rule excludes.
Cost is N lookups per registration, N being the live version count, currently one. The unique index is unaffected, *Consolidated into the register (ticket 33): R-CFG-003. Amend the table by ID, not this list.*
since an address can only ever be tombstoned once — reuse blocking is what prevents it being re-registered in between.

So §11's conclusion inverts while §6's DDL does not change, and the conflation is the lesson worth recording: **"reject
the key-version column" and "reject forward-only versioning" are two decisions, and §11 argued the first to settle the
second.**

**3. A third key exists, and ticket 09's routing was overturned.** Ticket 24 split `source.ip.hash` off the tombstone
key, because **one key cannot rotate for the IP stream and stay frozen for tombstones**, on SP 800-57 Part 1 Rev 5 §5.2
Key Usage. No schema impact — `source.ip.hash` is a log field and not a column — but it means the tombstone key's
inventory entry is narrower than ticket 09 assumed, and §11's "one key, one purpose" requirement is now satisfied three
ways rather than two.

**4. `validate` passing is not evidence that the intended database was reached — and this is the sharpest input to
§2.** Ticket 24 found that Boot's **embedded-datasource fallback** means an unset datasource URL starts cleanly on an
ephemeral in-memory H2, which **Flyway migrates and `ddl-auto: validate` passes**. So the entire gate in §2 can report
success against a database that exists only for the life of the process and contains nothing. Two consequences:

- §2's negative-test set proves that *validation runs*; it cannot prove *what it ran against*. Ticket 24 converts
  absence into failure at the config layer, and that conversion is now a **precondition** of §2's gate meaning
  anything. Recorded here so the dependency is visible from the schema side.
- It also explains a failure mode that would otherwise be baffling in `/do-work`: a green build, a migrated schema, a
  passing `validate`, and no data — because every run got a fresh empty database. Worth one line in the test plan
  handed to [ticket 16](16-test-plan.md). *Consolidated into the ADR routing (ticket 34): ADR-051 (attached amendment). Amend by ID, not this list.*

**5. One consistency check that passes.** Ticket 24 fixes every key at **exactly 32 decoded bytes**, on the ground that
32 is a compliance floor rather than a preference since ASVS Appendix C grades AES-128 as **Legacy**. That pairs
correctly with §4's `email_hmac VARCHAR(64)`: a 32-byte key into HMAC-SHA256 yields a 32-byte tag, which is 64
lowercase hex characters. No change.

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — two columns, one index, one predicate

**Two new columns on the user row**, both under the pessimistic lock ticket 09 §3 already takes, so no new
contention and no change to the lock ordering inside the transaction:

| Column | Type | Notes |
|---|---|---|
| `consecutive_failures_since_success` | integer, not null, default 0 | The NIST §3.2.2 cap counter. **Not** the same as `failed_login_attempts`, which is windowed. |
| `password_disabled_at` | timestamp, nullable | Null means live. The authenticator-disabled state; cleared only by rebinding. |

**The long name is deliberate and should not be shortened.** `failed_login_attempts` already exists and resets on
staleness; this one resets only on password-authenticator success. Two similarly-named counters on one row is how
the wrong one gets read, and ticket 09 records that a cap built on the windowed counter would be unreachable dead
code — the third instance on this map of the defect ticket 02 found in the standard.

**Why not reuse `enabled`:** it would collide with ticket 11's admin enable/disable — a re-enable would clear a NIST
cap, and the user list could not distinguish "disabled by admin" from "disabled by cap". Same reasoning as ticket
23's tier 2 holding its own state.

**`password_disabled_at` needs an index**, and its consumer is not a query you have seen: ticket 08 now owns an
idempotent startup **reconciliation sweep** that selects every user with the column set and deletes their sessions,
because Spring Session's `PROPAGATION_REQUIRES_NEW` makes a session kill non-atomic with the state change that
triggers it. Unindexed, the sweep is a full scan at every boot. Naming convention per your own rule: `ix_`.

**One predicate, two readers, spanning both tables.** Ticket 11's two-admin guard and ticket 21's
zero-authenticable-admins signal both need **`authenticable`** = enabled ∧ activated ∧ `password_disabled_at IS
NULL` ∧ TOTP row exists ∧ not tier-2 disabled. The guard reads three of those terms today and the signal needs five.
Relevant to you because it **spans `users` and `TOTP_USER_DETAILS`**, which is the same cross-table shape your
two-admin guard already had to lock on all four paths — so the predicate is defined once and consumed twice rather
than expressed as two queries that drift.

**`ddl-auto: validate` catches none of this**, per your own demotion of it to a typo-catcher: it would pass a
nullable-by-accident counter and a wrong-width timestamp alike. Both columns belong in your five-item
negative-test set. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-018. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-051 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 28 — a fifth `credential_issued_at` trigger

[Ticket 28](28-out-of-band-privileged-channels.md) §3's single break-glass run sets an operator-supplied password
and marks it `force_password_change`. So it is a forced-change credential issuance, and it **stamps
`credential_issued_at`**. That joins bootstrap seed, admin create, admin reset and re-enable as the **fifth**
trigger, and ticket 11's lazy 30-day expiry then applies to an operator-set password that is never changed.
There is no schema change: both columns already exist. *Consolidated into the ADR routing (ticket 34): ADR-046 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 29 (anonymous session-row growth)

- **Both of your sizing notes (`12:128-131`, `12:800-802`) are corrected.** It is 480 unexpired / 510 physically
  present per source, not ~450, and it is no longer the operative bound. The aggregate is capped at `N_max` =
  100,000 live `SPRING_SESSION` rows by ticket 29's shedding.
- **Physical size is not row size.** A session holds about 0.4 KB of data, but the H2 file costs 1.7–17 KB per
  session under burst. The cleanup's bulk DELETE roughly triples the file, and the file does not shrink while it is
  open ([asset](../research/anonymous-session-growth-and-h2-file-verification.md) §C.1).
- **§12's verbatim copy gains one consequence.** `DELETE_SESSION_QUERY` excludes rows with a negative
  `MAX_INACTIVE_INTERVAL` (§F.4), so no code path may ever save one. Ticket 29 §9 test 5 asserts it.
- **No schema change.** *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-024. Amend the table by ID, not this list.*
