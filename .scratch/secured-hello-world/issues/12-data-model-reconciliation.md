# 12 — Reconcile the data model and the Flyway migration set

Type: grilling
Status: open
Blocked by: 07, 08, 09, 10, 11

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
