# 19 — Resolve the MFA scope conflict raised by IM8 ac-2

Type: grilling
Status: resolved
Blocked by: —

## Question

IM8 mandates MFA for privileged access. The PRD declares MFA out of scope. Which gives, and what *Consolidated into the register (ticket 33): R-MFA-008. Amend the table by ID, not this list.*
exactly do we build?

This ticket exists because the map was wrong. "MFA" sat in Out of scope on the grounds that
`Appfw-Mfa-Standards/index.md` reads as a feature router rather than a blanket mandate. That reading
holds for the App Standard, but IM8 is an independent and unconditional source, and the map's own
entry made the exclusion conditional: *if IM8 forces MFA for privileged access, this returns.* It does.

## What the research found

From "Extract the IM8 and ARC controls that bind this app":

- `im8-reform-app-policy.md` → `## Access Control (ac)` → **ac-2** requires MFA for privileged
  account logins and privileged actions, including step-up authentication.
- `im8-review/SKILL.md` → `### ac-2` defines privileged accounts to include `ROLE_ADMIN` or any role
  that can manage users. Our ADMIN role exists for exactly that (PRD Stories 8–11).
- **ac-2 has no N/A branch.** Controls that can be ruled out — ac-6, ac-7, ac-8, as-12, ck-1/2/4,
  ga-8, lm-18, st-3, dp-8 — all carry an explicit not-applicable clause. ac-2's applicability offers
  only "privileged accounts only" versus "all accounts". The sole escape is having no privileged
  accounts, and the admin module is the PRD's own requirement.
- The PRD as written fails every ac-2 backend check: no MFA fields, no second-factor endpoint, no
  two-step login, no step-up gate on admin mutations.

A second finding compounds this, from "Verify the App Standard's controls are still current
practice": NIST SP 800-63B-4 §3.1.1.2 sets a **15-character minimum** for a password used as
single-factor authentication, permitting 8 only within a multi-factor process. Without MFA we are
single-factor, so the PRD's 12-character minimum is below the NIST floor. **Adding MFA and keeping a
shorter password are the same decision viewed from two directions**, which is why they should be
decided together rather than in separate tickets.

## Ruled out before the grill: email OTP via the stubbed `EmailService`

Asked and answered, so it does not get re-litigated mid-session. Reusing the PRD's stubbed
`EmailService` as an MFA delivery channel **is not viable**, for four independent reasons:

1. **NIST prohibits it.** [SP 800-63B-4 §3.1.3.1](https://pages.nist.gov/800-63-4/sp800-63b/authenticators/)
   states email shall not be used for out-of-band authentication — reachable with only a password,
   interceptable in transit and at intermediate mail servers, and vulnerable to DNS-spoofing reroutes.
   **Narrow carve-out that matters to us:** confirmation codes sent to *validate an email address*, or
   issued as recovery codes, are not authentication processes and are explicitly unaffected. So the
   registration email verification in ticket 10 is fine; the same mechanism as a login factor is not.
2. **The stub makes it self-defeating.** `EmailService` logs instead of sending, so the second factor
   lands in the application log. Anyone with log access authenticates as an admin.
3. **It would breach the logging contract.** App Standard §3.4 "What NOT to Log" names OTP values and
   authentication challenge responses explicitly.
4. **Email is already the account-recovery path**, so it cannot also be the second factor — one
   mailbox compromise yields both factors. Two factors sharing a single point of failure are one factor.

**Tension worth knowing:** `im8-review` → `### ac-2` lists "OTP via SMS or email" among acceptable
"something you have" factors, so email OTP would pass the automated check — but the same section's
manual review flag sets the real bar at "at least one 'something you have' factor — TOTP or hardware
token". The automated check is more permissive than the guidance behind it. Also note the
`Appfw-Mfa-Standards` router has **no standalone OTP path**: OTP appears only in the MCC variants,
delivered via the MCNS platform service, so it is not a sanctioned option for a standalone app.

**Also ruled out: the PIN factor.** `MFA_Core` offers a 6-digit bcrypt-hashed PIN and it looks cheap,
but a PIN is "something you know" — the same category as the password — so it is not a second factor
and fails ac-2's bar.

## What TOTP already gives us, prescribed rather than designed

`MFA_Core/Base_Standalone_Application_Standard.md` specifies TOTP as enforced constraints, so option 1
below is recipe-following, not fresh design: 20-byte random secret; encrypted at rest with a
configured key; `otpauth://totp/...` QR provisioning; a `PENDING_TOTP` record promoted to
`TOTP_USER_DETAILS` only after a confirmation code succeeds, inside `@Transactional`; verification
across three time windows (`counter-1`, `counter`, `counter+1`) with constant-time comparison; and
**`lastUsedCounter` replay prevention** rejecting any matched counter ≤ the stored value, so an
intercepted code cannot be replayed inside the 90-second skew window. Factor value arrives in an
`X-TOTP` header. RFC 6238 conformance (HMAC-SHA1) is mandatory — deviation breaks standard
authenticator apps.

Nothing is transmitted to the user, which is precisely why TOTP avoids every weakness that rules out
email.

## Recommendation going into the grill

**TOTP step-up on admin mutations only, plus a 15-character password floor for everyone.** Rationale:
closes ac-2 at its narrowest scope; satisfies the NIST single-factor floor; leaves the PRD's login flow
and Stories 1–5 untouched; follows a prescriptive recipe.

On the password floor specifically — take 15 even with MFA, because step-up does not make *login*
multi-factor (the second factor is demanded later, at the action, so the login boundary is still
single-factor), and regular USER accounts have no MFA at all under this option so 15 binds for them
regardless. Two minimums by role is complexity that buys nothing. NIST also requires permitting ≥64
characters, paste, and password managers, so 15 is not a usability problem. Note the resulting band is
15 characters to 72 *bytes*, which narrows for non-ASCII input.

## Options

1. **TOTP step-up on `/api/admin/**` mutations only** — not at login. `im8-review` accepts
   step-up-at-action for ac-2. Scope: one encrypted `totpSecret` column (ac-2 requires encryption at
   rest), an enrol endpoint, a verify endpoint, one guard. Smallest change that closes ac-2.
2. **TOTP at login for ADMIN accounts only** — two-step login with a partial-authentication state
   that cannot reach protected resources.
3. **TOTP at login for all accounts** — also drops the password floor from 15 to 8 and satisfies the
   strongest reading, at the highest cost.
4. **Accept the FAIL and register it as a deferral.** Defensible for a reference app, but it will
   surface as a top-band ac-2 FAIL in every future assessment, and `spec-compliance` Step 4 requires
   explicit user sign-off on FAILs before decomposition.

## What to decide

- Which option, and the consequence for the password minimum (15 if single-factor, 8 permitted if MFA).
- If MFA is built: which `Appfw-Mfa-Standards` path applies. The index routes "MFA TOTP Standalone on
  Login" through `MFA_Core` then `MFA_Frontend/Standalone`, and "MFA PIN/TOTP Standalone on Critical
  Transaction" through `MFA_Core` then `MFA_Critical_Transaction` then `MFA_Frontend/Standalone` —
  option 1 above maps to the critical-transaction path, not the login path. A follow-up research
  ticket to extract those recipes will be needed, which is real added scope.
- Where the TOTP secret's encryption key lives, given secrets management for non-local environments
  is still unspecified on the map.
- Whether this pushes MFA out of Out of scope entirely, or leaves a narrowed version there.

## Done when

An option is chosen, the password-minimum consequence is settled with it, and either MFA is moved out
of the map's Out of scope section with its follow-up tickets created, or the deferral is written with
the user's explicit sign-off recorded.

## Answer

**MFA is in scope. TOTP, enforced on the admin surface through Spring Security 7's native factor
authorities, plus a 15-character password floor for every account.** Option 1 as written in this
ticket was rejected; the chosen shape is a fifth option the ticket did not contain, because three of
the ticket's premises turned out to be wrong.

### The three corrections that drove the decision

1. **Step-up-at-action does not satisfy ac-2 — it converts a FAIL into a WARN.** This ticket asserted
   that `im8-review` accepts step-up-at-action. It does not. `im8-review/SKILL.md` → `### ac-2`
   applicability reads "privileged account logins **and** privileged actions", and the section runs two
   distinct check families: a two-step login with partial-auth state for privileged *accounts*, and a
   step-up gate for privileged *actions*. The skill's own worked example proves the consequence:
   `im8-review/output/im8-compliance-report.md` assesses an app using `@MultiFactorAuthentication` on
   two operations with no login-level MFA and records **WARN / Medium** — "step-up at operation level
   but NOT enforced at login for privileged accounts" — recommending TOTP at login for the admin roles.
   Option 1 alone would have bought a WARN, not a PASS.
2. **Spring Security 7 has first-class MFA, which nothing on this map knew.**
   ([Spring blog, 2025-10-21](https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7))
   Every successful authentication issues a timestamped `FactorGrantedAuthority`; authorization rules
   demand factors, via `@EnableMultiFactorAuthentication(authorities = {...})` globally,
   `AuthorizationManagerFactories.multiFactor().requireFactors(...)` per `requestMatcher`, or a custom
   `AuthorizationManager` wired through `setAdditionalAuthorization` for per-user rules. Factors carry
   `validDuration(Duration)`, so "re-verify if older than N minutes" is configuration rather than code.
   A custom provider joins in with `FactorGrantedAuthority.withFactor("TOTP")`. This collapsed the cost
   of every login-side option and made the chosen hybrid cheap.
3. **The prescribed enforcement layer is the wrong shape for this app.**
   `MFA_Critical_Transaction` adds **no endpoints** — the code rides as an `X-TOTP` header on the
   business request — and verifies **per method invocation with no cache**, while `MFA_Core` mandates
   rejecting any counter ≤ `lastUsedCounter`. An admin disabling three users therefore needs three
   codes from three separate 30-second windows. Meanwhile the corpus contains **no login-MFA mechanism
   at all**: no partial-auth state, no verification endpoint, no login challenge UI in
   `MFA_Frontend/Standalone` (§1.2 puts session management out of scope). Options 2 and 3 read *fewer*
   prescribed documents but would have left the enforcement layer entirely undesigned.

### The decision

**Keep `MFA_Core` as prescribed. Replace only the enforcement layer.**

- **Retained from `MFA_Core`, unchanged:** 20-byte `SecureRandom` secret; `otpauth://totp/...` QR
  provisioning; `PENDING_TOTP` → `TOTP_USER_DETAILS` promotion inside `@Transactional`, with
  `lastUsedCounter` initialised to the matched counter so the confirmation code cannot be replayed as a
  login; RFC 6238 HMAC-SHA1, 6 digits, ±1 window only; constant-time comparison; `lastUsedCounter`
  replay rejection.
- **Replaced:** `MFA_Critical_Transaction`'s AOP aspect, `@MultiFactorAuthentication` annotation,
  `MFATypeContainer`, and the `X-TOTP`-on-business-request pattern. In their place, two
  `AuthorizationManagerFactory` rules and a TOTP verification endpoint that grants
  `FactorGrantedAuthority.withFactor("TOTP")` into the existing session.
- **Applicable standards path:** `MFA_Core` → `MFA_Frontend/Standalone`. `MFA_Critical_Transaction` is
  consciously not followed. Note the router has no standalone OTP path either way — OTP lives only in
  the MCC variants, delivered by MCNS.

**The enforcement rules — two rules, one mechanism:**

| Surface | Rule | Closes |
| --- | --- | --- |
| Admin **reads** (`GET /api/admin/**`) | require `FACTOR_PASSWORD` + `FACTOR_TOTP`, no duration bound | ac-2 "privileged account logins" |
| Admin **mutations** | require `FACTOR_TOTP` with `validDuration(10 minutes)` | ac-2 "privileged actions", re-verification |

An unenrolled or unverified admin cannot see the admin UI at all, which is as close to login-level
enforcement as a cookie-session SPA gets. A burst of mutations runs on one verification; a stale
session re-verifies. Code exhaustion never arises.

**Password floor: 15 characters, every account, one rule.** NIST SP 800-63B-4 §3.1.1.2 sets 15 for a
password used as single-factor and permits 8 only within a multi-factor process. Regular USER accounts
have no second factor under this design, so the single-factor floor binds for them regardless; two
minimums by role would be complexity buying nothing. The PRD's 12 is overridden. NIST also requires
accepting ≥64 characters, paste, and password managers, so 15 costs no usability. Resulting band is
15 characters to BCrypt's 72 **bytes**, which narrows under non-ASCII input — ticket 07 owns that edge.

**Privileged-action scope.** ac-2's definition names "changing account credentials or MFA settings",
which strictly read reaches a regular user's own password change. Our stated interpretation: privileged
actions are the admin surface plus MFA-settings mutations, and **current-password re-entry is the
re-verification for a user changing their own password**. Recorded as an explicit interpretation in the
deferral register, not left implicit, so the assessor sees a line was drawn deliberately. *Consolidated into the register (ticket 33): R-MFA-009. Amend the table by ID, not this list.*

**TOTP secret at rest.** Key injected from the environment (`@Value`, absent from every profile, which
is also what IM8 **as-8** wants), AES-GCM via `AesGcmBytesEncryptor.withSecretKey(...)`, plus a
key-version column on the secret row so yearly rotation runs incrementally rather than stop-the-world.
The standard's instruction to store the key **in the database** is overridden: it puts the key in the
same store as the ciphertext it protects, so a database compromise yields both and the control buys
almost nothing against its primary threat. This is the standard being defective, not a preference —
precedent exists on this map, which already found the lockout control shipped with no observation
window. The yearly rotation obligation is kept.

**Do not use `Encryptors.stronger()` or `AesBytesEncryptor`.** CVE-2026-47842: `AesBytesEncryptor`
built with the two-argument constructor, or a null IV generator in CBC mode, encrypts under an all-zero
IV. Fixed in 6.5.10 / 7.0.5, and the same patch deprecates `AesBytesEncryptor` together with
`Encryptors.standard()`, `stronger()`, `text()` and `delux()`, introducing `AesCbcBytesEncryptor` and
`AesGcmBytesEncryptor`; `Encryptors.queryableText` was removed with no replacement because
deterministic encryption cannot resist the correlation the CVE describes
([migration guide](https://docs.herodevs.com/spring/additional-info/cve-2026-47842)). Our pinned
Security 7.1.1 carries both the new classes and the deprecations. Use the `withSecretKey` builder, not
`withPassword`, so there is no key derivation or salt to manage. `totpKey` is already a byte column, so
`BytesEncryptor` fits natively and no `TextEncryptor` wrapper is needed.

**Lost authenticator.** The corpus has no answer: no TOTP removal endpoint or recipe anywhere, no
recovery codes, and the sole admin-unlock recipe touches only `PIN_USER_DETAILS` — while the standard
forbids self-service re-provisioning once a key exists, and locks an account until administrative
review after 10 failures in an hour. As prescribed, a single-admin deployment that loses its phone is
permanently locked out of its own admin module. Our answer: **another admin resets it** through an
ADMIN-only, audited endpoint that is itself behind the factor; an **invariant of at least two enabled,
enrolled admins**; and a **break-glass runbook line** for the genuine zero-admin case. Recovery codes
are deferred with justification rather than built — they would duplicate the reset path for a
single-tenant reference app.

**Response contract.** The framework answers a missing factor with a redirect to the endpoint that
issues it, which is built for server-rendered pages. For our REST API that becomes **one coherent
RFC 9457 `ProblemDetail` mapping with a stable machine-readable type per outcome** — factor required,
enrolment required, invalid code, throttled (429 retaining `Retry-After`, the one part of the corpus's
contract that is unambiguously right). The corpus is not followed here because it contradicts itself
three ways on the single case of "user is not enrolled": 403 `SETUP_REQUIRED` in the
critical-transaction flowchart, 422 in the base standard and frontend contract, 412 from the
missing-code path — and it has the frontend branch on a response body containing the exact English
string `"User Details not found."`. Every one of these responses goes to an already-authenticated
session about its own account, so they may be specific without opening an enumeration channel; this is
the deliberate exception to ticket 06's uniformity instinct.

**Challenge timing: eager.** On login success the SPA asks what factors the session is missing and
routes an admin straight to the challenge, or to enrolment. The server-side gate on `/api/admin/**` is
what actually enforces ac-2 and does not move; the eager check is the client satisfying it up front, so
`im8-review`'s frontend checks see a genuine two-step login with separate forms, and the lazy path
survives as the backstop for anyone deep-linking into the admin area. Requires an endpoint reporting
the session's factor state — new surface for ticket 06, shape for ticket 14.

**Enrolment is admin-only.** `/settings/mfa` is role-guarded and the prescribed advisory `MFAPrompt`
is dropped. An opt-in factor that no authorization rule ever demands secures nothing while still
carrying enrolment endpoints, a reset path, and tests for every user in the system. The password floor
is 15 for everyone regardless, so opt-in buys no usability either.

### Demoability on split origins — checked, because it nearly went unstated

Two ports stay demoable and TOTP does not change that. `localhost:5173` and `localhost:8080` are
**cross-origin but same-site** — a browser's "site" is scheme plus registrable domain and excludes the
port — so `SameSite=Strict` cookies are still sent and ticket 02's tightening from `Lax` costs us
nothing locally. What is required is CORS with credentials: `Access-Control-Allow-Origin` naming the
SPA's exact origin (never `*` with credentials), `Allow-Credentials: true`, `credentials: 'include'`
client-side, and `Access-Control-Expose-Headers: Retry-After` for the 429 path. Two genuinely different
hosts is a different matter and belongs to ticket 20. Two practical notes: TOTP depends on the
**server's** clock, not the browser's, with ±1 window giving roughly 90 seconds of tolerance; and no
phone is required to demo, since 1Password, Bitwarden, or `oathtool -b --totp <secret>` all serve as
the authenticator.

### ADRs this decision owes (ticket 17)

1. **Enforcement layer replaced.** `MFA_Critical_Transaction`'s AOP aspect and `X-TOTP`-per-request
   pattern rejected in favour of Spring Security 7 factor authorities. Reasons: per-invocation
   verification with `lastUsedCounter` replay rejection exhausts codes during ordinary admin work; the
   corpus shape closes only one half of ac-2; it has no verification endpoint, which is exactly what
   `im8-review` looks for. *Consolidated into the ADR routing (ticket 34): ADR-021. Amend by ID, not this list.*
2. **TOTP secret encryption key not stored in the database**, contrary to `MFA_Core`; named primitive
   (AES-GCM) where the standard left it blank; key-version column added for incremental rotation. *Consolidated into the ADR routing (ticket 34): ADR-022. Amend by ID, not this list.*
3. **Password minimum 15, overriding the PRD's 12**, on NIST SP 800-63B-4 §3.1.1.2. *Consolidated into the ADR routing (ticket 34): ADR-002. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-023. Amend the table by ID, not this list.*
4. **`MFAPrompt` advisory component and all-user `/settings/mfa` access dropped**, deviating from
   `MFA_Frontend/Standalone`. *Consolidated into the ADR routing (ticket 34): ADR-023. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-MFA-008. Amend the table by ID, not this list.*
5. **Response contract deviates from the corpus's three contradictory enrolment-incomplete statuses**
   and drops the English-string dependency. *Consolidated into the ADR routing (ticket 34): REJ-059. Amend by ID, not this list.*
6. **Recovery codes deferred**, with the two-enrolled-admins invariant and break-glass runbook as the
   compensating control. *Consolidated into the ADR routing (ticket 34): ADR-024. Amend by ID, not this list.*

### Constraints handed to other tickets

- **06 (error envelope):** the four MFA problem types; the factor-state endpoint; the stated exception
  to enumeration uniformity for already-authenticated, self-scoped responses.
- **07 (password policy):** floor is settled at 15, not open for re-litigation; 15-to-72-**bytes** band.
- **08 (session and CSRF):** whether granting the TOTP factor into an existing session needs
  `changeSessionId()`; how factor authorities survive Spring Session JDBC serialisation.
- **09 (lockout and rate limiting):** `MFA_Core` mandates its own per-principal, per-factor persisted
  lockout — 10 failures in a 1-hour window, session kick-out, locked until administrative review — which
  must be reconciled with our lockout and dual rate-limiting design rather than bolted alongside it.
- **11 (admin module):** the two-enabled-enrolled-admins invariant; the factor gate over the whole admin
  surface; the ADMIN-only audited TOTP-reset endpoint; bootstrap admin must enrol before the admin
  surface opens.
- **12 (data model):** `TOTP_USER_DETAILS` and `PENDING_TOTP` (no `PIN_USER_DETAILS`, we are TOTP-only);
  the key-version column, which the standard's schema lacks; and a shape mismatch worth deciding —
  `im8-review` looks for an MFA flag **on the User entity** while the standard normalises that state
  into a separate table, so a correct implementation may read as absent to the automated check.
- **13 (audit events):** enrolment initiated, enrolment confirmed, factor verification success and
  failure, factor lockout, admin factor reset. No target field exists in the schema for the second party
  in the reset event, so `user.target.id` applies here too.
- **14 (frontend):** enrolment screen with QR from an object URL, the code-entry dialog, the eager
  post-login challenge, and the problem-type branches. `MFAPrompt` is not built.
- **16 (test plan):** the reset path and the two-admin invariant are scope the corpus never covers, so
  they carry no prescribed tests and need ours.
- **20 (origin topology):** CORS with credentials, `Retry-After` exposed, and the same-site-but-
  cross-origin reasoning above. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-009, T-ADM-027, T-ADM-028. Amend the table by ID, not this list.*

### Follow-up tickets created

- 22 — Extract the `MFA_Core` and `MFA_Frontend/Standalone` recipes at implementation fidelity
  (research, unblocked, runnable in parallel).
- 23 — Decide the TOTP enrolment, step-up, and factor-reset flows (blocked by 06, 08, 22).
- 24 — Decide secrets and configuration handling for non-local environments (blocked by 11, 20),
  graduated out of the map's fog because the TOTP key gave that patch a second hard requirement.

---

## Amendment from ticket 11

**The prohibition on `Encryptors.stronger()` recorded here is over-broad and would become a build instruction
nobody can justify.**

[CVE-2026-47842](https://access.redhat.com/security/cve/cve-2026-47842) is scoped to `AesBytesEncryptor` used
with its **two-argument constructor or a null IV generator in CBC mode**, which encrypts under a predictable
all-zero IV. The [patch](https://docs.herodevs.com/spring/additional-info/cve-2026-47842) deprecates
`AesBytesEncryptor` and adds `AesCbcBytesEncryptor` and `AesGcmBytesEncryptor`, leaving the original in place
only because it is the sole class able to read data it already wrote. `Encryptors.stronger()` is GCM with a
random IV generator and is not implicated.

Corrected rule: **use `AesGcmBytesEncryptor`; never the two-argument `AesBytesEncryptor` and never a null IV
generator.** The AES-GCM decision and the environment-supplied key are unchanged. *Consolidated into the ADR routing (ticket 34): ADR-022 (attached amendment). Amend by ID, not this list.*

### Ticket 11's answers to the four constraints this ticket handed it

- **Factor gate:** keyed on the single prefix `/api/admin/**`, reads unbounded, mutations at
  `validDuration(10 minutes)`, exactly as specified here.
- **TOTP reset endpoint:** `DELETE /api/admin/users/{uuid}/totp`, ADMIN-only, behind the factor, audited under
  `event.action: totp-remove`, and it **must invalidate the target's sessions** — otherwise a session already
  holding `FACTOR_TOTP` outlives the secret's destruction and stays privileged on a factor that no longer exists.
- **Two-enrolled-admins invariant:** as stated here it was unenforceable. Two admins demoting two different
  admins concurrently each read "2 remain" and both commit, leaving zero. It is now a **removal-only guard under
  a pessimistic lock over the admin row set**, counted in application code because H2 rejects `FOR UPDATE` on
  aggregate queries. It is not a bootstrap precondition: one admin is seeded, and the sole-admin **lockout**
  path is closed by lockout auto-expiry rather than by a second account, leaving the break-glass runbook to
  cover lost-authenticator only.
- **Bootstrap admin enrolment:** enrolment sits outside `/api/admin/**` so an unenrolled admin can reach it, and
  it is deliberately **not** on the forced-change filter's exempt list — password change comes first, then
  enrolment, then the admin surface opens.

One consequence for anything downstream that counts admins: **creating or promoting an ADMIN does not satisfy
the invariant**, because it counts *enrolled* admins and a new admin is unenrolled until enrolment completes.

## Amendment from ticket 10 (credential flows)

- **This ticket is now load-bearing for a risk it was never scoped against.** The stubbed `EmailService` logs the
  reset link it was asked to send, so anyone who can read application logs can take over any activated account.
  The leak is **total for ordinary users and partial for administrators**, and the only thing containing it is
  the TOTP factor decided here: a log reader who resets an admin's password authenticates with `FACTOR_PASSWORD`
  only, and every `/api/admin/**` row requires the factor — including `DELETE /api/admin/users/{uuid}/totp`, so
  they cannot bootstrap their own. Two caveats for the handover document: containment is **confidentiality-only**
  (the attacker can still change the admin's password and lock the real admin out until that admin recovers
  through the same email channel), and reset redemption is pinned to clear the *password* lockout only, never any
  TOTP counter or enrolment state (ASVS 6.4.3, L2).
- **Reopening trigger for ticket 10, recorded here because this is the ticket that would change.**
  [CVE-2026-56081](https://www.sentinelone.com/vulnerability-database/) is account pre-hijacking with a different
  payload: the attacker pre-registers the victim's address and then **enrols a second factor on the pending
  account**, permanently locking the real owner out. Ticket 10 is immune only because enrolment is admin-only here
  and ordered *after* the forced password change. If self-service TOTP enrolment is ever added, an unactivated
  record can accumulate a factor and ticket 10's "a pending record holds nothing to overwrite" argument dies. *Consolidated into the register (ticket 33): R-CRED-011. Amend the table by ID, not this list.*
- The admin-only enrolment decision also means **ASVS L2 is not claimable for the application as a whole**:
  6.3.3 (L2) requires MFA, or a combination of single-factor mechanisms, to access *the application*. The map's
  target is recorded as L1 with named L2/L3 controls, and this is the cited reason. *Consolidated into the register (ticket 33): R-MFA-015. Amend the table by ID, not this list.*

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**Your enforcement design survives; three of its stated mechanics do not.**

**1. `AuthorizationManagerFactories.multiFactor()...requireFactors(...)` is the wrong mechanism, not just one of
three options.** Verified from 7.1.x source: `DefaultAuthorizationManagerFactory.withAdditionalAuthorization` returns
`AuthorizationManagers.allOf(new AuthorizationDecision(false), additionalAuthorization, manager)`, and `allOf`
returns the **first** non-granted result and stops. So the factor check runs **before** `hasRole` and the role check
never executes. Two consequences: an ordinary `USER` hitting `/api/admin/**` gets a `FactorAuthorizationDecision` and
is therefore told to **enrol MFA for a surface they must never reach** — an ASVS 6.3.8 (L3) state disclosure — and
because `AllRequiredFactorsAuthorizationManager.getFactorGrantedAuthorities` returns an empty list for an
unauthenticated principal, **anonymous requests get 412 instead of 401**. Ticket 23 hand-composes the rules role-first
with the **two-argument** `allOf`, since the one-argument form defaults to **grant** on all-abstain.
`@EnableMultiFactorAuthentication` is also unusable: it registers one **global** factory ANDed into every rule in web
*and* method security, cannot hold two different `validDuration`s, and would demand the factor of regular users you
ruled out of scope. Also note `requireFactors(String...)` carries **no** `validDuration` — only
`requireFactor(Consumer<RequiredFactor.Builder>)` reaches it. *Consolidated into the ADR routing (ticket 34): ADR-026 (attached amendment). Amend by ID, not this list.*

**2. The AES-GCM envelope is 69 bytes, not the 48 recorded here.** `AesGcmBytesEncryptor` uses a **16-byte IV**, not
12, per its own javadoc. Byte diagram now owned by ticket 12. Also: **AAD is unreachable** — `BytesEncryptor`'s entire
surface is `encrypt(byte[])`/`decrypt(byte[])`, there is no AAD parameter, and the `Cipher` instances are private
finals, so binding context as AAD would mean hand-rolling `Cipher` with `updateAAD` and discarding the CVE-safe
implementation you chose the class for. Replaced by a **fixed-width context prefix inside the plaintext**
(`userUuid ‖ keyVersion ‖ secret`, verified after decrypt, mismatch audited as a security event), which gives the same
row-swap and key-version-replay resistance at zero crypto cost. ASVS imposes no AAD requirement; 11.3.3 (L2) merely
prefers AEAD, which GCM already is. *Consolidated into the ADR routing (ticket 34): ADR-028 (attached amendment). Amend by ID, not this list.*

Good news on the same file: **NIST SP 800-38D is satisfied, not deviated from.** The 96-bit figure is a non-normative
interoperability recommendation in **§5.2.1.1**, a fully random 128-bit IV is **inside** §8.2.2's RBG-based
construction and is the shape §8.2.2 itself recommends (random field ≥96 bits, empty free field), and §8.2's own
example names 128-bit IVs with that construction. The one `shall` in play — §8.3's **2³² invocations per key** — is
satisfied by roughly nine orders of magnitude at one encryption per enrolment against a yearly-rotated key. This
**removes** a register entry rather than adding one.

**3. The admin-read rule is now bounded, but not for the reason a reader will assume.** You left it unbounded and
ticket 22 asked whether to bound it. It gets `validDuration` equal to the absolute session lifetime — **as a
fail-closed type guard, not a time bound**. From source, `requiredFactorError` grants whenever `validDuration == null`
and the authority *string* matches, and `getFactorGrantedAuthorities` returns **all** authorities rather than only
`FactorGrantedAuthority` ones despite its javadoc, so any plain `GrantedAuthority` bearing `FACTOR_TOTP` satisfies an
unbounded rule. A non-null duration is the only thing that routes a degraded authority into `createExpired(...)`. The
duration can never fire in normal operation, because the factor is always granted after `AUTH_INSTANT`. The ADR must
say so explicitly, or a reviewer grades it as an 8-hour factor bound. *Consolidated into the ADR routing (ticket 34): ADR-021 (attached amendment). Amend by ID, not this list.*

**4. Your scope decisions all held**, and one gained an endorsement worth quoting: NIST SP 800-63B-4 **§4.1.2.1**
requires binding an additional authenticator at "either the maximum AAL currently available in the subscriber account
or the maximum AAL at which the new authenticator will be used, **whichever is lower**" — so **password-only first
enrolment is explicitly compliant**, which converts the first-enroller race from a finding into a sanctioned
decision. Its other SHALL, notifying the subscriber via an independent mechanism when an authenticator is added, is a
**named failure** with no mail channel, compensated by an alert-worthy enrolment audit event. *Consolidated into the register (ticket 33): R-MFA-019. Amend the table by ID, not this list.*

**5. Your reset endpoint grows by one row and your unlock story by one axis.**
`DELETE /api/admin/users/{uuid}/totp` also deletes any `PENDING_TOTP` row — otherwise a pending secret planted from a
hijacked password-only session survives the reset and the target can confirm the attacker's secret. And ticket 11's
`POST .../unlock` now clears the TOTP tier-1 lock, which resolves ticket 22's "no unlock path anywhere" without
destroying an enrolment.

**6. `MFA_Core`'s lockout is deviated from further than you assumed.** "Locked until administrative review" becomes a
two-tier scheme — 10 consecutive / 20-minute auto-lift, plus a monotonic 100-failure cap cleared only by the reset
above, which *is* NIST's required rebinding — because ticket 09's auto-lift alone gives an attacker who already holds
the password **≈55% over a year** against a 10⁶ credential space. Details in ticket 23 §5. *Consolidated into the ADR routing (ticket 34): ADR-027 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 12 (data model reconciliation)

**The shape mismatch this ticket handed to ticket 12 is answered: enrolment state is derived from
`totp_user_details` row existence, and there is no MFA flag on the User entity.**

The handoff noted that `im8-review` looks for an MFA flag **on the User entity** while the standard normalises that
state into a separate table, so a correct implementation may read as absent to the automated check. Resolved in favour
of the normalised form, on ticket 09's precedent: a `totp_enrolled` boolean would be **two stored sources of truth for
one fact**, which ticket 09 refused for `account_non_locked` and then wrote an ADR about. `im8-review`'s grep becomes a
**documented false negative**, consistent with the two other counts on which this map already records that tool as
unable to read this design — it targets Boot 3.4 / Security 6.4 config spellings and cannot read a plan at all. *Consolidated into the register (ticket 33): R-MFA-005. Amend the table by ID, not this list.*

The cost is real and is recorded rather than glossed: the two-admin invariant's read set becomes two tables, so ticket
11's guard now takes `FOR UPDATE` on `users` **and** `totp_user_details` on all four mutating paths, and because
`ON DELETE CASCADE` removes the TOTP row, **account deletion changes the enrolled-admin count through a table its own
statement never names**. H2 has no gap locking, so the guard is decrement-safe, not phantom-safe — an insert can only
raise the count. *Consolidated into the register (ticket 33): R-ADM-015. Amend the table by ID, not this list.*

Two figures from this ticket, both already superseded and now fixed in DDL: the envelope is **69 bytes** in
`VARBINARY(69)` on both tables with a named `OCTET_LENGTH` check, not 48; and the **key-version column** this ticket
asked for is `SMALLINT` with a named `0..255` range check, because the version is copied into a **one-byte** field
inside the encrypted plaintext and a larger value would truncate there silently.

---

## Amendment from ticket 25 — a reopening trigger on the recovery-codes deferral

**The recovery-codes deferral is what forces every expensive branch in ticket 25's break-glass construction, and
that was not visible when it was taken here.** *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*

Ticket 25 established that restoring admin access after authenticator loss is **NIST §4.2 account recovery**, and
that at AAL2 §4.2.2.2 requires one of three two-element combinations. Every available route to satisfying it either
reverses part of this deferral, accepts a correlation residual, or builds a feature to avoid reversing it:

- **Option 2 — issued recovery code plus password.** Taken, because a password *is* "a single-factor authenticator
  that is bound to the subscriber account" and no exclusion exists. Residual: §3.1.3.1 lists "Access using only a
  password" among email's weaknesses, so both elements can collapse to one secret if the recovery mailbox is
  protected by that password. Mitigated by requiring a **recovery address distinct from the login address**, which
  §4.2.1.2 independently obliges ("CSPs SHALL allow the subscriber to establish at least two recovery addresses").
  Still one secret plus one mailbox.
- **Option 1 via recovery contacts — rejected.** §4.2.1.3 requires the CSP to "allow the subscriber to specify one
  or more addresses of trusted associates" and to "provide methods for subscribers to view and manage recovery
  contacts". **This ticket's second admin is system-designated, nominated by nobody, with no management surface**,
  so nominating it would satisfy the shape of option 1 without satisfying the class it claims.
- **Option 1 via saved plus issued — the branch to take if this deferral is ever reversed.** Strongest, closes the
  correlation because the saved code lives offline rather than in the mailbox the password protects, and needs no
  second human and no contact-management surface.

**Reopening trigger, and the design notes so the branch is cheap when it arrives:**

1. **Feasibility is better than it looks.** §4.2.1.1 imposes **no channel constraint at all** and scopes issuance
   "At enrollment" — which is exactly when an admin holds a password-plus-freshly-confirmed-TOTP session. The
   in-session route does not need §3.1.2.1's AAL2 delivery rule at all.
2. **Mint at ≥112 bits, not at the 64-bit floor.** §4.2.1.1 requires "at least 64 bits from an approved random bit
   generator", but saved recovery codes are **not** classified as look-up secrets — §3.1.2 only says "A typical
   application of look-up secrets is for one-time saved recovery codes", one-directionally, and Table 2 lists them
   as separate secret types. That leaves the storage obligation ambiguous between §4.2.1.1's "approved one-way
   function" and §3.1.2.2's salted password-hashing scheme with a ≥32-bit salt. **≥112 bits dissolves the ambiguity
   and satisfies ASVS 6.5.2 (L2) and 6.5.4 (L2) in the same decision**, avoiding a second cost-factor argument.
3. **It adds a fourth throttled counter.** §4.2.1.1 imports §3.2.2 onto saved-code verification, beside ticket 09's
   password axis, this ticket's factor tiers, and ticket 25's recovery-code counter. Also a SHALL to
   **invalidate-and-reissue on use**.
4. **Declining recovery codes is not itself a NIST failure**, and this is worth keeping straight: §4.2.1.1's
   issuance clause is a **SHOULD**, scoped to "a CSP that supports this recovery option". What binds is §4.2.2.2's
   two-element combination, which the taken route satisfies. *Consolidated into the register (ticket 33): R-MFA-021. Amend the table by ID, not this list.*

**One consequence for this ticket's own two-enrolled-admins invariant:** it is now load-bearing for a third
decision (it is why recovery contacts were even considered), so removing it gets correspondingly harder. Recorded
on the invariant rather than in ticket 25. *Consolidated into the ADR routing (ticket 34): ADR-024 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 30 — the invariant no longer counts factor resets

To 19:512–514 ("load-bearing for a third decision"): the invariant still stands on disable, demote and delete, but
**the factor-reset path is exempt from its count** ([ticket 30](30-sole-admin-bootstrap-premise.md) §3). As built, the
guard refused A resetting B's TOTP at exactly two admins, so "admin-resets-admin plus a two-enrolled-admins
invariant" (19:224–232) refused its own compensating reset at the minimum population it enforced. Exempting it is
safe because the subject restores the count alone by re-enrolling. The recovery-codes deferral's compensating
control now works at two admins, not three; the cost is TM-08 widening at exactly two (ticket 15 amendment). *Consolidated into the ADR routing (ticket 34): ADR-024 (attached amendment). Amend by ID, not this list.*
