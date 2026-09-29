# 14 — Test and validation plan

Type: grilling
Status: open
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
