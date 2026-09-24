# Mutation Testing Report — Issue 11: Account lockout + IP throttle

**Date:** 2026-09-15
**Ecosystem:** Java 21 (JDK 25 runtime) + Maven 3.9.16 + Spring Boot 4.1.1 / Security 7.1
**Tool:** PIT (org.pitest:pitest 1.21.0 + pitest-junit5-plugin 1.2.3)
**Tool Config:** none in repo — PIT is not a declared plugin, so it was run via the
standalone entry point `org.pitest.mutationtest.commandline.MutationCoverageReport`
on the Maven test classpath (zero build-file changes, per skill rules) — the same
convention established by ticket 09.
**Baseline Command:** `mvn test` — PASS (83 tests, 0 failures)
**Mutation Command:**
`java -cp target/classes;target/test-classes;<mvn test classpath>;<pitest jars
 incl. commons-text/commons-lang3 for the XML report>
 org.pitest.mutationtest.commandline.MutationCoverageReport
 --reportDir target/pit-reports
 --targetClasses "com.example.helloauth.auth.LoginService,
                  com.example.helloauth.auth.IpThrottleService*,
                  com.example.helloauth.auth.LoginThrottledException,
                  com.example.helloauth.auth.AuthController,
                  com.example.helloauth.auth.ApiExceptionHandler,
                  com.example.helloauth.config.AppProperties*"
 --targetTests "com.example.helloauth.*"
 --outputFormats XML,HTML --threads 4 --timeoutConst 10000
 --sourceDirs src/main/java`
**Target Scope:** `--scope=changed` — the six production classes touched by the
ticket-11 working-tree diff vs HEAD `2101136` (`LoginService`, `IpThrottleService`
+ `FailureBucket`, `LoginThrottledException`, `AuthController`,
`ApiExceptionHandler`, `AppProperties` + `Lockout`/`IpThrottle` nested types).
`SecurityConfig`'s diff is comment-only and was not re-mutated.
**Mutation Threshold:** 70%

## Summary

- Mutation score: **98.8%** (79/80 killed) — **PASS** (≥ 70% threshold)
- Total 80 | Killed 79 | Survived 1 | No coverage 0 | Timeout 0 | Skipped 0
- Line coverage of mutated classes: 158/158 (100%); test strength 99%
- The single survivor is the `AuthController.login` `setContext` removal —
  the same mutant ticket 09 proved equivalent by inspection and experiment.

## Scores by Target

| Target | Mutants | Killed | Survived | No Coverage | Score |
|--------|---------|--------|----------|-------------|-------|
| `auth.LoginService` | 29 | 29 | 0 | 0 | 100% |
| `auth.IpThrottleService` | 9 | 9 | 0 | 0 | 100% |
| `auth.IpThrottleService$FailureBucket` | 8 | 8 | 0 | 0 | 100% |
| `auth.LoginThrottledException` | 0 | — | — | — | n/a (no mutants generated — only a `super(msg)` constructor) |
| `auth.AuthController` | 18 | 17 | 1 | 0 | 94% |
| `auth.ApiExceptionHandler` | 7 | 7 | 0 | 0 | 100% |
| `config.AppProperties` | 3 | 3 | 0 | 0 | 100% |
| `config.AppProperties$Lockout` | 3 | 3 | 0 | 0 | 100% |
| `config.AppProperties$IpThrottle` | 3 | 3 | 0 | 0 | 100% |

## New-code coverage notes (all killed)

The ticket-11 logic is fully pinned — no weak-assertion or boundary-gap
survivors in the new code:

- **Ordering invariant** (`LoginService.login`): negate-conditionals on the
  `remoteAddr != null && isThrottled` gate and the `isLocked` triple-condition
  are killed by `throttledAttemptPerformsNoLookupOrAuthentication`,
  `lockedAccountIsRejectedBeforeCredentialVerification`, and the
  `nullRemoteAddrSkipsTheIpLayer` defensive-path test.
- **Lock trigger / window math** (`recordFailure`): `ConditionalsBoundary` and
  `NegateConditionals` on `lastFailedAt.plus(window).isBefore(now)` and
  `failures >= maxFailures`, plus `MathMutator` on `failures + 1`, are killed
  by the streak tests including `failureAtExactlyTheWindowEdgeStillExtendsTheStreak`
  and `nthFailureInsideWindowSetsLockedUntil` (exact `locked_until = now + 15m`).
- **Success-reset guard** (`resetFailureState`): the three-part
  `attempts > 0 || lastFailedAt != null || lockedUntil != null` condition
  survives negation in every direction — killed by
  `successfulLoginWithCleanStateSkipsTheSave` (save must NOT happen) and
  `successResetsTheAccountFailureState` (save must happen).
- **Sliding window** (`FailureBucket.record`/`isThrottled`): all boundary and
  negate mutants killed by `IpThrottleServiceTests` (decay at one full quiet
  window, re-anchoring, threshold reach, per-IP independence).
- **HTTP seam**: the 429-vs-401 contract is pinned by
  `singleSourceIpCanNeverLockAnAccount` (asserts `application/problem+json`,
  counter frozen at 2, victim still logs in) and
  `throttleEngagesAcrossMultipleUsernames`.
- **Property binding** (`AppProperties` nested getters/setters):
  `NullReturnVals`/`PrimitiveReturns` mutants killed by
  `lockoutAndThrottleTunablesBindAsAppProperties`.

## Findings

### [F05] Equivalent mutation — confirmed by manual inspection AND experiment (carried over from ticket 09)

**Target:** `AuthController.login` line 98 —
`securityContextHolderStrategy.setContext(context)` removed
**Mutation:** VoidMethodCallMutator (13 tests ran it; none detected it)
**Severity:** Low
**Evidence:** Identical mutant, identical code — ticket 09's report documents
the two-part verification. (1) Inspection: Security 7's
`AnonymousAuthenticationFilter` wraps the holder in a lazy
`SingletonSupplier` deferred context;
`HttpSessionSecurityContextRepository.saveContext` (line 99, unchanged)
writes the `SPRING_SECURITY_CONTEXT` session attribute eagerly, so any holder
read after `saveContext` resolves the persisted authenticated context anyway —
and nothing reads the holder between `authenticate` and `saveContext`. The
ticket-11 refactor moved credential verification into `LoginService` but left
this block byte-identical, so the reasoning transfers verbatim. (2) Experiment
(ticket 09): physically removing the call still let `LoginContextHolderTests`
(a `postHandle` interceptor reading `SecurityContextHolder.getContext()`)
observe the authenticated principal — that test still exists and passes.
**Recommendation:** None — equivalent mutant, documented. Do not exclude in
config (no config exists); accept as a known-equivalent survivor.

## Remediation Priority

1. [Critical] — none outstanding (ordering/throttle/lock mutants all killed)
2. [High] — none outstanding
3. [Medium] — none outstanding
4. [Low] 1 confirmed-equivalent survivor — documented above

## Artifacts

- Machine-readable report: `backend/target/pit-reports/mutations.xml`
- HTML report: `backend/target/pit-reports/index.html`
- Test reports: `backend/target/surefire-reports/`

## History

- Previous comparable run: ticket 09 (`09-auth-spine-mutation.md`) — 98.8%
  (85/86) on the wider auth spine scope; same sole survivor.
- Current score: 98.8% (79/80) on the ticket-11 changed-class scope.
- Delta: stable — the new classes reach 100% killed; the only survivor is the
  pre-existing known-equivalent.

## Test changes made

None required by this run — the ticket-11 test suite
(`LoginServiceTests`, `IpThrottleServiceTests`, `LockoutAndThrottleApiTests`,
plus the `AuthFlowTests` cache-reset hook) already kills every mutant in the
changed code. Zero production code was touched.
