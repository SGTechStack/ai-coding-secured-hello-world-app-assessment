# Mutation Testing Report — Issue 13: Admin module (bootstrap, seeding, user management)

**Date:** 2026-09-16
**Ecosystem:** Java 21 (JDK 25 runtime) + Maven 3.9.16 + Spring Boot 4.1.1 / Security 7.1
**Tool:** PIT (org.pitest:pitest 1.21.0 + pitest-entry + pitest-command-line +
pitest-html-report 1.21.0 + pitest-junit5-plugin 1.2.3)
**Tool Config:** none in repo — PIT is not a declared plugin, so it was run via the
standalone entry point `org.pitest.mutationtest.commandline.MutationCoverageReport`
on the Maven test classpath (zero build-file changes, per skill rules) — the same
convention established by ticket 09 and reused for tickets 11–12.
`backend/target/test-cp.txt` was regenerated via
`mvn dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt
-Dmdep.includeScope=test` before the run. `junit-platform-launcher-6.0.3` (matching
the JUnit 6.0.3 engine on the test classpath) plus `commons-text-1.13.0` /
`commons-lang3-3.16.0` (for the XML/HTML report writers) were added from `~/.m2`.
Note: in PIT 1.21.x the `MutationCoverageReport` main class lives in
`pitest-command-line`, not `pitest-entry` — the classpath needs both.
**Baseline Command:** `mvn test` — PASS (117 tests, 0 failures)
**Mutation Command:**
`java -cp target/classes;target/test-classes;<mvn test classpath>;<pitest jars
 incl. pitest-command-line, junit-platform-launcher, commons-text/commons-lang3>
 org.pitest.mutationtest.commandline.MutationCoverageReport
 --reportDir target/pit-reports
 --targetClasses "com.example.helloauth.admin.*,
                  com.example.helloauth.session.*,
                  com.example.helloauth.auth.ApiExceptionHandler,
                  com.example.helloauth.config.AppProperties*,
                  com.example.helloauth.passwordreset.PasswordResetService"
 --targetTests "com.example.helloauth.*"
 --outputFormats XML,HTML --threads 4 --timeoutConst 10000
 --sourceDirs src/main/java`
**Target Scope:** `--scope=changed` — production classes touched by the ticket-13
working-tree diff vs HEAD `554813b`: the new `admin` package (`AdminService`,
`AdminController`, `AdminSeeder`, `AdminException` + nested types,
`AdminUserResponse`, `UpdateRoleRequest`, `UpdateStatusRequest`), the new
`session/SessionInvalidationService`, and the ticket-13 edits to
`auth/ApiExceptionHandler`, `config/AppProperties` (+ `Admin` nested type), and
`passwordreset/PasswordResetService` (delegates session purge to
`SessionInvalidationService`). `UserRepository` and
`PasswordResetTokenRepository` are interfaces (new derived queries only) — no
mutable bytecode. `AdminException`'s `super(msg)` constructors generate no
mutants.
**Mutation Threshold:** 70%

## Summary

- Mutation score: **98.9%** (89/90 killed) — **PASS** (≥ 70% threshold)
- Total 90 | Killed 89 | Survived 1 | No coverage 0 | Timeout 0 | Non-viable 0
- Line coverage of mutated classes: 221/225 (98%); test strength 99%
- The single survivor is the `PasswordResetService.hashToken` `StringBuilder`
  capacity-hint mutant — the same mutant ticket 12 proved equivalent (line moved
  139 → 136 by the session-invalidation refactor; bytecode unchanged).

## Scores by Target

| Target | Mutants | Killed | Survived | Score |
|--------|---------|--------|----------|-------|
| `admin.AdminService` | 17 | 17 | 0 | 100% |
| `admin.AdminController` | 5 | 5 | 0 | 100% |
| `admin.AdminSeeder` | 17 | 17 | 0 | 100% |
| `admin.AdminUserResponse` | 1 | 1 | 0 | 100% |
| `admin.AdminException` (+`SelfAction`,`UserNotFound`), `UpdateRoleRequest`, `UpdateStatusRequest` | 0 | — | — | n/a (no mutable code) |
| `session.SessionInvalidationService` | 1 | 1 | 0 | 100% |
| `auth.ApiExceptionHandler` | 13 | 13 | 0 | 100% |
| `config.AppProperties` (+`Admin`,`Lockout`,`IpThrottle`,`PasswordReset`) | 16 | 16 | 0 | 100% |
| `passwordreset.PasswordResetService` | 20 | 19 | 1 | 95% |

## New-code coverage notes (all killed)

The ticket-13 logic is fully pinned — no weak-assertion, boundary-gap, or
no-coverage survivors in the new code:

- **Self-action guard** (`AdminService.requireOtherAccount`): the
  `target.getUsername().equals(actorUsername)` NegateConditionals mutant is
  killed by `adminCannotChangeOwnStatus`/`adminCannotChangeOwnRole`/
  `adminCannotDeleteSelf` (self-target → 400, state unchanged) and the happy-path
  tests (other-target must proceed). The `findById` `orElseThrow` path is killed
  by `mutationsReturnNotFoundForUnknownUserId` (all three verbs → 404).
- **Disable/demote/delete → session invalidation** (`setEnabled`, `setRole`,
  `deleteUser`): the `!enabled` negation, the two-part demotion condition
  (`old == ADMIN && new == USER`), and every `VoidMethodCallMutator` on
  `invalidateAllFor`/`users.save`/`users.delete`/`tokens.deleteByUser` are killed
  by `statusToggleDisablesAccountKillsSessionsAndBlocksLogin` (replayed cookie →
  401, fresh login → 401), `roleChangePromotesAndDemotesAnotherAccount` (demoted
  session dead on `/api/admin/**` AND `/api/auth/me`), and
  `deleteRemovesAccountSessionsAndResetTokens` (outstanding reset token forces
  the `deleteByUser`-first ordering — removing that call makes the FK reject the
  user delete → 500 → killed).
- **Seeder guards** (`AdminSeeder.seedAdminIfAbsent`): negate-conditionals on
  `existsByRole(ADMIN)` (idempotence), `isBlank(username) || isBlank(password)`
  (not-configured stand-down), `existsByUsername` and `existsByEmail` (unique-
  constraint collision stand-downs), plus all the `User` setter/`users.save`
  removals, are killed by `AdminSeedingTests` — including
  `restartWithExistingAdminSeedsNothing`,
  `existingAdminSuppressesSeedingOfConfiguredAccount`,
  `blankCredentialsSeedNothing`,
  `configuredUsernameTakenByPlainUserSkipsSeed`, and
  `configuredEmailTakenByPlainUserSkipsSeed`. The weak-password warning
  (`password.length() < minLength`) conditional-boundary mutants are killed too —
  the boundary sits inside a log-only branch, but the surrounding condition's
  mutants feed into the seed decision paths the tests pin.
- **HTTP seam** (`AdminController`, `ApiExceptionHandler`): every endpoint's
  `authentication.getName()` null-return and service-call removals are killed by
  the `AdminApiTests` MockMvc suite; the two new `@ExceptionHandler` methods'
  `ProblemDetail` construction/`setTitle` mutants are killed by the 400/404
  `jsonPath("$.title")` assertions.
- **Property binding** (`AppProperties$Admin` getters/setters,
  `getAdmin`): `NullReturnVals`/`EmptyObjectReturnVals` mutants are killed by
  `AdminSeedingTests.startupSeedsConfiguredAdmin`, which boots the context with
  `app.admin.*` set and asserts the seeded row's username/email/BCrypt hash.
- **PasswordResetService delegation refactor**: all ticket-12 mutants remain
  killed, including the `sessionInvalidation.invalidateAllFor` call (the
  password-reset session-purge tests still cover it through the new seam) and the
  TTL/used-at boundaries.

## Findings

### [F05] Equivalent mutation — confirmed by manual inspection (carried over from ticket 12)

**Target:** `PasswordResetService.hashToken` line 136 —
`new StringBuilder(digest.length * 2)`
**Mutation:** MathMutator — replaced integer multiplication with division
(12 tests ran it; none detected it)
**Severity:** Low
**Evidence:** Identical mutant, same method — ticket 12's report documents the
reasoning, which transfers verbatim. The expression is only the `StringBuilder`
*capacity hint*; `digest.length / 2` under-sizes the buffer, which then grows
transparently. The emitted hex string is byte-identical — the mutation changes
allocation, not behavior. No test can (or should) observe a capacity hint. The
ticket-13 refactor only moved the line number (139 → 136); the mutated
instruction is unchanged.
**Recommendation:** None — equivalent mutant, documented. Accept as a
known-equivalent survivor (no config exists to exclude it in, per project
convention).

## Remediation Priority

1. [Critical] — none outstanding (self-action guard, session invalidation on
   disable/demote/delete, seeder idempotence mutants all killed)
2. [High] — none outstanding
3. [Medium] — none outstanding
4. [Low] 1 confirmed-equivalent survivor — documented above

## Artifacts

- Machine-readable report: `backend/target/pit-reports/mutations.xml`
- HTML report: `backend/target/pit-reports/index.html`
- Test reports: `backend/target/surefire-reports/`
- Regenerated test classpath: `backend/target/test-cp.txt`

## History

- Previous comparable run: ticket 12 (`12-password-reset-mutation.md`) — 98%
  (49/50) on the password-reset scope; same sole equivalent survivor.
- Current score: 98.9% (89/90) on the ticket-13 changed-class scope.
- Delta: stable — all new admin/session classes reach 100% killed; the only
  survivor is the pre-existing known-equivalent in `hashToken`.

## Test changes made

None required by this run — the ticket-13 test suite (`AdminApiTests` 14 tests,
`AdminSeedingTests` 6 tests) plus the pre-existing suite kill every
non-equivalent mutant in the changed code, including all boundary mutants on the
self-action guard, the demotion predicate, the seeder guards, and the
exception-handler status mapping. Zero production code was touched.
