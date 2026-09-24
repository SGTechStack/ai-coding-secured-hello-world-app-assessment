# Mutation Testing Report — Issue 09: Auth spine (register → login → hello)

**Date:** 2026-09-15
**Ecosystem:** Java 21 (JDK 25 runtime) + Maven 3.9.16 + Spring Boot 4.1.1 / Security 7.1
**Tool:** PIT (org.pitest:pitest 1.21.0 + pitest-junit5-plugin 1.2.3)
**Tool Config:** none in repo — PIT is not a declared plugin, so it was run via the
standalone entry point `org.pitest.mutationtest.commandline.MutationCoverageReport`
on the Maven test classpath (zero build-file changes, per skill rules).
**Baseline Command:** `mvn test` — PASS (49 tests, 0 failures)
**Mutation Command:**
`java -cp target/classes;target/test-classes;<mvn test classpath>;<pitest jars>
 org.pitest.mutationtest.commandline.MutationCoverageReport
 --reportDir target/pit-reports
 --targetClasses "com.example.helloauth.user.*,com.example.helloauth.auth.*,
                  com.example.helloauth.config.*,com.example.helloauth.HelloController,
                  com.example.helloauth.HelloAuthApplication"
 --targetTests "com.example.helloauth.*"
 --outputFormats XML,HTML --threads 4 --timeoutConst 10000`
**Target Scope:** `--scope=changed` — backend classes touched by diff `dfc0e5d..HEAD`
(`user/`, `auth/`, `config/` packages + `HelloController` + `HelloAuthApplication`)
**Mutation Threshold:** 70%

## Summary

- Mutation score: **98.8%** (85/86 killed) — **PASS** (≥ 70% threshold)
- Final run: Total 86 | Killed 85 | Survived 1 | No coverage 0 | Timeout 0 | Skipped 0
- Baseline run: Total 86 | Killed 35 | Survived 41 | No coverage 10 — score 41%
- Line coverage of mutated classes: 183/185 (99%); test strength 99%

## Scores by Target (final run)

| Target | Mutants | Killed | Survived | No Coverage | Score |
|--------|---------|--------|----------|-------------|-------|
| `user.UserService` | 10 | 10 | 0 | 0 | 100% |
| `user.AppUserDetailsService` | 4 | 4 | 0 | 0 | 100% |
| `user.User` (entity) | 9 | 9 | 0 | 0 | 100% |
| `auth.AuthController` | 21 | 20 | 1 | 0 | 95% |
| `auth.ApiExceptionHandler` | 6 | 6 | 0 | 0 | 100% |
| `config.SecurityConfig` | 33 | 33 | 0 | 0 | 100% |
| `config.AppProperties` | 1 | 1 | 0 | 0 | 100% |
| `HelloController` | 1 | 1 | 0 | 0 | 100% |
| `HelloAuthApplication` | 0 | — | — | — | n/a (no mutants generated) |
| auth/user DTOs + `Role`, `UserRepository`, `RegistrationException` | 1* | — | — | — | (*only constructor implicit mutants; none generated) |

## Survivors fixed (40 mutants across 12 sites)

All fixes were **test-only** — no production code changed.

| Finding | Severity | Mutation | Fix |
|---------|----------|----------|-----|
| `AuthController.login` — `SessionAuthenticationStrategy.onAuthentication` removed | **Critical** (session-fixation protection + CSRF rotation) | VoidMethodCall | `loginWithCorrectCredentialsCreatesSession` now asserts the cleared `XSRF-TOKEN` cookie (maxAge 0, empty value) that `CsrfAuthenticationStrategy` emits on rotation |
| `SecurityConfig.authenticationManager` — `setPasswordEncoder` removed | **Critical** (password verification) | VoidMethodCall | `SecurityConfigWiringTests.authenticationManagerAuthenticatesAndPublishesEvents` builds the real bean and authenticates against a BCrypt hash — the default `DelegatingPasswordEncoder` throws on a `$2a$` hash with no `{id}` prefix |
| `SecurityConfig.authenticationManager` — `setAuthenticationEventPublisher` removed | **High** (audit events; ticket-11 lockout listeners depend on them) | VoidMethodCall | Same test asserts `AuthenticationSuccessEvent` reaches a recording `ApplicationEventPublisher` |
| `SecurityConfig.securityFilterChain` / `h2ConsoleSecurityFilterChain` — return null | **Critical/High** | NullReturnVals | Wiring tests build both chains directly via the prototype `HttpSecurity` bean and assert non-null |
| `User.getLockedUntil` null + `AppUserDetailsService:36` negate (`lockedUntil != null` check) | **High** (lockout gate) | NullReturn / NegateConditionals / no-coverage | `loginRejectsLockedAccount` (HTTP seam, generic 401) + `AppUserDetailsServiceTests` unit tests for future/expired lock |
| `User.isEnabled` true-return | **High** (disabled-account gate) | BooleanTrueReturn | `loginRejectsDisabledAccount` (generic 401) + `disabledUserMapsToDisabledUserDetails` |
| `AppUserDetailsService` orElseThrow lambda null | High | NullReturnVals | `unknownUserThrowsUsernameNotFoundException` — note the HTTP seam can't see this: a thrown NPE is wrapped by `AbstractUserDetailsAuthenticationProvider` into `InternalAuthenticationServiceException` → same generic 401. Contract-level unit test kills it properly |
| `UserService.register` `<` boundary on `password-min-length` | High (validation) | ConditionalsBoundary | `registerAcceptsPasswordAtExactlyMinimumLength` — 12-char password must be accepted |
| `AuthController.login` — `setContext` removed | see below | VoidMethodCall | F05 equivalent — documented |
| `AuthController.roleOf` filter true/false returns | Medium | BooleanTrue/False | `AuthControllerRoleOfTests` — reflection-invoked with a `FACTOR_*`-first authority list (HTTP seam can't produce that ordering: Security 7 appends `FACTOR_PASSWORD` after `ROLE_*`) |
| `AuthController.csrf` null return | Medium | NullReturnVals | `csrfBootstrapEndpointEmitsTokenCookie` now asserts `$.token`/`$.headerName` body |
| `AuthController.login` + `ApiExceptionHandler` ×3 — `ProblemDetail.setTitle` removed | Medium (API contract) | VoidMethodCall | `$.title` assertions added to login-failure and all three register-error tests |
| `SecurityConfig` bean nulls ×6 (passwordEncoder, securityContextRepository, csrfTokenRepository, sessionAuthenticationStrategy, authenticationEventPublisher, clock) | Medium (wiring) | NullReturnVals | Direct bean-method assertions in `SecurityConfigWiringTests` (these lines are only covered during context creation, which coverage attributes to one test per cached context — HTTP-seam mutants here are structurally unchallengeable) |
| `SecurityConfig.corsConfigurationSource` — 5 void-call + null-return | Medium (CORS allow-list) | VoidMethodCall / NullReturnVals | `corsConfigurationMatchesTheRatifiedAllowList` asserts exact origins/methods/headers/credentials |
| `SecurityConfig.sessionCookieCustomizer` — null return + 7 negate-conditionals + 7 void-calls (4 previously no-coverage) | **High** (session cookie HttpOnly/SameSite/Secure = session security) | all | `sessionCookieCustomizerAppliesEveryConfiguredAttribute` writes a real `Set-Cookie` via `DefaultCookieSerializer` and asserts every attribute; httpOnly is asserted in the **false→absent** direction because the serializer defaults HttpOnly=true (the true direction is mutation-equivalent), plus `sessionCookieCustomizerAppliesHttpOnlyTrue` for the prod direction |
| `User` getters — getId/getEmail/getCreatedAt/getFailedLoginAttempts/getLastFailedAt (no coverage) | Low (entity boilerplate) | various | Assertions folded into `registerCreatesEnabledUserAccount` and `loginRejectsLockedAccount` |

## Remaining survivor (1)

### [F05] Equivalent mutation — confirmed by manual inspection AND experiment

**Target:** `AuthController.login` line 98 — `securityContextHolderStrategy.setContext(context)` removed
**Severity:** Low
**Evidence:** Verified two ways. (1) Code inspection: Security 7's
`AnonymousAuthenticationFilter` wraps the holder in a lazy
`SingletonSupplier` deferred context; `HttpSessionSecurityContextRepository.saveContext`
writes the `SPRING_SECURITY_CONTEXT` session attribute eagerly, so any holder
read after `saveContext` resolves the persisted (authenticated) context anyway —
and nothing reads the holder between `authenticate` and `saveContext`.
(2) Experiment: the call was physically removed and `LoginContextHolderTests`
(a `postHandle` interceptor capturing `SecurityContextHolder.getContext()`)
still observed the authenticated principal — the mutation is not observable in
this stack. The line is kept: it is part of the documented Spring manual-auth
pattern and guards any future in-request post-auth processing.
**Recommendation:** None — equivalent mutant, documented. Do not exclude in
config (no config exists); accept as a known-equivalent survivor.

## Remediation Priority

1. [Critical] Session-fixation strategy call, `securityFilterChain` null, `setPasswordEncoder` — **all fixed**
2. [High] Lockout/disabled gates, event-publisher wiring, session-cookie attributes, password-length boundary — **all fixed**
3. [Medium] Problem titles, CORS allow-list, CSRF body, roleOf factor-filter, bean wiring — **all fixed**
4. [Low] 1 confirmed-equivalent survivor — documented above

## Artifacts

- Machine-readable report: `backend/target/pit-reports/mutations.xml`
- HTML report: `backend/target/pit-reports/index.html`
- Test reports: `backend/target/surefire-reports/`

## History

- Previous score (first run, pre-fix): 41% (35/86 killed, 41 survived, 10 no-coverage)
- Current score: 98.8% (85/86 killed)
- Delta: **+57.8 points** — all Critical/High survivors eliminated

## Test changes made

- `AuthFlowTests` (+4 tests, +assertions): min-length boundary, locked account,
  disabled account, admin role; `$.title` on all problem responses; XSRF-TOKEN
  rotation cookie; CSRF body fields; entity field assertions.
- `SecurityConfigWiringTests` (new, 13 tests): direct `@Bean` factory behavior —
  chains build, BCrypt encoder, repositories/strategy types, auth-manager
  authenticate+events, CORS allow-list, session-cookie customizer both directions.
- `AppUserDetailsServiceTests` (new, 5 tests): UserDetails mapping contract —
  lock/disabled/unknown-user/authority mapping.
- `AuthControllerRoleOfTests` (new, 3 tests): `roleOf` factor-skip + fallback.
- `LoginContextHolderTests` (new, 1 test): post-login holder contents via
  `postHandle` interceptor (documents the deferred-context behavior).
