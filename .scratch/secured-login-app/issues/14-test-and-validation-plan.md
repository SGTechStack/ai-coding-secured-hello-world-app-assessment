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
