# 06 — Decide the API error envelope and the enumeration-safe response contract

Type: grilling
Status: resolved
Blocked by: 01, 02, 04

## Question

What does every error response from this API look like, and what precisely must be identical across
authentication outcomes so account existence cannot be inferred?

This is decided early because the credential flows, lockout, and admin tickets all emit errors and
must not each invent their own shape.

## Inherited from ticket 19

[Resolve the MFA scope conflict raised by IM8 ac-2](19-mfa-scope-conflict.md) added a TOTP factor gate
and, with it, four outcomes this envelope must express plus one deliberate exception to the enumeration
rule:

- **Four MFA outcomes need stable machine-readable types**: second factor required, enrolment required,
  invalid code, throttled (429 retaining `Retry-After`). The corpus was rejected as a source here because
  it gives three contradictory statuses for "not enrolled" — 403 `SETUP_REQUIRED`, 422, and 412 — and has
  the frontend branch on a body containing the exact English string `"User Details not found."`. We
  branch on a type, never on prose.
- **A factor-state endpoint** reports which factors the session holds and which it needs, so the SPA can
  challenge an admin eagerly after password login. Decide whether it is its own endpoint or a field on
  ticket 11's self-read endpoint.
- **The exception:** every MFA response goes to an already-authenticated session about its own account,
  so these may be specific without opening an enumeration channel. The uniformity rule this ticket exists
  to enforce does not extend to them, and that carve-out should be written down rather than inferred.

## What to decide

**Envelope shape.** RFC 9457 Problem Details (`application/problem+json`) or a custom envelope? The
App Standard demands a "stable, machine-readable error body" and names specific error codes —
`user exist`, `username change not allowed`, `account locked`, `account cannot authenticate`,
`too many requests`, `password reset token expired or invalid` — so the code vocabulary is partly
given. Decide whether those strings are the wire format or internal identifiers mapped to something
else, and whether the recipes already fix this (see "Inventory the prescribed recipes").

**Status code map.** The standard fixes much of it: 401 for auth failures, 403 for authorization and
CSRF failures, 400 for validation, 429 with `Retry-After` for rate limits. Confirm and fill gaps —
notably what a request against a *disabled* account returns, and what the reset-token endpoints
return.

**The enumeration contract.** The standard requires identical status codes, bodies, **and response
timing** across login, password reset, and registration regardless of account state. Decide:

- The single generic failure body for all auth failures. `account cannot authenticate` is the
  standard's catch-all — is that the wire code for wrong-password, unknown-user, locked, and
  disabled alike?
- How the internal reason is captured for operators without leaking it to the caller (log-only, per
  the standard).
- **Timing.** This is the hard part and the one most often skipped. Unknown username short-circuits
  before any hash verification, so it returns measurably faster than a wrong password. Decide the
  mitigation: verify against a dummy hash for unknown users, pad to a fixed floor, or something
  else. Note the interaction with BCrypt work factor from "Decide the password policy and hashing
  parameters" — a high work factor makes the timing gap larger, not smaller.
- Whether validation errors may be specific. The standard says yes for password strength and history
  ("a specific error must be returned indicating which rule was violated") while auth failures must
  be generic. Draw that line explicitly so it isn't decided ad hoc per endpoint.

**Known tension to resolve.** The standard's error list includes `user exist` (400) for duplicate
username or email, yet also demands enumeration-safe registration. The likely resolution is that
`user exist` belongs to the **admin-initiated** user creation flow, where the caller is trusted,
while **self-registration** must stay generic. Confirm or reject that reading — it directly
determines whether PRD Story 1's "clear validation error" survives.

## Done when

An implementer can write the error handler and the auth failure path from the answer without
guessing, the timing mitigation is chosen, and the `user exist` tension is resolved in writing.

## Answer

Resolved over two grilling rounds (Q1–Q9, Q10–Q16), all recommendations accepted. Source corpus read:
`Standalone_User_Access_Control_Application_Standard.md` §3.2/§3.5/§5 and its Failure Paths,
`Standalone_User_Access_Control_Application_Standard_Questions.md`, the four standalone recipes,
`Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Application_Standard.md` §3.2,
`MFA_Frontend/Standalone/MFA_Frontend_Standalone_Standard.md`, and the RFC 9457 precedent in
`Appfw-File-Standards/*/recipes/shared/13-rfc-9457-error-responses.md`.

### Three findings that reframed the ticket

1. **§5 requires error bodies to "conform to the documented schema" and that schema does not exist.**
   Grep for `problem`, `problem+json`, `ProblemDetail`, `7807`, `9457` across all of
   `Appfw-User-Standards/**` returns **zero hits**. The user pillar names eight lowercase prose strings
   and no field set. The **File** pillar mandates six fields (`type`, `title`, `status`, `detail`,
   `instance`, `traceId`) with a full `@ControllerAdvice` recipe; the **MFA** pillar's own
   reimplementation recipe uses `ProblemDetail`. So the "org API error schema" that MFA_Core §3.2 defers
   to is RFC 9457, and the user pillar is the outlier that never says so. Adopting 9457 is org-consistent,
   not a local invention.

2. **Two premises in this ticket's own body were wrong.** There is no `SETUP_REQUIRED` string anywhere in
   `Appfw-Mfa-Standards` (grep: zero hits), and no 403-for-not-enrolled — in the MFA frontend contract
   `403` means *insufficient privileges*. The real spread for "not enrolled" is 412 (MFA_Core's own
   flowchart), 422 with a prose `detail` (its Org Standard rule), and a client-side pre-check that never
   issues the request at all. MFA also lives in a separate pillar, not under `Appfw-User-Standards`.

3. **The corpus mandates identical response *timing* in four places and supplies no mitigation anywhere** —
   no dummy hash, no floor, no padding, in the standard, the Questions file, or any recipe. Worse, the
   prescribed login failure handler calls `recordFailure` only when `username != null` and does no hash
   work for unknown users, so the reference implementation exhibits the exact gap the standard forbids.

### 1. Envelope: RFC 9457, six fields minimum

`application/problem+json` per RFC 9457, with the File pillar's field set as the floor:
`type`, `title`, `status`, `detail`, `instance`, `traceId`, plus the extension members below.
`spring.mvc.problemdetails.enabled=true` (it defaults to `false`), and a `@RestControllerAdvice`
extending `ResponseEntityExceptionHandler` so framework exceptions get the same envelope.

Rejected: Boot's `BasicErrorController` default body (no code field, prose `message` that varies with the
exception — not the "stable, machine-readable error body" §3.2 line 244 demands) and the self-service
recipe's single-key `{"error": "..."}`. **ADR owed**: deviation from the login recipe's `sendError`, with
that recipe set's own three-incompatible-shapes contradiction as the justification. *Consolidated into the ADR routing (ticket 34): ADR-031. Amend by ID, not this list.*

### 2. The identifier lives in a `code` extension; `type` is derived

`code` is the single branch point for clients: SCREAMING_SNAKE, a closed Java enum so it cannot be
invented per-endpoint. It matches the MFA frontend's own test fixtures (`INVALID_FACTOR`,
`MISSING_HEADER`, `TOO_MANY_REQUESTS`). `type` is `https://<app>/errors/<kebab-of-code>` for RFC
conformance and doc linking only — **no client reads `type`**. The duplication is deliberate: branching on
a URI forces every client into string surgery on a URL.

The eight §3.2 strings are **`title` values and log reasons, never wire identifiers**. They are templates
for humans (`role <operation> not allowed`, `request body with list of more than {max} entries is not
allowed`), not constants. **We never branch on prose** — which is also why the MFA corpus's
`detail == "User Details not found."` contract is rejected as a branch key.

| `code` | Status | Replaces / fills |
|---|---|---|
| `AUTHENTICATION_FAILED` | 401 | §3.2 `account cannot authenticate` — the *only* auth failure code |
| `PASSWORD_CHANGE_REQUIRED` | 403 | the "specific error code" §3.2 requires and never defines |
| `CSRF_TOKEN_INVALID` | 403 | undefined in corpus |
| `ACCESS_DENIED` | 403 | §3.2 `role <operation> not allowed` + authorization failures |
| `VALIDATION_FAILED` | 400 | §3.1 format/length rejections |
| `PASSWORD_REJECTED` | 400 | Failure Path 18 strength/history |
| `RESET_TOKEN_INVALID` | 400 | §3.2 `password reset token expired or invalid` |
| `USER_EXISTS` | 400 | §3.2 `user exist` — **admin-initiated creation only** (see §5 below) |
| ~~`USERNAME_CHANGE_NOT_ALLOWED`~~ | ~~400~~ | **Removed** (ticket 16 amendment). Unreachable: ticket 11 has no generic update endpoint. |
| ~~`BATCH_TOO_LARGE`~~ | ~~400~~ | **Removed** (amendment at 06:355–357). Ticket 11 declined batch reset. |
| `TOO_MANY_REQUESTS` | 429 | §3.2 `too many requests` |
| `MISSING_FACTOR` / `INVALID_FACTOR` | 412 | MFA_Core §3.2 |
| `FACTOR_ENROLMENT_REQUIRED` | 422 | MFA_Core §3.2, minus the prose-matching |
| `INTERNAL_ERROR` | 500 | `detail` is a constant; never `ex.getMessage()` |

### 3. One generic authentication failure; `account locked` never reaches the wire

§3.2 lists `account locked` (401) and, in the same bullet, requires that response be identical to an
invalid-credential response. **Those two clauses cannot both hold.** Line 258 then calls
`account cannot authenticate` the catch-all for locked *or* disabled. Resolution: §3.2's Authentication
and Session list silently mixes wire codes with log reasons. `account locked` is a **log reason only**.

Wrong password, unknown user, locked, disabled, and grace-period-auto-disabled all return one identical
401 `AUTHENTICATION_FAILED`. The internal reason goes to the audit log at WARN, keyed by `session.hash`
and `trace.id` where the UUID is not yet resolved (§3.4 already prescribes exactly that). This must be
written down explicitly, because an implementer reading §3.2 alone *will* emit `account locked` and open
the channel. *Consolidated into the ADR routing (ticket 34): ADR-033. Amend by ID, not this list.*

### 4. What "identical" means, field by field

The rule is **no field may vary as a function of account state or flow** — it cannot mean byte-identical,
since `traceId` is per-request by definition. *(Amended by [ticket 27](27-inbound-trace-context.md): "by
definition" holds only because every inbound trace is restarted. Under Boot's default continuation, a caller could
pin it. The claim here, and "the operator's only handle" below, rest on that decision.)*

On every authentication failure: `type`, `title`, `status`, `detail`, `code` are compile-time constants.
`instance` is **explicitly set to a constant** (`about:blank`, or the member omitted) — *not* left to
Spring, which populates it from the request URI. That default is a live defect against §3.2 line 246:
login, password-reset-request, and registration are three different paths, so three different `instance`
values, on precisely the responses the uniformity rule protects. `traceId` varies freely; it correlates
with nothing an attacker can use and is the operator's only handle.

Expressed as a test assertion, not a principle: *assert the response body equals this exact literal, for
all six account states, across all three endpoints.* *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-006. Amend the table by ID, not this list.*

### 5. Timing: framework baseline plus two ordering rules, no artificial floor

`DaoAuthenticationProvider` already performs a dummy encode and a full `matches()` against a cached dummy
hash for unknown users, with `hideUserNotFoundExceptions=true` by default (established in
[ticket 02](02-verify-standard-currency.md)). The primary mitigation is therefore the framework's, and our
job is **not defeating it**:

- **Rule 1** — the per-account lockout check runs *inside or after* the authentication attempt, never as a
  pre-auth branch keyed on account existence.
- **Rule 2** — no `PasswordEncoder` wrapper or fast path may skip `matches()`.

A fixed response-time floor is **rejected**: it must be set above the slowest legitimate outcome, a naive
sleep-to-floor leaks whenever the floor is exceeded, and it is harder to test than the control it replaces.
Note the interaction with [ticket 07](07-password-policy-and-hashing.md): a higher BCrypt work factor widens
any gap that survives, it does not narrow it.

### 6. The specific-vs-generic line: split by *what the error is about*

Failure Path 18 and §5 line 507 require rule-specific errors for strength and history; §3.2 line 246
requires the password reset flow to be uniform. Both apply inside one request. The reconciliation, which is
nowhere in the corpus:

- **Account state** (exists, locked, disabled, credentials wrong) → **always generic**.
- **Submitted value quality** (strength rule, history reuse, format, length) → **always specific**,
  including inside reset and change flows. It leaks nothing about the account, only about the string the
  caller just typed.
- **Ordering rule** — the reset-token check must pass **before** any password-quality validation runs.
  Otherwise a specific strength error confirms the token was valid.

### 7. Validation error shape: an `errors` extension, on two codes only

`errors: [{field, rule}]`, permitted **only** on `VALIDATION_FAILED` and `PASSWORD_REJECTED`. `rule` is an
enum (`MIN_LENGTH`, `HISTORY_REUSE`, `BREACHED`, `CHARACTER_SET`, …), not prose — satisfying "indicating
which rule was violated" without shipping English through the API; the SPA renders its own copy. `detail`
stays constant per code. Explicitly **prohibited** on the 401/403 codes, which puts §6's split in the type
system rather than in a reviewer's memory. This is an extension, not inheritance: no field-error structure
exists anywhere in the corpus.

### 8. `user exist` and the PRD Story 1 conflict: split the flow, add activation

§3.2 gives `user exist` (400) for duplicates while line 246 names **user registration** as a flow that must
be enumeration-uniform. PRD line 38 makes the specific error acceptance criteria. All three cannot hold.

- **Admin-initiated creation → specific `USER_EXISTS` 400.** The caller is already privileged, so there is
  no enumeration channel, and the reviewer needs a usable admin UI.
- **Self-registration → uniform 202 regardless of outcome**, with the account usable only via a hashed
  single-use **activation token** issued only when the registration is genuinely new. Q22 of the Questions
  file already prescribes activation token hashing, so the corpus contemplates this flow.

Returning a bare uniform success with no activation flow was rejected as unworkable: with no email
verification in scope, a colliding user would get a success they could never act on.

**ADR owed** (deviation from PRD Story 1's "clear validation error"). **This grows
[ticket 10](10-credential-flows.md)** by an activation issue-and-redeem sub-flow — accepted knowingly, and
it also closes the unverified-email abuse vector where one party registers using another's address. *Consolidated into the ADR routing (ticket 34): ADR-032. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-009. Amend the table by ID, not this list.*

### 9. Headers: closing the 429 oracle

The uniformity rule covers status, body, and timing — **not headers**. §3.5 makes the login limit
per-account (10/min), so a per-account 429 that can only fire for accounts that exist is an enumeration
oracle the written rule does not cover.

- **The per-account limiter counts attempts against any submitted username, existent or not.** A miss
  creates a counter keyed by the submitted string rather than a resolved account, so an unknown username is
  throttled on exactly the same schedule as a known one.
- `Retry-After` carries **integer seconds** (MFA_Core line 198 fixes the unit; the user standard never does).
- **No `WWW-Authenticate`** on our 401s. Grep finds it neither required nor forbidden across
  `Appfw-User-Standards/**`; `BasicAuthenticationEntryPoint` would add one, which is wrong for a
  session-cookie SPA.

### 10. Status code map: the five contradictions resolved

| Case | Corpus | Decided |
|---|---|---|
| Logout on dead session | "401/403", stated as a pair three times, deliberately unresolved | **403** — the CSRF filter runs first and the session-bound token is already gone. Not engineered around; §5 explicitly forbids exempting logout from CSRF |
| Session expired (idle/absolute) | Failure Path 9 says redirect to `/login?expired` (302); the recipe filter returns 401 | **401**, never a redirect. A 302 to an HTML login page is incoherent for an SPA on its own origin |
| Batch payload too large | §3.2 and §5 say 400; admin recipe comments "413 / 400" | **400** |
| Forced password change | 403 + "a specific error code", never defined | **403 `PASSWORD_CHANGE_REQUIRED`** |
| Reset token invalid/expired | 400 | **400 `RESET_TOKEN_INVALID`** — only distinguishable from a strength failure because of the code field |

### 11. MFA outcomes: keep the statuses, drop the prose

MFA_Core §3.2 is contradicted by its own flowchart (422-for-not-enrolled vs 412 for "TOTP key null") and by
its reimplementation recipe (a third mapping: 422 / 401, with `pd.setDetail(ex.getMessage())`, which
violates line 195 of its own standard by piping internal exception messages to the caller).

Decided: **412 `MISSING_FACTOR` and 412 `INVALID_FACTOR`, 422 `FACTOR_ENROLMENT_REQUIRED`, 429
`TOO_MANY_REQUESTS` + `Retry-After`, 500 `INTERNAL_ERROR` with a constant `detail`.** Keeping the statuses
preserves MFA-pillar alignment at no cost, since [ticket 19](19-mfa-scope-conflict.md) replaced the
enforcement layer and [ticket 14](14-frontend-architecture.md) builds our own frontend rather than consuming
the prescribed hooks. The `"detail": "User Details not found."` mandate is **dropped** — a contract that
requires string-matching English is a defect, not a requirement. **ADR owed.** *Consolidated into the ADR routing (ticket 34): REJ-092. Amend by ID, not this list.*

**The enumeration carve-out, written down rather than inferred:** every MFA response goes to an
already-authenticated session about its own account, so it may be specific without opening an enumeration
channel. §3/§4's uniformity rule does **not** extend to the 412/422 responses.

Open dependency: which Spring Security 7 exception actually surfaces a missing factor authority is unknown —
[ticket 22](22-mfa-core-recipe-extraction.md) is the research ticket and is unclaimed;
[ticket 23](23-totp-enrolment-stepup-and-reset-flows.md) wires the plumbing. **This ticket fixes only the
wire contract.**

### 12. Factor state: a field on the self-read endpoint

`factors: { held: [...], required: [...], enrolled: bool }` on the existing self-read response, **not** a new
endpoint. The SPA already calls self-read immediately after login to discover auth state, so the eager admin
challenge costs zero extra round trips. The prescribed three-boolean-endpoint shape
(`/mfa/requirePinAndTotpSetup`, `/mfa/setupAllowed`, `/mfa/queryTotpKeyExists`) serves a *different*
enforcement model — opt-in per-user MFA — which ticket 19 put out of scope.

Constraint from Q23a of the Questions file: it warns that exposing lockout timing or failed-attempt counts
on the self-read helps attackers tune brute force. **Factor state ships; lock state does not.**

### 13. Four producers, one writer, and `sendError` prohibited

Spring Security handles its own exceptions and does **not** route them through `@RestControllerAdvice` — the
filter chain runs before the dispatcher servlet, so an `@ExceptionHandler` never sees
`InsufficientAuthenticationException` or `AccessDeniedException`. The envelope therefore has four
independent producers:

1. `@RestControllerAdvice extends ResponseEntityExceptionHandler` — controller and framework exceptions
2. `AuthenticationEntryPoint` + `AuthenticationFailureHandler` — the 401s, including the one that matters most
3. `AccessDeniedHandler` — CSRF and authorization 403s
4. the `/error` dispatch — anything escaping all three

**One `ProblemDetailWriter` component is injected into all four and is the only code path permitted to write
an error body. `response.sendError(...)` is prohibited outright** — it forwards to the container error
dispatch and hands formatting to `BasicErrorController`, which is exactly how the recipe set produces its
third envelope shape and how a 401 ends up differing from every other 401. **ADR owed** (deviation from the
prescribed login failure handler). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-008, T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001, T-AUTH-011. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-031. Amend by ID, not this list.*

Two traps to carry into implementation: Jackson treats `application/problem+json` as the producible media
type for `ProblemDetail`, so the SPA's `fetch` must not send a restrictive `Accept`; and
`spring.mvc.problemdetails.enabled=true` only covers producer 1, which is precisely why the other three get
forgotten.

### 14. CSRF 403 vs authorization 403 — distinguish by code, never by status

Client behaviour diverges sharply: a CSRF failure on a valid session means *re-bootstrap the token and
retry*; an authorization failure means *not permitted, do not retry*. [Ticket 04](04-prescribed-recipe-inventory.md)
found the recipes tell the SPA to treat 401/403 alike by clearing state and redirecting to login — which,
applied to a CSRF failure, logs out a legitimately authenticated user. It also found the token **must** be
re-fetched after login and after logout (`CsrfAuthenticationStrategy` / `CsrfLogoutHandler` clear it), so
this fires on the *normal* path, not an edge case.

Decided: `CSRF_TOKEN_INVALID` triggers **one** silent re-bootstrap-and-retry, capped at a single attempt so a
genuinely broken token cannot loop. `ACCESS_DENIED` never retries.

### 15. The documented schema lives in the repo

`docs/api/error-contract.md` in the built repo, plus the closed `code` enum as the executable half. The doc
is what [ticket 18](18-compliance-review-gate.md) points at to satisfy §5 line 508; the enum makes drift a
compile error rather than a review finding. Cheaper than an OpenAPI fragment at this size.
[Ticket 17](17-deferral-register-and-adrs.md) links it rather than restating it.

### ADRs owed by this ticket (5)

1. RFC 9457 envelope, deviating from the login recipe's `sendError` / `BasicErrorController` posture.
2. `sendError` prohibited; a single `ProblemDetailWriter` across all four producers.
3. Self-registration returns a uniform 202 with an activation token, deviating from PRD Story 1's "clear
   validation error"; `USER_EXISTS` narrowed to admin-initiated creation.
4. MFA `detail` prose-matching dropped; clients branch on `code`.
5. `account locked` reclassified as a log reason, never a wire code — the resolution of §3.2's
   self-cancelling bullet.

### Done-when check

An implementer can now write the error handler and the auth failure path without guessing: the envelope,
the closed code vocabulary with statuses, the four producers and the one writer, the field-by-field
definition of "identical", the specific-vs-generic split with its ordering rule, the timing mitigation, and
the `user exist` tension are all decided in writing. Met.

---

## Amendment from ticket 11

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
declined the batch password-reset endpoint, so **`BATCH_TOO_LARGE` is removed from the closed enum, which is now
13 codes, not 14.** Removed rather than left in place: an unreachable member of an enum this ticket deliberately
closed would read to a reviewer as a code the client must handle, and to an implementer as an endpoint that was
forgotten.

Nothing else in the contract moves. `USER_EXISTS` (400) stays admin-only and now explicitly covers tombstone
hits on admin-initiated creation, while self-registration's uniform 202 covers live duplicates and tombstone
hits identically — the split this ticket created is unchanged, only its coverage is stated. *Consolidated into the ADR routing (ticket 34): ADR-032 (attached amendment). Amend by ID, not this list.*

One addition rather than a correction: ticket 11 put a **lazy 30-day forced-change expiry** check on the login
path, and that refusal must return the same generic `401 AUTHENTICATION_FAILED` as every other login failure.
A distinct code there would be an oracle for "this account exists and was admin-provisioned", which is the
class of leak this ticket's uniform 401 exists to close.

## Amendment from ticket 10 (credential flows)

- **`VALIDATION_FAILED` gains rule `USERNAME_UNAVAILABLE`.** A self-registration username collision is reported
  specifically. This is **not** an exception to this ticket's split — it is an application of it: username
  availability is a property of the *submitted value*, exactly like password strength, whereas email existence is
  *account state*. The axes separate cleanly and testably: username conflicts are specific, email existence is
  never observable. The username is checked **before** the email axis, so no username is ever reserved by a
  request that collided on email. *Consolidated into the ADR routing (ticket 34): ADR-032 (attached amendment). Amend by ID, not this list.*
- **The closed enum stays at 13 rows / 14 identifiers.** Nothing is added. `USER_EXISTS` stays scoped to
  admin-initiated creation, and activation-redemption failure **reuses `RESET_TOKEN_INVALID`** rather than
  earning a member — activation and reset redemption are structurally identical operations on one table, and a
  distinct code would tell an unauthenticated caller which *kind* of token they hold.
- **The uniform-202 registration path was leaking through timing, not fields.** With a password collected at
  registration, the new-address path ran a BCrypt-12 encode (300–400ms per ticket 07) and the collision path
  created nothing — a 50–100× oracle on exactly the response §4's uniformity rule protects, and one this
  ticket's mitigation could not reach, because the dummy hash is `DaoAuthenticationProvider` behaviour and
  exists only on the login path. ASVS 6.3.8 (L3) names response time explicitly and extends the requirement to
  registration. Resolved by **moving the credential to activation**, deleting the asymmetry rather than masking
  it with a decoy hash. *Consolidated into the ADR routing (ticket 34): ADR-032 (attached amendment). Amend by ID, not this list.*
- This ticket's illustrative `rule` list (`BREACHED`, `CHARACTER_SET`, …) is superseded by ticket 07's closed six.

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**One new enum member, and a defect in your 401 contract that is wider than MFA.**

**1. The enum goes to 14: `FACTOR_ALREADY_ENROLLED` at `409`.** Ticket 23's provisioning endpoint must reject
re-provisioning when a confirmed TOTP row exists (STD §3.4 forbids overwriting without admin removal; Recipe 10
silently overwrites). That is a conflict with current resource state — RFC 9110 §15.5.10 — not a malformed payload.
The alternatives were both worse: a 400 `VALIDATION_FAILED` stacks a second semantic misuse on top of the two 412/422
ones already recorded, and 409 carrying `VALIDATION_FAILED` would break your fixed status/code pairing, which is a
worse precedent than one more member. Ticket 08's reuse precedent was considered and declined for that reason.

**2. Your 401 contract is broken for `FACTOR_PASSWORD`, independently of anything MFA-specific.** Verified from
7.1.x source. `FormLoginConfigurer.init` and `OneTimeTokenLoginConfigurer.init` auto-register their entry points with
`ExceptionHandlingConfigurer.defaultDeniedHandlerForMissingAuthority(...)` using
`getAuthenticationEntryPointMatcher(http)` — a **browser-request** matcher that requires an HTML-ish `Accept` and
excludes `X-Requested-With: XMLHttpRequest`. Our SPA uses `fetch` with a JSON `Accept`, so those denials never match
and fall through to `AccessDeniedHandlerImpl` → **403, not your 401**.

And `ExceptionTranslationFilter.handleAccessDeniedException` routes anonymous and remember-me denials to
`sendStartAuthentication` → the chain's **main** `authenticationEntryPoint`; the missing-authority map lives in the
`accessDeniedHandler`, which is the `else` branch. So this is **one fix, not two**: the main entry point must be your
`ProblemDetailWriter`-backed entry point with a matcher that actually matches `fetch`, and once it is, both the
anonymous-admin-path 401 and the `FACTOR_PASSWORD` 401 come out right.

**3. Two things ticket 23 did *not* need from you.** Your §11 carve-out (MFA 412/422 responses may be specific
because they go to an already-authenticated session about its own account) is what permits the missing-factor entry
point to perform one enrolment-state read and return 422 rather than a uniform 412 — without it,
`FACTOR_ENROLMENT_REQUIRED` would be dead code. And your §12 decision to put factor state on `GET /api/profile`
rather than a new endpoint held: ticket 23 adds no factor-state endpoint.

**4. No binary/PNG carve-out is needed after all.** Ticket 23's body flagged the PNG provisioning response as the one
endpoint that would not fit your envelope. It chose a JSON response carrying the QR as base64, so success and failure
are both JSON on every MFA route, `ProblemDetailWriter` needs no exception, and your §10 Jackson `Accept` trap does
not acquire a second instance.

**5. Recorded against you rather than fixed:** `412` for a missing factor is off-label (RFC 9110 §15.5.13 scopes it to
conditional-request precondition evaluation) and `422 FACTOR_ENROLMENT_REQUIRED` is an authorization outcome that
would more properly be 403. Both are kept for envelope consistency and recorded as deviations naming RFC 9470 as the
standardised shape we are re-deriving — 9470 being OAuth-scoped (§3, §9) and therefore not authority for
cookie-session auth. RFC 9457 imposes no constraint on status choice, so nothing in the envelope forced either. *Consolidated into the register (ticket 33): R-MFA-001, R-MFA-002. Amend the table by ID, not this list.*

---

## Amendment from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) draws on §3's rule and adds nothing to
the closed 13-code enum — no new wire code, no new envelope producer.

**§3's rule turned out to be the decisive argument in another ticket.** "The internal reason goes to the
audit log at WARN, keyed by `session.hash` and `trace.id` where the UUID is not yet resolved" is what
settled whether a failed-login row may carry `user.id`. Because the five internal reasons — wrong
password, unknown user, locked, disabled, grace-period-auto-disabled — are **already in the log**, the
recipes' enumeration protection (omit `user.id` on failures) had already been spent, and withholding the
UUID was cost without benefit. So the catalogue emits `user.id` when the account resolves and omits it
when it does not, on the authority of the Standalone standard §3.4 Privacy clause. The residual is
recorded honestly: the presence or absence of the field is itself a log-reader oracle, unavoidable under
any include-when-resolved scheme, and already implied by the reason field this ticket specified. *Consolidated into the register (ticket 33): R-AUD-007. Amend the table by ID, not this list.*

**The reason vocabulary is formalised as a sealed hierarchy, per family, serialised by an explicit
`code()` rather than `name()`** — these values become saved-query targets, and an enum rename would
otherwise silently break every dashboard built on them. §3's five internal reasons become the
`AUTHENTICATION_FAILED` family; ticket 23's `MISSING | EXPIRED` becomes the factor family; ticket 07's
six `PASSWORD_REJECTED` rules become the password-quality family unchanged.

**`event.reason` is reserved for explaining `event.outcome`**, which is what `Log_Schema.md` says it
does. The enable-versus-disable direction is therefore carried by two static `message` strings, not by
`event.reason` — because a *failed* enable attempt would otherwise have two claimants for one field, the
direction and this ticket's internal failure reason, and the catalogue's failed-administrative-attempt
row makes that collision real rather than hypothetical.

**Two rows respect this ticket's splits without adding codes.** Self-registration's uniform 202 is one
audit row with reason `NEW_ACCOUNT | EXISTING_ADDRESS` — specific in the log, uniform on the wire.
`USERNAME_UNAVAILABLE` stays specific on both, matching the split this ticket drew between
submitted-value quality and account state.

**`sendError` and the envelope producers are untouched.** The catalogue asserts against the same
`ProblemDetailWriter` surface; audit emission is a separate concern from response writing, per the typed
module's "audit logger owns no HTTP concerns".

**One addition that protects this ticket's ordering rule.** Ticket 07's constraint — on the reset path
the token check must pass before password-quality validation, or a specific strength error confirms the
token was valid — becomes a *logging* constraint too: the password-rejected audit row can never precede
a successful token check, because a row carrying a strength reason would confirm a valid token to a log
reader.

---

## Amendment from ticket 16 (test plan)

- **`USERNAME_CHANGE_NOT_ALLOWED` is removed from the closed enum.** It follows the `BATCH_TOO_LARGE` precedent. Ticket 11's endpoint set has no generic user update, so no request can produce this code. Standard §5:473, including its "lock via generic update" half, is N/A by construction. *Consolidated into the register (ticket 33): R-ADM-013. Amend the table by ID, not this list.*
- **The enum table at §2 was edited in place this time.** Both removed rows are struck through there. `BATCH_TOO_LARGE` had stayed live in the table since 06:355–357 removed it in prose only.
- **Standard §5:494's body cap reuses `VALIDATION_FAILED` (400).** No new code is added. The key and value are in ticket 09's amendment from ticket 16.
- **Ticket 16 owns** a contract test: a JSON Schema of the closed enum, which validates both the backend's envelope tests and the frontend's MSW fixtures. Its ID is minted by ticket 32. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-012. Amend the table by ID, not this list.*

---

## Amendment from ticket 32 (test-plan table transcription)

Two decided codes are missing from the §2 enum table, and both are lost hand-offs:
- `FACTOR_ALREADY_ENROLLED` at 409 was added by this ticket's own amendment from 23 (06:396), but the table was never updated.
- `FACTOR_DISABLED` at 423 was decided by ticket 14 (14:314). Ticket 14 lists the amendment to 06 as owed (14:637), but it never arrived.

Add both rows to §2. The enum-schema contract tests generate their schema from the enum: T-AUTH-012 validates the MSW fixtures and T-AUTH-011 the backend bodies. Until the table has both rows, those tests reject the fixtures and responses that T-MFA-019, T-MFA-021, T-FE-001 and T-FE-016 assert.
