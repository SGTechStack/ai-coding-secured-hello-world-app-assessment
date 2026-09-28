---
status: accepted
---

# ADR-068: A traceability gate binds each test to a canonical table row with `@Proves`

Every security control has a stable test ID in one canonical table, `docs/test-plan/test-plan.md`. Every automated
test cites its row. The Maven `verify` phase fails when the table and the tests disagree. An annotation gate over a
markdown table looks like ceremony, and it is the obvious thing to delete. It stays because it is the only thing
that stops a named control from quietly losing its test.

## Context

The controls in this design are mostly proved by negative assertions, counts and absences: a secret that never
appears in any sink, a call made exactly once, a route that does not exist. A test like that is easy to delete in
a refactor, and nothing fails when it goes, because the behaviour it guarded is still there. The table records
which test proves which control, the ASVS requirement it discharges (with its level), and its context and isolation
class. A table with no gate drifts from the suite the first time someone renames a test.

The table was **transcribed**, not generated. Every source it drew on was settled before transcription, so the
risk was loss in copying, not drift. That risk was controlled by exact per-source reconciliation. From now on,
amendments target the table by ID.

## Decision

- **Java:** a custom `@Proves("T-…")` annotation, meta-annotated with JUnit's `@Tag` so that the proving tests can
  be selected as a group. The gate reads each test's ID from the annotation's value.
- **Vitest and Playwright:** the ID appears in the test name.
- **`verify` fails when:**
  - a table row has no test;
  - a test cites an ID that is not in the table;
  - the file at `test-plan.path` (a Maven property) is missing or unreadable (T-BLD-007);
  - the file parses to zero rows (T-BLD-008).

  The last two stop a wrong path from passing as "no rows, no orphans".
- One ID may cover a parameterised or matrix-generated test.
- IDs carry a pillar prefix, and each row has exactly one pillar. IDs are never renumbered or reused. A deleted row
  retires its ID.
- The table is the only thing in its file that parses as rows.

## Considered options

- **No gate; the table as documentation.** It drifts silently.
- **Generate the table from the annotations.** That inverts the dependency: the table would record what the tests
  happen to cover, not what the controls require, and a missing test would produce a missing row instead of a
  failure.
- **A transcribed table plus a two-way gate (chosen).**

## Consequences

- **Stated limit:** the gate proves that a test exists for each row, not what that test asserts. Review and mutation
  testing (PIT, scoped to the security-decision classes) cover the rest.
- The gate runs under Failsafe, pinned at exactly 3.6.0 like the drift gate (ADR-069). Its only bypasses are
  `-DskipITs` and `-Dmaven.test.skip`. With no CI pipeline, a developer's green `verify` is the release gate.
- Moving the table is a one-line change to `test-plan.path`, not a broken gate.
