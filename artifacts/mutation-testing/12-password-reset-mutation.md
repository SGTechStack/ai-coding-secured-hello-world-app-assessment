# Mutation Testing — Ticket 12 (Password reset flow)

**Scope:** `com.example.helloauth.passwordreset` package, plus ticket-12 changes to
`auth/ApiExceptionHandler`, `config/AppProperties`, `user/UserRepository`
(working tree vs `f609e2d`).

**Tool:** PIT (standalone `MutationCoverageReport` entry point on the Maven
test classpath — same convention as ticket 11; see that report for setup).
`backend/target/test-cp.txt` regenerated via `mvn dependency:build-classpath`.

**Baseline:** `mvn test` 95/95 green before the run.

## Result

| Metric | Value |
|---|---|
| Mutants generated | 50 |
| Killed | 49 |
| Survived | 1 |
| **Score** | **98%** |

Machine-readable: `backend/target/pit-reports/mutations.xml`; HTML:
`backend/target/pit-reports/index.html`.

## Survivor analysis

**1 survivor — equivalent, Low severity, accepted.**

- `PasswordResetService.hashToken:139` — `MathMutator`: replaced integer
  multiplication with division in `new StringBuilder(digest.length * 2)`.
  The expression is only the `StringBuilder` *capacity hint*; `digest.length
  / 2` under-sizes the buffer, which then grows transparently. Output is
  byte-identical — the mutation changes allocation, not behavior. No test
  can (or should) observe a capacity hint. Documented equivalent, no fix.

## Test changes

**None.** The ticket-12 suite (`PasswordResetFlowTests` 9, `EmailServiceTests`
1) plus the pre-existing suite kill every non-equivalent mutant — including
all boundary mutants on the TTL check, the `used_at` single-use predicate,
and the session-purge call sequence.

## Notes

- The run completed under a prior agent session; the process exited before
  writing this report. Tallies above are reconstructed from the finished
  `mutations.xml` (well-formed, all 50 mutations detected, `</mutations>`
  closed).
