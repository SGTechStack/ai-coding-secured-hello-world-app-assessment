# 14 — Test and validation plan

Type: grilling
Status: resolved
Blocked by: 02, 08, 12
Map: [Secured Login App](../map.md)

## Question

What is the test plan, and what must pass before the build is considered done?

Reconcile two lists. The PRD requires automated integration tests for the security-critical paths and enumerates a minimum set (`prd/assessment-prd.md:150`): login (success, wrong password, unknown username with an identical generic error, locked account), lockout and cooldown reset, IP throttling independent of account lockout, session-cookie rejection after logout, reset token single-use and expiry, reset invalidating sessions, admin self-action guards, and a `USER` getting 403 on `/api/admin/**`.

The standard's §5 (`Standalone_User_Access_Control_Application_Standard.md:419`) requires **thirteen** categories, several of which the PRD's list does not cover: CSRF protection tests, user data access control, account hygiene, input validation, security headers and CORS, logging and audit assertions, dependency security checks, and its own test-data guidance. Per the authority order, §5 wins — so produce the merged, deduplicated plan, and state which §5 categories are vacuous given the out-of-scope rulings (account hygiene being the obvious candidate).

Then settle the mechanics:

- What counts as an integration test here: `@SpringBootTest` with MockMvc, full HTTP via `TestRestTemplate`, H2 versus Testcontainers (which interacts with 02's database decision and with the schema-portability requirement — testing only on H2 proves less about Postgres portability).
- How lockout-cooldown and token-expiry tests control time without sleeping.
- How audit/log assertions are made (12's contract has to be assertable, or it is decoration).
- Frontend testing: scope and tooling, or an explicit decision to leave it to implementer discretion as the PRD permits.
- `Dependency Security Checks` (§5, line 519) — which tool, and whether a finding fails the build. `dependency-vuln-scan` exists in this repo's skills.
- Coverage expectations, if any.

**This ticket also graduates the map's "handoff definition of done" fog**: decide here whether `im8-review`, `semgrep`, `arch-tests` and/or `pre-prod-check` must run clean before [17](17-handoff-to-delivery-pipeline.md) closes.

Blocked on 02 (test database), 08 (the authorization matrix is what the authz tests assert) and 12 (the audit contract is what the logging tests assert).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Add to the test plan a block for the **custom JSON `AuthenticationFilter`**. 01 chose JSON-only login (`POST /api/v1/auth/login`), and `Standalone_User_Access_Control_Application_Standard.md:44` states this requires a custom `AuthenticationFilter` — which **no recipe supplies**. It is therefore the one security-critical component in this build with no standards recipe behind it, and the tests are the only thing standing in for that missing review. At minimum assert: JSON body parsed and authenticated; session ID **rotates** on success (session-fixation defence, `Standalone_Session_Login_with_CSRF_Bootstrap.md:32`); failures still reach the lockout counter via the retained `failureHandler`; `200` carries an **empty** body; `401` message is generic and identical for unknown-username and wrong-password; `429` carries `Retry-After`; filter ordering relative to `CsrfFilter` holds.

Note also that 01 restated `prd/assessment-prd.md:160` — the role-enforcement test is now "a plain `USER` receives 403 on `GET /api/v1/users`", since `/api/admin/**` no longer exists.

**Amended by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md).** 05 established what logging conformance actually consists of here, which turns this ticket's existing "how are audit/log assertions made" line into a concrete list. Three items.

**A latent defect that the build will otherwise ship silently.** `Recipes/Logging_AuthN_And_AuthZ_Events.md:112-118` resolves the authentication method for the audit line by **switching on the authentication class name**. [01 — Context, topology and API surface](01-context-topology-and-api-surface.md) chose a *custom subclass* of `UsernamePasswordAuthenticationFilter` to parse the JSON login body — so the recipe's resolver will yield `"unknown"` instead of `"password"`, breaching `Structured_Logging_Application_Standard.md:240`, which mandates recording the authentication method on every authentication audit event. Nothing fails visibly. Add an assertion that a successful login's audit event carries the authentication method `password`. This belongs in the custom-filter test block 01 already added to this ticket.

**Log assertions the plan must cover**, all of which 05 established as mandatory rather than optional:

- `trace.id` and `span.id` are **present on every log line** — the recipe's own verification bar (`Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:1074`) and a required field set (`Structured_Logging_Application_Standard.md:126`). Worth asserting on the failed-login and 403 paths specifically, since 05 found `trace.id` is the *only* permitted subject field on an unknown-username failure, and [09 — HTTP security, CSRF, CORS, headers](09-http-security-csrf-cors-headers.md)'s filter ordering is what makes it present there. If the MDC filter is ordered wrong, these lines lose their only identifier and nothing else catches it.
- **MDC does not leak between requests.** `Structured_Logging_Application_Standard.md:323` requires clearing in `finally`; 05 flagged that `putCloseable` closes *before* a `catch` runs and *removes* rather than restores a pre-existing key (`Recipes/Enriching_Logs_With_MDC.md:233-235`) — both on the failed-login and lockout paths. A test that a failed login followed by a successful one does not carry the first request's MDC state.
- **No plaintext password, and no reset token or link, in any log output** — the PRD criterion plus `Recipes/Sensitive_Data_Masking_For_Logs.md:24` and [11 — Password reset flow](11-password-reset-flow.md)'s stubbed `EmailService`. 05 established this is satisfied by **prevention, not masking** (`Mask:150` calls masking "a second line of defense, not a substitute", and `Mask:189` excludes the `message` field from the decorator's reach) — so the test is the *only* enforcement. Assert against captured log output across the register, login-failure, reset-request and reset-confirm paths.
- **Audit events reach the separate destination.** `Structured_Logging_Application_Standard.md:327` is an `[Enforced Constraint]` requiring audit logs on a dedicated appender; assert routing, not just emission. `Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md:327` notes a `ListAppender` makes this assertable without an integration test.
- **Enumeration resistance holds in the logs, not just the responses.** The identical-generic-error test the PRD requires (`prd/assessment-prd.md:150`) has a logging twin: an unknown username and a wrong password must produce audit lines that are equally indistinguishable (`Recipes/Logging_AuthN_And_AuthZ_Events.md:31,37`). This is a real gap class — a correct HTTP response with a leaky log line passes every test currently planned.
- **Level assignment**: login success `INFO`, failure `WARN`, **lockout `ERROR`** (`AuthN:37`). Cheap to assert and easy to get wrong.

**One §5 category to check off explicitly.** This ticket's brief already notes §5 requires "logging and audit assertions" as one of its thirteen categories (`Standalone_User_Access_Control_Application_Standard.md:419`). The list above is what satisfies it; 05's Answer is the source for the full mandatory event set that these assertions sample from, which is roughly **2.5× the PRD's audit list** — so decide whether every mandatory event gets an assertion or only a named subset, and say which.

**Note on the handoff-definition-of-done fog this ticket graduates.** [05](05-logging-standards-applicability.md) confirmed the logging standard needs **no** external log platform, collector or trace exporter to conform (`Trace:14`, `Trace:10`, `Structured_Logging_Application_Standard.md:287` note) — so logging conformance is fully assertable in-repo, and cannot be deferred to an integrator as an excuse.

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md).** 02 resolved this ticket's "test database" blocker and, in doing so, handed it one hard constraint and three assertions.

**The constraint: a Testcontainers PostgreSQL run of the security-critical integration suite.** 02 answered `Q6` as *PostgreSQL declared target, H2 confined to `dev`/`test`*, and chose Liquibase over Flyway, both explicitly in order to make the PRD's "schema portable to Postgres/MySQL later" a **testable claim rather than an aspiration**. Executing the suite against real PostgreSQL is how that claim is discharged; H2's `MODE=PostgreSQL` narrows the gap but does not close it. **The test design is this ticket's** — which subset runs twice, whether the H2 run stays the default, how the two are wired.

02 also resolved the tension this raises, so it need not be re-litigated here: the PRD's containerization exclusion is read as covering **deployment packaging**, not test infrastructure. Docker-for-tests is materially different from shipping a container image. It is a judgement call and is recorded as one.

**A recommendation, not a decision: a fixed `Clock` bean.** This ticket's brief asks how lockout-cooldown and token-expiry are tested without sleeping. 02 made every timestamp a UTC `Instant` in a `TIMESTAMP WITH TIME ZONE` column, which makes an injectable `Clock` the natural answer — and 07's lockout state and 11's token expiry both read from it.

**Three additional assertions 02's decisions make necessary:**

- **Liquibase runs clean on both vendors**, since `spring.jpa.hibernate.ddl-auto: validate` means a changelog that drifts from the entities fails at startup rather than at runtime. Cheap to assert and it protects every other test.
- **The `PRINCIPAL_NAME` index exists.** 02 routed Spring Session's DDL through Liquibase via `sqlFile` against the *packaged* per-vendor script precisely because a hand-copy that dropped that index would break Story 7's "invalidate all sessions" in a way `validate` does **not** catch. Liquibase's checksum on that file is the tripwire for a Spring Session upgrade changing the DDL — worth a note that a checksum failure is the intended loud failure, not a bug to suppress.
- **Story 12's no-duplicate-admin branch is now reachable.** 02 chose a file-based `dev` H2 specifically because an in-memory database starts empty every boot, leaving that acceptance criterion with no observable branch. An integration test asserting bootstrap idempotence across two starts against a persistent database is what cashes that in — see [16](16-admin-bootstrap-and-seeding.md).

**One scope note.** 02's IP-throttle counters are **in-memory**, so they are neither restart-durable nor multi-instance-safe. Tests must not assume throttle state survives a context restart, and the "IP throttling engages independently of account lockout" case the PRD requires (`prd/assessment-prd.md:150`) is asserted against an in-memory counter, not a table.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** Three things land here. **Four ArchUnit rows** are already decided and this plan owns running them: no `System.out.println`/`e.printStackTrace()` (`Structured_Logging_Application_Standard.md:330`); only `com.assessment.auth.audit` logs to the audit logger; **no `@Async` and no `@Scheduled` anywhere under `com.assessment.auth`** (the tripwire keeping 05's 1,254-line batch recipe dormant — framework-internal scheduling such as 02's Spring Session sweep is invisible to the rule and unaffected); and no manually constructed HTTP clients (`:322`). **`dependency-check-maven`** is wired in a non-default Maven profile with `failBuildOnCVSS=7`, so this plan decides whether that profile is part of the definition of done or an on-demand run. And **`application-test.yaml` lives in `backend/src/test/resources/`** with in-memory H2 in `MODE=PostgreSQL`, alongside the Testcontainers PostgreSQL portability run 02 handed over.

**Amended by [04 — Password policy and history](04-password-policy-and-history.md).** One trap and several concrete assertions.

- **Do not copy the recipe's password-history verification procedure.** `Standalone_Self-Service_Password_and_History_Management.md:269-272` says: set the password to `TempPassword1!`, perform three subsequent updates, then verify that `TempPassword1!` is **accepted** again. That is only true under the recipe's own reading, where the history list holds three entries *including* the current password. 04 took the literal clause reading of `Standard:355` — "retain **3 previous** passwords" — so this app blocks **four** values: the current one plus three prior. A test transcribed from the recipe will fail against a correct implementation. Assert the four-value behaviour: after three updates from `TempPassword1!`, that value is still **rejected**; it becomes available only after a fourth.
- **Assert the 72-byte boundary.** 04 capped password length at 72 because `BCrypt` in Spring Security 7.0.6 throws `password cannot be more than 72 bytes` rather than truncating. A 72-character password must be accepted and a 73-character one must be rejected **as a validation error, not a `500`** — the failure mode this cap exists to prevent. Charset is printable ASCII only, so characters and bytes coincide here by design.
- **Assert the policy is enforced identically at all four write paths** — registration, reset-confirm, self-service change, admin-create. 04 chose a shared `PasswordPolicy` + `PasswordHistoryService` precisely so they cannot drift, which makes a parameterised test across the four endpoints the natural shape. Include: length floor 12, the denylist, the username/email-local-part containment rule, and — for the three paths where a user already exists — the history rule.
- **Assert composition is *not* required.** 04 deliberately dropped the recipe's regex, so a 12-character all-lowercase passphrase with spaces must be **accepted**. This is the assertion most likely to be written backwards by someone reading the recipe rather than the standard.
- **Assert the `PasswordChangeFilter` allowlist exactly.** A user with `requirePasswordChange=true` reaches only `GET /api/v1/csrf`, `GET /api/v1/currentUser`, `PATCH /api/v1/currentUser/changePassword` and `POST /api/v1/auth/logout`; every other row of 01's inventory returns `403`, including for a `USER_MANAGER`. Logout is the row the privileged recipe omits and 04 added — assert it explicitly, since it is the one a reviewer comparing against `Privileged:533-537` would flag as a deviation.
- **Assert both flag escape routes.** Reset-confirm clears `requirePasswordChange` (the public-endpoint path around the filter), and a successful self-service change clears it. Also assert the trap in the negative: a flagged user who completes the email reset flow is **not** still blocked.
- **Assert the full change-password sequence**: `{currentPassword, newPassword}` with a wrong current password is rejected; a correct one returns `204`, invalidates **all** sessions including the caller's own, and invalidates any pending unused reset token for that account (`Standard:356`).
- **Assert cost 12.** `BCryptPasswordEncoder` defaults to 10; the standard requires ≥12 (`Questions.md:287`). A stored hash's `$2a$12$` prefix is the cheapest possible check that the encoder was not defaulted.
- **Assert the seeded admin is flagged** — 04 handed 16 that constraint, and it is observable on first boot.
- **No scheduled-job assertions.** 04 ruled the 30-day grace period out of scope with the hygiene jobs, so there is nothing to test for auto-disable; 15's ArchUnit rule banning `@Scheduled` is the standing guard, and it already covers this.
- **Schema portability.** 04's `password_history` changelog uses no vendor-specific types, so it must apply cleanly under both H2 and the Testcontainers PostgreSQL run this ticket owns.

**Amended by [19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md).** 19 confirmed the custom encoder is built, which adds two **named** test cases — not review notes — plus one assertion that guards a compliance clause.

- **Assert the three throwable-less events emit nested `error.code`.** This is the single most important logging assertion in the plan, because it guards 19's one intentional departure from its recipe. `Recipes/Logging_AuthN_And_AuthZ_Events.md:76-87`, `:89-101` and `:438-449` template account-lockout (`ERROR`, `error_code: 423`), authentication failure (`WARN`, `401`) and authorisation denial (`WARN`, `403`); **none** attaches a throwable. Under `Custom_Structured_Log_Encoder.md:408` as written, the encoder strips the workaround keys and the `error` object is never created, so **all nine fields vanish silently**. 19 deviated — the encoder creates the object — because the drop violates `Structured_Logging_Application_Standard.md:167`. Assert on captured log output that lockout yields `error.code == 423`, `error.category == "cert/auth"`, `error.follow_up_action == true`, and no `error.type`/`error.message`/`error.stack_trace`. A regression here would gut the audit trail with **no failing build and no error line** — exactly the class of failure a test exists for.
- **The Spring Boot upgrade retest is a compliance retest, not hygiene.** `Custom_Structured_Log_Encoder.md:24` warns the encoder extends an internal Spring Boot class (`org.springframework.boot.logging.logback.StructuredLogEncoder`) with no migration guarantee, and 15 recorded that liability against the Boot 4.0.x pin. 19 raised the stakes: the encoder is now the **only** mechanism discharging `Structured_Logging_Application_Standard.md:328`'s masking constraint, so a silent break on upgrade is an IM8/Appfw compliance break, not a formatting glitch. Name the encoder test class in the plan as a required gate, so a Boot bump cannot be merged without it.
- **Assert the emitted format is JSON on every appender, and that no appender was left on a literal.** `:320` requires format selection via `CONSOLE_LOG_STRUCTURED_FORMAT` / `FILE_LOG_STRUCTURED_FORMAT`; `:326` bans mixing plain text with JSON in one stream. With three appenders (19's correction to 15: `CONSOLE`, `APPLICATION`, `AUDIT`), the cheap structural check is that each parses as JSON and carries `ecs.version`.
- **Assert masking at the encoder boundary, and sanitisation at the log site separately.** `Sensitive_Data_Masking_For_Logs.md:230` means masking never reaches `message` text, so the two need distinct assertions — a masked field path, and a log site that does not put the value in the message in the first place. The field list is 12's; the plan needs a case per entry.

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** The matrix is the single source of authorization truth now, so it becomes a test table rather than prose — and 08 added two recipe-free components to the list 01 started.

- **Walk the matrix.** One positive and one negative case per row of 08's 21-row table. This is what discharges `Standalone_User_Access_Control_Application_Standard.md:427` (each HTTP method independently authorized), `:428` (`200/201` for authorized, `403` for not) and `:429` (user-management endpoints reject non-administrators **including on their own account** — so `GET /users/{ownId}` as a `USER` is a `403` row, not an oversight).
- **Two acceptance criteria that only pass because of explicit configuration**, both worth a test that names why: a plain `USER` gets `403` on `GET ${api.base-path}/users` (Story 8), and an **unauthenticated** call to `GET ${api.base-path}/hello` gets **`401`** (Story 5). The second is the load-bearing one — 08 verified that without an explicit `authenticationEntryPoint`, `ExceptionHandlingConfigurer.createDefaultEntryPoint` returns `Http403ForbiddenEntryPoint` and the answer is `403`. Assert the status, not just "rejected".
- **The role hierarchy has exactly one load-bearing row, so it needs exactly one test:** a `USER_MANAGER` reaching `GET ${api.base-path}/hello`, which they hold no explicit grant for. 08 narrowed 03's ruling — the three platform/self rows became `authenticated`, leaving `/hello` as the only `hasRole('USER')` row. If this test is dropped, deleting `role-hierarchy` breaks nothing visible.
- **The `/error` dispatch.** Assert that a genuine `400` from a controller reaches the client as a `400`. 08 verified `AuthorizationFilter`'s `filterErrorDispatch` defaults to `true` in `spring-security-web-7.0.6`, so without row 6's `permitAll` on `/error` every error forward returns `403` instead. This is a one-line regression away at all times.
- **The `PasswordChangeFilter` preempts the matrix.** Assert that a flagged `USER_MANAGER` calling `GET ${api.base-path}/users` gets `PASSWORD_CHANGE_REQUIRED`, **not** an authorization denial — and that the response body actually carries the code, which is the part `Priv:599`'s `sendError` cannot do. Also assert the negative: no authorization-failure audit line is written for that request (12 owns the consequence; this is the test that pins it).
- **A failed current-password check on `PATCH ${api.base-path}/currentUser/changePassword` returns `400`**, never `401` or `403`. The reason is behavioural and worth stating in the test name: `:437`'s global SPA interceptor logs the user out on either of those, so a typo would look identical to a successful change.
- **Two recipe-free components now owe tests**, joining 01's custom JSON `AuthenticationFilter`: the `authenticationEntryPoint`/`accessDeniedHandler` pair, and the `PasswordChangeFilter`'s response writing. No recipe in the binding set configures `exceptionHandling` at all.
- **Do not copy `RBAC:114-116`'s verification steps verbatim.** Step 1's `USER_READ` authority does not exist under 03's role model, and step 3's "unconfigured endpoints return `403`" is true for authenticated callers but `401` for anonymous ones under 08's entry point.

## Answer

Resolved by grilling, one round. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**Full-HTTP integration tests via `TestRestTemplate`, H2 by default and the whole security-critical suite re-run against Testcontainers PostgreSQL. A fixed `Clock`. `ListAppender` for log assertions. No coverage threshold — a named required-test list instead. And the handoff-definition-of-done fog dissolves rather than resolving: the gates cannot run before 17 closes, because there is no code yet.**

### The merged plan: §5's thirteen categories

Per the authority order §5 wins, so the plan is organised by its categories rather than the PRD's list, with the PRD's eight folded in as cases.

| §5 category | Status | Source of cases |
|---|---|---|
| Authentication and session | **in** | `Std:433-438`; PRD login/logout; 13; 01's custom filter block |
| Account lockout and rate limiting | **in** | `Std:448-453`; PRD lockout + IP throttling; 07's three counters |
| Password policy and history | **in** | `Std:467`, `:508`; 04's seven assertions |
| Password reset | **in** | `Std:458-461`; PRD Stories 6–7; 11 |
| User data access control | **in** | `Std:471-480`; 08's matrix walk; 10's three projections |
| Role-based access control | **in** | `Std:423-429`; 08; 03's role hierarchy |
| Account enumeration resistance | **in** | `Std:504-507`; PRD identical-error; 11's timing floor |
| CSRF protection | **in** | `Std:87`, `:443-446`; 09 |
| Security headers and CORS | **in** | `Std:498-501`; `HDR:155`; 09 |
| Input validation | **in** | `Std:86`; 04; 21's envelope |
| Logging and audit | **in** | 12's 23 events; 05; 19's encoder |
| Dependency security checks | **in** | `Std:519`; 15's `dependency-check-maven` |
| Account hygiene | **VACUOUS** | scheduled hygiene jobs are out of scope; 15's ArchUnit `@Scheduled` ban is the standing guard |

**Three more categories are vacuous and are recorded rather than silently skipped**, so a reviewer sees they were read:

- **Bulk/batch operations** — 10 ruled `Q24` out, so `Std:107`, `:255` and `Priv:484`'s 5000-entry limit have no endpoint to bind. **No test**, because writing one would require building the endpoint to test it.
- **Admin-generated password composition** (`Std:357-361`) — 11's token model means the system never generates a password. No subject.
- **Multi-instance / distributed behaviour** (`Std:453`, `:434`'s distributed clause) — 01 fixed single-instance; 02's throttle counters are explicitly not multi-instance-safe. Asserted as *documented limitations* in 17's deltas, not as passing tests.

### Mechanics

**Integration tests are full HTTP: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate`.** Not MockMvc for the security-critical suite. MockMvc runs the filter chain but simulates the servlet layer, and this application's correctness lives precisely in things MockMvc smooths over: real `Set-Cookie` attributes (`SameSite`, `Secure`, `Path` — 13 flagged that logout's cookie deletion must match them or the browser ignores it), the CSRF bootstrap round-trip through `GET /csrf` into an `X-CSRF-TOKEN` header, and `getRemoteAddr()` returning something real for 07's throttle key and 18's `source.ip`. A test that passes under MockMvc and fails in a browser is worse than no test.

MockMvc keeps a narrower job: controller-level slices for request-validation and serialization shapes, where the servlet layer is genuinely irrelevant.

**Database: H2 by default, PostgreSQL for the whole security-critical suite.** 02 handed over the portability obligation and left the design here.

- Default profile `test`: in-memory H2 in `MODE=PostgreSQL`, config in `backend/src/test/resources/application-test.yaml` (15). Fast, runs on every `mvn test`.
- **The entire security-critical suite re-runs against Testcontainers PostgreSQL** under a `postgres` profile, bound to `mvn verify`. Not a subset — subsetting means choosing which portability bugs to not find, and 02 chose Liquibase specifically to make portability testable rather than aspirational. The cost is wall-clock on `verify`, which is the right place to pay it.
- 02 already ruled the PRD's containerization exclusion covers deployment packaging, not test infrastructure. Not re-litigated.

**Time: one fixed `Clock` bean**, replacing the application's injectable `Clock` in tests. Lockout cooldown (07's 20 minutes), absolute session timeout (13's 8 hours), idle timeout (15 minutes) and reset-token expiry (11's 30 minutes) all read from it, so every one is tested by advancing a `Clock`, never by sleeping. This was 02's recommendation and it is adopted as the decision.

**Log assertions: Logback `ListAppender`.** `Centralising_Audit_Logging_With_A_Typed_Module.md:327` notes it makes routing assertable without an integration test. Two appenders are attached in tests — one to the `audit` logger, one to root — so "this event reached the audit stream **and not** the application stream" is directly assertable, which is what `Std:327` actually requires.

**Coverage: JaCoCo report generated, no build-failing threshold.** §5 mandates no coverage figure. A percentage gate on a security application rewards testing getters and says nothing about whether the 21-row matrix is walked — and this plan's real gate is the named required-test list below, which a coverage number cannot substitute for. Recorded as a deliberate choice rather than an omission, since `build-check` expects thresholds and will otherwise read this as a gap.

**Which of 12's 23 audit events get assertions: all of them, at two depths.** A named subset invites the unasserted ones to rot, and emission assertions are nearly free via `ListAppender`. So:

- **All 23** get an emission assertion — the event fires on its trigger, with the right `event.action`, `event.reason` and level.
- **Six get a full field-schema assertion** — login success, login failure (unknown username), lockout, authorization denial, forced-change denial, reset requested. These are the security-critical lines, the ones 18 and 12 fought over, and the ones where a missing field is invisible.

### Frontend testing: narrow and named

The PRD permits implementer discretion. **Vitest + React Testing Library, scoped to two things only**: the auth context (how `requirePasswordChange` and `role` are read from `GET /currentUser` and gate routes) and the global axios interceptor (that it distinguishes a bare `401` from a `403` carrying `PASSWORD_CHANGE_REQUIRED` or `SELF_ACTION_NOT_ALLOWED`, and logs out only on the first).

Those two are where 10's refinement of 08's constraint actually lands — the whole reason 21's `code` field exists is so the interceptor can tell session death from an in-app error, and nothing on the backend can test that. Everything else on the frontend is left to implementer discretion, explicitly.

**No Playwright/E2E suite in this plan.** The repo's `browser-test` skill is an acceptance gate against the issue's criteria, and it belongs to 17's handoff rather than to this test plan — running it needs a built application. Named here so its absence is a decision, not an oversight.

### Dependency security (`Std:519`)

**`dependency-check-maven` in its non-default profile with `failBuildOnCVSS=7` (15), part of the definition of done, run at `verify` — not on every build.** A finding at CVSS ≥ 7 fails the gate.

Two honest operational notes, because this gate fails for boring reasons more often than real ones: it needs an **NVD API key** to avoid severe rate-limiting, which is an environment variable an integrator must supply; and its first run downloads a large vulnerability database, so a cold CI cache makes it slow. Both belong in 17's handoff rather than being discovered on the first pipeline run. 15 left the *schedule* to the integrator and that stands — this plan fixes only that it must pass once before the build is called done.

### The definition-of-done fog dissolves

This ticket was handed the map's **"handoff definition of done"** patch — whether `im8-review` and/or `pre-prod-check` must run clean before 17 closes.

**They cannot run before 17 closes, because when 17 closes there is no code.** The map is a planning effort; 17's job is to hand a specification to `stories-to-issues` and `/do-work`. Every one of those skills inspects a codebase that does not yet exist. The question as posed has no answer, and the right response is to say so rather than to invent a gate.

**So the definition of done belongs to the build, and 17's job is to record it, not satisfy it.** Two distinct lists:

**A. Per slice, inside `/do-work`'s loop:**
1. `mvn verify` green — unit, integration (H2), and the Testcontainers PostgreSQL run
2. **Five ArchUnit rows** — 15's four (no `System.out`/`printStackTrace`; only `com.assessment.auth.audit` logs to the audit logger; no `@Async`/`@Scheduled`; no manual HTTP clients) plus 13's fifth (**no `response.sendError` under `com.assessment.auth`**, now three filters deep)
3. `semgrep` clean on changed code
4. Frontend: `tsc --noEmit`, lint, Vitest

**B. Once, before the build is called finished:**
5. `im8-review` clean — 05 removed the excuse: logging conformance needs no external platform, collector or exporter (`Trace:14`, `Std:287` note), so it is fully assertable in-repo
6. `dependency-vuln-scan` / `dependency-check-maven` with no CVSS ≥ 7
7. `browser-test` against the PRD's twelve stories — **including 16's three-step first boot** (log in → change password → log in again), which reads as broken software if a reviewer meets it unannounced
8. `arch-tests-plan` / `arch-tests-gen` rows all green

**`pre-prod-check` is not required, and that is a decision with a reason.** It orchestrates seven gates — API code docs, authorization matrix verification, OWASP threat modelling, technical architecture doc with dependency scan, full-codebase IM8/ARC, observability readiness, secrets and config audit. Three of them are already discharged by this map and would be re-derived from scratch (the authorization matrix is 08's; the threat surface is spread across 07/09/11/18; the secrets posture is 15's), and it terminates in a **tech-lead sign-off report** — a human governance step, not a build gate. Recorded as *available and recommended before any real deployment*, not as a condition for calling this build done. Reopening it is an integrator's call.

### The required-test list: cases that would otherwise ship silently

Beyond the category sweep, these are the ones where a plausible implementation passes every other test and is still wrong. Each is named so it cannot be quietly dropped.

**From 09 — and this one runs first:**
- **A `Secure` session cookie round-trips over `http://localhost`.** 09 kept `secure: true` in every profile on the basis that browsers treat localhost as a trustworthy origin — and explicitly recorded that this was *not verified in a browser*. It is the single load-bearing unverified claim on the map, and **everything else in the integration suite depends on it**. First test to run; if it fails, 09's fallback is a one-line `dev` override.
- No `XSRF-TOKEN` cookie exists (`HDR:157`) — proves session-bound CSRF, not double-submit
- `POST /auth/login` without a CSRF token returns `403` (`Std:87`)
- The five headers via `curl -I` equivalent (`HDR:155`); `preload` **absent** from HSTS; `Referrer-Policy` present
- A non-allowlisted origin is rejected (`Std:501`)
- `/actuator/**` returns `403` for everyone including `USER_MANAGER`

**From 19 — the highest-value logging assertion in the plan:**
- **Lockout emits `error.code == 423`, `error.category == "cert/auth"`, `error.follow_up_action == true`, and no `error.type`/`message`/`stack_trace`.** Under `Encoder:408` as written, all nine fields across the three throwable-less security events vanish **silently** — no failing build, no error line, a gutted audit trail. 19 deviated deliberately; this test is what pins the deviation.
- The encoder test class is a **named required gate**, because the encoder extends an internal Spring Boot class with no migration guarantee (`Encoder:24`) and is now the only mechanism discharging `Std:328`'s masking constraint. A Boot bump that breaks it is a compliance break, not a formatting glitch.
- Every appender's output parses as JSON and carries `ecs.version` (`Std:320`, `:326`)

**From 05 — the defect that ships silently:**
- **A successful login's audit event carries authentication method `password`.** `AuthN:112-118` resolves the method by switching on the authentication **class name**, and 01 chose a custom subclass of `UsernamePasswordAuthenticationFilter` — so the recipe's resolver yields `"unknown"`, breaching `Std:240`. Nothing fails visibly.
- `trace.id` and `span.id` on **every** line, asserted specifically on the failed-login and `403` paths — where 12 established `trace.id` plus `source.ip` are the *only* fields present
- MDC does not leak between requests: a failed login followed by a successful one carries no residue (`Std:323`; `MDC:233-235`'s `putCloseable` trap, which 12 banned outright)

**From 12 — enumeration resistance in the logs, not just the responses:**
- **An unknown username and a wrong password produce audit lines that are equally indistinguishable.** A correct HTTP response with a leaky log line passes every other test in this plan.
- No log line anywhere contains a password, a password hash, a reset token (plaintext **or** hashed), a session id, a username, or an email address
- `event.reason` never takes a value outside 12's closed vocabulary
- Audit events reach the `AUDIT` appender **and not** the application one

**From 04 — the test most likely to be written backwards:**
- **After three updates from `TempPassword1!`, that value is still rejected**; it frees only on the fourth. `Self-Service:269-272` says the opposite, and a test transcribed from the recipe fails against a correct implementation.
- A 12-character all-lowercase passphrase **with spaces** is accepted — composition is deliberately not required
- 72 characters accepted, 73 rejected **as a validation error, not a `500`**
- Stored hashes carry `$2a$12$`, proving the encoder was not left at its default cost of 10
- The `PasswordChangeFilter` allowlist is exactly four entries, **including logout** — the row the privileged recipe omits and 04 added

**From 08 — two criteria that pass only because of explicit configuration:**
- **Unauthenticated `GET /hello` returns `401`, not `403`.** Without the explicit `HttpStatusEntryPoint`, `createDefaultEntryPoint` returns `Http403ForbiddenEntryPoint`. Assert the status, not "rejected".
- A genuine controller `400` reaches the client as a `400` — without row 6's `permitAll` on `/error`, `filterErrorDispatch=true` turns every error forward into a `403`
- `USER_MANAGER` reaches `GET /hello` — the **only** load-bearing role-hierarchy row; drop this and deleting `role-hierarchy` breaks nothing visible
- A flagged `USER_MANAGER` gets `PASSWORD_CHANGE_REQUIRED` in the **body**, and **no authorization-failure audit line** is written (12's named hole, pinned here)
- A wrong current password returns `400` — `401`/`403` would log the user out on a typo

**From 10, 11, 13, 16:**
- Delete writes a tombstone **before** removing the row, atomically; re-registering a tombstoned username **or email** both fail; `password_history` rows vanish on delete
- `/currentUser` never returns a hash, a lock state or a failed-attempt count; a `USER` calling `GET /users/{ownId}` gets `403`
- Reset: token single-use; expiry on the fixed `Clock`; a new token invalidates the prior; confirm invalidates all sessions and clears `requirePasswordChange`
- Identical body **and bounded timing** for registered versus unregistered reset email (11's floor is a *partial* discharge of `Std:247` — assert the bound, do not claim constant time)
- A second login expires the first session; the concurrent limit **survives a restart** (`Std:409`'s actual property, and what `SessionRegistryImpl` would have broken)
- A replayed post-logout cookie is rejected; `Clear-Site-Data` present on logout
- **Bootstrap idempotence across two starts against file-based H2** — the branch 02 chose a persistent dev database to make observable; plus startup failure when `APP_ADMIN_PASSWORD` is absent or violates 04's policy

**From 02 and 15:**
- Liquibase applies clean on **both** vendors; a checksum failure on Spring Session's packaged DDL is the **intended loud failure** on upgrade, not a bug to suppress
- The `PRINCIPAL_NAME` index exists — `ddl-auto: validate` does **not** catch its absence, and Story 7 breaks without it

**From 01 — the component with no recipe behind it:**
- The custom JSON `AuthenticationFilter`: JSON body parsed; **session id rotates** on success; failures still reach the lockout counter through the retained `failureHandler`; `200` carries an empty body; `401` is generic and identical for unknown-username and wrong-password; ordering relative to `CsrfFilter` holds

Three components now carry the "no standards recipe behind it" flag — 01's JSON filter, 08's entry-point/access-denied pair, and the `PasswordChangeFilter`'s response writing — plus 19's encoder. For all four, **the tests are the only thing standing in for a missing standards review**, and that is why they are named individually rather than left to a coverage number.

### Amends

- **15** — a fifth ArchUnit row (no `response.sendError`); `dependency-check-maven`'s profile is part of the definition of done at `verify`; note the NVD API key requirement.
- **17** — takes definition-of-done list **B** verbatim, plus the `browser-test` note about 16's three-step first boot, plus the two documented limitations (throttle counters not restart-durable, single-instance only) that must appear as deltas rather than as failing tests.
- **12** — all 23 events are asserted for emission; six for full field schema.
- **09** — the `Secure`-cookie-on-localhost test is the first gate; its failure has a known one-line fix.
- The map's **handoff definition of done** fog patch is **dissolved, not graduated**: the gates run against a build, and 17 closes before one exists.
