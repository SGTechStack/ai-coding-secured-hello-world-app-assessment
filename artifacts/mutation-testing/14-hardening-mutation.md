# Mutation Testing Report — Issue 14: Hardening (audit logging, prod polish, reset-token housekeeping)

**Date:** 2026-09-16
**Ecosystem:** Java 21 (JDK 25 runtime) + Maven 3.9.16 + Spring Boot 4.1.1 / Security 7.1
**Tool:** PIT (org.pitest:pitest 1.21.0 + pitest-entry + pitest-command-line +
pitest-html-report 1.21.0 + pitest-junit5-plugin 1.2.3)
**Tool Config:** none in repo — PIT is not a declared plugin, so it was run via the
standalone entry point `org.pitest.mutationtest.commandline.MutationCoverageReport`
on the Maven test classpath (zero build-file changes, per skill rules) — the same
convention established by ticket 09 and reused for tickets 11–13.
`backend/target/test-cp.txt` was regenerated via
`mvn dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt
-Dmdep.includeScope=test` before the run (required — ticket 14 added the
`logstash-logback-encoder` dependency). `junit-platform-launcher-6.0.3` (matching
the JUnit 6.0.3 engine on the test classpath) plus `commons-text-1.13.0` /
`commons-lang3-3.16.0` (for the XML/HTML report writers) were added from `~/.m2`.
Note: in PIT 1.21.x the `MutationCoverageReport` main class lives in
`pitest-command-line`, not `pitest-entry` — the classpath needs both.
**Baseline Command:** `mvn test` — PASS (153 tests, 0 failures; 148 at the
pre-remediation baseline)
**Mutation Command:**
`java -cp target/classes;target/test-classes;<mvn test classpath>;<pitest jars
 incl. pitest-command-line, junit-platform-launcher, commons-text/commons-lang3>
 org.pitest.mutationtest.commandline.MutationCoverageReport
 --reportDir target/pit-reports
 --targetClasses "com.example.helloauth.audit.*,
                  com.example.helloauth.config.ProdAdminCredentialsValidator,
                  com.example.helloauth.config.SecurityTunablesValidator,
                  com.example.helloauth.config.SecurityConfig,
                  com.example.helloauth.config.AppProperties*,
                  com.example.helloauth.passwordreset.PasswordResetTokenJanitor,
                  com.example.helloauth.passwordreset.PasswordResetService,
                  com.example.helloauth.auth.LoginService,
                  com.example.helloauth.admin.AdminService"
 --targetTests "com.example.helloauth.*"
 --outputFormats XML,HTML --threads 4 --timeoutConst 10000
 --sourceDirs src/main/java`
**Target Scope:** `--scope=changed` — production classes touched by the ticket-14
working-tree diff vs HEAD `a6998f7`: the new `audit` package (`AuditLogger` incl.
the `afterCommit` emission seam + `Reasons`, `AuditAuthenticationEvents`), the new
`config/ProdAdminCredentialsValidator` and `config/SecurityTunablesValidator`
fail-fast beans, `passwordreset/PasswordResetTokenJanitor`, and the ticket-14
edits to `passwordreset/PasswordResetService` (one-live-token + atomic
`consumeIfUnused` + audit calls), `auth/LoginService` (throttled/locked/lock-
engaged audit calls), `admin/AdminService` (change-guarded audit calls),
`config/AppProperties` (new `Cors` group), and `config/SecurityConfig` (CORS now
bound from properties). `PasswordResetTokenRepository` is an interface (new
`@Modifying` queries only) — no mutable bytecode; the queries themselves are
pinned end-to-end by `PasswordResetHousekeepingTests`. `PasswordResetToken`
(comment-only), `AdminSeeder` (javadoc-only), and `HelloAuthApplication`
(`@EnableScheduling` annotation only) produce no new mutants.
**Mutation Threshold:** 70%

## Summary

- Mutation score: **99.4%** (165/166 killed) — **PASS** (≥ 70% threshold)
- Total 166 | Killed 165 | Survived 1 | No coverage 0 | Timeout 0 | Non-viable 0
- Line coverage of mutated classes: 347/350 (99%); test strength 99%
- First run (before remediation): 159/166 (95.8%) — 4 SURVIVED + 3 NO_COVERAGE,
  all in `AuditAuthenticationEvents.reasonOf` and `PasswordResetTokenJanitor.
  purgeDeadTokens`. All 7 were fixed by test-only additions (see "Test changes
  made"); the only remaining survivor is the pre-existing known-equivalent
  `hashToken` `StringBuilder` capacity-hint mutant (same as tickets 12–13;
  it sits at line 164 after the ticket-14 edits).

## Scores by Target (final run)

| Target | Mutants | Killed | Survived | Score |
|--------|---------|--------|----------|-------|
| `audit.AuditLogger` (+`$1` sync, `Reasons`) | 17 | 17 | 0 | 100% |
| `audit.AuditAuthenticationEvents` | 9 | 9 | 0 | 100% (was 4/9) |
| `config.ProdAdminCredentialsValidator` | 5 | 5 | 0 | 100% |
| `config.SecurityTunablesValidator` | 2 | 2 | 0 | 100% |
| `config.SecurityConfig` | 32 | 32 | 0 | 100% |
| `config.AppProperties` (+`Admin`,`Cors`,`IpThrottle`,`Lockout`,`PasswordReset`) | 18 | 18 | 0 | 100% |
| `passwordreset.PasswordResetTokenJanitor` | 3 | 3 | 0 | 100% (was 1/3) |
| `passwordreset.PasswordResetService` | 22 | 21 | 1 | 95% |
| `auth.LoginService` | 34 | 34 | 0 | 100% |
| `admin.AdminService` | 24 | 24 | 0 | 100% |

## New-code coverage notes (all killed)

The ticket-14 logic is fully pinned — every non-equivalent mutant in the new
code dies, including the audit-critical seams:

- **Commit-aware emission** (`AuditLogger.emit`): the
  `isSynchronizationActive` negation and the `registerSynchronization` /
  `write` removals are killed by `AuditLoggerTests`' real-DataSource-transaction
  tests — `eventInsideATransactionEmitsOnlyAfterCommit` (synchronous emission
  would append inside the tx; nothing did) and
  `eventInsideARolledBackTransactionIsNeverEmitted` (a rollback leaves zero
  audit lines — the false-positive window this class exists to close).
- **Field vocabulary** (`AuditLogger` event methods): every
  `VoidMethodCallMutator` on `emit`/`fields.put` and every marker-carrying
  call is killed by `AuditLoggerTests` (per-method marker assertions) and
  `AuditLoggingTests` (HTTP-seam markers + a real `LogstashEncoder` round-trip
  asserting `event`/`actor`/`reason` land as top-level JSON keys).
- **Reason mapping** (`AuditAuthenticationEvents.reasonOf`): all four branches
  pinned — `bad_credentials` via the wrong-password HTTP tests, `disabled`
  via the new `disabledAccountLoginEmitsDisabledReason` (provider's pre-auth
  `DisabledException` → event → `reason=disabled`), and `locked` + the
  `unknown` collapse via the new narrow-seam `AuditAuthenticationEventsTests`
  (concrete `AuthenticationFailureLockedEvent` /
  `AuthenticationFailureServiceExceptionEvent` fed straight to the listener —
  the service-level lock gate throws before `authenticate()`, so those events
  have no HTTP trigger).
- **Service-level rejections** (`LoginService`): `audit.loginThrottled`,
  `audit.loginFailed(…, "locked")`, and the lock-engagement refactor
  (`lockedUntil` ternary + post-`save` `audit.accountLocked`) — every
  `VoidMethodCallMutator` and conditional negation is killed by
  `LockoutAndThrottleApiTests` (behavioral) and `AuditLoggingTests`
  (`reason=ip_throttled` + `remote_addr`, `account_locked` + `locked_until`,
  `reason=locked` assertions).
- **One live token + atomic consume** (`PasswordResetService`): the
  `invalidateOutstandingForUser` call removals (request-side and
  confirm-side), the `consumeIfUnused(...) == 0` negation/boundary, and the
  `passwordResetCompleted` removal are killed by
  `PasswordResetHousekeepingTests` — `newRequestInvalidatesTheAccounts
  OutstandingTokens` (superseded link → generic 400, fresh link works),
  `confirmInvalidatesOtherOutstandingTokensForTheAccount` (sibling dies with
  the consumed one), and `consumeIfUnusedClaimsTheTokenExactlyOnce`
  (first UPDATE → 1 row, second → 0). The `used_at != null || !expires.isAfter`
  generic-rejection boundaries stay killed by `PasswordResetFlowTests`.
- **Admin audit calls** (`AdminService`): the `changed` guard negations and
  all three `VoidMethodCallMutator`s on `adminStatusChanged`/`adminRoleChanged`/
  `adminUserDeleted` are killed by `AuditLoggingTests.
  adminMutationsEmitActorAndTarget` (actor+target+change in the marker).
- **Fail-fast validators**: `SecurityTunablesValidator`'s `throttleMax >=
  lockoutMax` boundary/negation mutants are killed by
  `StartupConfigValidationTests` — including the equality case
  (`5 vs 5` must refuse, pinning `>=` not `>`). `ProdAdminCredentialsValidator`'s
  `isBlank ||` negations and the throw removal are killed by
  `prodProfileWithoutAdminCredentialsRefusesToStart` (prod boot fails naming
  `APP_ADMIN_USERNAME`) plus `ProdProfileTests` (prod boot succeeds with creds).
- **CORS binding** (`AppProperties$Cors` + `SecurityConfig`): the
  `getCors().getAllowedOrigins()` `NullReturnVals`/`EmptyObjectReturnVals`
  mutants and the `setAllowedOrigins` removal are killed by
  `ProdProfileTests.corsAllowListComesFromTheEnvironment` (env-bound origins
  reach `CorsConfigurationSource`, localhost default absent under prod) and
  `SecurityConfigWiringTests` (default wiring).
- **Janitor** (`PasswordResetTokenJanitor.purgeDeadTokens`): after remediation,
  the `deleted > 0` guard mutants are killed by the new
  `janitorLogsThePurgeCountOnlyWhenRowsWereDeleted` — "Purged 1" logged on a
  real purge, silence on a no-op run.

## Findings

### [F05] Equivalent mutation — confirmed by manual inspection (carried over from tickets 12–13)

**Target:** `PasswordResetService.hashToken` line 164 —
`new StringBuilder(digest.length * 2)`
**Mutation:** MathMutator — replaced integer multiplication with division
**Severity:** Low
**Evidence:** Identical mutant, same method — tickets 12 and 13 document the
reasoning, which transfers verbatim. The expression is only the `StringBuilder`
*capacity hint*; `digest.length / 2` under-sizes the buffer, which then grows
transparently. The emitted hex string is byte-identical — the mutation changes
allocation, not behavior. No test can (or should) observe a capacity hint. The
ticket-14 edits only moved the line number (136 → 164); the mutated instruction
is unchanged. (The first run of this session happened to kill it — the mutant
is detectable only by accident, e.g. timing-dependent flakiness, not by any
assertion; the second, clean run leaves it as the documented survivor.)
**Recommendation:** None — equivalent mutant, documented. Accept as a
known-equivalent survivor (no config exists to exclude it in, per project
convention).

## Remediation performed (all test-only; zero production changes)

The first run left 7 non-killed mutants; all were closed by strengthening or
adding tests:

1. **[F01/F03] `reasonOf` `disabled` branch** (lines 55–56, SURVIVED —
   Critical domain: audit reason vocabulary). `AdminApiTests` already drove a
   disabled-account login through the provider (`DisabledException` →
   `AuthenticationFailureDisabledEvent`), but no test asserted the emitted
   reason. Added `AuditLoggingTests.disabledAccountLoginEmitsDisabledReason`:
   seeded user disabled via the repository, login → 401, asserts a single
   `reason=disabled` marker with `actor=alice`. Kills both the
   NegateConditionals and the `return ""` EmptyObjectReturnVals mutants.
2. **[F01] `reasonOf` `locked`/`unknown` branches** (lines 58–61, NO_COVERAGE —
   Critical domain). The service-level lock gate throws before `authenticate()`
   runs, so provider-level `LockedException` events (and unmapped exception
   types) have no HTTP trigger. Added `audit/AuditAuthenticationEventsTests`
   (3 tests, same narrow-seam ListAppender idiom as `AuditLoggerTests`):
   `AuthenticationFailureLockedEvent` → `reason=locked`,
   `AuthenticationFailureDisabledEvent` → `reason=disabled` (belt-and-suspenders
   at the unit seam), and `AuthenticationFailureServiceExceptionEvent` →
   `reason=unknown` with negative assertions that neither the exception's
   message nor its class name leaks into the field vocabulary.
3. **[F02] `PasswordResetTokenJanitor` `deleted > 0` log guard** (line 44,
   2 SURVIVED — Medium: ops INFO log, not an audit line, but load-bearing for
   log truthfulness). Added `PasswordResetHousekeepingTests.
   janitorLogsThePurgeCountOnlyWhenRowsWereDeleted`: ListAppender on the
   janitor logger — a 1-row purge must log exactly
   "Purged 1 dead password-reset token(s).", a 0-row run must log nothing
   (kills both the negation and the `>=` boundary).

## Remediation Priority

1. [Critical] — none outstanding (all audit-emission, reason-vocabulary,
   atomic-consume, and fail-fast mutants killed)
2. [High] — none outstanding
3. [Medium] — none outstanding (janitor log-guard mutants killed this session)
4. [Low] 1 confirmed-equivalent survivor — documented above

## Artifacts

- Machine-readable report: `backend/target/pit-reports/mutations.xml`
- HTML report: `backend/target/pit-reports/index.html`
- Test reports: `backend/target/surefire-reports/`
- Regenerated test classpath: `backend/target/test-cp.txt`

## History

- Previous comparable run: ticket 13 (`13-admin-module-mutation.md`) — 98.9%
  (89/90) on the admin-module scope; same sole equivalent survivor.
- Current score: 99.4% (165/166) on the ticket-14 changed-class scope
  (95.8% before this session's test-only remediation).
- Delta: improved — all new audit/validator/janitor classes reach 100% killed;
  the only survivor is the pre-existing known-equivalent in `hashToken`.

## Test changes made

Test-only; zero production code touched:

- `backend/src/test/java/com/example/helloauth/AuditLoggingTests.java` —
  added `disabledAccountLoginEmitsDisabledReason` (10 tests total).
- `backend/src/test/java/com/example/helloauth/audit/AuditAuthenticationEventsTests.java` —
  new file, 3 tests covering the `locked`/`disabled`/`unknown` reason
  mappings through concrete failure events at the listener seam.
- `backend/src/test/java/com/example/helloauth/PasswordResetHousekeepingTests.java` —
  added `janitorLogsThePurgeCountOnlyWhenRowsWereDeleted` (5 tests total).

Full suite re-verified after remediation: 153 tests, 0 failures, 0 errors.
