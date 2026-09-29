# 04 — Password policy and history

Type: grilling
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

## Question

What is the password strength policy, and what password history and rotation policy applies?

Answer `Q12` (minimum password strength requirements) and `Q13` (password history and rotation policy) of the standard's question set.

- PRD sets a floor only: length ≥ 12 (`prd/assessment-prd.md:36`), BCrypt via `BCryptPasswordEncoder`, no custom hashing. The standard's `Q12` asks for more dimensions than length — settle whether character-class rules, denylists or breach checks apply, and where validation lives so that registration (Story 1) and reset-confirm (Story 7) enforce it identically.
- Password history is **in scope** per the map's authority order (the PRD is silent; the standard requires a policy). Decide the retained-hash count, whether rotation/expiry is enforced, and how history rows are stored — the PRD's data model has no table for them, so this extends the schema.

**Also settle a scoping question this ticket exposes.** Password history and self-service password change ship in the *same* recipe, [`Standalone_Self-Service_Password_and_History_Management.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Self-Service_Password_and_History_Management.md). History is in scope; self-service change (a logged-in user changing their own password, distinct from the PRD's forgot-password reset) has no PRD story. Rule it in or out here, and record it on the map accordingly — if out, it belongs in **Out of scope**, not in **Decisions so far**.

This ticket gates account lifecycle (10) and the password reset flow (11).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Also rule in or out **forced password change**, a standard feature with no PRD story: `Standalone_Privileged_User_Administration_and_Password_Reset.md:531-539` describes a `requirePasswordChange` flag and a `PasswordChangeFilter` that blocks every endpoint except `GET ${api.base-path}/csrf`, `GET ${api.base-path}/currentUser` and `PATCH ${api.base-path}/currentUser/changePassword`.

This is no longer hypothetical: 01 ruled `POST /api/v1/users` (admin creates a user with an admin-supplied password) **in scope**, and that is precisely the case forced change exists for. If ruled in, settle what sets the flag (admin create? admin `resetPassword`? password age?) and confirm the filter's allowlist against 01's endpoint inventory.

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md).** Schema mechanics are settled, so this ticket's schema extension has a prescribed home and fixed column conventions:

- **Liquibase**, with `spring.jpa.hibernate.ddl-auto: validate` — so the changelog is the source of truth and any drift from the entities fails at startup.
- **This ticket owns its own changelog file** under `db/changelog/db.changelog-master.yaml`, one versioned file per logical change. 02 locked that convention because several tickets add schema concurrently and a single growing changelog would guarantee conflicts.
- **Column conventions are not this ticket's to choose:** primary keys are `UUID`, declared as `java.sql.Types.UUID` so Liquibase resolves the vendor type; every timestamp is `TIMESTAMP WITH TIME ZONE` mapped to `java.time.Instant` and stored UTC, read through an injectable `Clock`.
- 02's table inventory already **reserves the slot** for what this ticket adds; only the columns are open.
- Whatever DDL this ticket writes must hold on **both** H2 and PostgreSQL — 02 answered `Q6` as PostgreSQL-declared-target, and [14](14-test-and-validation-plan.md) now runs the security-critical suite against real PostgreSQL via Testcontainers, so vendor-specific DDL will fail visibly rather than silently.

## Answer

### Scope rulings the ticket asked for

**Self-service password change is IN scope.** The ticket framed this as "a standard feature with no PRD story", which the map's authority rule 3 would push out. It does not apply: rule 3 is about standard-only *subsystems* (the hygiene jobs, the role-sync machinery), and this is one endpoint with one command. Against ruling it out: `Standalone_User_Access_Control_Application_Standard.md:240` is a bare `must` in the Security Contract, `:110` and `:129` are rejection rules, `:69-72` is a numbered step in the §2 behavioural contract, `:463-467` is a named test section, and 01 had already put `PATCH /api/v1/currentUser/changePassword` in the endpoint inventory. Decisively: it is the only endpoint a forced password change can send anyone to, so ruling it out would have made the next ruling unanswerable.

**Forced password change is IN scope — but its grace period is OUT, and the split is the point.** The `requirePasswordChange` flag and its `PasswordChangeFilter` (`Standalone_Privileged_User_Administration_and_Password_Reset.md:531-539`) are request-time code, and 01's decision to rule `POST /api/v1/users` in scope creates exactly the case the flag exists for: an admin-supplied password the user has never chosen. `Standard:130` independently mandates the flag on admin re-enable.

The grace period is a different animal. `Standard:50` and `:372` require that a user who has not changed their password within 30 days is **disabled automatically** — which can only be enforced by a scheduled sweep. `Standalone_Scheduled_Account_Hygiene_Jobs.md` is already out of scope on the map, and 15's ArchUnit rule **bans `@Scheduled` under `com.assessment.auth`** precisely to keep 05's 1,254-line batch logging recipe from activating. Implementing the grace period would trip that tripwire. It is therefore recorded on the map's Out of scope as a *consequence* of the existing hygiene-jobs exclusion, not as a fresh scoping decision. The flag is set and enforced; it is never time-expired.

### `Q12` — password strength

| Dimension | Value | Source |
|---|---|---|
| Minimum length | **12** | `prd/assessment-prd.md:37`, `Questions.md:295` — both agree |
| Maximum length | **72** | see below |
| Character composition | **none** | `Questions.md:302`, `:305` |
| Allowed charset | **all printable ASCII, including space** | `Questions.md:308` |
| Hash | **BCrypt, cost 12** | `prd:116` + `Questions.md:287` |
| Banned passwords | **bundled static denylist** | `Questions.md:314`, `:317` |

**No composition rules, and this overrides the recipe.** `Standalone_Privileged_User_Administration_and_Password_Reset.md:109` enforces a regex demanding digit + lower + upper + one of `@#$%^&+=` and `(?=\S+$)` — no whitespace. The standard's clause list contains **no composition clause for user-chosen passwords**: `Standard:357-362`'s one-of-each-class rule is scoped to the *administratively generated* 12-character password, and nothing else. `Questions.md:305` argues against composition rules outright, and the recipe's own note at `Privileged:112` says to remove the check for strict NIST compliance. The map's standing rule from 05 — *the recipes are guides; the standard is the clause list* — settles it. The no-whitespace clause is dropped as well: it is recipe-only and it bans passphrases, the strongest thing a user can type. Composition **is retained** for the admin-generated password, where it is an actual clause.

**Maximum length is 72, not `Questions.md:296`'s recommended 128 and not the recipe's 1024, because BCrypt in the pinned version throws rather than truncates.** Verified against the jar 15's pin resolves to:

```
$ unzip -p ~/.m2/repository/org/springframework/security/spring-security-crypto/7.0.6/spring-security-crypto-7.0.6.jar org/springframework/security/crypto/bcrypt/BCrypt.class | grep -a "72 bytes"
password cannot be more than 72 bytes
```

At 128 or 1024, a long passphrase becomes a `500`, not a validation error. This is a concrete defect in copying either source as written. **A consequence of the charset ruling makes the cap exact:** because the charset is printable ASCII only, 72 characters *is* 72 bytes, so a character-count cap needs no multibyte reasoning. Had Unicode been allowed (`Questions.md:310`), the cap would have had to be expressed in bytes and would have rejected some 30-character passwords — a reason beyond passphrase support to have taken the ASCII option.

**BCrypt survives the standard, and the cost does not come from the PRD.** `Standard:354` prefers Argon2id or scrypt but lists BCrypt as acceptable, and `prd:116` pins `BCryptPasswordEncoder` explicitly — so this is one of the few places the PRD's choice stands against a standard's stated preference. But `BCryptPasswordEncoder`'s default strength is **10** and `Questions.md:287` requires **cost ≥ 12**; the PRD is silent on cost, so the standard fills the gap. The encoder must be constructed with an explicit strength of 12, never defaulted.

**Banned passwords: a bundled classpath denylist, no network call — and HIBP is declined, not merely skipped.** The recipe leaves the Have I Been Pwned check commented out (`Privileged:110`). A live HIBP lookup is rejected for a reason specific to this app: `Standard:247` requires *identical response timing* across login, password reset and registration to defeat enumeration and timing attacks, and a variable-latency third-party HTTP call on the registration path directly undermines the clause. It also introduces egress from an app whose hosting story is a PRD exclusion. In its place: a top-10k common-password list as a classpath resource, plus rejection of any password containing the submitted username or the email local-part. This satisfies `Questions.md:314`'s intent locally and deterministically.

### `Q13` — password history and rotation

**History length `3`, meaning three *previous* passwords plus the current one: four blocked values.** The sources disagree and the disagreement is not cosmetic. `Standard:355` says "Retain **3 previous** passwords" — previous meaning prior-to-current, so the current hash is blocked *in addition*. The recipe implements something different: `Self-Service` Step 3 holds the current hash inside the history list and trims at `maxHistoryLength`, giving three blocked values in total.

The literal clause reading wins, per the map's authority rule. It is also the stricter arm. **This has a direct downstream consequence that must not be missed: the recipe's verification procedure at `Self-Service:269-272` is wrong for this app.** That procedure seeds a password, performs three updates, and expects the original to be *accepted* again — which is only true if the cap is three including the current. A test copied verbatim from the recipe will fail against a correct implementation here, so 14 must assert the four-value behaviour instead.

**There is no password expiry and no forced rotation.** The ticket's brief asks "whether rotation/expiry is enforced". The honest finding is that **the standard never asks for it**: the clause list contains no maximum-password-age clause anywhere, and `Questions.md:321`'s "rotation policy" heading, read against its own body at `:325`, turns out to parameterise nothing but the history count. The only age-like rule in the whole standard is the forced-change grace period, ruled out above. NIST SP 800-63B — which `Questions.md:305` already defers to for composition — argues against scheduled rotation. Recorded as a finding rather than an omission, so a later reviewer does not read the absence as a gap.

### Storage

Dedicated **`password_history` table**, filling the slot 02 reserved at `02:105`, not a JPA `@ElementCollection` (the recipes' `List<PasswordHistoryEntry>` shape). A table keeps "is this a reuse?" a single indexed query and makes the retention trim explicit rather than a side effect of list mutation.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` (`java.sql.Types.UUID`) | 02's PK convention, application-generated |
| `user_id` | `UUID`, FK → `users.id`, `NOT NULL` | indexed; disposition on delete follows 10's tombstone ruling |
| `password_hash` | `VARCHAR(60)` `NOT NULL` | BCrypt output is fixed-width |
| `created_at` | `TIMESTAMP WITH TIME ZONE` `NOT NULL` | UTC `Instant` from the injectable `Clock`, per 02 |

**The current password's hash is written here too**, as the newest row — so the reuse check is one query with no special case for the live credential, and the four-value rule reduces to "keep the 4 newest rows, reject a match against any of them". Older rows are deleted on each successful change, so the table cannot grow unbounded without a scheduled job (which 15's ArchUnit rule forbids anyway). Registration and admin-create each seed the first row, matching the pattern at `Privileged:165`.

This ticket owns its own Liquibase changelog file under `db/changelog/`, included from `db.changelog-master.yaml`, per the convention 02 locked. The DDL uses no vendor-specific types, so it holds on both H2 and the Testcontainers PostgreSQL run 14 owns.

**One deviation from the recipe's configuration, recorded so it is not read as a mistake.** `Self-Service:26-32` names the property `spring.password.sso.max-password-history-length`. That key is wrong here twice over: it squats on the `spring.*` namespace, and it says `sso` in an app the map has ruled Standalone. Use `app.password.history-length: 3` under 15's own `app.*` namespace.

### Where enforcement lives

An explicit **`PasswordPolicy`** component (strength, charset, denylist) and a **`PasswordHistoryService`** (reuse), called from the domain layer by all four write paths — registration, reset-confirm, self-service change, admin-create — rather than a Bean Validation annotation on the DTOs.

Bean Validation cannot perform the history check at all, since it needs the user record; using it for strength would split enforcement across two mechanisms and reintroduce exactly the "enforce identically" failure this ticket was written to prevent. It also **avoids inheriting an unresolved contradiction**: 05 found the binding set disagrees on the log level for Bean Validation failures (`Structured_Logging_Application_Standard.md:97` says `ERROR`, `Structured_Logging_Application_Standard_Questions.md:298` says `WARN`). Routing password failures through a domain exception keeps this ticket clear of that conflict, which the error-contract fog still has to settle for genuine Bean Validation failures elsewhere.

**Two error clauses that look contradictory, and do not conflict.** `Standard:110` requires "a specific error … indicating which rule was violated", while `Standard:125`/`:247` require identical bodies, statuses and timing to prevent enumeration. `:247` enumerates its own scope — "invalid credentials, locked account, non-existent user, disabled account" — all *authentication* outcomes. A password-policy violation is not among them and discloses nothing about who exists, so `:110`'s specific error is permitted. Recorded so it is not re-litigated when 08 and the error-contract fog reach it.

### The `requirePasswordChange` lifecycle

| Event | Flag | Source |
|---|---|---|
| Admin creates a user (`POST /api/v1/users`) | **set** | `Privileged:166`, `Standard:122` |
| Admin resets a password (`PATCH /users/{userId}/resetPassword`) | **set** | `Privileged:438` |
| Admin re-enables a disabled account | **set** | `Standard:130` |
| Bootstrap admin seeded at startup | **set** | judgement — below |
| Self-registration (Story 1) | not set | the user chose the password |
| Reset-confirm (Story 7) | **cleared** | judgement — below |
| Self-service change | **cleared** | `Self-Service` Step 3 |

Two entries are judgement calls rather than transcription.

**The seeded bootstrap admin is flagged.** `Standard:122` scopes the mandate to "all users created by administrators", and the bootstrap admin is created by configuration at startup, not by an administrator — so it falls outside the clause's literal subject. It is flagged anyway: its password arrives as `APP_ADMIN_PASSWORD` (15) and is known to whoever performed the deployment, which is precisely the exposure the flag exists to close. 16 receives this as a constraint it may not quietly drop for demo convenience.

**Reset-confirm clears the flag, and this is a trap worth naming.** A flagged user is blocked from everything except four paths — but the password-reset endpoints are *public*, so the filter's `auth != null` guard never evaluates them. A flagged user can therefore route around the forced change entirely via the Story 6/7 email flow. That escape hatch is harmless only because reset-confirm sets a password of the user's own choosing, which is the flag's whole purpose. If it did **not** clear the flag, that user would be permanently trapped: holding a password only they know, behind a filter that still blocks every endpoint. So the flag clears on any successful password change performed by the account owner; never on an admin-driven reset, which sets it.

### The `PasswordChangeFilter` allowlist

Checked row by row against 01's endpoint inventory. Allowed while `requirePasswordChange=true`:

- `GET /api/v1/csrf`
- `GET /api/v1/currentUser`
- `PATCH /api/v1/currentUser/changePassword`
- `POST /api/v1/auth/logout`

Everything else in 01's inventory returns `403` with a specific error code (`Standard:51`, `:165`).

The recipes disagree on the fourth row and the disagreement matters. `Privileged:533-537` lists only the first three; `Self-Service:158-162` allows the change endpoint and `/logout`. Logout is included because the privileged recipe's omission leaves a flagged user unable to end their own live session — they can only abandon it, leaving a valid session cookie alive for its full lifetime. The public rows (`/auth/login`, `/auth/register`, `/auth/password-reset/request`, `/auth/password-reset/confirm`) need no allowlist entry: the filter's `auth != null` guard already skips unauthenticated requests. That is the same mechanism that creates the reset-confirm escape hatch handled above — one guard, two consequences, both deliberate.

The three allowed authenticated paths are exactly what the SPA's bootstrap sequence needs (01 fixed it as `GET /csrf` → `POST /auth/login` → `GET /currentUser`), so a flagged user can still complete login and discover their own state before being redirected to the change screen.

### The change-password contract

`PATCH /api/v1/currentUser/changePassword`, body `{currentPassword, newPassword}`, response `204 No Content`.

**The recipe's DTO is insufficient as written.** `Self-Service` Step 2 accepts `newPassword` only, and demotes `currentPassword` to a §5.1 "hardening" recommendation. `Standard:129` and `:240` both make verifying the current password a `must`, even for a caller with an active session. The current-password field is mandatory, not hardening.

On success, in order:

1. Validate strength (`PasswordPolicy`) and reuse (`PasswordHistoryService`, four-value rule).
2. Verify `currentPassword` via `PasswordEncoder.matches` — rejected per 08's matrix, never by revealing anything about the account.
3. Encode at cost 12, write the new `password_history` row, trim to four.
4. Clear `requirePasswordChange`.
5. Invalidate **all** pending unused password-reset tokens for the account (`Standard:356`) — an interaction 11 must honour from the other side.
6. Invalidate **all** sessions for the account, including the caller's own.
7. Notify the account owner.

**Step 6 is a fork, resolved toward the clause list.** `Standard:72` says "invalidates all active sessions"; `Self-Service:246` says "all **other** active sessions". The standard wins: all, including the caller's. The SPA therefore lands on the login screen after a successful change. For the forced-change flow this is the correct ending anyway — the user re-authenticates with the credential they just chose, which is the only way the app ever observes that they know it. Implemented through the `FindByIndexNameSessionRepository` 02 already established as available, the same mechanism `:128` requires for reset.

**Step 7 adds a method the PRD did not anticipate.** `Standard:65` and `:72` require notifying the account owner on both admin-initiated reset and self-service change. The PRD's stub `EmailService` (`prd:20`) has only `sendPasswordResetEmail`. Add `sendPasswordChangedEmail(...)` as a second logging stub. This stays inside the PRD's real-SMTP exclusion, which excludes *delivery*, not the call site.

**Not this ticket's to decide:** the `/currentUser` path string itself is 10's, per 01's note that `Privileged:536` says `/currentUser` while `Common_Secure_Self-Read_User_Endpoint.md:45` mounts at `/profile`. Whatever 10 rules, the change endpoint and the filter allowlist follow it; they are written against the segment, not a literal.

### Downstream

- **[10 — Account lifecycle and delete semantics](10-account-lifecycle-and-delete-semantics.md)** — the `/currentUser` path string now carries the change endpoint and two filter allowlist entries; `password_history` rows need a disposition under the tombstone ruling.
- **[11 — Password reset flow](11-password-reset-flow.md)** — reset-confirm must run both checks and clear the flag; a self-service change invalidates its pending tokens; admin `resetPassword` sets the flag and must avoid generating a password already in history (`Privileged:427`).
- **[12 — Audit and logging contract](12-audit-and-logging-contract.md)** — `Standard:283-284` and `:324` make password changes `INFO` audit events; the forced-change `403` is an authorisation denial, one of 18's six `source.ip` classes.
- **[14 — Test and validation plan](14-test-and-validation-plan.md)** — assert the four-value history rule, **not** the recipe's three-value procedure at `Self-Service:269-272`; assert the 72-byte boundary; assert the filter allowlist including logout.
- **[16 — Admin bootstrap and seeding](16-admin-bootstrap-and-seeding.md)** — the seeded admin is flagged `requirePasswordChange=true`.
- **[08 — Authorization matrix](08-authorization-matrix.md)** — the filter runs after authentication and before the matrix; the four allowlisted paths need their own row.
