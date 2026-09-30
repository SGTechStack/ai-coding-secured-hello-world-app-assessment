# 11 — Decide the admin module, role model, and initial admin bootstrap

Type: grilling
Status: resolved
Blocked by: 01, 04, 05, 06, 19

## Question

What does the admin surface look like, how are roles defined and assigned, and how does the very
first admin account come into existence safely?

Bootstrap is merged in here because it is a role-assignment question wearing a different hat.

## Inherited from ticket 19 — constraints, not open questions

[Resolve the MFA scope conflict raised by IM8 ac-2](19-mfa-scope-conflict.md) put a TOTP factor gate
across this whole surface. Four consequences land here:

- **The entire admin surface sits behind `FACTOR_TOTP`** — reads unbounded, mutations re-verified every
  10 minutes. Every endpoint decided below is gated, so "which endpoints need the factor" is not a
  question; it is all of them.
- **An ADMIN-only, audited TOTP-reset endpoint exists**, itself behind the factor. It belongs to the
  admin endpoint set decided here, alongside the unlock endpoint the standard already mandates. The
  corpus prescribes no such endpoint, so its shape is ours.
- **Invariant: at least two enabled, enrolled ADMIN accounts.** This is the compensating control for
  having no recovery codes, and it constrains disable, delete, and role-demotion — the same four
  operations as the self-action guard, which argues for one central guard rather than two.
- **The bootstrap admin cannot reach this surface until enrolled.** Confirm nothing in the seeding path
  needs the admin surface to seed itself.

## The conflict to resolve first

The PRD (Story 10) gives admins an endpoint to change another user's role between USER and ADMIN.
The App Standard says role **definitions** are configuration-driven, immutable via API, loaded at
startup with fail-fast on duplicates, and that "users cannot assign themselves or others to roles
through the application" — while also saying "role assignments must be configured and managed by
role administrators only."

Those two sentences can be read as flatly prohibiting Story 10, or as permitting admin-performed
assignment while prohibiting self-assignment. Resolve the reading explicitly. Check
`Common_Role-Based_Access_Control_Configuration.md` via "Inventory the prescribed recipes" — it
probably settles this and the file format.

## Inherited from ticket 06 — constraints, not open questions

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
added one field to this ticket's self-read endpoint and narrowed one error code:

- **Factor state is a field on the self-read response, not a new endpoint:**
  `factors: { held: [...], required: [...], enrolled: bool }`. The SPA already calls self-read immediately after
  login to discover auth state, so the eager admin challenge costs zero extra round trips. The prescribed
  three-boolean shape (`/mfa/requirePinAndTotpSetup`, `/mfa/setupAllowed`, `/mfa/queryTotpKeyExists`) serves the
  opt-in per-user model that ticket 19 put out of scope.
- **Factor state ships; lock state does not.** Q23a warns that exposing lockout timing or failed-attempt counts
  on the self-read helps attackers tune brute force.
- **`USER_EXISTS` (400) is admin-only.** Admin-initiated creation returns the specific duplicate error because
  the caller is already privileged; self-registration is uniform (see ticket 10). `USERNAME_CHANGE_NOT_ALLOWED`
  (400) and `BATCH_TOO_LARGE` (400, **not** the admin recipe's "413 / 400") are the other admin-surface codes.
- **The forced-password-change filter returns 403 `PASSWORD_CHANGE_REQUIRED`** — that is the "specific error
  code" §3.2 requires and never defines, and it is what lets the SPA tell it from an authorization denial.
- The self-read endpoint name collision (`/currentUser` vs `/api/v1/profile`) is still open and still yours.

## What to decide

**Role model.**

- Role *definitions* in a config file (name → privileges) vs the PRD's plain `USER`/`ADMIN` enum.
- The **role-based authorization matrix** — role → allowed paths and HTTP methods, loaded at startup.
  This is a whole mechanism the PRD never mentions. Decide whether we adopt it or defer it with
  justification, given the PRD scopes authorization to a USER/ADMIN check on admin endpoints. If
  adopted, decide the file format and how it composes with `@PreAuthorize`.
- Whether role changes are read from config, database, or both, and what "synchronise roles at
  startup" means for us.

**Admin endpoints.** For each of PRD Stories 8–11, decide path, method, request/response shape, and
authorization:

- List users — must never return password hashes. Decide the exact field set and whether emails are
  returned to admins (PII minimisation cuts against it; the PRD asks for it).
- Enable/disable — dedicated endpoint, not the generic update. The standard requires sensitive
  operations to use dedicated endpoints and forbids setting a lock field through generic update.
- Change role — subject to the conflict resolution above.
- Delete — see soft-delete below.
- Unlock — required by the standard, absent from the PRD. Decide whether to add it (it is the only
  way to lift a lockout early, and the standard mandates it).
- The **self-action guard**: admins cannot disable, demote, or delete themselves (PRD Stories 9–11)
  and cannot unlock themselves (standard). Decide where this lives so it cannot be bypassed by
  adding a new endpoint later — one guard, centrally applied, not four copies.

**Soft delete.** The standard requires deleted users retained as tombstones, and requires user
creation to be rejected when the username exists in a tombstone. The PRD says the account is
"removed". Decide the tombstone's fields and retention, and what a tombstone does to email reuse as
well as username reuse. Note the tension with data-minimisation and any IM8 retention control from
"Extract the IM8 and ARC controls".

**Self-read endpoint.** The standard mandates a dedicated endpoint returning only the caller's own
record; `Common_Secure_Self-Read_User_Endpoint.md` likely prescribes it. This is also what the SPA
needs to discover auth state behind an HttpOnly cookie, so its shape is consumed by "Design the
frontend architecture". Decide the field set — it should differ from the admin view.

**Initial admin bootstrap (PRD Story 12).**

- Credential source: the PRD suggests `app.admin.username` / `app.admin.password`. Decide whether a
  plaintext password in a properties file is acceptable (it is not, for anything but local dev) and
  what replaces it — environment variable, fail-fast when unset, refusal to start with a weak or
  default value.
- Whether the seeded admin is flagged for mandatory password change on first login. The standard
  requires this for admin-created users and on re-enable; the same logic applies here, and it closes
  the "shipped with a known bootstrap password" hole.
- Idempotency: seed only when no `ADMIN` exists (PRD Story 12), and decide what happens if an admin
  exists but is disabled or soft-deleted.
- Whether the seed is profile-gated. The standard says development-only seed accounts must not be
  present in production — decide whether the bootstrap admin is a dev seed or a production
  necessity, because they need different treatment.

## Done when

The Story 10 conflict has a written resolution, every admin endpoint is specified with its guard,
the tombstone model is decided, and the bootstrap cannot ship with a known password.

---

## Answer

**Eight endpoints behind one factor-gated prefix, two roles whose definitions live in YAML but are
persisted read-only, one central guard holding both the self-action rule and the two-admin invariant
under a pessimistic lock, and a bootstrap that validates during context refresh and seeds in a runner.**

### The conflict this ticket was created to resolve

Story 10 is permitted. The blocker was never `ImmutableSecurityHandler` — ticket 04 already established
it guards role *definitions* — and in fact the handler is **inert in our stack**: its `SecurityResource`
parameter type is never defined anywhere in the corpus, and `@RepositoryEventHandler` only fires for
Spring Data REST-exported resources, which we do not use. The real tension is §3.5:384, "Users cannot
assign themselves **or others** to roles through the application", which the admin recipe's own
`existing.setRoles(request.roles())` flatly contradicts.

Resolution: §3.5:384 is read as barring **self**-assignment, which is the only reading under which the
standard and its own prescribed recipe can both be satisfied, and which Decision Logic §120 independently
supports ("Users cannot perform create, update, or delete operations on their own account via the admin
update endpoint"). The immutability requirement it protects is satisfied a different way — role
*definitions* are never mutable at runtime because they come from YAML and a seeded lookup table with no
write path, so there is no endpoint for the handler to guard in the first place.

### Role model

Two roles, `USER` and `ADMIN`, one role per user (Q17a: No). Q17b is **not open**: PRD Story 10 forces
"modifiable anytime after creation".

- **YAML is the source of truth.** `app.security.roles` declares the set.
- **Flyway seeds the two rows** into a `roles` lookup table, idempotently, and `users.role` carries a
  **foreign key** to it. The FK is the point: without it the table is decorative and a reviewer spots it
  immediately. This satisfies §4's Enforced Constraint "user account records and role definitions are
  persisted in a relational database" *literally*, which the round-1 position did not.
- **A startup validator fails if YAML and table disagree**, and it runs in the same refresh-phase bean as
  the bootstrap property validation, so a mismatch aborts before the port binds. *Consolidated into the register (ticket 33): R-DATA-003. Amend the table by ID, not this list.*
- **Rejected:** the privilege layer, `role-hierarchy`, the `roles`↔`privileges` join table, and the entire
  `SsoUserStartupConfiguration` synchroniser. The synchroniser is not merely heavy: `syncDBUsersBasedOnDefinedUsers()`
  performs `userRepository.deleteAll()` and re-seeds from properties, gated by a **flag, not a profile**, so a
  single property in the wrong file wipes the user table. It is also unannotated as printed and hooks
  `ApplicationReadyEvent`, which Boot documents as firing *after* runners — later still than the "before
  serving traffic" ordering §2 Happy Path 1 demands. No dev seed accounts of any kind.

**Authority naming — one convention, applied everywhere:** granted `ROLE_USER` / `ROLE_ADMIN`, guarded with
`hasRole('ADMIN')`. This resolves ticket 04's risk item 4. Both corpus conventions are rejected:
`hasRole('USERS_UPDATE')` requires a `ROLE_USERS_UPDATE` authority the RBAC YAML never grants, so the admin
recipe's guards **deny every request** as printed; `hasAuthority('SELF_READ')` is the lone expression that is
internally consistent and is stylistically the outlier.

### Authorization matrix

Adopted narrowly. A typed `@Validated @ConfigurationProperties` record declaring role → list of
(method, path), applied in `authorizeHttpRequests` as **whitelist first, guards second,
`anyRequest().denyAll()` last**, with `@PreAuthorize` on the service methods as defence in depth and an
assertion test proving chain and annotations agree. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-012. Amend the table by ID, not this list.*

Two recipe defects fixed rather than inherited:

- The recipe registers `url-guards` **before** the whitelist under first-match-wins, so any whitelist entry
  matching a guard pattern is silently guarded. We invert the order.
- The recipe's `url-guards` YAML (`- GET: /api/v1/users/**`) is a list of single-entry maps with *dynamic*
  keys, while its code calls `guard.getMethod()` / `guard.getPath()`. That does not bind without a `Guard`
  type and a converter, and `requestMatchers(String, String)` is the wrong overload besides. **The prescribed *Consolidated into the register (ticket 33): R-STD-032. Amend the table by ID, not this list.*
  matrix does not compile.** Ours uses explicit `method:` / `path:` keys.

This file doubles as **ac-4's declared per-account permission baseline**, converting a flat FAIL into
"baseline declared, scheduled compare-and-revoke deferred with justification". *Consolidated into the register (ticket 33): R-ADM-001. Amend the table by ID, not this list.*

### Path scheme

`api.base-path=/api`, admin surface at `/api/admin/**`, giving PRD Story 8's literal `GET /api/admin/users`.
`/v1` is dropped: the standard's `/api/v1` is hedged "e.g." and PRD Story 5's `GET /api/hello` fixes the
unversioned base. Ticket 03's own worked example already logs
`url.path: /api/admin/users/{uuid}/role` — two tickets had silently agreed on this and neither had said so.

**`spring.mvc.servlet.path` is prohibited.** The base path lives in controller mappings only.
[CVE-2026-22753](https://zeropath.com/blog/cve-2026-22753-spring-security-servlet-path-bypass) (CVSS 7.5,
Security 7.0.0–7.0.4) is exactly this configuration shape: `securityMatchers(String)` built a fresh default
`PathPatternRequestMatcher.Builder` that ignored the context bean Boot publishes for `spring.mvc.servlet.path`,
so `securityMatchers("/admin/**")` never matched requests arriving at `/api/admin/**` and **the filter chain
applied no security at all**. The article's worked example is our endpoint. Our pinned version is above the
affected range, but the rule stands independently: the strings the security layer matches must be the strings
the container receives. Also record that Spring Security 7 defaults to `PathPatternRequestMatcher`, so nobody
should reintroduce `AntPathRequestMatcher` and its trailing-slash traps.

### The endpoint set

| Endpoint | Factor rule | Guard checks | Session invalidation | Audit obligation |
|---|---|---|---|---|
| `GET /api/admin/users` | TOTP, unbounded | — | — | `adminUserListed` + **`user.target.count`** |
| `GET /api/admin/users/{uuid}` | TOTP, unbounded | — | — | admin read-one; event owed, ticket 13 |
| `POST /api/admin/users` | TOTP, 10 min | duplicate username **or** email, incl. tombstones → `USER_EXISTS` 400 | — | `user-provisioning` / creation |
| `PUT /api/admin/users/{uuid}/enabled` | TOTP, 10 min | actor ≠ subject; invariant when disabling an ADMIN | **target's sessions killed on disable** | `adminUserStatusChanged` |
| `PUT /api/admin/users/{uuid}/role` | TOTP, 10 min | actor ≠ subject; invariant when demoting an ADMIN | **target's sessions killed** | `adminUserRoleChanged` + `user.target.roles` |
| `DELETE /api/admin/users/{uuid}` | TOTP, 10 min | actor ≠ subject; invariant when deleting an ADMIN | **target's sessions killed** | `adminUserDeleted`; tombstone in same tx |
| `POST /api/admin/users/{uuid}/unlock` | TOTP, 10 min | actor ≠ subject (no self-unlock) | none — a grant, not a revocation | event owed + **`user.target.unlockReason`** |
| `DELETE /api/admin/users/{uuid}/totp` | TOTP, 10 min | actor ≠ subject; **exempt from the two-admin count** (ticket 30 — subject re-enrols alone) | **target's sessions killed** | `event.action: totp-remove` |
| `GET /api/profile` | **none — password only** | — | — | `event.action: profile-read` |

All admin rows additionally carry `hasRole('ADMIN')`. Every mutation is `PUT`/`POST`/`DELETE` with an explicit
value, never a toggle: a toggle is non-idempotent and two concurrent admin clicks land wherever the race ends.

Three consequences worth stating because they are easy to miss:

- **Admin TOTP reset must kill the target's sessions.** Otherwise a session already holding `FACTOR_TOTP`
  outlives the secret's destruction and stays privileged on a factor that no longer exists.
- **Creating or promoting an ADMIN does not satisfy the invariant**, because the invariant counts *enrolled*
  admins and a new admin is unenrolled until they complete enrolment.
- **Re-enable sets `forcePasswordChange = true` and `credentialIssuedAt = now`** per Decision Logic §131.
  The clock starts when the obligation is imposed, not when the old credential was issued.

**Declined, with justification:** batch password reset. The PRD never asks for it, and it multiplies the
"plaintext token returned exactly once, never logged" problem by up to 5000 in one response body. Its own
recipe cannot decide the status code ("413 / 400") while the standard prescribes 400. **Consequence:
`BATCH_TOO_LARGE` is removed from ticket 06's closed enum, taking it to 13 codes** — amended there, not left
as an unreachable member.

**Left to ticket 10:** admin-initiated single password reset issuance. This ticket reserves the path and the
guard; ticket 10 owns whether both issuance paths exist onto one redemption endpoint.

### The central guard, and the race that made it real

One `AdminActionGuard`, invoked from the single service method every admin mutation routes through. Not an
annotation and not a filter, because both are opt-in and the eleventh endpoint added next year forgets them —
which is not hypothetical: ticket 07 found a shared validator that was simply never called, and the admin
recipe **permits self-disable**, contradicting both Decision Logic §120 and PRD Story 9. Backed by an
architecture test asserting no controller in the admin package reaches a repository directly. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-014. Amend the table by ID, not this list.*

Two checks:

1. **actor ≠ subject** for disable, demote, delete, unlock, factor reset.
2. **≥ 2 enabled *and enrolled* ADMIN accounts** evaluated against the proposed post-change state.

Check 2 as originally drafted was not an invariant but a stated intention. Two admins demoting two different
admins concurrently each read "2 remain" and both commit, leaving zero. The concurrency control is decided
here: the guard opens its transaction with a **pessimistic lock over the admin row set**, counts in
application code, then mutates — one `@Transactional` unit.

**H2 constraint, load-bearing:** `SELECT COUNT(*) ... FOR UPDATE` is **invalid**. H2's SELECT documentation
states the `FOR UPDATE` clause "is not allowed in DISTINCT queries and in queries with non-window aggregates,
GROUP BY, or HAVING clauses". So the guard must select the admin **rows** for update and count them in Java.
H2 does support row-level `FOR UPDATE` plus `NOWAIT`, `SKIP LOCKED` and `WAIT <timeout>`, so the mechanism is
available — just not in its obvious form. Serialisable-with-retry was rejected: a retry loop around a security
invariant is a correctness risk under load.

Locking only the existing ADMIN rows is sufficient, and the reason should be recorded so nobody "improves" it:
every removal path must touch one of those rows, so all removals serialise, while a concurrent promotion only
increases the count and cannot break a "≥2 must remain" rule. H2's note that uncommitted rows from other
transactions cannot be selected or locked is the phantom case, and it is harmless in the safe direction.

**Operational consequence, for ticket 25:** at exactly two admins, neither can be removed until a third
exists. Promote first, then demote. That is the invariant working, not a bug, and an admin trying to offboard
a leaver will hit it.

### Soft delete and the tombstone

`[Enforced Constraint]` §4 requires soft delete, so PRD Story 11's "the account is removed" means removed from
the active table with a tombstone retained. Fields: `uuid`, `username` (plaintext), `emailHmac`, `deletedAt`,
`deletedById`. Retention **indefinite** (Q29's recommended option). *Consolidated into the register (ticket 33): R-ADM-002. Amend the table by ID, not this list.*

**The tombstone blocks both username and email reuse.** The corpus checks
`users.existsByUsername(...) || deletedUsers.existsByUsername(...)` for username but only
`users.existsByEmail(...)` for email — **no tombstone check on email at all**, and Q29's own context sentence
scopes tombstones to preventing "username reuse". That is a hijack path, not an asymmetry: email is the
password-reset identifier, so an unblocked address lets a new account claim a deleted user's recovery channel.

**Email is stored as a keyed HMAC; username as plaintext.** The PDPA framing is the reason, not IM8's silence —
IM8 contains no retention control of any kind, which is a compliance observation and not a privacy argument,
while PDPA's Retention Limitation Obligation applies to personal data independently. The email is the
higher-value identifier and the recovery channel; the username is a handle the admin list already shows every
admin. Honest limit: a keyed HMAC is **pseudonymisation, not anonymisation**, since the key holder can still
confirm a guessed address — it reduces exposure, it does not discharge the obligation the way deletion would.

**Canonicalisation, and why it is not optional.** `HMAC("Alice@Example.com") ≠ HMAC("alice@example.com")`, so
without a named canonical form the hash silently reintroduces the exact hijack the tombstone exists to prevent.
The rule: **NFC-normalise, trim, lowercase** — both identifiers, one shared implementation, applied identically
to the live `users` table and the tombstone. No dot-folding and no `+tag` stripping: RFC 5321 makes only the
domain case-insensitive and leaves local-part semantics to the provider, so folding one provider's conventions
onto all of them rejects legitimately distinct addresses. Lowercasing the whole address goes beyond what
RFC 5321 guarantees and is universal practice; recorded as a deliberate deviation. *Consolidated into the register (ticket 33): R-CRED-014. Amend the table by ID, not this list.*

**A "case-insensitive unique index" is not an available alternative on this stack**, and this corrects an
assumption worth writing down. H2 does not support expression indexes — a maintainer's own answer is that
function indexes are unsupported and must be emulated with computed columns — so
`CREATE UNIQUE INDEX ON users(LOWER(email))` fails. H2's `VARCHAR_IGNORECASE` exists but is proprietary and
would break ticket 12's vendor-neutral DDL rule while silently changing behaviour on Postgres. The portable
answer for plaintext and HMAC alike is therefore identical: canonicalise in application code, store the
canonical value, constrain that. The burden is unavoidable, not a cost of hashing.

**Username format rejects `@`**: `[a-z0-9._-]{3,32}`, canonicalised lowercase. Without this the
plaintext-username / HMAC-email split is cosmetic, because users who register their address as their username
put readable addresses back in a plaintext column. It also removes login-form ambiguity and blocks a
confusion attack the corpus never considers — registering a username equal to another user's email. Visible
product cost: users cannot log in with their email address, and the PRD is silent. *Consolidated into the register (ticket 33): R-CRED-015. Amend the table by ID, not this list.*

**Enumeration:** a tombstone hit on self-registration takes the **identical** path as a live duplicate —
uniform 202, nothing created, no activation token minted. Admin-initiated creation returns the specific
`USER_EXISTS` including on tombstone hits, because that caller is already privileged and factor-verified.
No notification is possible on a tombstone hit since we hold no address, which is consistent rather than a gap.

### Admin-created users, and the deadline that can land without a scheduler

Admin-created users are **immediately active**: a 20-character generated password (ticket 07's floor, not the
corpus's 12), returned to the admin exactly once, `forcePasswordChange = true`, `credentialIssuedAt = now`.
Ticket 10's activation-token machinery stays scoped to self-registration.

The reason is the stubbed transport: PRD line 24 makes `EmailService` a stub that logs instead of sending, so
an activation link cannot be delivered in this build and an undeliverable link is a bricked account. The
argument that "the admin already has an authenticated channel" is **wrong and must not appear in the ADR** —
the admin's channel to the *application* is authenticated; the admin's channel to the *user* is Teams, email
or a phone call, and that hop is where the credential leaks into a chat history outliving the forced change.
That leak is recorded as the acknowledged cost of immediate-active, mitigated by the expiry below. A claim that
current NIST guidance mandates minutes-to-hours expiry for admin-issued temporary credentials **could not be
substantiated in the primary text** — §3.1.1 says only that passwords "SHALL either be chosen by the subscriber
or assigned randomly by the CSP" — so it is deliberately not relied on.

**The 30-day deadline lands, lazily.** The standard pairs forced change with auto-disable after a 30-day grace
period, and this map had twice conceded "the flag lands and the deadline does not" because the reaper is an
out-of-scope hygiene job. It does not need a reaper. `credentialIssuedAt` is set whenever a forced-change
credential is created — bootstrap seed, admin create, admin reset, re-enable — and **checked at login**: if
`forcePasswordChange` is true and the credential is older than 30 days, the login is refused and an admin
reissue is required. Same security outcome, evaluated lazily, no scheduled infrastructure, no conflict with the
hygiene-jobs deferral. It converts a standing admin-known credential of unlimited life into one with a real
expiry, and it closes the same gap on the bootstrap admin.

One rule attached: that refusal returns the **same generic `401 AUTHENTICATION_FAILED`** as every other login
failure. A distinct code would be an oracle for "this account exists and was admin-provisioned".

### The forced-change filter, and the bootstrap deadlock

Order pinned: absolute-session filter (ticket 08) before `CsrfFilter`; forced-change filter registered
`addFilterBefore(..., AuthorizationFilter.class)`.

**Exempt list, exhaustive:** `POST /api/login`, `GET /api/csrf`, `GET /api/profile`, `POST /api/logout`,
`POST` change-password. TOTP enrolment is **not** exempt — enrolment comes *after* the password change.

This is also the resolution of a corpus defect: the two prescribed forced-change allowlists are **mutually
incompatible**. `PasswordChangeFilter` omits `/logout`, so a flagged user is trapped; the self-service recipe's
version omits `/csrf`, and with ticket 08's session-bound header-only CSRF a client without `/csrf` cannot
perform the change at all. Neither is implementable as printed. Ours is the union plus self-read. *Consolidated into the register (ticket 33): R-STD-029. Amend the table by ID, not this list.*

Fresh-deploy sequence, which is the thing Q7 was trying to protect and nearly broke one layer up: seed → login
→ 403 `PASSWORD_CHANGE_REQUIRED` on everything outside the five → change password → flag cleared → enrol →
factor granted → `/api/admin/**` opens.

### Bootstrap

- **Validation during context refresh, seeding in a runner.** `@Validated @ConfigurationProperties` binds and
  strength-validates `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` during refresh, through the same
  `PasswordService` seam ticket 07 mandated — no second validator — so failure aborts before the port binds.
  Seeding alone lives in an `ApplicationRunner`. **A runner cannot fail fast**: Boot documents
  `ApplicationStartedEvent` as firing "after the context has been refreshed but before any application and
  command-line runners have been called", and the web server is started by `WebServerStartStopLifecycle` during
  refresh — so a runner-based check means "bind the port, accept traffic, then die". `ACCEPTING_TRAFFIC`
  readiness does flip only after runners, but readiness is consumed by orchestrator probes and we have no
  orchestrator in scope.
- **Flyway rejected, on checksum immutability.** A BCrypt hash inside a versioned migration is checksum-frozen,
  so cost 12 could never be re-tuned without a repair, and committing any hash of a real credential to VCS is
  the as-8 exposure being avoided. The argument that a migration *cannot* reach `PasswordEncoder` is **false** —
  Flyway 6+ supports injected `JavaMigration` beans and Boot's Flyway auto-configuration collects them — and
  must not appear in the ADR, or a reviewer disproves it in one sentence. This also supersedes ticket 04's
  framing of Q3 as a clean Liquibase→Flyway swap: one of Q3's four options ("Migrate existing admins only")
  names no mechanism at all.
- **Credentials from the environment only**, absent from every committed file including dev. No default, no
  generate-and-log fallback. The seeded password clears ticket 07's full 15-character floor and zxcvbn gate;
  the seed path is not a policy exemption.
- **Idempotency:** seed only when no `ADMIN` row exists. If an ADMIN exists but is **disabled**, do not seed —
  silently minting a fresh way in around a deliberate disable is the hole, not the fix. If the configured
  username exists as a **tombstone**, fail fast with a clear message rather than skipping silently, since the
  tombstone legitimately blocks it.
- **Not profile-gated.** Story 12 makes this a production necessity, not a dev convenience; what is gated is
  the *secret*, not the mechanism.

**One admin, not two.** Two seeded admins needs two env-supplied passwords and two enrolments before the
surface opens, and creates a second standing credential just as likely to be shared or forgotten — it converts
a recovery problem into a credential-hygiene problem.

> **Premise corrected in place by [ticket 30](30-sole-admin-bootstrap-premise.md).** The original sentence rested on
> "per-account lockout auto-expires after 20 minutes, so a locked-out sole admin is delayed rather than locked out".
> Ticket 09 §R's NIST cap removed that, and ticket 28 made its replacement a planned outage. Current premise set:
> the **lockout** path is closed by auto-expiry (password ladder 20/40/60, TOTP tier 1 at 20 minutes); the
> **everyday** cases — forgotten password, single-account cap, lost phone — are closed in-app **only when a second
> `authenticable` admin exists**, which is why seeding one is **conditional on a second enrolled admin being invited
> before go-live**; with **no other `authenticable` admin** — sole admin, targeted cap on every admin, zero admins —
> the only route is ticket 28's runner, a planned outage, pass-with-note pending the first rehearsal. A second
> *seeded* admin would change none of that (a targeted cap takes ≈14 h per account whatever the source count), so
> seeding stays at one. The two-admin rule remains a **removal-only guard**, never a bootstrap precondition — now
> because a gate would lock the survivor out, not because of auto-expiry.

### Wire contract

- **The public identifier is the `uuid`, never the primary key.** Ticket 03 already forces a `uuid` column
  distinct from the PK so audit logs can carry UUIDs. Returning the sequential PK would publish row counts,
  give `/api/admin/users/{id}` a guessable identifier, and create two identifier vocabularies where the audit
  trail uses one and the API the other. Ticket 12 owns the column; this ticket owns the wire contract.
- **Entities are never serialised.** Response records only, no `@JsonIgnore` as the primitive — it protects
  nothing against projections, error bodies or actuator. Two tests: an architecture test forbidding entity
  types in controller return signatures, and an API-wide assertion that `passwordHash`, `totpSecret` and
  `emailHmac` never appear in any response body, error bodies included.
- **List bounding:** default page size 20, **hard maximum 100**, `sort` restricted to an allowlist
  (`username`, `createdAt`, `enabled`, `role`) because an open sort parameter is its own injection and DoS
  surface. Every list read emits `adminUserListed` with `user.target.count`. This is the right minimisation
  control for the PDPA tension, better than masking, and it costs no PRD criterion — it converts an
  unmonitored bulk export into a bounded, recorded one.
- **Admin list fields** exactly as Story 8 — `username`, `email`, `role`, `enabled`, `createdAt` — plus `uuid`.
  Email is returned: the PRD is binding on features, the caller is privileged and factor-verified, and an admin
  who can trigger a reset reaches the address anyway.
- **`GET /api/profile`** returns `uuid`, `username`, `email`, `role`, `createdAt`, `forcePasswordChange`, and
  ticket 06's `factors: { held, required, enrolled }`. On the forced-change allowlist, reachable with
  password-only authentication and no factor, `Cache-Control: no-store`. Excluded: `accountNonLocked`,
  `failedLoginAttempts`, `lockedUntil`, per ticket 06's Q23a. `enabled` is omitted as **constant-true by
  construction** — disable kills sessions and login checks the flag, so any caller reading this endpoint is
  enabled by definition. Stated that way deliberately: grouping it with the lock fields implies a leak that
  does not exist.
- **Path resolved:** `/api/profile`. `/currentUser` is what the standalone corpus assumes (the forced-change
  filter allowlist names it) but no file implements; `/api/v1/profile` is what the shared recipe implements,
  with a projection declaring `getFirstName()`/`getLastName()` against an entity that has only `fullName`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-016, T-ADM-008. Amend the table by ID, not this list.*

### Unlock

Manual admin unlock exists (Q15, "admin manual unlock with mandatory reason"), clears `failedLoginAttempts`
and `lockedUntil`, does not invalidate sessions, cannot target the actor.

**The reason is a closed enum** — `USER_REQUEST | FALSE_POSITIVE | PASSWORD_RESET_COMPLETED | OTHER` — carried
as `user.target.unlockReason`, with **no free-text field at all**. Free text would write cleartext emails into
an audit stream where ticket 03 bans exactly that, and newlines in it would corrupt the "NDJSON to stdout, one
event per line" Enforced Constraint. An enum is also queryable, which free text in a log never is. Q15 offers
a "mandatory reason" option and no column, row or field for it exists anywhere in the corpus; keeping it in
the event rather than a column means ticket 12's table set is unchanged.

**Two asymmetries to record.** This is the **third** custom log field after `user.target.id` and
`user.target.roles` — and ticket 03's deferral item C8 raises a schema amendment for the first only, though
the second is used in its own worked example and is equally absent from `Log_Schema.md`. The amendment package
owes three fields; C8 names one. Second: ticket 03 concluded the application is **not** responsible for the
90-day retention TTL at all — it is the central platform's index lifecycle policy — so the accountability
record for an unlock has a lifetime set by a system nobody on this project configures, while the tombstone it
relates to is indefinite. *Consolidated into the register (ticket 33): R-AUD-004. Amend the table by ID, not this list.*

### Population declaration — closing ticket 01's last open item

All six controls that ticket 01 said hinged on this, resolved by declaration:

- **ac-12** (SSO for internal services) — N/A. End users are external and self-registering; administrators are
  application-local accounts, external for this build. *Consolidated into the register (ticket 33): R-ADM-003. Amend the table by ID, not this list.*
- **ac-8** (automated account lifecycle) — N/A on the same basis; our manual admin CRUD is appropriate. *Consolidated into the register (ticket 33): R-ADM-004. Amend the table by ID, not this list.*
- **ac-7** — **binds**, and is satisfied here: provisioning and deprovisioning are the create / disable /
  delete endpoints specified above, with the audit trail. *Consolidated into the register (ticket 33): R-ADM-005. Amend the table by ID, not this list.*
- **dp-8** (classification labels) — **binds regardless of population** and remains ticket 14's; the
  declaration does not excuse it. *Consolidated into the register (ticket 33): R-FE-002. Amend the table by ID, not this list.*
- **lm-18** — N/A, declaration-dependent. *Consolidated into the register (ticket 33): R-FE-003. Amend the table by ID, not this list.*
- **st-3** — N/A, declaration-dependent. *Consolidated into the register (ticket 33): R-FE-004. Amend the table by ID, not this list.*

**Reopening trigger, recorded:** if this is ever deployed where administrators are internal officers, ac-12
binds the admin login, the admin authentication design on this map is superseded by the SSO standard, and that
is a new effort rather than a patch. Compensating control meanwhile is the TOTP gate ac-2 wanted anyway. *Consolidated into the register (ticket 33): R-ADM-003, R-ADM-006. Amend the table by ID, not this list.*

### Premises of this ticket that were wrong

- **The list-users endpoint does not exist in the corpus.** No `@RepositoryRestResource`, no projection, no
  pagination, no `@JsonIgnore` on `passwordHash`. As printed, an exported `users` repository serialises the
  hash. Story 8's "never password hashes" is ours to build, not adopt.
- **Neither do the dedicated enable/disable or unlock endpoints.** All four are prose mandates with no path,
  method or payload, and locking has *no* permitted route at all since the generic path rejects it.
- **`ImmutableSecurityHandler` is inert here**, not merely narrow in scope.
- **The RBAC recipe does not settle the matrix**, because as printed it neither binds nor compiles.

### Standards and corpus defects found (seven through twelve on this map)

7. `createUser` checks tombstones for username but **not email** — the recovery-channel hijack above. *Consolidated into the register (ticket 33): R-STD-028. Amend the table by ID, not this list.*
8. The two prescribed forced-change allowlists are **mutually incompatible**, and neither is implementable
   alongside session-bound header-only CSRF. *Consolidated into the register (ticket 33): R-STD-029. Amend the table by ID, not this list.*
9. `updateUser` **permits self-disable**, contradicting Decision Logic §120 and PRD Story 9. *Consolidated into the register (ticket 33): R-STD-030. Amend the table by ID, not this list.*
10. §3.5:384's "or others" **flatly contradicts** the admin recipe's own `existing.setRoles(request.roles())`. *Consolidated into the register (ticket 33): R-STD-031. Amend the table by ID, not this list.*
11. The RBAC `url-guards` block **does not bind and does not compile**; `hasRole('USERS_*')` guards deny
    universally against the same recipe's unprefixed authorities. *Consolidated into the register (ticket 33): R-STD-032. Amend the table by ID, not this list.*
12. Three conventions for one resource (`SELF_READ` / `USERS_*` / `USER_READ`), never reconciled. *Consolidated into the register (ticket 33): R-STD-033. Amend the table by ID, not this list.*

Compile-level also: `Set.of(roleRepository.findByName("USER"))` over an `Optional`-returning finder;
`Paths.get(baseUrl, "/csrf")` building URL paths with the OS separator; `PasswordChangeFilter` calling
`log.atWarn()` with no `@Slf4j`; the batch cap refusing to choose between 413 and 400 where the standard
prescribes 400. *Consolidated into the register (ticket 33): R-STD-034. Amend the table by ID, not this list.*

### ADRs owed (15)

1. Role model: two roles, single-role-per-user, YAML source of truth plus Flyway-seeded read-only lookup table
   with FK and a startup validator; no privileges, no hierarchy, no synchroniser. *Consolidated into the ADR routing (ticket 34): ADR-042. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-DATA-003. Amend the table by ID, not this list.*
2. Authority naming: `ROLE_*` with `hasRole`, rejecting both corpus conventions. *Consolidated into the ADR routing (ticket 34): REJ-023. Amend by ID, not this list.*
3. Authorization matrix adopted narrowly with whitelist-first ordering; doubles as ac-4's baseline, scheduled *Consolidated into the register (ticket 33): R-STD-033. Amend the table by ID, not this list.*
   revoke deferred. *Consolidated into the ADR routing (ticket 34): ADR-043. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-001. Amend the table by ID, not this list.*
4. Path scheme `/api/admin/**` in controller mappings; `spring.mvc.servlet.path` prohibited (CVE-2026-22753). *Consolidated into the ADR routing (ticket 34): REJ-024. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-001, R-STD-014. Amend the table by ID, not this list.*
5. Story 10 permitted; §3.5:384 read as barring self-assignment only. *Consolidated into the ADR routing (ticket 34): REJ-025. Amend by ID, not this list.*
6. Batch password reset declined; `BATCH_TOO_LARGE` removed from ticket 06's enum. *Consolidated into the ADR routing (ticket 34): REJ-026. Amend by ID, not this list.*
7. Tombstone: HMAC email, plaintext username, indefinite retention, both reuses blocked, PDPA framing,
   pseudonymisation-not-anonymisation limit stated. *Consolidated into the ADR routing (ticket 34): ADR-044. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-002. Amend the table by ID, not this list.*
8. Canonicalisation rule (NFC, trim, lowercase; no dot/tag folding), shared across live table and tombstone;
   H2 has no expression indexes, so this is unavoidable rather than a cost of hashing. *Consolidated into the ADR routing (ticket 34): ADR-045. Amend by ID, not this list.*
9. Username rejects `@`. *Consolidated into the ADR routing (ticket 34): REJ-027. Amend by ID, not this list.*
10. Admin-created users immediately active; reason is the stubbed transport; credential-leak cost acknowledged;
    the NIST temporary-credential claim explicitly not relied on. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*
11. Lazy 30-day forced-change expiry at login instead of a reaper, behind a generic 401. *Consolidated into the ADR routing (ticket 34): ADR-046. Amend by ID, not this list.*
12. Bootstrap: refresh-phase validated properties plus runner seeding; Flyway rejected on checksum
    immutability, **not** on dependency-injection grounds; one admin seeded, **conditional on a second enrolled *Consolidated into the register (ticket 33): R-DATA-001. Amend the table by ID, not this list.*
    admin being invited before go-live** (amended by ticket 30). *Consolidated into the ADR routing (ticket 34): ADR-047. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-007. Amend the table by ID, not this list.*
13. Two-admin invariant as a removal-only guard under a pessimistic row lock **on disable, demote and delete;
    factor reset exempt from the count** (amended by ticket 30). Recovery in three routes keyed on the single
    `authenticable` predicate: auto-expiry for lockout; in-app reset when another `authenticable` admin exists
    (waiting on auto-expiry if that admin is locked out); ticket 28's runner, as a planned outage pending first
    rehearsal, when none does. Full wording: ticket 30 §4. *Consolidated into the ADR routing (ticket 34): ADR-048. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-008. Amend the table by ID, not this list.*
14. Unlock reason as a closed enum in the audit event, not a column. *Consolidated into the ADR routing (ticket 34): REJ-028. Amend by ID, not this list.*
15. Population declaration with its reopening trigger. *Consolidated into the ADR routing (ticket 34): REJ-029. Amend by ID, not this list.*

Glossary terms for `CONTEXT.md`: **Deleted-user tombstone**, **Canonical identifier**, **Forced-change
credential**, **Enrolled admin**.

### Handoffs

- **09:** confirm lockout auto-expiry at 20 minutes — the sole-admin recovery story now depends on it.
  Also inherit [CVE-2026-22746](https://www.sentinelone.com/vulnerability-database/cve-2026-22746/): the
  framework timing defence was bypassable for disabled, expired and locked accounts, which extends ticket 06's
  rule from "no pre-auth branch on account existence" to "no pre-auth branch on account **state**".
- **10:** admin-initiated reset issuance is yours; the canonicalisation rule and the username format are fixed
  here and must be reused, not re-derived.
- **12:** `roles` lookup table and FK; `credentialIssuedAt`; tombstone columns with `emailHmac`; the `uuid`
  column as the public identifier; no reason column for unlock.
- **13:** the per-endpoint audit table above; `user.target.count` and `user.target.unlockReason` as new fields;
  C8's amendment package owes three fields, not one.
- **14:** `/api/profile` shape, `factors`, the admin list's page cap, and dp-8 remaining yours.
- **17:** 15 ADRs; the six-control declaration; the deferred ac-4 revoke half. *Consolidated into the register (ticket 33): R-ADM-001. Amend the table by ID, not this list.*
- **24:** third key — the tombstone HMAC key. Own property, **must not share the TOTP key** (which rotates
  yearly by design), **cannot rotate at all**, and its absence must be fail-fast because the failure mode is
  silent fail-open on reuse blocking.
- **25:** two named fresh-install lockout scenarios, and the promote-before-demote consequence of the
  two-admin invariant.

### Amendments made to already-resolved tickets

- **03** — C8's schema amendment covers `user.target.id` only; `user.target.roles` is equally an extension. *Consolidated into the ADR routing (ticket 34): REJ-044. Amend by ID, not this list.*
- **05** — the BCrypt encode/verify asymmetry belongs to CVE-2025-22228's fix, not CVE-2025-22234, which is
  the timing regression that fix caused. Conclusion unchanged.
- **06** — `BATCH_TOO_LARGE` removed; the closed enum is 13 codes. *Consolidated into the ADR routing (ticket 34): spec §Error contract. Amend by ID, not this list.*
- **19** — the prohibition on `Encryptors.stronger()` is over-broad: CVE-2026-47842 is scoped to
  `AesBytesEncryptor` with the two-argument constructor or a null IV generator in CBC mode. The rule is
  "use `AesGcmBytesEncryptor`, never the two-arg `AesBytesEncryptor`". *Consolidated into the ADR routing (ticket 34): ADR-022 (attached amendment). Amend by ID, not this list.*
- **07 / 02** — the map's NIST claim is **vindicated**, against a plausible correction. The final
  SP 800-63B-4 §3.1.1.1 requires single-factor passwords to be a minimum of 15 characters as a `SHALL`, with
  the 8-character floor applying only to passwords used within MFA; the widely-quoted "SHALL 8 / SHOULD 15"
  is draft-era text. The ADR should say we were below a `SHALL`. Second-order finding nobody had noticed:
  because ticket 19 put TOTP on the admin surface only, it is the **regular USER population** — single-factor
  by design — that forces 15, not the admins, so the floor cannot be relaxed by adding MFA unless MFA covers
  everyone, which is out of scope. *Consolidated into the ADR routing (ticket 34): ADR-002 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 09 (lockout and dual rate limiting)

**Your handoff is answered: lockout auto-expiry is confirmed at 20 minutes with automatic lift.** Q15's
"Permanent (admin unlock required)" was rejected on the record, so your one-seeded-admin bootstrap holds
unchanged. Ticket 09 adds a third independent reason the auto-lift cannot later be removed, beyond your
bootstrap and its own NIST deviation: **WSTG-ATHN-03 attaches a precondition to manual administrator unlock**
— the administrator should also have a recovery method in case their own account gets locked — and warns that
admin-only unlock can itself become the DoS. So your unlock endpoint is WSTG's tier 3 *backed by tier 1*, and
the two controls are load-bearing for each other.

**Your CVE line is stale and should be corrected.** [CVE-2026-22746](https://spring.io/security/cve-2026-22746):
affected 7.0.0–7.0.4, plus the 6.5.x, 6.4.x, 6.3.x, 5.8.x and 5.7.x lines; **fixed in 7.0.5**; CVSS 3.1 **3.7
LOW**, CWE-208. The map pins Spring Security **7.1.1**, so we are **patched**, not "patched above our pinned
version" — the ticket currently implies 7.0.x was the only affected line, which is also wrong. Keep the
assertion test: the risk was never the version, it is someone setting
`alwaysPerformAdditionalChecksOnUser` to `false`, which looks like a free optimisation and reintroduces the
exact bypass. Note the declaring class is **`AbstractUserDetailsAuthenticationProvider`**; the advisory names
it under `DaoAuthenticationProvider` by inheritance. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-005. Amend the table by ID, not this list.*

**Your generic-update lock guard changes shape, because `accountNonLocked` is no longer a column.** Ticket 09
derives `isAccountNonLocked()` from `locked_until` (`null || <= now`) rather than storing it, since two sources
of truth for one fact would drift and lazy derivation is what makes auto-lift free. The admin recipe's guard
reads `existing.getAccountNonLocked()` to detect a locking attempt; with no column to read, the guard becomes
**"reject any attempt to set lock fields at all"** on the generic update path. Your conclusion that locking has
no permitted route is unchanged and now easier to enforce.

**Admin password reset does not clear the lock.** Ticket 09 followed standard L131 over the admin recipe's
`ResetPasswordCommand` (L440-442, made mandatory by its own verification list at L731), because the recipe's
version is an undocumented unlock path that bypasses your audited endpoint and its mandatory
`unlockReason` enum. So `failed_login_attempts` is **not** reset and the lock is **not** lifted by a reset —
which means an admin resolving a locked-and-forgotten-password case performs two actions, reset then unlock,
with `PASSWORD_RESET_COMPLETED` already in your enum for exactly that. The friction is real and goes into
ticket 25's handover, or support reads a lingering lock as a failed reset.

**Your row-lock idiom is now a codebase convention.** Ticket 09 reuses it for the failed-login counter (single
row by primary key, so no H2 aggregate restriction applies). Pinned ordering across all three call sites:
**user rows before session rows, always** — otherwise the deadlock only appears under load an attacker
supplies. H2's `LOCK_TIMEOUT` defaults to 1000 ms and is now pinned explicitly on the JDBC URL.

## Amendment from ticket 10 (credential flows)

**Admin-create becomes an invite token.** The reason this ticket chose a 20-character generated password — that
the stubbed transport cannot deliver a link, so "an undeliverable link is a bricked account" — is retired by the
same reasoning ticket 10 accepted for admin-reset: with a stub, the admin reads the link exactly as they read
the generated password, and relays it the same way. Leaving the two paths inconsistent became the expensive
option, because it keeps both alive. ASVS **6.4.6 (L3)**: an administrator may initiate a reset but must not be
able to change or choose the user's password. Consequences here:

- `POST /api/admin/users` **drops its password field**. It creates a pending record and mints an `ACTIVATION`
  token, plaintext returned once. `USER_EXISTS` behaviour on duplicates and tombstones is unchanged.
- **Admin-create's forced-change branch goes away** — the user sets their own password at redemption, so there is
  nothing to force. `credentialIssuedAt` is **no longer stamped on admin create or admin reset**; for reset, the
  token's own 30-minute expiry is the deadline.
- **Forced-change cases reduce from four to two:** the bootstrap seed (operator-configured) and re-enable (the
  user's own prior hash, no new credential issued). `credentialIssuedAt` keeps its 30-day job for those two on
  this ticket's original grounds. Because no system-generated secret is relayed by a third party anywhere in the
  build any more, ASVS 6.4.1 (L1) is satisfied via its **short-lifetime** limb rather than its first-use limb,
  and the compliance mapping stops depending on which limb a reviewer tests.
- Admin-created users are created **unactivated**, so ticket 10's `enabled && activated_at != null` composition
  covers them with no special case, and admin-create converges with self-registration onto one
  pending-record-plus-token-plus-redemption path.

**The two-enrolled-admins invariant needs its predicate fixed, or the invite model silently defeats it.** Under
the old model an admin-created admin had a credential immediately and would be activated at once. Under the
invite model they are unactivated *and* unenrolled — so if `AdminActionGuard` counts admin rows, one real admin
plus one pending invite reads as two, and the real admin can demote themselves to **zero usable admins**,
defeating the invariant this ticket took a pessimistic row lock to protect. The predicate becomes
**`activated_at IS NOT NULL` and TOTP-enrolled, both.** The row-level `FOR UPDATE`-then-count-in-Java idiom is
unchanged.

Also amended:

- **`PATCH /api/profile/password`**, not `POST`. Ticket 09's spelling wins — it sits in a table of ten concrete
  routes and matches the corpus's own `PATCH /currentUser/changePassword`.
- **`PUT .../enabled` and `DELETE .../{uuid}` gain a side effect:** both invalidate all pending credential tokens
  of both types for the target. Without this a user disabled or deleted while holding a live token can still
  redeem it, and delete would leave a token pointing at a vanished row.
- **`POST .../{uuid}/password-reset` is the ninth admin endpoint**, token-based, `hasRole('ADMIN')` with TOTP
  re-verified within 10 minutes, and **no `actor ≠ subject` rule** — an admin resetting their own password is
  legitimate.
- **ASVS 6.3.2 (L1) is satisfied by the bootstrap decision here**, and more strongly than it asks: "credentials
  from the environment only … no default, no generate-and-log fallback", plus the refusal to re-seed when an
  ADMIN row exists but is disabled, which 6.3.2 does not reach. Recorded because a reviewer will look for it.
- The identifier canonicalisation helper and `[a-z0-9._-]{3,32}` username format are **reused unchanged** by
  ticket 10, with one negative assertion added: the helper is never applied to a password field. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-007. Amend the table by ID, not this list.*

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**Your unlock endpoint clears both axes, your role hierarchy exclusion has a new consequence, and your bootstrap
sequence is vindicated by a NIST SHALL.**

**1. `POST /api/admin/users/{uuid}/unlock` clears the TOTP tier-1 lock in addition to the three password columns.**
One endpoint for both axes rather than a second endpoint; it does **not** clear ticket 23's tier-2 cumulative
disable, which is cleared only by `DELETE /api/admin/users/{uuid}/totp`. NIST's AAL constraint on resets is
satisfied by construction, since the unlocking admin's session holds the factor.

**2. `DELETE /api/admin/users/{uuid}/totp` deletes the `PENDING_TOTP` row too**, and the reason is a live attack
rather than tidiness: a pending secret left by someone who reached provisioning with a hijacked password-only
session otherwise survives the reset, and the target's next enrolment attempt can confirm **the attacker's secret**.
Your three stated consequences hold unchanged, including that reset must kill the target's sessions.

**3. Your role-hierarchy exclusion has an unexpected dependent.** Ticket 23 abandons
`AuthorizationManagerFactories` (its composition evaluates the factor before `hasRole`, which leaks state to
non-admins and breaks 401 for anonymous), so the admin rules are built from a hand-constructed
`AuthorityAuthorizationManager`, which defaults to `NullRoleHierarchy` and does **not** pick up a bean the way the
DSL's `hasRole()` would. Because you ruled the hierarchy out of scope this is correct rather than a regression — but
it is now load-bearing, so ticket 23 adds a one-line assertion that no `RoleHierarchy` bean exists. If the hierarchy
is ever reinstated, the admin rules silently stop honouring it unless `setRoleHierarchy` is called. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-017. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-042 (attached amendment). Amend by ID, not this list.*

**4. The factor rules compose *with* your authorization matrix, not beside it.** The matrix stays the single source
of the role→path mapping; the factor requirement is applied to the manager the matrix produces, whitelist first,
guards second, `anyRequest().denyAll()` last. Method-aware matchers split `GET /api/admin/**` from the mutations.
`spring.mvc.servlet.path` remains prohibited and `PathPatternRequestMatcher` remains the only matcher.

**5. `/api/mfa/**` is a new matrix row: `ROLE_ADMIN`, no factor.** It sits deliberately outside `/api/admin/**` so an
unenrolled admin can reach it, and deliberately off your five-path forced-change allowlist, so your fresh-deploy
sequence is unchanged: seed → login → change password → enrol → factor granted → surface opens. The forced-change
filter must run **before** ticket 23's factor filters, which share its `AuthorizationFilter` anchor.

**6. Your bootstrap shape is endorsed by NIST SP 800-63B-4 §4.1.2.1**, which is worth recording because it forecloses
a control someone will otherwise propose. Binding an additional authenticator SHALL require authentication at "either
the maximum AAL currently available in the subscriber account or the maximum AAL at which the new authenticator will
be used, **whichever is lower**" — and for a first enrolment the account's available maximum is password-only. So
**password-only enrolment is explicitly compliant**, and the first-enroller race (whoever reaches provisioning with
the seed password first binds their own authenticator) is a residual to record, not a deviation to mitigate. *Consolidated into the register (ticket 33): R-ADM-016. Amend the table by ID, not this list.*

Also recorded: pre-enrolment the admin surface is **closed, not password-protected** — nobody is enrolled, so the
factor check denies — which means a "refuse to serve `/api/admin/**` until an admin is enrolled" control is a no-op,
and seed-disabled-then-activate is **not implementable**, because enabling a user goes through
`PUT /api/admin/users/{uuid}/enabled` and requires an enrolled admin. ASVS **6.3.2 (L1)** does not bite: it names
default accounts with default credentials, and yours has an operator-supplied username and no default credential.

**7. Your invariant is unchanged and was not re-decided.** `AdminActionGuard`, the pessimistic lock over admin rows
counted in Java because H2 rejects `FOR UPDATE` on aggregates, and the `activated_at IS NOT NULL` **and**
TOTP-enrolled predicate all stand as the single enforcement point, including for factor reset.

---

## Amendment from ticket 12 (data model reconciliation)

Six changes, one of which contradicts a principle this ticket chose deliberately.

**1. The sequential primary key is withdrawn, and this ticket's wire-contract rule becomes vacuous rather than
enforced.** Ticket 12 makes the UUID the **sole** primary key on `users`, named `id`, application-generated via
`@UuidGenerator`. So "the public identifier is the `uuid`, never the primary key — returning the sequential PK would
publish row counts" describes a leak that **no longer has a mechanism**: there is one identifier and no second value to
return by mistake. Keep the API-wide test, but its justification changes from enforcement to regression cover. Two of
the four supporting arguments are new and worth carrying: identity-column syntax (`GENERATED BY DEFAULT AS IDENTITY`
against `AUTO_INCREMENT`) is itself a three-way portability break, so deleting the bigint *removes* a seam; and
`@UuidGenerator` assigns **before execution**, so the id is readable after `persist()` without a flush, which a
`BIGINT IDENTITY` cannot offer. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-008. Amend the table by ID, not this list.*

**2. Neither tombstone identifier can be a foreign key, and that is forced by this ticket's own delete semantics.**
Line 277's "removed from the **active table**" plus the two-repository reuse check means the `users` row is genuinely
deleted. So a foreign key on the tombstone's own identifier would be violated the moment it was written, and one on
`deletedById` would break — or cascade away an accountability record — as soon as the deleting administrator was
themselves deleted. Both columns are bare `UUID`s with a DDL comment, or a later reviewer files the absent constraints
as a defect. The field `uuid` is **renamed `user_id`**, since it is the deleted user's own primary key and the name
should say so. *Consolidated into the ADR routing (ticket 34): ADR-044 (attached amendment). Amend by ID, not this list.*

**3. Password-history purging on delete is forced, not chosen.** A foreign key to `users` must either block the delete
or cascade, so `ON DELETE CASCADE` is the single mechanism across `password_history`, `credential_tokens`,
`totp_user_details` and `pending_totp`. **Sessions deliberately do not cascade** — `SPRING_SESSION.PRINCIPAL_NAME` is a
`VARCHAR(100)` and not a foreign key — so every revocation path in the endpoint table above still needs its explicit
`SpringSessionBackedSessionRegistry` call. Worth stating, because "we cascade the children" invites the assumption that
sessions are children.

**4. The guard's lock set widens to two tables on all four paths, and its guarantee is narrower than it looks.** TOTP
enrolment is derived from `totp_user_details` row existence rather than a flag on `users` (ticket 09's
`account_non_locked` precedent), so the invariant reads two tables and the guard takes `FOR UPDATE` on both, in the
order `users` → `totp_user_details`, on **all four** mutating paths — not only the ones that name the TOTP table. The
delete path is why: **the cascade removes a `totp_user_details` row, so deletion changes the enrolled-admin count
through a table its own statement never mentions.** Both selects fetch rows and count in Java, per this ticket's H2
`FOR UPDATE` finding. And the honest limit, which must be written down: an unenrolled admin has **no** row, and H2 has
no gap or predicate locking, so the lock set cannot cover a concurrent insert. The invariant survives only because an
insert can only **raise** the count, while every mutation that can lower it acts on rows that exist and therefore get
locked. **The guard is decrement-safe, not phantom-safe.** *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-015. Amend the table by ID, not this list.*

**5. The canonicalisation principle is overridden on MySQL, and it is entry six of ticket 12's seam register.** This
ticket chose NFC with **no dot-folding and no `+tag` stripping**, on the principle that one provider's conventions must
not be imposed on all of them. MySQL's default collation is `utf8mb4_0900_ai_ci` — case- **and accent**-insensitive —
and **NFC does not fold accents**, so under MySQL `josé@…` and `jose@…` collide where on H2 they do not. The uniqueness
rule there would be *stricter than the canonicalisation contract states*, and a registration that succeeds on H2 would
be rejected. Asserted and never executed, like everything in that register, but it is a folding this ticket explicitly
declined being reintroduced by a default nobody wrote. *Consolidated into the ADR routing (ticket 34): ADR-045 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-DATA-009. Amend the table by ID, not this list.*

**6. The canonical branch this ticket left half-open is closed, and the two identifiers diverge.** The canonical value
**replaces** the stored value — one `username` column, one `email` column, no second column — which is what lets
`ux_users_email` be named for the column it is actually on. But since the username charset `[a-z0-9._-]{3,32}` makes
canonicalising a *valid* username a no-op, the behaviours differ: **username canonicalisation rejects**, because silent
transformation would mean the account's name is not the one the user typed and it is the plaintext username that lands
in the tombstone forever; **email canonicalisation transforms**, with the ADR owing the failure mode — a user at a
case-sensitive provider cannot receive reset mail, and reset is email's only consumer. *Consolidated into the ADR routing (ticket 34): ADR-045 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-014. Amend the table by ID, not this list.*

Confirmed unchanged: the `roles` lookup table with `name VARCHAR(20)` as its primary key so `users.role`'s foreign key
carries the readable value and needs no join; Flyway seeding the two rows in a **versioned** migration, since a
repeatable one re-runs on checksum change and not on data drift and so would be no more self-healing; the admin
bootstrap staying in a runner on checksum-immutability grounds; **no unlock-reason column**; and no dev seed accounts
of any kind, making `roles` the only seeded data in the system.

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — one reopen trigger, ADR 13 narrowed, and your invariant demoted

**Your bootstrap decision survives. Three other things here do not, and the first is a defect rather than an
amendment.**

### 1. Reopen trigger: the 30-day expiry's audit reason is a password oracle

`credentialIssuedAt` is checked via **`isCredentialsNonExpired()`**, which Spring evaluates in
`DefaultPostAuthenticationChecks` — reached **only** when `additionalAuthenticationChecks` already matched. So
`CredentialsExpiredException` and its audit reason fire **if and only if the submitted password was correct**.
With ticket 06 routing the internal failure reason to the audit log, ticket 13 putting `user.id` on resolved
failure rows, and ticket 10 recording the log leak as total for ordinary users, a log reader gets a clean
password-confirmation oracle on any account whose credential has aged past 30 days. Ticket 06's wire-uniformity
rule does not reach it: the channel is the audit stream, not the response. Verified at
[§18 of the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md).

**Ticket 13's "the enumeration protection was already spent" argument does not cover this, and does not merely
omit it — its own list of already-routed reasons contains `grace-expired`.** So the reason was swept into an
*enumeration* argument when what it leaks is strictly worse: password correctness, not account state. The general
rule, for anything added later: **a status check whose audit reason must not confirm a guessed password belongs in
`preAuthenticationChecks`**; the post-auth slot is only safe for a reason already implied by success. Ticket 09
§R.4 hosts the NIST cap on that rule. *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*

### 2. ADR 13 narrowed, not reversed — and the wording ticket 09 gave you is amended

Your ADR 13 reads "lockout auto-expiry closes the sole-admin lockout path", resting on ticket 09's handoff
"lockout auto-expiry at 20 minutes **confirmed**". Two changes:

- **The duration is no longer flat 20 minutes.** It escalates **20 / 40 / 60** as an account approaches the NIST
  cap. Your argument survives unchanged in substance — an hour is still *delayed* rather than locked out — but the
  quotation you rest on is no longer accurate as written.
- **The cap is a third fresh-install path, and auto-expiry cannot close it.** Your sequence is seed → login →
  forced change → enrol → factor granted; before it completes the sole admin has no TOTP, no second admin, and —
  because ticket 13 confines the stubbed reset link to a `dev`-only logger — **no reset channel outside `dev`**.
  So a day-one cap on the seeded admin is unrecoverable over HTTP entirely, and it is reachable by roughly 100
  unauthenticated requests against a username ticket 10's `USERNAME_UNAVAILABLE` makes *confirmable*.

**Your "one admin, not two" survives**, because ticket 09 §R.3 closes that path with an operator-invoked rebinding
runner rather than a second seeded credential. So your credential-hygiene argument never has to lose to an
availability one. **ADR 13's new wording: auto-expiry closes the *lockout* path; the operator rebinding entrypoint
closes the *cap* path.**

### 3. The two-admin invariant is monitorable, not enforceable, against this

It counts **enrolment**, not authenticability, so two password-cap-disabled admins still read as two enrolled
admins while neither can log in — durably, until rebinding, where tier-1 lockout only did it transiently. And your
own finding is why no guard can fix it: **"the guard is decrement-safe, not phantom-safe"**, and it acts on
mutation paths, whereas the cap arrives through the **authentication** path, which no guard observes. So this is
handed to ticket 21 as a **zero-authenticable-admins signal** rather than a predicate change here.

**One `authenticable` predicate, defined once, two readers.** It spans five terms across two tables — enabled,
activated, not password-disabled, TOTP row present, not tier-2 disabled. Your guard reads the first three today;
ticket 21's signal needs all five. Two copies drift silently and the monitoring quietly stops matching the guard. *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.*

### 4. A third forced-change case, and `credentialIssuedAt` is not stamped on it

Ticket 10 took your forced-change cases from four to two. Ticket 09 §R.9 adds a third: **a tier-2 TOTP disable is
treated as a credential-compromise signal that forces password rebinding at next login.** Hosted on your
forced-change **flag**, deliberately — not on the 30-day expiry, which would rebuild §1's oracle. The flag is
evaluated *after* a successful authentication, which is the one thing the post-success slot is safe for.

**`credentialIssuedAt` must not be stamped on it**, following the same call you already made for admin create and
admin reset. Stamp it and the 30-day clock runs while the admin waits for another admin to re-enrol their factor,
so a six-week wait produces a generic 401 on top of the disable and needs a second intervention to clear.

Honest framing for the ADR: the consequence is **deferred, not proportionate**. A tier-2-disabled admin cannot
complete stage 2, so the forced change fires only once someone re-enrols them — it must not be described as
session-preserving relief for someone already locked out. *Consolidated into the ADR routing (ticket 34): ADR-046 (attached amendment). Amend by ID, not this list.*

### 5. A reserved-name denylist, justified on availability and explicitly not on 6.3.2

One reserved-name set, two readers: your refresh-phase bootstrap validator and ticket 10's registration username
validator. Reserving only at bootstrap leaves `administrator` available to the next self-registrant.

**It cannot be justified on ASVS 6.3.2 (L1) and must not be.** Your line 693 wording is correct and is the one to
cite if anyone raises it — 6.3.2 "names default accounts with default credentials, and yours has an
operator-supplied username and no default credential". The denylist is an **availability control against ticket 09
§R.2's targeting**, and explicitly **not** an anti-enumeration measure, since `USERNAME_UNAVAILABLE` keeps any name
confirmable either way. Your 6.3.2 rows are untouched. *Consolidated into the ADR routing (ticket 34): ADR-047 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 15 (threat model) — one matrix row, one layering asymmetry, one invariant demoted again

Three items. The third continues a demotion your own §R.3 started.

### 1. `GET /api/hello` needs a matrix row — TM-05

PRD Story 5's only endpoint has **no authorization-matrix row anywhere on this map**. It appears three times in
the whole `issues/` tree and never as a decision: `03:239` and `13:254-257` both say do not log it, and your own
`11:192` cites it only to fix the unversioned base path. It also has no rate-limit row, which is
[ticket 26](26-unbudgeted-routes-and-audit-volume.md)'s.

It **fails closed**, because `anyRequest().denyAll()` is last — so this is a completeness defect rather than a
hole, and it is recorded because it is the second endpoint to fall between the three registries after ticket 14
found `GET /api/profile` audited on every call and throttled on none. **Amendment: `GET /api/hello`,
`ROLE_USER`, no factor, on the matrix.** Not on the forced-change allowlist — a user under forced change has no
business reading the protected greeting, and the five-path list stays exhaustive. *Consolidated into the ADR routing (ticket 34): ADR-043 (attached amendment). Amend by ID, not this list.*

### 2. `@PreAuthorize` is defence in depth for the role and not for the factor — TM-04

Your matrix is applied in `authorizeHttpRequests` **and** as `@PreAuthorize` on the service methods, which you
call defence in depth. Ticket 23 then abandoned `@EnableMultiFactorAuthentication` because it would AND the
factor into every rule in *both* web and method security — correct, for the reason given. The composition is
that the **role gate has two layers and the factor gate has one**, and the single layer is the request-matcher
layer, including the method-aware GET-versus-mutation split that decides whether a caller needs an 8-hour type
guard or a 10-minute re-verification.

So a mis-written admin matcher removes the factor with nothing behind it, while the same mistake on the role is
caught. No design change is recommended; the remedy is an assertion **enumerated from this ticket's matrix**
rather than hand-written, which ticket 16 now owes. Recorded on both tickets because neither owns it alone. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-002. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-MFA-003. Amend the table by ID, not this list.*

### 3. The two-enrolled-admins invariant now has three non-mutation channels — TM-14

Your §R.3 demoted it from enforceable to monitorable against the NIST cap, on the argument that the cap
"arrives through the **authentication** path, which no guard observes", and that your own finding — "the guard
is decrement-safe, not phantom-safe" — is why no guard can fix it. That reasoning generalises further than it
was applied. There are now **three** channels that change the enrolled-admin count without passing a mutation
path:

1. **`ON DELETE CASCADE`** removing a `totp_user_details` row — ticket 12's finding, "deletion changes the
   enrolled-admin count through a table its own statement never names";
2. **the NIST cap** — your §R.3;
3. **the two out-of-band channels** — ticket 09 §R.3's rebinding runner at scope `totp`/`both`, and ticket 25
   §6's break-glass path, which clears a tier-2 disable *and* a TOTP enrolment with no authenticated session at
   all. These are outside any transaction the guard could join, not merely outside the paths it guards.

**Amendment: state the invariant as guarded on mutation paths and *monitored* everywhere.** No guard change —
break-glass must be able to take the count to zero, that is its purpose — but your single `authenticable`
predicate with two readers must have its second reader (ticket 21's zero-authenticable-admins signal) observe
all three channels, or the monitoring silently stops matching the guard in exactly the incident the signal
exists to catch. Amended onto ticket 21 as well.

One thing this does **not** change: your "one admin, not two" decision. Ticket 09 §R.3's runner is what closes
that path, and TM-12 is about the runner's *output channel*, not its existence — see
[ticket 28](28-out-of-band-privileged-channels.md). *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.*

### 4. Two findings against your admin surface that are accepted risks rather than changes

- **Any admin can take over any other admin's account** (TM-08). `POST /api/admin/users/{uuid}/password-reset`
  deliberately carries no `actor ≠ subject` rule and returns a plaintext token for any target;
  `DELETE /api/admin/users/{uuid}/totp` destroys a target's factor, bounded only by the two-enrolled-admins
  guard, so with three or more enrolled admins the composed path is open. You named self-disable and
  self-demotion; nobody named admin-on-admin takeover as a whole. It is inherent to a flat role model the PRD
  asks for, the detector is ticket 13's row 18 with reason `ADMIN_RESET` — which ticket 10 calls the sole
  detector of admin abuse — and it goes to ticket 17 as an accepted risk with that detector's 90-day horizon
  and ASVS 16.4.2 (L2) **F** stated beside it. *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*
- **The tombstone records no role** (TM-09). `deleted_users` holds `user_id`, `username`, `email_hmac`,
  `deleted_at`, `deleted_by_id` and is retained indefinitely; the audit row that says the deleted account was
  an ADMIN expires on a TTL ticket 03 established the application does not own. So past 90 days the permanent
  record can say an account existed and who deleted it, but not whether it was privileged — and IM8 **ac-7**
  binds deprovisioning. Accepted rather than fixed: adding the column widens indefinite retention of
  personal-adjacent data, which cuts against the PDPA argument that made the email an HMAC in the first place. *Consolidated into the register (ticket 33): R-ADM-010. Amend the table by ID, not this list.*

---

## Amendment from ticket 30 (sole-admin bootstrap premise)

[Ticket 30](30-sole-admin-bootstrap-premise.md) re-decided "one admin, not two" against the current premise set. The
premise paragraph (394–400) and ADRs 12 and 13 (513–516) are corrected **in place**. What else changes here:

1. **Seeding survives, conditionally.** One admin is seeded; the decision holds only because a second *enrolled*
   admin is invited before go-live (a handover item, deliberately not a gate). Pending invites do not count.
2. **`DELETE /api/admin/users/{uuid}/totp` is exempt from check 2 (≥ 2 enabled and enrolled).** Check 1
   (`actor ≠ subject`) stays. The endpoint table's "invariant (removes an enrolment)" at line 217 now reads
   "actor ≠ subject; **exempt from the two-admin count**" (edited in place). Reason: after a factor reset the subject restores the count alone
   (23:621–623); after disable or demote only another admin can. At exactly two admins the guard previously refused
   the lost-phone reset ticket 19 relied on. Disable, demote and delete keep the invariant unchanged.
3. **§R.3's "four mutating paths" become three** for the count; the `authenticable` predicate (816–818) is unchanged
   and is now the single key for ADR 13's routes.
4. **TM-08 framing at 905–908 is superseded.** "Bounded only by the two-enrolled-admins guard" no longer holds at
   exactly two: A can take B over completely (password reset plus factor reset, and A may be the one who re-enrols as
   B). Detectors: row 18 `ADMIN_RESET`, row 33 `totp-remove`; the authenticable-admins gauge if the deployer enables
   export. *Consolidated into the ADR routing (ticket 34): ADR-047 (attached amendment) / ADR-048 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*

---

## Amendment from ticket 16 (test plan)

- **Standard §5:424 (duplicate role definitions fail fast).** The check lives in this ticket's refresh-phase validator (11:155–157), beside the YAML-versus-table check. A duplicate role name in `app.security.roles` aborts refresh; it is not collapsed silently. Ticket 16 owns the test.
- **Standard §5:425 (role-definition mutation → 403).** The matrix gains an **explicit `denyAll()` matcher for role-definition paths** (`/api/admin/roles/**`, all methods), placed before the `/api/admin/**` guards. Without it, an ADMIN request there matches the admin guard and gets a 404/405 from MVC. So a USER or an ADMIN gets 403 `ACCESS_DENIED` literally, and the rule survives a future `/api/admin/**` wildcard handler. Anonymous callers get the entry point's 401, as everywhere.
  - **The test pins, owed to ticket 32 as T-IDs.** It covers every method on `/api/admin/roles/**`:
    - **anonymous → 401**;
    - **USER → 403** and **ADMIN → 403**, both with envelope `code` **`ACCESS_DENIED`**. The status alone is not enough.
    - **Mutating methods carry a valid CSRF token**, so the 403 comes from `denyAll()`, not `CsrfFilter`'s `CSRF_TOKEN_INVALID`.
    - **The ADMIN holds no TOTP factor**, so a 403 rather than the factor guard's 412/422 proves the matcher sits before the factor rule.
    - The fixture accounts have `forcePasswordChange` clear, so the forced-change filter's 403 `PASSWORD_CHANGE_REQUIRED` cannot mask the result.
- **Standard §5:426 (mapping change on next load).** This is stated rather than implied: the matrix binds once at refresh, so a restart is the only reload path. The test is a two-run restart-harness case with a changed matrix. *Consolidated into the ADR routing (ticket 34): ADR-043 (attached amendment). Amend by ID, not this list.*
- **Standard §5:473 is N/A by construction.** There is no generic update endpoint (11:209–218) and no lock route (11:483). `USERNAME_CHANGE_NOT_ALLOWED` is removed from ticket 06's enum. *Consolidated into the register (ticket 33): R-ADM-013. Amend the table by ID, not this list.*
- **Standard §5:485 (re-enable forces change).** Already decided at 11:229–230. Ticket 16 owns the test: re-enable, then the next request outside the allowlist gets 403 `PASSWORD_CHANGE_REQUIRED`.
- **Standard §5:479.** Ticket 16 adds a USER → `GET /api/admin/users/{uuid}` → 403 row. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-018, T-ADM-019, T-ADM-020, T-ADM-006, T-ADM-005, T-ADM-009. Amend the table by ID, not this list.*
