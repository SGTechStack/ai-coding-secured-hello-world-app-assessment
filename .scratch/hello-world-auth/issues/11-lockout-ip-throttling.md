# 11: Lockout + IP throttling

**What to build:** The two-layer brute-force defense activates inside the login path, ordered IP-throttle check → `locked_until` check → credential verification — so throttled attempts never increment `failed_login_attempts`. Per-account: N consecutive failures within a window (tracked via `last_failed_at`) sets `locked_until` for the cooldown; locked accounts reject even correct credentials; success after cooldown resets the counter. Per-IP: an in-memory Caffeine sliding-window bucket keyed by `getRemoteAddr()` (never `X-Forwarded-For` without a stripping proxy) throttles independently of any account's lockout; a successful login does NOT reset the IP bucket. All thresholds/windows/cooldowns are `app.*` properties; time math goes through an injected `Clock` bean. `locked_until` surfaces via `UserDetails.isAccountNonLocked()`.

**Blocked by:** 09: Auth spine — register → login → hello

**Status:** implemented

- [x] N failed attempts within the window → `locked_until` set; further logins rejected until expiry (even with correct password)
- [x] Success after cooldown → login works, `failed_login_attempts` resets to 0
- [x] IP throttle engages across multiple usernames independently of account lockout state — an attacker cannot lock out a user by failing their password from one IP
- [x] Throttled attempt does not increment `failed_login_attempts` (ordering invariant — covered by narrower service-level unit tests)
- [x] IP bucket is NOT reset on a successful login (window decay only)
- [x] Lockout/throttle parameters are configurable `app.*` properties
- [x] API-seam tests simulate distinct IPs via `.remoteAddress(...)` and control time via the `Clock` bean

Implementation notes: enforcement is service-level in the new `LoginService`
(`auth` package), called by `AuthController.login` with
`request.getRemoteAddr()`. Order is written down in code: (1)
`IpThrottleService.isThrottled` → `LoginThrottledException` → 429
problem+json (a `RuntimeException`, deliberately *not* an
`AuthenticationException`, so it escapes the controller's generic-401 catch);
(2) `locked_until` check → `LockedException` → generic 401; (3)
`authenticationManager.authenticate`. `BadCredentialsException`/
`UsernameNotFoundException` failures record on *both* layers; status
exceptions surfacing from the provider (`Locked`/`Disabled` — the backstop
pre-auth checks) record nothing, since credentials never ran.

Per-account window is literal via `last_failed_at` (ratified deviation): a
failure more than `lockout.window` after the previous one restarts the streak
at 1. `IpThrottleService` is a Caffeine cache `Cache<String, FailureBucket>`
— `(count, lastFailure)` sliding window anchored at the last failure, all
time math via the injected `Clock`; `expireAfterAccess(2×window)` and
`maximumSize` are memory hygiene only. Success resets the account triple
(`failed_login_attempts`/`last_failed_at`/`locked_until`) but never touches
the IP bucket. `LoginService` is intentionally not `@Transactional` — the
failure path exits via exception, which would roll back the very counter it
recorded; each `save` commits independently.

**Deviation from the research's illustrative config:** the research suggested
`ip-throttle.threshold=20` > `lockout.threshold=5`, under which a focused
single-IP attack still locks the account — violating this ticket's literal AC
("an attacker cannot lock out a user by failing their password from one IP").
Defaults are `lockout.max-failures=5` / `ip-throttle.max-failures=4`: the IP
gate trips on the 5th attempt, so one source can record at most 4 failures —
never enough to lock. Documented in `AppProperties`/`application.yml`.

Test seams used: primary HTTP seam (`LockoutAndThrottleApiTests` —
`MockMvc` + `@SpringBootTest`, `.remoteAddress()` per attempt, `@Primary`
`MutableClock` bean injected via nested `@TestConfiguration`, real CSRF
bootstrap) and the narrower service seam (`LoginServiceTests` — Mockito
repo/manager, real `IpThrottleService` + `MutableClock`; pins ordering via
`verifyNoInteractions`) plus `IpThrottleServiceTests` for bucket decay.
`AuthFlowTests` `@BeforeEach` now clears the context-scoped throttle cache —
a leftover trap: the Caffeine singleton survives `deleteAll()`, so failures
from one test could throttle a later test's shared `127.0.0.1`.

No frontend change: the SPA surfaces `problem+json` `detail` verbatim via
`ApiError`, so the 429 text displays as-is. No `Retry-After` header — it
would tell an attacker exactly when the window decays.

New trap worth noting: Mockito `argThat` lambdas are evaluated with `null`
during stubbing — null-guard them; and re-stubbing a `thenThrow` method via a
second `when(...)` call re-invokes the stubbed throw during recording (use
per-argument stubs instead).

## Comments

**Verification (do-work-min, 2026-09-15):**

- Implemented: `LoginService` ordered IP-throttle → account-lock →
  credentials; `IpThrottleService` (Caffeine, keyed on `getRemoteAddr()`);
  `LoginThrottledException` → 429 problem+json via `ApiExceptionHandler`;
  `app.lockout.{max-failures,window,cooldown}` +
  `app.ip-throttle.{max-failures,window,max-entries}` in `AppProperties`/
  `application.yml`; Caffeine dep added (Boot-managed version).
- Test seams: HTTP seam (`LockoutAndThrottleApiTests` 9 tests:
  `.remoteAddress` IPs, `MutableClock` time control, real CSRF flow) +
  service seam (`LoginServiceTests` 14: ordering invariant,
  window/cooldown math, reset semantics) + `IpThrottleServiceTests` 6.
- Verification steps: `mvn test` 83/83 green across 12 classes. Live curl on
  embedded Tomcat (port 8081 — 8080 held by a stale pre-existing process,
  left untouched; `--app.ip-throttle.window=20s` override): register 201,
  4× bad login → 401 generic, 5th attempt → 429 `problem+json` even with
  the *correct* password and rotating `X-Forwarded-For`, post-decay correct
  login → 200 (account never locked: only 4 < 5 failures recorded).
- Deviation recorded: `ip-throttle.max-failures < lockout.max-failures`
  (4 < 5) so the anti-DoS AC holds literally; the research's illustrative
  20 would have allowed single-source lockout.
- Reviewer loop: Must-fix=0; one human decision — enforce the
  `ip-throttle < lockout` invariant at startup, or docs-only. User chose
  docs-only now + fail-fast validation in ticket 14 (added to its ACs;
  recorded in `docs/agents/reviewer-decisions.md`).
- Final gate: semgrep/spring-web PASS; thermo-nuclear WARN (duplicated
  failure-streak/lock predicates — consolidation advised, non-blocking);
  spring-security WARN (the same invariant, rendered decided/deferred) →
  aggregate **PASS**; report
  `artifacts/code-reviewer/11-lockout-ip-throttling-compliance.html`.
- Mutation gate: 98.8% killed (79/80, 100% line coverage) — zero
  Critical/High/Medium survivors, no test changes needed; sole survivor is
  the ticket-09 confirmed-equivalent `setContext` void-call; report
  `artifacts/mutation-testing/11-lockout-throttle-mutation.md`.
- Not yet (later tickets): reset flow (12), admin (13), audit logging +
  prod-profile hardening (14 — auth events already fire via the wired
  `DefaultAuthenticationEventPublisher` for ticket 14's audit consumer).
