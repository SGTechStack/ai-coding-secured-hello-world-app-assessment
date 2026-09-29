# Independent review: tickets 01 and 02 (`1198c5b..806135c`)

Reviewer: independent second pass (read-only). Commits in range:

```
806135c merge: ticket 02 SPA skeleton and document headers
7bbbbc9 chore(frontend): pin LF line endings for the SPA sources
b9f150d docs(review): add ticket 02 compliance report
f96a55b test(frontend): add Vitest + MSW and Playwright harnesses
a0b57ce feat(frontend): add SPA skeleton with hardened document headers
6a5e45d merge: ticket 01 backend skeleton and test harness
6295268 docs(review): add ticket 01 compliance report
f205d93 feat(backend): add Spring Boot 4.1 skeleton and test harness
2110984 chore: add root .gitignore for backend, frontend, H2 and log outputs
```

Sources used: tickets `.scratch/secured-hello-world-build/issues/01-*.md` and `02-*.md`; `docs/spec.md` (Observability,
Frontend, Testing sections); `docs/test-plan/test-plan.md` rows for every T-ID cited in the range; ADR-001, ADR-040,
ADR-060, ADR-061, ADR-065, ADR-066, ADR-067. The repo has no coding-standards document (no `CODING_STANDARDS.md`,
`CONTRIBUTING.md` or `docs/agents/`), so the Standards axis is the skill's smell baseline plus the ADRs, which work as
the project's binding design rules. Both axes were run inline by this reviewer rather than in sub-agents. They are
still reported separately below.

"HEAD" means `18a7654` (merge of ticket 16).

## Summary

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 6 |
| Low | 5 |

Nothing is exploitable in the range as shipped, because every non-health route is `denyAll()`. The defects are in two
groups. One is a proof gap: tests whose `@Proves` or T-ID claim is stronger than what they check. The other is a
latent control gap that the skeleton hands on to later tickets. Four of the six Medium findings still apply at HEAD.

---

## Spec axis

### M1. Every refused anonymous request creates a server session (fixed at HEAD)
`backend/src/main/java/sg/securedhello/security/SecurityConfig.java:18-25` (at 806135c)

The chain keeps Spring Security's defaults for the request cache and the CSRF token repository. With
`anyRequest().denyAll()`:
- any anonymous browser-style `GET` to a refused path (for example `/actuator/env` or `/anything` with `Accept: */*`)
  goes through `ExceptionTranslationFilter.sendStartAuthentication`, and `HttpSessionRequestCache.saveRequest`
  calls `request.getSession()`;
- any `POST`/`PUT`/`PATCH`/`DELETE` has `CsrfFilter` generate a token and save it to `HttpSessionCsrfTokenRepository`,
  which also creates a session before the 403.

So from ticket 01 onward, an unauthenticated client could create one in-memory session per request, for 30 minutes
each. ADR-040 says only `GET /api/csrf` may create an anonymous session, and names both framework paths as ones that
must be closed. That ADR is formally scheduled for ticket 09, but ticket 01 shipped the public surface without it,
and no test asserts "no `Set-Cookie` on a refused request".
**At HEAD:** fixed. `SecurityConfig` sets `requestCache(new NullRequestCache())` and a `SessionOnlyCsrfTokenRepository`.

### M2. The T-OBS-010 tests pass even without the explicit actuator `denyAll()` rule (still applies)
`backend/src/test/java/sg/securedhello/web/ActuatorExposureTest.java:44-66`, versus `SecurityConfig.java:23`

T-OBS-010 reads: "every actuator endpoint other than health is refused by **the explicit `denyAll()` rule**". ADR-061
says that rule exists "so an endpoint switched on by accident is refused rather than left to the catch-all rule".
The tests cannot tell the two rules apart:
- every path they probe (`/actuator/info`, `/env`, `/beans`, `/metrics`, `/shutdown`, ...) is **not exposed**, so
  `EndpointRequest.toAnyEndpoint()` (which matches the base path and exposed endpoints only) does not match most of
  them, and they are refused by `anyRequest().denyAll()`;
- deleting line 23 (`.requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll()`) leaves every test green.

A test that proves the row needs an endpoint that really is exposed. One way is a dedicated context with
`management.endpoints.web.exposure.include=health,info` and `management.endpoint.info.access=read-only`, asserting
`/actuator/info` is refused. Another is to assert that the rule order places the endpoint matcher before any
whitelist route. This matters more at HEAD, where whitelist and role routes sit between the actuator rules and the
catch-all.
**At HEAD:** still applies. The same probe lists are used; only the anonymous status changed to 401.

### M3. T-HDR-006 is proven only against a placeholder page with no Base UI component (still applies)
`frontend/src/App.test.tsx:6-13`

The row says: "Mounting **every component** (including Base UI with `CSPProvider disableStyleElements`) inserts no
inline `<script>` element." The test mounts `<App/>` at `/`. At 806135c that renders a static `<main><h1>` with no
Base UI component, so the assertions (no inline script, no `<style>`) are trivially true. Deleting
`disableStyleElements` from `App.tsx` would not fail the test. The Base UI half of the row, which is the reason the row
exists, is unproven.
**At HEAD:** still applies. `App.test.tsx` is unchanged and still only mounts the home route. It should mount (or
iterate) every page and component, or at least one Base UI component that would inject a `<style>` element without
the provider flag, and include a check that goes red when the flag is removed.

### M4. The `NO_AMBIENT_TIME` rule does not cover the easiest bypasses (still applies)
`backend/src/test/java/sg/securedhello/architecture/ArchitectureRules.java:39-48`

The ticket's acceptance criterion is "Main code gets time only from the `Clock` bean". The rule only bans the
literal T-ARCH-001 list. These all pass it:
- `Clock.systemUTC()` / `Clock.systemDefaultZone()` used anywhere except `ClockConfig`. This escapes the
  `MutableClock` completely, which is exactly what ADR-066 exists to stop;
- `LocalTime.now()`, `Year.now()`, `YearMonth.now()`, `Calendar.getInstance()`;
- method references such as `Supplier<Instant> s = Instant::now`. ArchUnit's `callMethod` matches `JavaMethodCall`
  only, not `JavaMethodReference`.

A fix is to add `Clock.system*` (with `ClockConfig` excepted), the other `java.time` `now()` overloads, and a
`accessTargetWhere` / method-reference condition. Add a self-test fixture for each, as the existing fixtures do.
This is a judgement call, because the test-plan row lists exactly what is implemented, but the rule is weaker than
the acceptance criterion it is cited for.
**At HEAD:** still applies. The rule is unchanged, though no current main code uses a bypass.

### M5. `MutableClock`'s documented invariant is false: a standing clock falls behind framework time (still applies)
`backend/src/test/java/sg/securedhello/testsupport/MutableClock.java:11-17`, `TestClock.java:9`

The Javadoc (like ADR-066) says the clock "never moves backwards, so our time stays at or ahead of framework code
that reads `Instant.now()` directly". But the clock "stands still until a test advances it". It is created once at
class-load time and shared by the whole sequential suite, so after N minutes of test run our time is N minutes
**behind** Spring Session's and `FactorGrantedAuthority`'s `Instant.now()`, not ahead. Any later code or test that
compares an instant from our clock with a framework-stamped instant gets an offset that depends on suite order and
wall time. That is flaky, and in the fail-closed direction it causes premature expiry. A design that matches the
stated invariant is "real time plus the accumulated forward offset", clamped to stay monotonic.
**At HEAD:** still applies. The class is unchanged. `AbsoluteLifetimeFilter` avoids the hazard today by not mixing
sources (the authenticated branch uses our clock, the anonymous branch uses framework times only), but nothing
enforces that, and the Javadoc invites the mistake.

### M6. BCrypt cost has no floor; production can be silently downgraded (still applies)
`backend/src/main/java/sg/securedhello/security/PasswordProperties.java:11-12`, `PasswordEncoderConfig.java:21-23`

`bcryptStrength` is an unvalidated `int`. `APP_SECURITY_PASSWORD_BCRYPT_STRENGTH=4` (or any property source) in a
non-dev deployment gives cost-4 hashes, with no startup refusal and no test that would notice. The cost-12 test in
`ctx-nondev` reads the committed `application.yml` only. ADR-001 adopts "cost factor ≥12" as a floor, and the spec's
refresh-phase refusals exist for this kind of posture downgrade. The shared contexts legitimately need 4, so the fix
is a refusal outside `dev` (in the prohibited-configuration validator from ticket 06, or `@Validated` together with
a profile-aware check), not a plain `@Min(12)`.
**At HEAD:** still applies. `PasswordProperties` has gained policy fields but still has no validation, and a search
of main code shows no check on `bcryptStrength`.

### L1. Ticket 02 names React Hook Form + Zod, but neither is installed (fixed at HEAD)
`frontend/package.json:18-29`. The ticket says "using shadcn/ui on Base UI, React Hook Form + Zod and TanStack
Query". At 806135c neither `react-hook-form` nor `zod` is a dependency, and the ticket is marked done. **At HEAD:**
both have been added.

### L2. T-E2E-001 covered only the load half of its row (fixed at HEAD)
`frontend/e2e/document.spec.ts:21-26`. The row reads "after loading **and signing in**". The deferral is acknowledged
in a comment, but the T-ID is in the test name. **At HEAD:** the signed-in half has been added.

### L3. The production actuator posture is proven only under the `dev` profile
T-OBS-009/010/011 run in `ctx-default` (the `dev` profile). `ctx-nondev` asserts the port, the datasource and BCrypt,
but not `show-details: never`, `exposure.include: health` or `max-permitted: read-only` from the production
configuration. If an `application-dev.yml` ever diverges from `application.yml` on `management.*`, the production
posture becomes untested. Adding a few `productionProperty(...)` assertions would close this. Still applies at HEAD.

---

## Standards axis (smell baseline + ADR conventions)

### L4. `ctx-nondev` "production" values can come from the machine's environment (possible Mysterious Name / hidden input)
`backend/src/test/java/sg/securedhello/testsupport/CtxNondevTest.java:23-27`

`productionEnvironment()` starts from `new StandardEnvironment()`, which includes OS environment variables and
system properties. A developer or CI environment with `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`,
`SPRING_DATASOURCE_URL` or `APP_SECURITY_PASSWORD_BCRYPT_STRENGTH` set changes what these "production value" tests
read. That can give a false pass (for example, env supplies 12 while the committed yml says otherwise) or a false
fail. Use an environment containing only the config-data sources (for example a `StandardEnvironment` with the
system property and env sources removed before `applyTo`). The same applies to `productionContextRunner()`. Still
applies at HEAD (file unchanged).

### L5. `NO_SLEEP` bans only `Thread.sleep` / `TimeUnit.sleep`
`ArchitectureRules.java:50-52, 65-70`. `Object.wait(long)`, `LockSupport.parkNanos`, `CountDownLatch.await(timeout)`
and similar real waits pass the rule. This matches the ADR's literal wording ("`Thread.sleep` is prohibited"), so it
is a judgement call: the rule's name promises more than it checks. Still applies at HEAD.

### Checked and found sound (no finding)
- ADR-061 configuration: every property is written as the ADR requires. The `ManagementWebSecurityAutoConfiguration`
  back-off test (T-OBS-011) is a real condition-report check. Health is strict `{"status":"UP"}`.
- The context harness uses one temporary H2 file per context with the lock timeout resolved from a placeholder, and
  its cleanup is registered first so it is destroyed last. `ctx-locktimeout` really runs at 50 ms. There is no `test`
  profile, and BCrypt 4 is a context property (ADR-067).
- T-ARCH-006 slice detection (meta-annotation on `OverrideAutoConfiguration`/`TypeExcludeFilters`, including
  superclasses and enclosing classes) is correct and self-tested with fixtures. The fixtures use `@PlaceholderProves`,
  so they cannot pollute traceability.
- The document CSP meets ADR-060: the templated meta tag comes after the referrer meta and before every script and
  link, the policy strings match ADR-060 verbatim, `frame-ancestors` appears only in the header, and a missing
  `VITE_CSP` fails the config load. Built-document tests (T-BLD-002/003/009/010, REJ-053/054) parse the real
  `vite build` output. T-HDR-004 checks both layers on `vite preview` against the value `loadEnv` resolves.
- Surefire and Failsafe are pinned at 3.6.0, and the suite runs sequentially.
- The only duplication is `CtxDefaultTest`/`CtxLockTimeoutTest` repeating `@SpringBootTest @AutoConfigureMockMvc` and
  the `mockMvc` field. That is minor, and it is the intended way to name contexts, so there is no finding.

---

**Per-axis summary.** Spec: 9 findings (M1-M6, L1-L3); the worst is M1 (anonymous session creation on every
refused request, fixed at HEAD), and the worst still open is M2 (T-OBS-010 cannot detect removal of the explicit
actuator deny). Standards: 2 findings (L4, L5); the worst is L4 (the production-value tests read ambient environment
variables).
