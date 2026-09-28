# 16 — Decide the test plan: which test proves which control

Type: grilling
Status: resolved
Graduated: 32 (transcribe the test-plan table)
Blocked by: 09, 10, 11, 12, 13, 26, 27, 28, 29, 30, 31

## Question

For every security control on this map, what is the specific automated test that proves it works —
and what proves it *fails closed* when it should?

## Starting point

The PRD mandates integration tests for the security-critical paths and lists a minimum set: login
outcomes including identical generic errors, lockout and cooldown reset, IP throttling independent of
account lockout, session cookie rejected after logout, reset token single-use and expiry, reset
invalidating sessions, admin self-action guards, and a USER receiving 403 from `/api/admin/**`.

The App Standard §5 adds a much longer list across roles and authorization, sessions, CSRF, lockout
and rate limiting, reset, self-service change, user administration, data access, input validation,
security headers and CORS, enumeration, and logging.

## Inherited from ticket 06 — two assertions it owes you, and one it makes provable

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
turned the "prove identical response timing" problem flagged in ticket 04 into something testable, and added a
test this plan would otherwise miss:

- **The uniformity assertion is now a literal-equality test, not a judgement call.** "No field may vary as a
  function of account state or flow" cashes out as: *assert the response body equals this exact literal, for all
  six account states (unknown, wrong password, locked, disabled, grace-period-disabled, not-yet-activated),
  across all three endpoints (login, password-reset-request, registration)*. `traceId` is the only member
  permitted to vary. Watch `instance` specifically — Spring populates it from the request URI when left null,
  which silently breaks the assertion across the three flows.
- **Assert all four envelope producers agree.** Spring Security never routes through `@RestControllerAdvice`, so
  a unit test on the advice proves nothing about `AuthenticationEntryPoint`, `AuthenticationFailureHandler`,
  `AccessDeniedHandler`, or the `/error` dispatch. Each needs its own assertion that it emitted the shared
  envelope, plus a negative test that no `sendError` path survives (a `BasicErrorController` body is the
  tell-tale: `timestamp`/`error`/`message`/`path` instead of `type`/`code`).
- **Timing** is asserted indirectly rather than by wall-clock measurement: prove the ordering rules hold
  (lockout not checked as a pre-auth branch on account existence; no `PasswordEncoder` fast path). An artificial
  response-time floor was rejected, so there is no floor to assert.
- Rate limiting: assert the per-account 429 fires for an **unknown** username on the same schedule as a known
  one — that is the existence-oracle test. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-006, T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001, T-AUTH-011, T-AUTH-003, T-RL-017. Amend the table by ID, not this list.*

## Inherited from ticket 20 — four rows this plan owes, and one trap

[Decide the origin topology](20-deployment-origin-topology.md) settled two origins, `SameSite=Strict`,
and a document CSP, which lands here as:

- **The CSP assertion runs against `vite preview`, not `vite dev`.** Preview serves the built artifact
  under `preview.headers`, so the policy under test is the production one. The dev policy deliberately
  relaxes `script-src` and `style-src` for Vite's own injected code, so asserting against `vite dev`
  would certify a policy we never ship. Assert the header *and* the templated meta tag, and include
  `frame-ancestors`, which exists only in the header.
- **The `SameSite=Strict` assertion will contradict the standard's own prescribed test** at line 499
  ("Session and CSRF cookies have the Secure, HttpOnly, and `SameSite=Lax` attributes set"). That is by
  design; carry a comment naming the ADR so the mismatch reads as a decision rather than a defect.
- **A profile-scoped cookie-name assertion**: `SESSION` under dev, `__Host-SESSION` under the non-dev
  profile. This is the only test that exercises the production cookie configuration at all, since
  production is a documented requirement rather than an environment.
- **The two prescribed CORS tests stay in scope** — preflight from an allow-listed origin succeeds,
  a non-allow-listed origin is rejected, no wildcard. Keeping them implementable was one of the reasons
  two origins won, so dropping them here would forfeit the argument. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-004, T-SES-002, T-SES-011, T-HDR-007, T-HDR-008. Amend the table by ID, not this list.*

The trap: `curl -I` against `:8080` passes the headers recipe's own verification step while the API's
CSP protects no browsing context. Any header test must distinguish the **document** policy from the
API's defence-in-depth policy, or it will certify the wrong thing.

## What to decide

- The mapping table: control → test → level (unit, Spring integration slice, full context, frontend).
  Every control from the standard's §5 that is in scope needs a row, and every row needs to name
  which PRD story or standard clause it discharges.
- **H2 only**, per the out-of-scope decision. Decide what that costs in fidelity and what cannot be
  asserted as a result — for instance anything depending on Postgres locking or type behaviour.
- **Time control.** Lockout cooldown, token expiry, and session timeouts all need controllable time.
  Decide the mechanism (inject `Clock`, and make it the production design rather than a test hack) and
  reject `Thread.sleep` explicitly.
- **How to test timing consistency** for the enumeration requirement. This is genuinely hard — wall
  clock assertions are flaky in CI. Decide whether to assert the *mechanism* (a dummy hash
  verification happens on the unknown-user path) rather than the wall-clock timing, and be honest in
  the plan that the mechanism is what is verified.
- **How to test the two rate limiters are independent** — the PRD's Story 3 requirement that an
  attacker cannot lock out a user from one IP. Decide the concrete scenario and how client IP is
  varied in a test.
- **How to test session invalidation** across Spring Session JDBC, including the replay of a captured
  cookie after logout and after password reset.
- **How to test audit logging** — appender capture, and assertions on structured fields plus the
  negative assertions that secrets never appear. The negative tests matter more than the positive.
- Frontend tests: Vitest + Testing Library scope, and what is deliberately not tested.
- Whether Playwright E2E earns its place. Currently unscoped; decide in or out here rather than
  leaving it ambiguous.
- Test data discipline: synthetic usernames and emails only, per the standard's guidance.
- Coverage expectations, and whether mutation testing (`mutation-testing` skill) is worth running on
  the security-critical classes — surviving mutants in a lockout counter are exactly the bugs that
  matter.

## Done when

Every in-scope control has a named test, the time-control and timing-assertion approaches are
decided, and the Playwright question is answered either way.

---

## Amendment from ticket 09 (lockout and dual rate limiting)

Six tests that exist because each failure mode is **silent** — the control stops working and nothing else
breaks. Ticket 02 had already flagged two of these areas (the observation window and `429` reachability); the
other four come out of ticket 09's resolution.

1. **429-not-401 for the per-account limiter.** `unsuccessfulAuthentication` branches on exception type inside
   the one component whose job is response uniformity. A later "always 401" simplification deletes the
   per-account limiter's only observable behaviour and breaks nothing except §5:452's prescribed test.
2. **N+1 requests past the per-account budget leave `failed_login_attempts` unchanged.** The per-IP guarantee
   is filter ordering; the per-account guarantee is *where the throw happens* — a converter throw never reaches
   `ProviderManager`, so no `AuthenticationFailureBadCredentialsEvent` fires. Two different mechanisms for the
   same property means two tests, not one.
3. **`alwaysPerformAdditionalChecksOnUser` is `true`.** Declared on
   `AbstractUserDetailsAuthenticationProvider`, default `true`, and it is the CVE-2026-22746 mitigation: when a
   pre-check throws it still runs the password comparison, swallows the result and rethrows, so a locked account
   costs the same as an unlocked one. `false` reads as a free optimisation.
4. **Concurrent failures reach exactly the threshold, not fewer.** Fire ten parallel wrong-password attempts at
   one account and assert the counter and lock state are what five-then-locked implies. Without the pessimistic
   row lock, several requests read the same starting value and the threshold goes soft. Paired assertion: a
   lock-timeout inside the counting listener must **not** change the response — 401, never 500, because a 500
   on a contended account against 401 everywhere else is the state oracle ticket 06 exists to prevent.
5. **The first failure after auto-lift does not re-lock.** With lazy expiry the counter stays at 5 after the
   lock lifts; the 20-minute observation window's staleness reset is what stops the next wrong password
   re-locking for another 20 minutes. Fails silently as "lockout is stickier than documented".
6. **`forward-headers-strategy` is never `framework`, and `source=proxy` without an explicit trusted-proxy
   list fails to start.** Assert the default too, since the secure default is inherited rather than chosen.

Two tests the standard prescribes that we **cannot** implement, both recorded as deferrals rather than gaps: *Consolidated into the register (ticket 33): R-RL-006. Amend the table by ID, not this list.*
§5:453 distributed-limiter consistency (single-instance by decision), and any test asserting the per-account
`429` is reachable by pure failure traffic — lockout reaches 5 first, so it is reachable only on mixed or
successful traffic. *[Restated at resolution: the second item holds for the **submitted-username axis only**. *Consolidated into the register (ticket 33): R-STD-018. Amend the table by ID, not this list.*
The §R third axis's `429` is reachable by pure failure traffic by design (09:482–485), and §R test 1 asserts
it.]* *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-002, T-RL-003, T-AUTH-005, T-LCK-008, T-AUTH-016, T-LCK-001, T-CFG-001. Amend the table by ID, not this list.*

---

## Inherited from ticket 09 (§R, the ticket 21 reopening) — eight tests, handed over as a delta

**Ticket 09 already owed you six tests "that exist because the failure mode is silent". These eight are additions to
that list, not a replacement for it** — handed over as a delta deliberately, because a replacement list is how half of
the originals stop getting written.

| # | Assertion | Why it exists |
|---|---|---|
| 1 | `429`, not `401`, on the third limiter axis | Same shape as the existing per-account exemption: the uniform-401 handler branches, so a later "always 401" simplification deletes the axis's only observable behaviour and nothing fails. |
| 2 | The cardinality key is derived from **tier-1 lockout transitions**, not from attempts | The attempts formulation trips on every shared NAT. A refactor to "count attempts" looks like a simplification and silently converts an availability control into an outage. |
| 3 | The Route C converter sets `WebAuthenticationDetails` | Two readers depend on it and they fail in **opposite** directions — the audit `source.ip_hash` quietly collapses to one key, and the cardinality axis trips at once and `429`s everyone. Assert the details are non-null **and** that two different source addresses produce two different keys; a non-null check alone passes a constant. |
| 4 | No default authentication-failure event is configured on the publisher | The cap's refusal throws a custom `AuthenticationException`, which publishes no event *because* the lookup is by exact class name with a null default. Set a default and the refusal starts incrementing counters. One property away, DEBUG-only when it breaks. |
| 5 | A capped account's response time matches a wrong password | The refusal sits in `preAuthenticationChecks` so it inherits the real-hash normalisation via `alwaysPerformAdditionalChecksOnUser`. ASVS **6.3.8 (L3)** names response times explicitly. Pair it with the negative assertion that **no `UserCache` bean exists**, since a non-null cache makes the pre-check-failure path pay **two** BCrypt verifies and answer measurably *slower*. |
| 6 | The reset-request and redemption paths never route through `AuthenticationManager` | If they ever do, the disabled password authenticator blocks the only operation that can clear it. Self-blocking recovery, failing exactly when needed. Best expressed as an architecture assertion, not a behavioural one. |
| 7 | The reconciliation sweep is idempotent, and reconciles a disable written without its session kill | Session writes use `PROPAGATION_REQUIRES_NEW`, so the kill can never be atomic with the disable. Owned by ticket 08 for all five of its triggers; the test belongs to whichever of you writes ticket 08's suite. |
| 8 | The escalating-ladder constants are bound to their properties | Per the map's mechanism/constants seam rule. Assert the ladder's *shape* off configuration (20/40/60) rather than three hard-coded numbers, or the property becomes decoration. |

**Two notes on framing.** Test 3 is the highest-value one here despite looking like plumbing: it is the only single
point whose absence breaks a compliance field *and* inverts a control. And several of these assert **negatives** —
no cache bean, no default event, no `AuthenticationManager` on a path — which is the map's recurring shape for
controls whose failure mode is a configuration someone adds later. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-006, T-RL-007, T-RL-005, T-LCK-009, T-AUTH-003, T-CRED-009, T-SES-022, T-LCK-010. Amend the table by ID, not this list.*

---

## Amendment from ticket 25 (operational handover document)

Three items, one of which is a test no other ticket owns, and one of which is a *procedure* that has to be
scheduled rather than automated — recorded here because this plan is where the boundary between the two belongs.

**1. The source-control-metadata assertion, with two filenames nobody expects on it.** ASVS **13.4.1 (L1)** was
found unowned by ticket 24 and adopted by ticket 25. It needs a jar-content and bundle-directory assertion: no
`.git` or `.svn` in the emitted static bundle directory and none inside the jar. Note the requirement is
**disjunctive** — "either without any source control metadata ... or in a way that these folders are inaccessible
both externally and to the application itself" — and we take the first branch, so the test asserts *absence*, not
reachability.

The same test carries two further filenames for a different reason: **`git.properties` and
`build-info.properties`**. Ticket 21's argument that the actuator `info` endpoint is empty rests on this project
generating neither file, and four sentences later that same ticket calls adding one "mild" — so the absence is
load-bearing and currently unenforced. A ticket 24 prohibited-configuration entry **cannot** catch it, because
adding `git-commit-id-maven-plugin` is a build-plugin change rather than a bound property. One line on this test
converts a declared position into an enforced one. Scoping: that half is **13.4.6 (L3)** plus 13.4.5 (L2), *not*
13.4.1, because `git.properties` is derived from `.git` rather than being the folder 13.4.1 names.

**2. The `verify`-phase drift gate is a build gate, and this plan should say whether it is also a test.** Ticket 25
settled that the compliance register and the handover document are two renderings of one mechanically extracted
table, with the extractor bound to the Maven **`verify`** phase failing when the committed renderings differ from
regenerated output — same precedent as OWASP Dependency-Check, since no CI is in scope. That gate is what keeps
five documentation-conformance requirements satisfied: **6.3.1 (L1)**, 6.1.2 (L2), 6.2.11 (L2), 16.2.3 (L2),
16.3.3 (L2). Decide here whether it is expressed as a build-plugin execution, a test, or both — and note that
6.3.1 is **L1**, so its drift detection sits inside the mandatory target rather than beside it.

**3. The rehearsal is an acceptance check, not a test — and that distinction is this plan's to draw.** Ticket 25's
sequence step 7 is *rehearsing* the recovery procedures on the deployed topology before real accounts exist: run
ticket 09 §R.3's `--rebind=<username>` runner, clear a tier-2 factor disable through the break-glass path, confirm
the single-use token's output warning holds. It is simultaneously a deployment step and the only evidence
available for two rows that otherwise carry the sentinel *"none possible — attests a named role is filled and its
holder reachable."*

It is deliberately **not** automated, and the reason belongs in this plan rather than being inferred: the thing
being verified is that a human with deploy-level shell access exists and is reachable, which no assertion can
establish. What *can* be automated around it, and should get rows here: the runner's token is single-use (ASVS
**6.4.1 (L1)**, satisfied on its single-use limb rather than on short expiry — the requirement is disjunctive);
the token never reaches any log, shell transcript or CI artefact (a negative assertion, already on ticket 13's
negative list and in ticket 24's prohibited-configuration set, so this plan asserts the *absence of a logger on
that path* rather than re-testing the warning text); and the bootstrap admin username is refused if it matches
the reserved-name denylist (ticket 11's refresh-phase validator).

**One test that is explicitly out of scope, so it is not written by accident.** Ticket 25 recorded NIST §4.2.3's
recovery-notification SHALL as **failed**, single cause: no mail transport outside `dev`. There is nothing to
assert until a transport exists, and asserting the stub would certify the leak ticket 13 confines to `dev`. The
reopening trigger is a mail transport entering scope, at which point three notification SHALLs become testable at *Consolidated into the register (ticket 33): R-CRED-022, R-CRED-026. Amend the table by ID, not this list.*
once. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-004, T-BLD-005, T-BLD-006, T-AUD-027, T-ADM-022; retired items are recorded in ticket 32's reconciliation. Amend the table by ID, not this list.*

---

## Inherited from ticket 15 (threat model) — eight assertions, and three new blockers

**Your blockers grew by three.** [26](26-unbudgeted-routes-and-audit-volume.md),
[27](27-inbound-trace-context.md) and [28](28-out-of-band-privileged-channels.md) each add or change a control,
and a test plan written before them would be complete against a control set that no longer exists. Stated as a
wiring change rather than a note because it moves you off the frontier and you should know why.

Eight assertions. Two shapes recur and are worth naming before the list, because they are the shapes that make
these tests different from the thirty-odd you already carry: **three of the eight are enumerated rather than
written**, and **three are negative assertions about a configuration someone adds later** — the map's recurring
form for controls whose failure mode is an addition, not an omission.

| # | Assertion | Why it exists |
|---|---|---|
| 1 | **The factor assertion, enumerated from ticket 11's authorization matrix.** For every admin route and method: a session holding `ROLE_ADMIN` + `FACTOR_PASSWORD` and no `FACTOR_TOTP` is refused; for every mutation, a factor older than 10 minutes is refused. | TM-04. The factor requirement exists in **one** layer — ticket 23 abandoned `@EnableMultiFactorAuthentication` for good reasons, so `@PreAuthorize` covers the role and not the factor. Enumeration is the whole point: a hand-written list omits the route added next year, which is precisely the failure. Assert it is generated from the matrix, not that it passes. |
| 2 | **The tier-2 trip acquires the `users` row before the TOTP row.** | TM-03. The trip writes `users.force_password_change` while holding the `totp_user_details` lock, inverting the order three tickets pinned, against `AdminActionGuard`'s `users` → `totp` acquisition. Needs contention on two tables at one instant, so it is near-undiscoverable by accident — and the failure is asymmetric: the guard's side logs row 34 `LOCK_TIMEOUT`, ticket 23's side rejects a **correct** code because it deliberately does not fail open. |
| 3 | **A request to a route with no budget row is nonetheless budgeted.** | TM-01. Ticket 09's table is a route allowlist; the default is what ticket 26 decides. Whatever it decides, the test is the same shape, and without it the property is a table rather than a rule. |
| 4 | **No inbound trace header, valid or not, ever sets `trace.id`.** *(Restated by [ticket 27](27-inbound-trace-context.md) §7; was "`traceparent` is length- and content-validated before use", which is moot for a header that is never parsed.)* | TM-02, mitigated by the trace restart. Cases: **(i) per format**: a valid `traceparent` with and without `tracestate`, single-header `b3`, and multi-header `X-B3-*`; each time the logged `trace.id` ≠ the inbound trace-id. **(ii) Collision**: two requests pinning the same `traceparent` get **different** `trace.id`s. This is the actual TM-02 attack. **(iii) Sampling**: with the sampling probability overridden to `0.0` (ticket 03 line 276 sets `1.0` for tests), a `-01` request yields a span that is **not sampled**. **(iv) Logging**: the raw header value appears in no **application** log line, with the Tomcat access log disabled. The valve runs before the wrapper, which is a ticket 27 handover item. Companion tests from ticket 27: the `fields()`-subset-of-fixed-list guard (§4), and the behavioural baggage-off test (§5). |
| 5 | **Invocation of the rebinding runner is argument-gated, not property-gated** — a bound property carrying the runner's name must not trigger it. | TM-12. Boot binds command-line arguments as the highest-precedence property source, and ticket 24 found the adjacent failure on the same mechanism: a leftover environment variable silently beats a mounted file and nothing fails. Property-gated, a leftover `--rebind` in a unit file re-fires on **every restart**, invalidating the hash and printing a fresh token each time. |
| 6 | **The recovery-code route and the recovery-address confirmation are structurally unavailable while the transport is the stub** — not merely unused. | TM-13. Activating them against the stub puts the credential whose purpose is surviving TOTP loss into the `dev` log, which inverts ticket 10 §12's containment from partial to total for administrators. Ticket 13's dev-only confinement is the wrong control here, because it confines *to* `dev` and `dev` is where the stub is. |
| 7 | **The effective `ObjectInputFilter` on Spring Session's deserialisation path** — a first-implementation check, then an allowlist over the attribute types actually stored (`SecurityContext`, `FactorGrantedAuthority`, `AUTH_INSTANT`, the CSRF token). | TM-06. Ticket 05 kept JDK serialization deliberately, ticket 12 copied the DDL verbatim, ticket 23 asserts the round trip preserves a `FactorGrantedAuthority` — nobody looked at the deserialisation side. **The default is unverified**, which is why this is a check that establishes a fact before it asserts one; see [verification asset §2](../research/threat-model-external-fact-verification.md). |
| 8 | **`server.tomcat.threads.max` is pinned**, and ticket 21's Tomcat and Hikari saturation meters are present and non-zero after exercising a password path. | TM-07. Every limiter on the map is per-key, so aggregate CPU and thread occupancy are unbounded across sources. Those meters are the **only evidence for ticket 09's thread-exhaustion argument**, which is what declined sleep-based backoff — and ticket 21 already found the harness trap that silently leaves every meter assertion finding no meter. |

**One test you do not owe, recorded so it is not added.** A wall-clock assertion that a CSRF-less POST to an
unmatched path is rate-limited *before* the audit row is written. The ordering is a property of the filter chain,
not of the emitter, and asserting it by timing would be the flaky wall-clock test your own brief tells you to
reject. Assert the budget (row 3) and assert the truncation ceiling ticket 26 names; the interleaving needs no
test of its own.

**And one narrowing for the register rather than a test:** ticket 10's "the unauthenticated-BCrypt resource lever
disappears" is true of **registration only**. `POST /api/register/activate` and `POST /api/password-reset/confirm`
both run the full four-BCrypt `PasswordService` sequence unauthenticated, bounded by token possession. No test —
the cost is intended — but the claim should not be inherited broadly. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-002, T-MFA-007, T-RL-016, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028, T-RUN-003, T-CRED-023, T-SES-025, T-OBS-005. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-016. Amend the table by ID, not this list.*

---

## Handed from ticket 28 — the runner's negative tests

[Ticket 28](28-out-of-band-privileged-channels.md) §6 and §11. Each test proves a named control, and none can be
dropped without that control becoming an assertion by argument.

1. **Full refresh:** the runner's context contains the refresh-phase validator bean.
2. **Wrong database path:** the run refuses and **creates no file** (`IFEXISTS=TRUE`).
3. **Schema mismatch:** a newer jar against an older schema refuses, names both versions, and does not migrate.
4. **Seeder excluded:** against an empty database, runner mode seeds nothing.
5. **Existence by scope:** a missing or tombstoned account refuses; `totp` with no factor row refuses. There is
   also a **positive** test: `totp` on an enrolled, healthy, unlocked admin is **admitted**, because that is the
   lost-authenticator case the tool exists for. It guards against check 5 being tightened back up.
6. **App running:** non-zero exit and **zero bytes appended** to the audit file. This fails until the appender
   is attached lazily, which is the point.
7. **Digest:** a stale digest refuses. A leftover invocation carrying a valid `--confirm` fires **at most once**,
   and the second invocation refuses. For batch, changing one account's state between plan and apply invalidates
   the whole confirm.
8. **Argument gating:** `REBIND` or `APP_REBIND` in the environment fires nothing. EOF or `/dev/null` on stdin
   aborts and never hangs.
9. **No secret emitted:** run with a known password; the string appears in none of stdout, stderr or the audit
   file.
10. **No command line on rows:** no `process.command_line` or `process.args` key on any runner row.
11. **Identity fallback:** when `ProcessHandle` reports no user, the row records absence and never
    `System.getProperty("user.name")`.
12. **Validator entries:** `AUTO_SERVER=TRUE` in the URL, `FILE_LOCK=NO` in the URL, and an H2 TCP server bean
    each trip the refresh-phase validator.

Tests 2, 6 and 7 are also run by hand in ticket 25's step-7 rehearsal on the deployed topology, since a test
against our own assembled context cannot prove the deployer's launch command. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-004, T-RUN-005, T-RUN-006, T-RUN-007, T-RUN-008, T-RUN-009, T-RUN-010, T-RUN-011, T-RUN-003, T-AUD-027, T-AUD-029, T-RUN-001, T-CFG-012, T-CFG-013, T-CFG-014. Amend the table by ID, not this list.*

---

## Handed from ticket 29 (anonymous session-row growth)

Thirteen tests are listed at [ticket 29](29-anonymous-session-row-growth.md) §9. Four of them exist because their
failure is silent:

- **Test 1 must assert the positive case as well.** `GET /api/csrf` with no session creates exactly one row.
  Otherwise the test passes on a system that creates no sessions at all.
- **Test 2** pins the controller's `getSession(true)`. Without it, `/api/csrf` stops creating sessions and nothing
  fails.
- **Test 5**: no row with `MAX_INACTIVE_INTERVAL < 0` ever exists. Such a row cannot be deleted by any code path.
- **Test 10**: `/actuator/health` never runs a COUNT of its own. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-026, T-SES-027, T-SES-024, T-OBS-006. Amend the table by ID, not this list.*

Your blockers grow by one: [ticket 31](31-ipv6-source-keying.md).

## Amendment from ticket 31 (IPv6 source keying)

Ticket 31 is resolved; your blocker list shrinks by one. It owes you **twelve tests**, listed at
[31 §9](31-ipv6-source-keying.md). Two of them change tests you already hold:

- **Test 3's second assertion (`16:151`)** changes from "two different source addresses produce two different keys"
  to: two /64s give two keys; two addresses in one /64 give one key; `::ffff:a.b.c.d` gives the same key as
  `a.b.c.d`; spelling variants give one key.
- **Test 8 (the ladder shape, `16:156`)** gains a sibling: the startup floor rejects a ladder whose derived
  time-to-cap is under 840 min or whose warning is under 580 min. The cases are a rung lowered, the threshold raised
  to 6, and the alert moved to 60. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-005, T-LCK-011. Amend the table by ID, not this list.*

---

## Amendment from ticket 30 (sole-admin bootstrap premise) — seven tests

Bootstrap. Only the reserved-name refusal (16:206–207) existed; 16:262's tombstone case is the runner's, not the
bootstrap's.

1. Missing or policy-failing `APP_ADMIN_*` fails refresh **before the web server starts**.
2. An existing `ADMIN` row → no seed.
3. A disabled `ADMIN` row → no reseed.
4. A tombstoned seed username → fail fast.

Factor-reset exemption from the two-admin count:

5. At exactly two enrolled admins, A **can** reset B's TOTP.
6. At exactly two enrolled admins, A still **cannot** disable, demote or delete B — only the factor-reset path is
   exempt.
7. `actor ≠ subject` still refuses A resetting A's own factor; and after (5), B re-enrols unaided and the
   `authenticable` count returns to 2 — the self-reversal property the exemption rests on.

Also owed from ticket 30 §5, carried from ticket 29's pattern: the authenticable-admins gauge is non-`NaN` after a GC. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-023, T-ADM-024, T-ADM-025, T-ADM-026, T-ADM-027, T-ADM-028, T-ADM-029, T-OBS-007. Amend the table by ID, not this list.*

---

## Grilling log (in progress, not the Answer)

Kept so the session survives compaction. The `## Answer` is written only after the user confirms shared understanding.

### Round 1: settled, with the verified corrections applied

- **Q1 Table shape.** One canonical table in ticket 16, each test with a stable ID. A build-phase traceability gate uses a custom `@Proves("T-…")` annotation meta-annotated with `@Tag`, plus Vitest and Playwright name equivalents.
  - The gate fails `verify` when a plan ID has no test, or a test cites an unknown ID.
  - One ID may cover a parameterised or matrix-generated test (ticket 15 #1 needs this).
  - Stated limit: the gate proves a test exists, not what it asserts.
  - Why transcription is allowed this time: the sources are resolved, so the risk is loss in copying, not drift. It is controlled three ways:
    - exact per-source reconciliation ("ticket 29: 13 tests → 13 IDs");
    - a "superseded by T-…" pointer left in each source list;
    - a rule that future amendments target ticket 16 by ID, never a source list.
- **Q2 Levels.** U / C / P / R / B / A / F / E as proposed.
  - `@WebMvcTest` and all other slices are **banned outright**.
  - A small, fixed, named set of context configurations. Extra `@MockitoBean`s or property overrides fragment the context cache.
  - The BCrypt test cost is decided in round 2.
- **Q3 ArchUnit** is adopted. The version is re-checked at build setup: current is 1.5.1 (2026-09-25), and the arch-tests-gen skill's 1.3.0 is stale.
  - "Reset never reaches `AuthenticationManager`" gets a behavioural companion: reset works while the password authenticator is disabled.
- **Q4 Time.** One mutable, forward-only clock that starts at real time. A fixed Instant is rejected because two clock domains coexist:
  - Spring Session's `Instant.now()`;
  - `FactorGrantedAuthority.build()` stamping `Instant.now()` when `issuedAt` is unset. The DAO provider uses `fromAuthority()` with no hook (7.1.1 source).

  Adapters, all production design:
  - A Caffeine `Ticker` and a Bucket4j `TimeMeter` via `withCustomTimePrecision`, both built from the `Clock`.
  - Bucket4j's own default is `SYSTEM_MILLISECONDS` (wall-clock), so losing monotonicity on an NTP step is a property of both the default and our adapter. It is accepted and recorded. *Consolidated into the register (ticket 33): R-RL-004. Amend the table by ID, not this list.*
  - Our own code that builds a `FactorGrantedAuthority` sets `issuedAt(clock.instant())`.

  Bans and bounded waits:
  - ArchUnit bans every no-argument `now()` (`Instant`, `LocalDate`, `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`), `new Date()`, `System.currentTimeMillis()` and `System.nanoTime()` in main code. `now(Clock)` is allowed.
  - Idle expiry is tested by aging `SPRING_SESSION` rows.
  - The only permitted real wait is a lock-timeout test in its own named context, with `LOCK_TIMEOUT=50` on its URL. It is paired with a binding check that the production URL carries `LOCK_TIMEOUT=1000`. `SET LOCK_TIMEOUT` on pooled connections is prohibited because it is per-session and leaks.
  - Ticket 05's 2-second test and its `Instant.now()` samples are amended.
- **Q5 Timing** is tested by counting `matches()` only:
  - exactly **one** per request that reaches the provider;
  - exactly **zero** per limiter refusal (per-IP filter 429, per-account converter throw);
  - known and unknown usernames alike.

  `encode()` is excluded. The success-path upgrade is conditional on a non-NOOP `UserDetailsPasswordService` and `upgradeEncoding()`, and the dummy hash is a one-time lazy encode (7.1.1 source). The test also catches a `UserCache` hit, which makes two `matches()` calls. There is no wall-clock assertion anywhere. ASVS 6.3.8 (L3) goes to ticket 17 as "verified by mechanism". 09 §R row 5 is restated as this count.
- **Q6 Story 3** asserts the design, not the PRD sentence:
  - (i) tier 1 auto-lifts;
  - (ii) a steady single-source attack reaches the tier-2 Disable no sooner than the 840-minute floor, and the 580-minute warning fires first.

  Test IPs must map to distinct keys under ticket 31: distinct /64s, and `::ffff:` collapses to IPv4. The cardinality axis cannot trip on scenario (ii), because it counts distinct accounts per source. It does constrain fixture setup (round 2).
- **Q7 Replay tests** are enumerated from ticket 08's final trigger table (08:205–215, nine rows), plus ticket 09 §R's cap disable, plus the two rows that invalidate nothing. They are never taken from a count. Ticket 16 owns the reconciliation-sweep test outright.
- **Q8 Canaries**, suite-wide:
  - Fixed canaries for client-supplied secrets.
  - Per-test registration of server-generated secrets, captured from responses: session id, CSRF token, reset, activation and rebind tokens, TOTP secret and codes, and the environment AES key.
  - Every encoded form of each secret is scanned.
  - Scanned sinks: all loggers, stdout and stderr, the audit file appender, and runner output.
  - The suite runs sequentially, because `OutputCaptureExtension` captures JVM-global streams.
- **Q9 Playwright is in**, narrow.
  - `webServer` is an array that starts `vite preview` and the backend.
  - The `input-otp` check counts `securitypolicyviolation` events and asserts zero.
- **Q10 PIT** is scoped, in a `-Pmutation` profile, with an explicit `--threshold=85`.
  - Design consequence: the guard, token consume and TOTP window logic are extracted into pure decision functions, so PIT does not boot a context per mutant.
- **Q11 Drift gate.** One test, run under **Failsafe ≥ 3.6.0**, so `-DskipTests` does not skip it (Failsafe no longer binds that property). `-DskipITs` and `-Dmaven.test.skip` are the named, accepted bypasses.
- **Q12 §5 rows:**
  - (a) Name where the check lives: ticket 11's refresh-phase validator.
  - (b) A body-size cap before the Route C converter reads the body, returning 400 `VALIDATION_FAILED`. The number goes to ticket 09 as a named owed input. The batch half is N/A (ticket 11). *Consolidated into the register (ticket 33): R-ADM-014. Amend the table by ID, not this list.*
  - (c) An explicit `denyAll()` matcher for role-definition paths, so 403 is literal.
  - (d) N/A by construction. It also covers "lock via generic update". Remove `USERNAME_CHANGE_NOT_ALLOWED` and fix ticket 06's enum table itself, including `BATCH_TOO_LARGE` at 06:142. *Consolidated into the register (ticket 33): R-ADM-013. Amend the table by ID, not this list.*
  - (e) Cite 05:754, assert the other three headers on every response, and keep ticket 20's document-versus-API CSP distinction.
  - (f) Amend 01:144 and 01:306 to name the real replacements: per-IP 429 at N+1 of its budget, and the per-account 429 on mixed traffic. 16:135–138 is restated for §R (round 2).
  - Tickets 06, 09 and 11 are amended in place, with no reopenings.

### Round 2 settled, with the verified corrections

- **Q11 correction verified.** Maven Failsafe **3.6.0** no longer binds `skipTests` to the user property.
  - Evidence: `IntegrationTestMojo` at tag `surefire-3.6.0`; PR apache/maven-surefire#3371 (SUREFIRE-823, milestone 3.6.0, merged 2026-07-31); the Surefire site's "What's New › 3.6.0".
  - Jira SUREFIRE-823 still reads "3.x-candidate / Reopened", which is why a search misses it.
  - Failsafe is pinned at **exactly 3.6.0**. The recorded bypasses are `-DskipITs` and `-Dmaven.test.skip`.
  - On any version below 3.6.0, `-DskipTests` bypasses the gate again. *Consolidated into the register (ticket 33): R-BLD-009. Amend the table by ID, not this list.*
- **Every context uses a per-context temp-dir `jdbc:h2:file` path.** Ticket 24 §9 rejects in-memory H2 in every profile, so round 1's "named H2 database" was never valid.
- **Restart harness.** It runs outside the Spring test context cache, as two sequential `SpringApplicationBuilder` runs on one file path.
  - The first context is fully closed before the second opens. `FILE_LOCK=NO` is banned (28:315).
  - One forward-only clock instance is shared by both runs.
- **Spring Session cleanup cron** is set to `-` (`Scheduled.CRON_DISABLED`) in the shared contexts, with one dedicated cron-enabled test.
  - The value is owned by no ticket. Ticket 05:299–300 sets `0 */5 * * * *`, while 05:284 and ticket 29:282's 510 figure assume every minute (see round 3).
- **Fixture isolation** has three parts:
  - A fixture allocator hands out fresh usernames and source keys (TEST-NET IPv4, distinct `2001:db8::/32` /64s). It is enforced: the extension fails any request that arrives from MockMvc's default `127.0.0.1`.
  - Global counts assert a before/after delta or get their own database. This covers the two-admin invariant, the authenticable-admins gauge, ticket 29's exactly-one-row test and the saturation meters.
  - The "own context for more than k accounts" exception is dropped, because cardinality state is per source key. Ticket 31's shared `unparseable` bucket (31:343) is the one shared key (see round 3).
  - The table gains an isolation column: `keyed` / `delta` / `own-DB` / `own-context`.
- **Sequential execution** is load-bearing for the canaries, the shared clock and the shared contexts.
- **The fidelity list is split in two:**
  - *Not exercised by this harness*: H2 locking and types, TLS/HSTS/`__Host-`, multi-instance, the launch command, wall-clock timing, and browser engines other than those Playwright runs.
  - *Not built*: the §5 hygiene rows, 90-day retention, owner notifications, Dependency-Check as a CI gate. These go to ticket 17's register, not the fidelity list.
- **Frontend.** Vitest + Testing Library + MSW. MSW fixtures are validated against the backend contract under their own T-ID.
  - Ticket 22's arithmetic: 31 = 7 `MFADialog` + 8 `MFAPinForm` + 11 `useMFA` + 5 `useMFAPinForm`. The last three (24 cases) are code not being built, and 1 `MFADialog` case is vacuous, so **6 are usable and 25 are not**.
  - `MFATotpForm` and `SetupMFA` have zero prescribed cases, so their tests are new.
  - The accessibility assertions cover ticket 14's behaviours only. The conformance target stays in fog. *Consolidated into the register (ticket 33): R-FE-005. Amend the table by ID, not this list.*
- **Table.**
  - It lives in an asset: one machine-readable table with fixed columns, at a path the build reads.
  - IDs are never renumbered or reused.
  - Each row has exactly one pillar, chosen by the control it proves.
  - Fields: ID, pillar, control, assertion, level, context, isolation, clause (with ASVS level), source ticket and line, negative/positive.
- **Q7:** 16:135–138 is restated so the pure-failure line covers only the submitted-username axis (09:482–485).
- **Q8:** the two rows that invalidate nothing get negative tests. Each test states its reason, citing ticket 08's ticket-10 amendment.
- **Keys.** Ticket 09 names no key for the ladder, the per-IP and per-username budgets, the NIST alert/cap, or k. k exists only as "≈5". map.md:94 overstates this (see round 3).

### Round 3 settled, with the verified corrections

- **Plugin pins.** Surefire and Failsafe are both pinned at exactly **3.6.0**, which is the current release of each on Central. Any version bump reopens the `-DskipTests` question. *Consolidated into the register (ticket 33): R-BLD-005. Amend the table by ID, not this list.*
- **Test contexts.** `ctx-default` runs the dev profile with a capturing `EmailService` as its one fixed override. One dedicated dev-confinement test runs the real stub, with the canary scan scoped by logger.
- **Recovery routes (TM-13).** Ticket 16 row 6 comes from ticket 15. The recovery-code route and the recovery-address confirmation are **not built**: 25:865 says "with no transport the route does not exist". No gate mechanism is specified anywhere, so no bean-keyed gate exists for the override to switch on.
  - Today the assertion is **absence**. It checks that neither route is in `getHandlerMethods()`, and that a request to either one gets `denyAll()`'s 403. It runs in `ctx-default`, so the override is under test too.
  - Owed constraint on ticket 25's mail-transport reopening trigger: when the routes are built, their gate keys on a declared transport property, **never** on the `EmailService` bean type. *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
  - Ticket 15 needs no amendment.
- **The race test (09 row 4)** runs in `ctx-nondev` at BCrypt cost 12. All ten requests are released from one `CountDownLatch`. A one-time lock-removed demonstration is recorded in `/do-work` slice evidence, and goes stale on any refactor of the guard.
  - PIT is dropped on the merits. `NON_VOID_METHOD_CALLS` is optional and replaces the call with the type's default value (`null`), so the mutant dies of an NPE. No mutator swaps a locking query for a non-locking one (pitest.org mutators page).
- **The cleanup cron** is pinned at `0 * * * * *`, which is also Spring Session's own `DEFAULT_CLEANUP_CRON`. This is an amendment on ticket 29, and ticket 05's snippet is superseded. The binding test reads the production configuration (`ctx-nondev` or the property file), never a shared test context, where the cron is set to `-`.
- **k is decided at 5.** Every derivation plugs in exactly 5: 09:1259, 09:1541, 31:87, 31:139 ("P ÷ k = 20") and 31:154. 31:190 and 31:448 treat any change to k as a reopening trigger. No text calls it provisional; the "≈" is prose only (09:324, 09:1253). So this is an **amendment** on ticket 09 stating k = 5 exactly, under 13:143's rule (it weakens nothing). It is not a reopening. *Consolidated into the register (ticket 33): R-RL-022. Amend the table by ID, not this list.*
  - Side note for the same amendment: because of the first-insertion expiry pin, the "rolling hour" label behaves as a fixed hour per entry.
- **Owed keys.** The missing key names are recorded as owed inputs with a single greppable placeholder form, `KEY:<name>`. The transcription ticket's done-condition fails if any `KEY:` survives.
- **The unparseable bucket's tests** are merged into one ordered test.
- **Playwright** runs Chromium and Firefox. WebKit and Safari go in the fidelity list, and browser binaries are pinned through the Playwright version.
- **The transcription** graduates to a new AFK task ticket, blocking 17, 18 and the `/to-spec` handoff. It inherits "every in-scope control has a named test" word for word. Its done-condition:
  - exact per-source counts;
  - zero `KEY:` placeholders;
  - IDs minted for every binding test this session's amendments create.
- **Amendments** go to tickets 01, 05, 06, 08, 09, 11, 14, 17, 18 and 29, plus map.md:94. Ticket 15 needs none. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-024, T-RL-018, T-MFA-008, T-ARCH-001, T-LCK-019, T-LCK-002, T-LCK-020, T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-AUD-013, T-HDR-003, T-ADM-018, T-RL-019, T-ADM-020, T-HDR-001, T-HDR-002, T-SES-023, T-ARCH-002, T-AUTH-012, T-AUD-030, T-SES-028, T-RL-020, T-RL-007, T-AUTH-003, T-SES-022, T-LCK-008, T-AUTH-016, T-BLD-006, T-RL-002, T-RL-003. Amend the table by ID, not this list.*

---

## Answer

**Every security control on this map gets a stable test ID in one machine-readable table. A build-phase gate fails
when an ID and a test do not match. What proves a control "fails closed" is carried mostly by negative assertions,
counts and absences rather than by happy paths.** This ticket settles the decisions. The rows themselves are
transcribed by [ticket 32](32-test-plan-table-transcription.md), which inherits the "named test" clause of the
original "Done when" word for word. The detail and its verification trail are in the grilling log above; this
section is the record.

### 1. "Done when", amended

The original read: *every in-scope control has a named test, the time-control and timing-assertion approaches are
decided, and the Playwright question is answered either way.* This ticket discharges clauses 2 and 3 (§3, §4, §8).
Clause 1 moves to ticket 32, whose done-condition is:
- exact per-source counts;
- zero `KEY:` placeholders;
- an ID for every binding test this ticket's amendments created.

Closing this ticket without that transfer would be the silent gap this map keeps catching.

### 2. The table and its gate

- **One canonical table**, transcribed rather than generated. This is allowed because every source is resolved, so the risk is loss in copying, not drift. Loss is controlled three ways:
  - exact per-source reconciliation;
  - a `superseded by T-…` pointer left in each source list;
  - a rule that **future amendments target this table by ID, never a source list**.
- **Traceability gate.** A custom `@Proves("T-…")` annotation, meta-annotated with `@Tag`, plus Vitest and Playwright name equivalents. `verify` fails on a plan ID with no test, or on a test that cites an unknown ID. One ID may cover a parameterised or matrix-generated test (ticket 15 #1 needs this). **Limit, stated:** the gate proves a test exists, not what it asserts.
- **IDs** carry a pillar prefix: `T-AUTH`, `T-SES`, `T-CSRF`, `T-LCK`, `T-RL`, `T-CRED`, `T-ADM`, `T-MFA`, `T-AUD`, `T-HDR`, `T-CFG`, `T-RUN`, `T-OBS`, `T-FE`, `T-E2E`, `T-BLD` or `T-ARCH`.
  - Exactly one pillar per row, chosen by the control it proves. The factor assertion is `T-MFA` even though it enumerates admin routes.
  - IDs are never renumbered or reused. A deleted row retires its ID.
- **Columns:** ID, pillar, control, assertion, level, context, isolation, clause (with ASVS level), source ticket and line, and polarity (negative/positive).
- **The asset** is one table with fixed columns and nothing else in it that looks like a row. It lives at `docs/test-plan/test-plan.md` in the application repository. The gate reads that path through a Maven property (`test-plan.path`), so `/to-spec` moving the file is a one-line change rather than a broken gate. *[Edited after resolution.]* **The gate fails closed** in two cases. Either one would otherwise let a wrong path pass as "no plan IDs, no orphans":
  - the file at `test-plan.path` is missing or unreadable;
  - it parses **zero rows**. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-007, T-BLD-008. Amend the table by ID, not this list.*

### 3. Levels and contexts

- **Levels:**
  - U: unit
  - C: full context, MockMvc
  - P: full context on a real port. Needed wherever MockMvc bypasses Tomcat: `RemoteIpValve`, raw `Set-Cookie`, Tomcat meters, the trace-header wrapper, duplicate cookies, the 8 KB header budget.
  - R: runner process
  - B: build artefact (jar and bundle inspection, DDL blob hash)
  - A: ArchUnit, version re-checked at build setup (1.5.1 current)
  - F: Vitest
  - E: Playwright
- **`@WebMvcTest` and every other slice are banned** for security-control tests.
- **Fixed contexts**, named, so the context cache is not fragmented:
  - `ctx-default`: dev profile, with one override, a capturing `EmailService`. The dev-only reset-link logger never receives a token, and tests read tokens from the capture.
  - `ctx-port`
  - `ctx-locktimeout`: `LOCK_TIMEOUT=50` on its own URL.
  - `ctx-nondev`: prod-only values, and BCrypt cost 12.
  - the restart harness: outside the cache. Two sequential `SpringApplicationBuilder` runs on one file path, with the first context fully closed (`FILE_LOCK=NO` is banned) and one shared clock instance.
  - the runner.
  
  There is **no `test` profile**: ticket 24's dev/non-dev split would class it as production.
- **Every context uses a temp-dir `jdbc:h2:file` path**, because ticket 24 §9 rejects in-memory H2 in every profile.
- **BCrypt:** cost 4 in `ctx-default`, `ctx-port` and `ctx-locktimeout`. A binding test asserts that production binds 12.
- **Cleanup cron** is set to `-` in shared contexts. One dedicated test runs with it enabled. The production-value binding test reads the production configuration.
- **Suite execution is sequential.** The canaries, the shared clock and the shared contexts all depend on it. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-025, T-SES-023, T-SES-028. Amend the table by ID, not this list.*

### 4. Time

- **One mutable, forward-only clock that starts at real time.** A fixed Instant is rejected because two clock domains coexist:
  - Spring Session's `Instant.now()`;
  - `FactorGrantedAuthority.build()` stamping `Instant.now()` when `issuedAt` is unset (7.1.1).
- **Adapters, all production design:**
  - Caffeine `Ticker` and Bucket4j `TimeMeter` (`withCustomTimePrecision`) are both built from the `Clock`. Losing monotonicity on an NTP step is accepted and recorded; Bucket4j's own default, `SYSTEM_MILLISECONDS`, has the same property. *Consolidated into the register (ticket 33): R-RL-004. Amend the table by ID, not this list.*
  - Our own `FactorGrantedAuthority` construction sets `issuedAt(clock.instant())`.
- **ArchUnit bans** every no-argument `now()`, `new Date()`, `System.currentTimeMillis()` and `System.nanoTime()` in main code. `now(Clock)` is allowed.
- **`Thread.sleep` is prohibited.** Idle expiry ages `SPRING_SESSION` rows instead. The **only** real wait is the lock-timeout test in `ctx-locktimeout`, paired with a binding check that the production URL carries `LOCK_TIMEOUT=1000`. `SET LOCK_TIMEOUT` on pooled connections is prohibited. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-018, T-MFA-008, T-ARCH-001, T-LCK-019, T-AUTH-016. Amend the table by ID, not this list.*

### 5. Controls whose proof is not the obvious one

- **Timing is verified by mechanism, and the plan says so.** Only `matches()` is counted:
  - exactly **one** per request that reaches the provider;
  - exactly **zero** per limiter refusal;
  - known and unknown usernames alike.

  `encode()` is excluded: the success-path upgrade is conditional, and the dummy hash is a one-time lazy encode. The same count catches a `UserCache` hit (two calls). There is no wall-clock assertion anywhere. ASVS 6.3.8 (L3) is "verified by mechanism" in ticket 17. 09 §R row 5 is restated as this count. *Consolidated into the register (ticket 33): R-AUTH-004. Amend the table by ID, not this list.*
- **Story 3** asserts the design, not the PRD sentence:
  - (i) tier 1 auto-lifts;
  - (ii) a steady single-source attack reaches the tier-2 Disable no sooner than 840 minutes, and the 580-minute warning fires first; *Consolidated into the register (ticket 33): R-AUTH-004. Amend the table by ID, not this list.*
  - (iii) five failures from five distinct source keys lock the account.

  The source keys are distinct under ticket 31's rules.
- **The 09 §R row 4 race test** runs in `ctx-nondev` at cost 12, with all ten requests released from one `CountDownLatch`. A one-time lock-removed demonstration is recorded in `/do-work` slice evidence and goes stale on any refactor of the guard. PIT is not evidence for the lock.
- **Session replay** is enumerated from ticket 08's trigger table, with negative tests for the two rows that invalidate nothing (ticket 08's amendment from 16). This ticket owns the reconciliation sweep.
- **Canaries**, suite-wide:
  - fixed canaries for client-supplied secrets;
  - per-test registration of server-generated secrets captured from responses (session id, CSRF, reset, activation and rebind tokens, TOTP secret and codes, the environment AES key);
  - every encoded form of each secret is scanned.

  The scan covers all loggers, stdout/stderr, the audit file appender and runner output. The one dev-confinement test runs the real stub, with the scan scoped by logger.
- **Fixture isolation.** An allocator hands out fresh usernames and source keys. The extension **fails any request from MockMvc's default `127.0.0.1`**. Global counts (two-admin invariant, authenticable-admins gauge, ticket 29's row counts, saturation meters) assert deltas or use their own database. The shared `unparseable` bucket's tests are **one merged ordered test**. The isolation column takes `keyed`, `delta`, `own-DB` or `merged`.
- **"Reset never reaches `AuthenticationManager`"** is tested at level A, with a behavioural companion: reset works while the password authenticator is disabled.
- **Standard §5 rows no ticket had decided** (amendments to 06, 09 and 11):
  - §5:424: refresh-phase validator.
  - §5:425: explicit `denyAll()` on role-definition paths, so 403 is literal.
  - §5:473: N/A, and `USERNAME_CHANGE_NOT_ALLOWED` is removed. *Consolidated into the register (ticket 33): R-ADM-013. Amend the table by ID, not this list.*
  - §5:494: a 16 KiB body cap before the Route C converter, returning 400 `VALIDATION_FAILED`. The batch half is N/A. *Consolidated into the register (ticket 33): R-ADM-014. Amend the table by ID, not this list.*
  - §5:498: HSTS on secure requests only (05:754). The other three headers are asserted on every response, keeping the document-versus-API CSP distinction.
  - §5:485 and §5:479: tests only. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-003, T-LCK-002, T-LCK-020, T-LCK-003, T-LCK-008, T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-SES-022, T-AUD-013, T-AUD-030, T-ARCH-002, T-RL-020, T-RL-007, T-CRED-024, T-ADM-018, T-ADM-020, T-RL-019, T-HDR-001, T-HDR-002, T-ADM-005, T-ADM-009. Amend the table by ID, not this list.*

### 6. What this suite does not prove

**(a) Not exercised by this harness:**
- Postgres/MySQL locking, types and gap/phantom locking. H2 guards decrements, not phantoms. *Consolidated into the register (ticket 33): R-DATA-014. Amend the table by ID, not this list.*
- MySQL accent-insensitive collation. *Consolidated into the register (ticket 33): R-DATA-014. Amend the table by ID, not this list.*
- DDL portability beyond ticket 12's seam register. *Consolidated into the register (ticket 33): R-DATA-014. Amend the table by ID, not this list.*
- Real TLS, HSTS behaviour and `__Host-` over HTTPS. *Consolidated into the register (ticket 33): R-CFG-004. Amend the table by ID, not this list.*
- Multi-instance limiter and session consistency (§5:453). *Consolidated into the register (ticket 33): R-RL-006. Amend the table by ID, not this list.*
- The deployer's launch command. Ticket 28 tests 2, 6 and 7 also run in the step-7 rehearsal. *Consolidated into the register (ticket 33): R-BLD-008. Amend the table by ID, not this list.*
- Wall-clock timing uniformity. *Consolidated into the register (ticket 33): R-AUTH-004. Amend the table by ID, not this list.*
- WebKit and Safari. Playwright runs Chromium and Firefox, with browser binaries pinned through the Playwright version. *Consolidated into the register (ticket 33): R-FE-006. Amend the table by ID, not this list.*

**(b) Not built, so nothing to exercise.** These are register rows in ticket 17, not test limitations:
- the §5 hygiene rows (90/180-day, scheduler serialisation, batch-job session kill); *Consolidated into the register (ticket 33): R-ADM-011. Amend the table by ID, not this list.*
- 90-day audit retention; *Consolidated into the register (ticket 33): R-AUD-022. Amend the table by ID, not this list.*
- owner notifications, including NIST §4.2.3; *Consolidated into the register (ticket 33): R-CRED-022. Amend the table by ID, not this list.*
- Dependency-Check as a CI gate (bound to `verify`). *Consolidated into the register (ticket 33): R-BLD-007. Amend the table by ID, not this list.*

### 7. TM-13 (ticket 15 #6)

The recovery-code route and the recovery-address confirmation **do not exist** (25:865). No path, matcher or gate is designated anywhere, so no single HTTP status can be pinned: an unmatched path returns 401 to anonymous and 403 to authenticated callers, and a path under a guarded prefix returns 404. The test is therefore **structural**, in `ctx-default`, so the capture-bean override is under test:
- no handler in `getHandlerMethods()` issues or redeems the issued-recovery-code token type;
- ticket 26's three-registry disposition test contains no recovery entry.

When the routes are built behind the mail-transport trigger, their gate keys on a declared transport property, never on the `EmailService` bean type, and this test gains a pinned status per path and principal (ticket 25's amendment from 16). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-023. Amend the table by ID, not this list.*

### 8. Build-level decisions

- **Playwright is in, narrow.** About eight E tests against `vite preview` and the backend, both started by a `webServer` array:
  - CSP enforcement, with zero `securitypolicyviolation` events including `input-otp`'s sheet;
  - `Clear-Site-Data` scoping;
  - `SameSite=Strict` delivery across `:5173` and `:8080`;
  - real-origin CORS;
  - origin agreement;
  - one golden path (login, then admin TOTP step-up).

  Ticket 18's `browser-test` stays separate.
- **Frontend:** Vitest + Testing Library + MSW. Fixtures are validated against the backend enum schema under their own T-ID. Ticket 22's 31 prescribed cases break down as 7 + 8 + 11 + 5, of which 24 are on code not being built and 1 is vacuous: **6 are ported and 25 are not**. `MFATotpForm` and `SetupMFA` get new tests. Accessibility assertions cover ticket 14's behaviours only; the conformance target stays in fog. *Consolidated into the register (ticket 33): R-FE-005. Amend the table by ID, not this list.*
- **Coverage and mutation:** 80% JaCoCo instruction and 80% Vitest remain the floor, not the target. PIT runs in a `-Pmutation` profile, `--threshold=85`, scoped to the security-decision classes, and the pure decision functions are extracted for it.
- **ticket 25's drift gate** is one test under Failsafe. **Surefire and Failsafe are pinned at exactly 3.6.0.** From 3.6.0 `-DskipTests` no longer skips Failsafe (apache/maven-surefire#3371). The accepted bypasses are `-DskipITs` and `-Dmaven.test.skip`, and **any version bump reopens this**. *Consolidated into the register (ticket 33): R-BLD-005, R-BLD-009. Amend the table by ID, not this list.*
- **Test data:** synthetic identities under `example.test`, and tokens from the production generator. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-003, T-HDR-005, T-E2E-002, T-E2E-003, T-E2E-004, T-E2E-005, T-AUTH-012, T-BLD-006. Amend the table by ID, not this list.*

### 9. Supersessions and restatements that ticket 32 must apply

- 16:135–138: narrowed to the submitted-username axis (restated in place).
- 09 §R row 5: becomes the `matches()` count.
- 09 §R row 3's second assertion and row 8: extended by ticket 31 (16:312–324).
- 15 row 4: restated by ticket 27.
- 28 test 9: supersedes 13:794–801 and 24's eleventh prohibited-config entry.
- Duplicates, each counted once:
  - 21:624 is the same test as 09 §R row 5;
  - 11:888, 23:1167 and 15 #1 are one test;
  - 23:1144 and 15 #2 are one test;
  - 28 test 12 duplicates 24's validator entries.
- 16:262's tombstone case belongs to the runner (ticket 30).
- 12:1088's additions join the invariant set, not the five-item set.
- 05 risk 3's 2-second test: restated on the clock.
- 01's "6th attempt": amended.

### Handover items (ticket 25)

- **Run the build with Surefire and Failsafe at the pinned 3.6.0, and never with `-DskipITs` or `-Dmaven.test.skip` for a release build.** These are the only bypasses of the traceability, drift and reconciliation tests.
  - Discharges: ASVS 6.3.1 (L1), through the drift test's documentation-conformance family.
  - Enforceable by the application: partly. The pin is in the POM; the flag is a human choice.
  - Proof: build log shows the Failsafe 3.6.0 summary with non-zero test counts. *Consolidated into the register (ticket 33): R-BLD-009. Amend the table by ID, not this list.*
- **Treat a failed `verify` as a release blocker.** With no CI, a developer's `verify` run is the only gate.
  - Discharges: ASVS 6.3.1 (L1), plus Standard §5:521 (Dependency-Check in `verify`, deviation from "CI gate").
  - Enforceable by the application: no.
  - Proof: a green `verify` log attached to the release. *Consolidated into the register (ticket 33): R-BLD-007. Amend the table by ID, not this list.*
- **Run the E-level suite in Chromium and Firefox before release, and accept WebKit/Safari as unexercised or run it by hand.** No application control applies.
  - Enforceable by the application: no.
  - Proof: the Playwright report for both projects. *Consolidated into the register (ticket 33): R-FE-006. Amend the table by ID, not this list.*

### Owed

- **ADRs:**
  1. Slices banned for security tests. *Consolidated into the ADR routing (ticket 34): ADR-065. Amend by ID, not this list.*
  2. The forward-only real-time clock and its adapters as production design. *Consolidated into the ADR routing (ticket 34): ADR-066. Amend by ID, not this list.*
  3. Timing verified by mechanism. *Consolidated into the ADR routing (ticket 34): REJ-056. Amend by ID, not this list.*
  4. Playwright in, narrow. *Consolidated into the ADR routing (ticket 34): REJ-057. Amend by ID, not this list.*
  5. No `test` profile; a capture-bean override instead. *Consolidated into the ADR routing (ticket 34): ADR-067. Amend by ID, not this list.*
  6. Traceability gate with transcription. *Consolidated into the ADR routing (ticket 34): ADR-068. Amend by ID, not this list.*

  Each passes the three-part test. Rejected: a "sequential suite" ADR, which is easy to reverse. *Consolidated into the ADR routing (ticket 34): REJ-058. Amend by ID, not this list.*
- **Register rows:** §6(a) and §6(b), the Failsafe residual, and ASVS 6.3.8 by mechanism. *Consolidated into the register (ticket 33): R-ADM-011, R-AUD-022, R-AUTH-004, R-BLD-007, R-BLD-008, R-BLD-009, R-CFG-004, R-CRED-022, R-DATA-014, R-FE-006. Amend the table by ID, not this list.*
- **Glossary terms:** *canary secret*; *isolation class*; *context configuration*.
- **Reopening triggers:**
  - any Surefire or Failsafe version bump; *Consolidated into the register (ticket 33): R-BLD-005. Amend the table by ID, not this list.*
  - a mail transport entering scope (TM-13 status pin); *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
  - any change to k or the cleanup cron (via tickets 09 and 29). *Consolidated into the register (ticket 33): R-RL-022. Amend the table by ID, not this list.*
- **Amendments made:** 01, 05, 06, 08, 09, 11, 14, 17, 18, 25 and 29, plus map.md:94.
- **Graduated:** [ticket 32](32-test-plan-table-transcription.md).
- **Note to the user, outside the map:** `.kiro/skills/arch-tests-gen/SKILL.md` pins ArchUnit 1.3.0; 1.5.1 is current.

---

## Amendment from ticket 32 (transcription)

The table is at `docs/test-plan/test-plan.md` (Answer §2's path). From here on, amendments target it by ID.

One erratum here. Answer §5's canary list names "rebind tokens" (16:554), but ticket 28 removed the runner token, so no rebind token exists. T-AUD-013 has been corrected. The runner's stdin password is a fixed client-supplied canary instead, covered by T-AUD-027.
