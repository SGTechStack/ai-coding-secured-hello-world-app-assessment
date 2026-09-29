# 05b — Audit events, field sets and masking

Research slice of [05 — Which logging standards actually bind a two-process app](../issues/05-logging-standards-applicability.md).
Scope: `Recipes/Logging_AuthN_And_AuthZ_Events.md`, `Recipes/Sensitive_Data_Masking_For_Logs.md`,
`Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md`, `Recipes/Logging_Application_Lifecycle_Events.md`.
All four read in full. `Recipes/Enriching_Logs_With_MDC.md` read only at §3.4 (another agent owns that file).
Cites are `<FileName>.md:<line>` under `App-Standards/Appfw-Logging-Standards/` unless another path is given.

Filenames are abbreviated below: **AuthN** = `Logging_AuthN_And_AuthZ_Events.md`, **Mask** =
`Sensitive_Data_Masking_For_Logs.md`, **Typed** = `Centralising_Audit_Logging_With_A_Typed_Module.md`,
**Lifecycle** = `Logging_Application_Lifecycle_Events.md`, **Std** = `Structured_Logging_Application_Standard.md`,
**Schema** = `Log_Schema.md`, **PrivAdmin** =
`App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Privileged_User_Administration_and_Password_Reset.md`.

---

## 0. The single most important framing

**The recipes are not the source of the obligation.** The mandatory catalogue lives in the parent standard's
Audit Contract: "Audit logs **must** record the following:" (`Std:239`), followed by a 14-item list
(`Std:240-253`). The four recipes in my scope are *how-to* documents: they supply templates and field values
but almost never use "must" about **whether** to log a thing (see the modal audit in §4 and §6 below).

Consequence for the map: a PRD event with no recipe template is **still mandatory** if `Std:239`'s list covers
it. Absence of a template is an implementation gap, not an exemption.

## 1. Audit-event catalogue vs the PRD list vs ticket 01's endpoints

### 1.1 What the AuthN recipe actually templates

| # | Recipe template | `event.action` | Level | Cite |
|---|---|---|---|---|
| A | Authentication success | `user-authentication` / `success` | INFO | AuthN:59-69 |
| B | Authentication failure (non-lockout) | `user-authentication` / `failure` | WARN | AuthN:89-100 |
| C | Account lockout | `user-authentication` / `failure`, `error_code` 423 | **ERROR** | AuthN:76-87 |
| D | Logout | `user-logout` / `success` | INFO | AuthN:159-168 |
| E | Session created | `session-start` / `success` | INFO | AuthN:216-224 |
| F | Session destroyed/expired | `session-end` / `success` | INFO | AuthN:248-256 |
| G | MFA enrol / remove (± failure) | `{factor}-enrol` / `{factor}-remove` | INFO / WARN | AuthN:291-328, :273 |
| H | Authorisation success, sensitive op only | e.g. `data-export` / `success` | INFO | AuthN:359-368 |
| I | Authorisation denied | `access-control` / `failure`, `error_code` 403 | WARN | AuthN:438-449 |

The **Typed** recipe re-templates a subset as typed methods: `loginSuccess` (Typed:102-115), `loginFailure`
(Typed:117-134), `accessDenied` (Typed:136-157), `logout` (Typed:159-175), `profileRead` (Typed:177-192).
`Lifecycle` adds: `application-startup` success (Lifecycle:40-49), `application-startup` failure
(Lifecycle:230-241), dependency health check (Lifecycle:147-166), `application-shutdown` (Lifecycle:273-280).

### 1.2 One-to-one map onto the PRD's seven events and ticket 01's endpoints

PRD audit list is `prd/assessment-prd.md:122` ("login success/failure, lockout triggered, password reset
requested/completed, and role change/enable/disable/delete (actor + target). Never log passwords.").

| PRD event | Endpoint (ticket 01) | Recipe template? | `event.action` |
|---|---|---|---|
| Login success | `POST /api/v1/auth/login` | **Yes** — AuthN:59-69 / Typed:102-115 | `user-authentication` |
| Login failure | `POST /api/v1/auth/login` | **Yes** — AuthN:89-100 / Typed:117-134 | `user-authentication` |
| Lockout | `POST /api/v1/auth/login` | **Yes** — AuthN:76-87 (ERROR) | `user-authentication` |
| Password reset requested | `POST /api/v1/auth/password-reset/request` | **No template in my four files.** Nearest is PrivAdmin:451-456 (`password-reset`), which is the *admin-initiated* reset, i.e. the completion, not the request | `password-reset` (Schema:136) |
| Password reset completed | `POST /api/v1/auth/password-reset/confirm` | **Partial** — PrivAdmin:451-456 templates the admin path; no template for token-confirm self-service | `password-reset` |
| Role change | `PATCH /api/v1/users/{userId}/role` | **No AuthN template.** PrivAdmin:242-249 logs the *rejection* of self-role-change under `user-administration`; the success path is at PrivAdmin:183-188 for creation only | `user-administration` |
| Enable/disable | `PATCH /api/v1/users/{userId}/status` | **No template** | `user-administration` |
| Delete (tombstone, ticket 10) | `DELETE /api/v1/users/{userId}` | **No template** | `user-administration` |

**PRD events the recipes have no template for:** password-reset **requested**, password-reset **confirmed via
self-service token**, role change, status change, delete. All five are nonetheless **mandatory** —
`Std:242` ("Credential changes, including password changes, password resets") and `Std:244` ("Privileged and
admin operations, including user account creation, deletion, and lockout, and role or permission changes").
These must be hand-written from the schema; `Std:255` gives the required context ("who performed the action
(`user.id` UUID), what action was performed, what resource was affected, when it occurred, and whether it
succeeded or failed").

**Events the standards require that the PRD does NOT list** — each a real, in-scope endpoint:

1. **Logout** — `POST /api/v1/auth/logout`. AuthN:136-171 templates it; `Std:250` ("Session management
   failures") plus the recipe's own coverage make it expected. Not in `prd:122`.
2. **Session start / session end** — AuthN:216-256, and `Std:250`. Two more events per login, and ticket 01
   already commits to a JDBC session store, so they will fire.
3. **Authorisation denial** — `access-control` / 403, AuthN:438-449, mandatory via `Std:243` ("Authorisation
   failures and access to sensitive data"). Directly exercises ticket 01's "plain `USER` receives 403 on
   `GET /api/v1/users`" (`prd:160` as restated).
4. **Authorisation success on privileged ops** — AuthN:337, `Std:60`, `Std:244`. Every `/api/v1/users/**`
   admin call is in this class.
5. **Sensitive-data read** — `profile-read` (Typed:177-192, Schema:136), i.e. `GET /api/v1/currentUser` and
   `GET /api/v1/users`. `Std:243`.
6. **User creation** — `POST /api/v1/users` (in scope per ticket 01 `Q10`) and self-registration
   `POST /api/v1/auth/register`. `Std:244` names account creation explicitly; PrivAdmin:183-188 templates it as
   `user-administration`, PrivAdmin:141-156 templates the duplicate-username/email rejections.
7. **Application startup, startup failure, shutdown** — `Std:252` + Lifecycle §3, §5, §6. See §6 below.
8. **Password change (self-service)** — `PATCH /api/v1/currentUser/changePassword`. `Std:242` covers it; no
   PRD audit line.
9. **Repeated input-validation failures** — `Std:249`. This is the natural home for the PRD's **IP-level
   throttling** event (`prd:54`), which otherwise has no audit line at all in `prd:122`.
10. **Password-change enforcement** — `password-change-enforcement` (Schema:136, PrivAdmin:595). Fires only if
    ticket 04 rules the `requirePasswordChange` filter in scope.

*Inference (mine, not the standard's):* the delta is ~10 event types beyond the PRD's 7, so ticket 12's audit
contract is roughly 2.5× the PRD's list. That is a scope fact worth stating explicitly in 12.

### 1.3 `event.action` is a closed enum, and ticket 01 assumed a value outside it

`Schema:136` enumerates the allowed `event.action` values. For this app the reachable set is:
`user-authentication`, `user-logout`, `session-start`, `session-end`, `user-provisioning`,
`user-administration`, `profile-read`, `password-reset`, `password-change-enforcement`, `access-control`,
`data-export`, `application-startup`, `application-shutdown`.

**There is no `user-status-change` and no `user-role-change`.** Ticket 01's `Q9` rationale
(`issues/01-context-topology-and-api-surface.md:99`) states that sub-resource verbs make "ticket 12's audit
events one-to-one with operations — `event.action` distinguishes `user-status-change` from `user-role-change`
without inspecting a request body." **That is not achievable inside the schema enum**: both collapse to
`user-administration`. The distinction must move to another field (candidates: `reason`, as PrivAdmin:143 uses
for rejection reasons, or the `message` string). `Q9`'s *decision* survives — the endpoints are still
correctly split — but its stated *benefit* does not, and ticket 12 must pick the discriminator field.
AuthN:13 makes the enum binding on this recipe: "Schema compliance: Use field values from Log_Schema.md for
`event.category`, `event.type`, `event.action`, and `error_category`".

### 1.4 "actor + target" has no schema slot — and `user.id` is overloaded

`prd:122` requires **actor + target** on role change / enable / disable / delete. The schema has exactly one
identity field, `user.id` (`Schema:104`), and **no target/resource field anywhere** (verified: `grep -n target
Log_Schema.md` returns nothing in the field table). Worse, the two standards use `user.id` for opposite
things on the same kind of line:

- **AuthN / Typed / MDC: `user.id` = the actor.** AuthN:67 resolves it from `Authentication.getPrincipal()`;
  Typed:194-199 likewise; `Enriching_Logs_With_MDC.md:320` writes the authenticated principal's UUID into MDC
  as `user.id`, so it lands on **every** line in the request automatically.
- **PrivAdmin: `user.id` = the target.** PrivAdmin:185 logs `saved.getId()` (the newly created user),
  PrivAdmin:213 and :244 log `existing.getId()` (the user being updated), PrivAdmin:453 logs the user whose
  password was reset. The actor is never logged in those templates.

So on an admin mutation the MDC-injected actor `user.id` and an explicitly-added target `user.id` **collide on
one key**. This is a hard contradiction between two binding documents, unresolvable by reading; it is HD-1
below. `Std:255` confirms both are required ("who performed the action … what resource was affected") without
naming a field for the second.

## 2. Exact field set per auth event

Mandatory-by-prose, applying to **every** auth event:

- **`user.id` as UUID, never a username/email** — "Raw usernames and email addresses must never appear in
  logs. Credentials must never be logged. Always use the system-generated `user.id` UUID" (AuthN:17); repeated
  AuthN:488, Mask:77, Typed:320, Schema:104.
- **`source.ip` on every event** — "Always include `source.ip` on every event as it **is required** for audit
  and incident investigation" (AuthN:37); AuthN:490 restates it as required. Source differs by event:
  `WebAuthenticationDetails.getRemoteAddress()` for authentication (AuthN:121-126), `request.getRemoteAddr()`
  for logout (AuthN:167) — Typed:321 states the rule explicitly.
- **Omit `user.id` on authentication failure** — "For failures, omit `user.id` — the account may not exist,
  and logging it would confirm account existence" (AuthN:37); "do not fall back to logging a hashed username"
  (AuthN:31); Typed:118-119, Typed:319.
- **No enumeration signal** — "Never log the submitted credential or distinguish account-not-found from
  bad-credential" (AuthN:37); "Authentication failures **must not** distinguish account-not-found from
  bad-credential" (AuthN:491).

Per-event field sets, verbatim from the templates:

| Field | A success | B failure | C lockout | D logout | E session-start | F session-end | H authz OK | I authz denied |
|---|---|---|---|---|---|---|---|---|
| `event.kind` | `event` | `event` | `event` | `event` | `event` | `event` | `event` | `event` |
| `event.category` | `["process"]` | `["process"]` | `["process"]` | `["process"]` | `["process"]` | `["process"]` | `["process"]` | `["process"]` |
| `event.type` | `["user"]` | `["user"]` | `["error"]` | `["end"]` | `["start"]` | `["end"]` | `["allowed"]` | `["denied"]` |
| `event.action` | `user-authentication` | `user-authentication` | `user-authentication` | `user-logout` | `session-start` | `session-end` | op-specific | `access-control` |
| `event.outcome` | `success` | `failure` | `failure` | `success` | `success` | `success` | `success` | `failure` |
| `event.severity` | `low` | `medium` | `high` | `low` | `low` | `low` | `low` | `medium` |
| `auth.method` | **yes** | – | – | – | – | – | – | – |
| `user.id` | yes | **omit** | **omit** | yes | **omit** | yes | yes | yes |
| `source.ip` | yes | yes | yes | yes | – | – | – | – |
| `error_code` | – | `401` | `423` | – | – | – | – | `403` |
| `error_category` | – | `cert/auth` | `cert/auth` | – | – | – | – | `cert/auth` |
| `error_follow_up_action` | – | `false` | **`true`** | – | – | – | – | `false` |
| other | – | – | – | – | `session.max_inactive_interval` | – | domain id e.g. `export.id` | – |
| Level | INFO | WARN | **ERROR** | INFO | INFO | INFO | INFO | WARN |
| Cite | AuthN:59-69 | AuthN:89-100 | AuthN:76-87 | AuthN:159-168 | AuthN:216-224 | AuthN:248-256 | AuthN:359-368 | AuthN:438-449 |

Notes on the table:

- **Reason codes.** There is no dedicated reason field in the auth templates. The *only* "reason" mechanism the
  standards demonstrate is PrivAdmin's free-string `reason` key (`duplicate_username` PrivAdmin:143,
  `duplicate_email` :152, `username_change_attempted` :214, `password_change_via_update` :226,
  `self_role_change` :245). It is **not** in `Schema`'s field table. Failure reason on auth events is
  deliberately suppressed instead (AuthN:37). `Std:255`: "For failures, include a generic reason without
  exposing internal detail."
- `auth.method` is `"password"` for this app — AuthN:113 maps `UsernamePasswordAuthenticationToken` →
  `password`. Ticket 01's custom JSON filter subclasses that class
  (`issues/01-context-topology-and-api-surface.md:52`), so `resolveAuthMethod`'s `switch` on
  `getClass().getSimpleName()` will return **`"unknown"`**, not `"password"`, unless the subclass is handled.
  Concrete implementation defect for ticket 12/14. (*This is my inference from reading both files, not a
  statement in either.*)
- `trace.id` is added explicitly in the **Typed** variants (Typed:113, :132, :151, :169, :190) but **not** in
  the AuthN variants — the AuthN recipe relies on MDC to supply it (see §5).
- `error_*` keys use **underscores, not dots**, deliberately: "the ECS formatter pre-seals the `error.*` nested
  object" (Typed:322; also Lifecycle:248).
- **Direct contradiction to flag:** AuthN:377 says on denial "Never expose internal permission logic, role
  names, **or resource paths**", and AuthN:479 verifies "No resource path or role name appears". But
  Typed:148-149 adds `http.request.method` and **`url.path`** to `accessDenied`. The two recipes cannot both
  be followed. → HD-3.

## 3. Masking

**What must not be logged** — Mask:22-33 ("Common examples of data that must not be logged"), the items that
touch this app: passwords, API keys, authentication tokens and credentials (:24); session identifiers
(:25); encryption/private keys (:27); **PII: email addresses, phone numbers, physical addresses, full names**
(:31); full filesystem paths revealing deployment structure (:33). Plus AuthN:17 (usernames, emails,
credentials), AuthN:273 (MFA secrets — vacuous here), Mask:132 (SQL statements, connection strings),
Lifecycle:70 (credentials in startup config logging), PrivAdmin:461 (the one-time plaintext password returned
by admin reset — "treat as sensitive, never log").

Also binding and easy to miss: **`DEBUG` and `TRACE` must be disabled in production** (Mask:87, :226, :234).

### The prescribed mechanism, and whether it is exclusive

**Prevention at the source is the primary control; Logback masking is explicitly secondary and explicitly
optional.**

- "The safest approach is to prevent sensitive data from reaching the logging layer in the first place. …
  Masking after the fact is a fallback — prevention at the source is the primary control." (Mask:36)
- "This is a second line of defense, not a substitute for prevention. … Apply masking for fields where
  prevention is not feasible." (Mask:150)
- **Masking is conditional on an encoder choice:** "This section is only relevant if the application uses
  `logstash-logback-encoder` as its Logback encoder. **If the application does not use this encoder, skip this
  section and rely on prevention at the source as described in section 5.**" (Mask:146)

So: **no, `MaskingJsonGeneratorDecorator` is not the only permitted mechanism — it is not even required.**
Mask:137-143 lists five permitted alternatives for a value that must be logged: exclude entirely, replace with
an identifier, log outcome only, sanitize parts, dedicated safe DTOs. The mechanism when masking *is* used is
`net.logstash.logback.mask.MaskingJsonGeneratorDecorator` with `<defaultMask>****</defaultMask>` and
`<path>` entries, **masked by field path, not by value** (Mask:166-186), and "Add only field paths required by
policy" (Mask:186).

A separate mandatory control that is **not** masking: **sanitisation of user input against log injection** —
`value.replaceAll("[\\r\\n|]", "")` before logging (Mask:113-129). Masking explicitly cannot help here:
"Masking applies to JSON fields only, **not** to the log message text" (Mask:189).

### "Plaintext password is never logged" — how it is covered

The PRD criterion (`prd:40`, `prd:122`) is satisfied, and specifically **by prevention, not by masking**:

1. Mask:24 lists passwords as data that must not be logged; Mask:36 makes prevention the control of record.
2. AuthN:17 "Credentials must never be logged"; AuthN:37 "Never log the submitted credential"; AuthN:488
   restates it.
3. The login-failure templates **take no credential argument at all** — AuthN:89-100 receives only the
   `Authentication`, Typed:117 receives only `HttpServletRequest`, and neither reads a password. The typed
   module makes this structural: no call site can pass a password because no method accepts one.
4. Mask:38-58 forbids the delivery mechanism that would leak it: logging a whole object. Never
   `addKeyValue("loginRequest", req)`; log named safe fields only.
5. Mask:87 disables DEBUG/TRACE in production, closing the accidental-payload-dump path.
6. Mask:92 keeps credentials out of stack traces via externalised secrets.
7. Masking would **not** cover it anyway: a password in the `message` string is unmaskable (Mask:189-193).

**Verification is prescribed:** Typed:298-307 (`loginFailure_emitsWarnEvent_withNoUserIdentity` asserting
`doesNotContainKey("user.id")`), Mask:202-215 (assert `doesNotContain(<secret>)` against captured log output),
Mask:218-227 checklist. Ticket 14 can lift these directly.

*Inference:* the PRD criterion is therefore testable as a **negative assertion on captured log output**
(`ListAppender`, Typed:251-313) rather than as a masking-config check.

## 4. Is a typed audit module MANDATORY or RECOMMENDED? — **RECOMMENDED**

**Finding: RECOMMENDED. The recipe contains no mandatory modal about adopting it.** Verified by grepping
`must|shall|required|mandatory|recommend|should` across all 343 lines of **Typed**; every hit is about
something else:

- Typed:5 — "When the schema changes, every copy **must** be updated" (a statement about the *problem*, not an
  instruction).
- Typed:253 — "no **mandatory** application context" (about testability).
- Typed:294, :320, :333 — "**never** log hashed PII / raw usernames / need a database lookup" (the PII rule,
  which *is* mandatory but is inherited from AuthN:17).
- Typed:327 — "no integration test **required** for the schema" (a benefit claim).

The framing is a design argument, not an obligation: "As a service grows, security events … are logged in many
handlers and controllers. Without a dedicated owner, the same pattern repeats" (Typed:5); "A typed
audit-logger module **concentrates this knowledge behind a small, stable interface**" (Typed:7). §7 is titled
"Key decisions" (Typed:316) — decisions, not requirements. The Key Takeaways are bare imperatives ("Inject
`AuditLogger` as a Spring bean", Typed:331) with no normative weight.

**Contrast — what IS mandatory nearby, and must not be confused with it:**

- **`Std:239`**: "Audit logs **must** record the following" — the event catalogue is mandatory.
- **`Std:327`**: "**[Enforced Constraint]** … **Separate critical audit logs from standard application logs by
  routing them to a dedicated appender and log destination**" — a **separate audit appender is mandatory**.
  Note the Typed recipe's `LoggerFactory.getLogger("audit")` (Typed:100) is exactly the named-logger hook that
  makes `Std:327` configurable. **`Std:369`**: "Audit events are present in the dedicated audit log
  destination, not only in the standard application log."
- **`Std:262`**: "Alert when audit logging fails" — mandatory.
- **AuthN:35**: "Register a single event listener to centralise all authentication logging rather than
  scattering log statements across individual providers or filters" — a bare imperative for *centralising*,
  without "must".

*My reading, stated as inference:* the dedicated-appender mandate (`Std:327`) plus the one-`audit`-logger
convention the Typed recipe uses (Typed:100) means that **something** must own the `"audit"` logger name even
if the typed module is declined. Building the typed module is the cheapest way to satisfy `Std:327`
consistently, but declining it is not a standards violation. → HD-2.

## 5. Does the AuthN recipe depend on MDC? — **It depends on MDC for two fields, and the dependency is soft.**

**Stated dependency.** AuthN:12 lists as a prerequisite: "Familiarity with MDC filters. See Enriching Logs
with MDC". AuthN:6 describes the design as using "MDC propagation to carry user context across all log entries
in a request". AuthN §9 (`:454-456`) instructs: "Register an `MdcUserFilter` after Spring Security's
authentication filters to write `user.id` into MDC. Once set, the UUID appears automatically on all log
entries within the request", pointing to `Enriching_Logs_With_MDC.md` §3.4 for the implementation
(`Enriching_Logs_With_MDC.md:310-329` is the filter; `:355` the registration:
`http.addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class)`).

**Where it is load-bearing.** Two places, both about failure events:

1. AuthN:31 — for pre-authentication failures, "Omit [`user.id`] entirely and **rely on `trace.id` for
   correlation**". AuthN:489 repeats: "Use `trace.id` for correlation instead."
2. Typed:319 — "the `trace.id` is sufficient for post-incident correlation", and
   `MDC.get("trace.id")` is read directly at Typed:113/:132/:151/:169/:190. Typed:12 makes it a hard
   prerequisite: "An MDC-based correlation filter that populates `trace.id` for every request."

So: **the AuthN recipe's own nine templates stand alone** — none of them calls `MDC.get(...)`; every field in
§2's table is set explicitly. Strip MDC entirely and all nine still compile and emit their listed fields.
**But the recipe's stated correlation story collapses**: the failure and lockout events carry *no* identity by
design, and their only substitute is `trace.id`, which only MDC supplies. A lockout event with neither
`user.id` nor `trace.id` is an unattributable line.

**Verbatim verdict:** AuthN:482 lists under Verification, "All log entries within an authenticated request
carry `user.id` automatically" — i.e. the recipe's own acceptance test **cannot pass without `MdcUserFilter`**.

*Conclusion (mine):* the templates are independent; the recipe as a whole is not. MDC must be in scope. And
note the collision this creates with §1.4 — `MdcUserFilter` puts the *actor* on every line under the key
`user.id`, which is the same key PrivAdmin uses for the *target*.

## 6. Lifecycle (startup/shutdown) — **MANDATORY, via the parent standard**

- **The obligation:** `Std:252` — "Audit logs **must** record … **Application startup and shutdown**". That
  settles it; the recipe is only the how.
- **The recipe's own language is non-normative.** Grep for mandatory modals across all 337 lines of
  **Lifecycle** finds only: ":11" ("Required for error field mapping" — a prerequisite on the encoder), ":70"
  ("**Never** log credentials, connection strings with passwords, API keys, tokens, or authentication
  secrets"), ":74" ("`service.*` fields … **must not** be set via `addKeyValue`"). Everything about *whether*
  to log lifecycle events is a bare imperative: "Use `ApplicationRunner` as the hook" (:16), "listen for
  `ApplicationFailedEvent`" (:210), "Listen for `ContextClosedEvent`" (:254).

What exactly, then:

| Event | Hook | Fields | Level | Cite |
|---|---|---|---|---|
| **Startup success** | `ApplicationRunner.run` | `event.kind: event`, `category ["process"]`, `type ["start"]`, `action application-startup`, `outcome success`, `severity low`, **`host.name`**, **`host.ip`** | INFO | Lifecycle:40-49, verified :297 |
| **Startup failure** | `@EventListener(ApplicationFailedEvent)` | same but `type ["error"]`, `outcome failure`, `severity critical`, `error_code 500`, `error_category application`, `error_follow_up_action true`, **`.setCause(failure)`** | ERROR | Lifecycle:230-241, verified :298 |
| **Shutdown** | `@EventListener(ContextClosedEvent)` | `type ["end"]`, `action application-shutdown`, `outcome success`, `severity low`. No host fields. | INFO | Lifecycle:273-280, verified :302 |
| **Dependency health check** | `@EventListener(ApplicationReadyEvent)` | **`event.kind: state`** (not `event`), `category ["configuration"]`, `type ["connection"]`, `service.connection` array of `{name,type,status}`; on failure `severity critical` + the three `error_*` keys | INFO / ERROR | Lifecycle:147-166, verified :299-301 |

Binding details:
- **`ContextClosedEvent`, not `@PreDestroy`** — "`@PreDestroy` fires later in the shutdown sequence, per bean,
  … which is too late for a reliable shutdown log" (Lifecycle:287).
- **No duplicate entries** — "None of the lifecycle entries appear more than once (no duplicate listeners)"
  (Lifecycle:303).
- **`host.name`/`host.ip` rationale is containerisation** (Lifecycle:16, :310) — this effort excludes
  containerisation (`map.md:59`), so the *fields* are required by the template while their *motivation* is
  absent. `InetAddress.getLocalHost()` may return `127.0.0.1` (Lifecycle:74). *Inference:* keep the fields —
  the verification line (:297) asserts them — and accept the low-value values. Cheap either way.
- **The health-check template's shape only half-fits.** Lifecycle:123-143 checks a database *and* an external
  API. This app has exactly one dependency (the JDBC datasource, per ticket 01 `Q1`). The DB half applies
  verbatim; the external-API half is vacuous. The `service.connection` array degenerates to one element. Also
  note `Std:252` mandates startup/shutdown but **not** the health check — the health check is recipe-only, so
  **RECOMMENDED**.
- **Prerequisite caveat:** Lifecycle:11 makes the **Custom Structured Log Encoder** recipe a prerequisite "for
  error field mapping". That file is outside my scope, but it means startup-failure logging cannot be
  implemented from Lifecycle alone. Flagged for whoever owns `Custom_Structured_Log_Encoder.md`.

## 7. Vacuous for this app shape

Stated as vacuous with the reason, so it is skipped knowingly rather than silently:

| Requirement | Cite | Why vacuous |
|---|---|---|
| **MFA enrolment/removal audit** — "must be audited" | AuthN:269-330, AuthN:472-475, `Std:241` | MFA is a PRD exclusion and `Appfw-Mfa-Standards/` does not apply (`map.md:57`). The eight `{factor}-enrol/remove` actions have no surface. **Note the modal is "must"** — this is a mandatory requirement rendered vacuous by scope, not a waived one. It binds if MFA ever enters scope. |
| **MFA factor in `auth.method`** | `Std:240` ("the MFA factor used when applicable") | "when applicable" — not applicable. |
| **JWT/OAuth2 token-validation failure logging; "Never log the token value"** | AuthN:132 | JWT is documented-design-only (`map.md:56`); no token is ever validated. |
| **`remember-me` as an `auth.method` value** | AuthN:116 | Remember-me is out of scope (`map.md:63`). `resolveAuthMethod` can drop the branch. |
| **`oauth2` / `jwt` branches of `resolveAuthMethod`** | AuthN:114-115 | Standalone app, session cookies only. |
| **`data-export` action** | AuthN:363, Schema:136, `Std:246` | No export, report or bulk-query surface in the endpoint inventory. *Caveat:* `PATCH /api/v1/users/batchResetPassword` (ticket 01 inventory) is a bulk operation; PrivAdmin:519-528 mandates aggregate-count-only logging for it (`success_count`, `total_count`, "**never** log per-user data inside the loop"). So bulk logging is **not** vacuous — it just isn't `data-export`. |
| **Masking of `url.query`** as configured | Mask:174 | No endpoint in ticket 01's inventory takes a sensitive query parameter; credentials travel in JSON bodies (`Q32`) and the session cookie. *Inference:* the masking decorator has no field path worth configuring today. Prevention (Mask §5) is the whole control. |
| **`MaskingJsonGeneratorDecorator` as a whole** | Mask:146 | Conditional on choosing `logstash-logback-encoder`; the standard explicitly permits skipping the section. Which encoder is chosen is another agent's finding (`Custom_Structured_Log_Encoder.md`). |
| **Cross-service field consistency** rationale for UUIDs | AuthN:17 ("consistent across all services") | One service. The rule still binds (PII), but its stated justification is absent. |
| **External-API half of the dependency health check** | Lifecycle:135-143, :179-192 | Exactly one dependency (JDBC datasource). |
| **Distributed/containerised rationale for `host.name`/`host.ip`** | Lifecycle:16, :310 | Single instance, no containerisation (`map.md:59`, ticket 01 `Q1`). Fields kept; rationale absent. |
| **`Logging_Batch_And_Scheduled_Jobs.md` cross-references** | Lifecycle:70 ("For scheduled job registrations at startup") | Confirms the map's existing ruling (`map.md:27`) from a second direction: no scheduled jobs, so nothing to register at startup. |
| **MDC in async/batch contexts** | `Enriching_Logs_With_MDC.md:454-563` (headings only) | No async or batch work. Another agent owns the file; noted only because AuthN §9 sends the reader there. |

## 8. Questions needing a HUMAN decision

Each is a candidate wayfinder ticket. Ordered by blocking power over ticket 12.

**HD-1 — What field carries the *target* user, given `user.id` already means the actor?** *(highest priority;
blocks ticket 12 entirely)*
`prd:122` demands actor + target on role change / enable / disable / delete. `Schema:104` defines only
`user.id` and the schema has **no** target/resource field. AuthN:67, Typed:196 and
`Enriching_Logs_With_MDC.md:320` all bind `user.id` to the **actor** (and MDC writes it on every line
automatically). PrivAdmin:185/:213/:244/:453 binds `user.id` to the **target**. On an admin mutation both want
the same key. `Std:255` requires both facts. Options: (a) add a non-schema key such as `target.user.id` or
`target_user_id`, accepting deviation from `Schema`; (b) follow PrivAdmin and let `user.id` mean the target on
admin events, accepting that MDC then overwrites or conflicts and the actor is lost; (c) suppress
`MdcUserFilter`'s `user.id` on `/api/v1/users/**` and log both explicitly. This is a genuine contradiction
between two binding documents — it cannot be resolved by more reading.

**HD-2 — Build the typed `AuditLogger` module, or inline the log statements?**
The module is **RECOMMENDED, not mandatory** (§4: zero mandatory modals across 343 lines). But `Std:327` makes
a **dedicated audit appender mandatory**, and Typed:100's `LoggerFactory.getLogger("audit")` is the hook that
makes it configurable. Given the standing preference for fuller standards conformance, the likely answer is
"build it" — but it is a cost the PRD does not name, and it is the user's call, not mine. If built, it also
becomes the single enforcement point for HD-1 and for the "no password argument anywhere" property (§3).

**HD-3 — On authorisation denial, log `url.path` and `http.request.method`, or not?**
AuthN:377 — "Never expose internal permission logic, role names, or **resource paths**" — and AuthN:479
verifies "No resource path or role name appears". Typed:148-149 adds `http.request.method` and **`url.path`**
to the very same event. Two binding recipes give opposite instructions. Without the path, a 403 on
`GET /api/v1/users` is indistinguishable from a 403 anywhere else, which guts the PRD's `prd:160` test as
restated in ticket 01.

**HD-4 — How are `user-status-change` and `user-role-change` distinguished, since `event.action` cannot?**
`Schema:136`'s enum has no such values; both collapse to `user-administration`. Ticket 01's `Q9` rationale
(`issues/01-context-topology-and-api-surface.md:99`) assumed otherwise. Options: a non-schema `reason` key
(the only precedent is PrivAdmin:143's free strings, itself not in the schema); the `message` string (not
queryable as a field); or extend the enum (a standards deviation). Ticket 12 cannot write its contract without
this.

**HD-5 — Does the PRD's IP-level throttling event get an audit line, and under what action?**
`prd:54` requires IP throttling; `prd:122` does **not** list it as an auditable event. `Std:249` mandates
logging "Repeated input validation failures, which may indicate brute force or automated attack attempts", and
`Std:248` "Security control bypass attempts, including … anti-automation". No recipe in my scope templates it,
and the lockout template (AuthN:76-87) is account-scoped, not IP-scoped. Also: what `event.action`, and is it
ERROR (matching lockout's "active attack pattern" rationale, AuthN:492) or WARN? Interacts with ticket 07.

**HD-6 — Which of the ~10 non-PRD mandatory events are in scope for the build?**
§1.2 lists them (logout, session start/end, authz denial, authz success on privileged ops, `profile-read`,
user creation, startup/shutdown, self-service password change, validation-failure/throttling,
password-change-enforcement). Every one is mandatory under `Std:239-253`, and the standing preference for full
conformance suggests all in — but that is ~2.5× the PRD's stated audit scope and ticket 14's test count scales
with it. Needs an explicit yes so it is not discovered mid-build.

**HD-7 — Does `Std:327`'s mandatory separate audit destination get implemented, and how?**
"**[Enforced Constraint]** … route audit logs to a dedicated appender and log destination"; `Std:369` verifies
their presence there. The PRD says "Structured log lines (no dedicated table required)" (`prd:122`) — which
rules out a table but says nothing about a second appender. A separate file appender is cheap; `Std:262`'s
"Alert when audit logging fails" and `Std:259`'s tamper-resistance are not, and arguably fall under the PRD's
hosting-infrastructure exclusion (`map.md:59`). Needs a line drawn.

**HD-8 — Is the self-service password-reset *request* audited at all, given enumeration resistance?**
`prd:122` requires "password reset requested"; `prd:120` and `prd:82` require that the response never reveal
whether the email exists. AuthN:31 and AuthN:37 forbid logging an identifier for an unresolved subject
precisely because it "would confirm account existence to anyone who can read logs" — and Typed:118-119
restates it. A reset request for an **unknown** email is exactly that case. So the request event may have to
be logged with `source.ip` and `trace.id` only, with no identity at all — which raises whether it satisfies
`prd:122` in any useful sense. Interacts with ticket 11.

**HD-9 — `session-start` / `session-end`: keep both, given the acknowledged double-logging?**
AuthN:265 states that with both `LogoutAuditHandler` and `SessionEventLogger` active, "an explicit logout
produces two log entries … This is expected behaviour." With `HttpSessionEventPublisher` registered
(AuthN:188-191) and a JDBC session store (ticket 01 `Q1`), a single login/logout cycle emits four events
(session-start, login success, logout, session-end). Accept the volume, or drop the session pair as not named
by `prd:122`? Note `Std:250` mandates "Session management failures" — arguably narrower than normal
start/end. Interacts with ticket 13.

**HD-10 — Fix `resolveAuthMethod` for the custom JSON filter.**
Arguably an implementation note rather than a human decision, recorded here so it is not lost. AuthN:112-118
switches on `auth.getClass().getSimpleName()`; ticket 01 mandates a **subclass** of
`UsernamePasswordAuthenticationFilter` (`issues/01-context-topology-and-api-surface.md:52`). If that filter
produces a custom token subclass, `auth.method` silently becomes `"unknown"` instead of `"password"`, breaking
`Std:240`'s requirement to record the authentication method. Ticket 14's test for the custom filter
(`:55`) should assert `auth.method == "password"`.
