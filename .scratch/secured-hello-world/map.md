# Wayfinder Map: Secured Hello World Auth App

Label: `wayfinder:map`

## Destination

A decided, compliance-reviewed **plan** for the auth app described in `prd/assessment-prd.md` —
every security-load-bearing and hard-to-reverse decision resolved and written down, ready to hand
to `/to-spec` → `/to-tickets` → `/do-work`.

This map produces **no application code**. It is done when nothing is left to decide before
someone goes and builds it.

## Notes

**Domain:** React SPA (own origin) + Spring Boot REST API (own origin), cookie-based server-side
sessions, standalone username/password auth with account lockout, rate limiting, password reset,
and admin user management. Source of truth for scope: `prd/assessment-prd.md`.

**Governing standards (binding, read before deciding anything):**

- `App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Application_Standard.md`
  — governs almost every decision on this map.
- `App-Standards/Appfw-Logging-Standards/` — audit log schema and structured logging.
- `App-Standards/Appfw-User-Standards/Shared_Recipes/` and
  `.../User_Standalone/Standalone_User_Access_Control_Recipes/` — prescribed implementation recipes.
- IM8 + ARC via the `policies` skill. **This plan is formally assessed against IM8/ARC**, so those
  controls are early design constraints, not a late review.

**Conflict rule (settled):** where the PRD and the App Standard disagree, the **standard wins on
how a control behaves**; the **PRD wins on what features exist**. Every deviation from the PRD is
recorded as a **register row** keyed on the PRD story and acceptance criterion, naming what the PRD said, what we
did, why, and the residual. **It additionally gets an ADR only if it passes the ADR filter:** "would a
maintainer reading the code and the spec plausibly undo this?" *(Amended by ticket 17. This rule previously
required an ADR for every PRD deviation. Ticket 18 grades PRD-deviation completeness against the register, not
the ADR set, so a PRD deviation with a register row and no ADR is complete, not missing.)*

**Survival rule (settled in ticket 17):** `.scratch/` is deleted after `/to-spec`. What survives is the code,
`docs/` and `CONTEXT.md`. Nothing that survives may cite a ticket, a `NN:line`, a ticket-local ADR number or a
`.scratch/` path. Each artefact carries its own rationale and its primary-source citations, and cross-references
only surviving IDs (`ADR-…`, `R-…`, `T-…`). `threat-model/` moves to `docs/`. `research/`,
`test-plan/transcription/` and `deferral-register/inventory/` die with `.scratch/`.
*(Amended after ticket 18: **the spec is written to `docs/spec.md`, not to the tracker's default
`.scratch/<slug>/spec.md`.** The rule assumed the spec survives, but this repo's tracker is local markdown and would
have put the spec inside the directory being deleted. `docs/spec.md` follows the same citation rule as the rest of
`docs/`. It must carry three things that otherwise exist only in ticket 18: the gate list, the Low Risk and
population declarations, and the register and handover schema (REJ-075). The error contract's prose form is a build
deliverable (R-AUTH-003), not spec content. Delete `.scratch/` only after `docs/spec.md` is committed.)*

**Currency rule (settled):** the standard is authoritative but may be dated. Before adopting any
control from it, verify it is still current practice against primary sources (NIST SP 800-63B-4,
OWASP 2025 cheat sheets). Suspected stale items are listed in "Verify the App Standard's controls
are still current practice".

**Stack baseline (settled, no ticket):**

- Spring Boot 4.1.x on Java 21 (Temurin 21.0.9 installed), Maven 3.9.13. Java 17 is the Boot 4
  baseline; 21 is the installed LTS.
- Spring Session **JDBC** (not Redis, not plain Tomcat sessions) — required because password reset
  must invalidate all of a user's sessions, which needs principal-indexed lookup via
  `SpringSessionBackedSessionRegistry`.
- Flyway versioned SQL against **H2 only**, written vendor-neutral so it ports to Postgres/MySQL
  later. `ddl-auto: validate`. No Postgres, no Testcontainers.
- **BCrypt** password hashing (user decision; the standard permits it, the PRD mandates it), **cost 12
  behind `DelegatingPasswordEncoder`**, permitted band 15 characters to 72 bytes — settled in "Decide the
  password policy and hashing parameters", which also added `com.nulab-inc:zxcvbn:1.9.0` as a server-side
  strength gate and zxcvbn-ts on the client.
- React 19.3 + Vite + TypeScript (strict) + shadcn/ui on Base UI + React Hook Form + Zod +
  TanStack Query. Base UI chosen for real focus management and ARIA rather than hand-rolled a11y;
  accepted cost is that vendored shadcn components do not receive upstream fixes automatically.
- **Dual rate limiting**: per-account lockout (standard) *and* per-IP throttling (PRD Story 3).
  Both, as independent limiters — they defend different attacks.
- OWASP Dependency-Check bound to the Maven `verify` phase with a CVSS failure threshold, since
  there is no CI pipeline in scope.

**ASVS target (settled in "Decide the credential flows"):** **OWASP ASVS 5.0 Level 1**, with named L2 and L3
controls adopted where they are cheap. Every ASVS citation on this map must carry its level, because four of the
seven requirements that decided ticket 10 are L3 and an unlevelled citation reads as stronger than it is. A
whole-application **L2 claim would be false**: 6.3.3 (L2) requires MFA, or a combination of single-factor
mechanisms, to access *the application*, and ticket 19 scoped TOTP to the admin surface with MFA for regular
users ruled out of scope. Knowingly failed and recorded with causes: 6.3.8 (L3) on the username axis at
registration, 6.2.9 (L2) for passwords over 72 UTF-8 bytes.

**Skills every session should consult:** `grilling` and `domain-modeling` by default; `research`
for the research tickets; `policies` for anything IM8/ARC; `prototype` for the frontend ticket.

**Working agreements:** claim a ticket (`Status: claimed`) before any work. Resolve with an
`## Answer` section, set `Status: resolved`, then add a one-line pointer here under Decisions so
far. One ticket per session, except research tickets which may run in parallel.

**Handover-item declaration rule (added mid-resolution of [ticket 25](issues/25-operational-handover-document.md);
effective immediately for every open and in-flight ticket, including 14, 15, 16 and 17):** ticket 25 owns the
operational handover document, and every one of its contents arrives from some *other* ticket. Twelve tickets
already owe it something, and **at least one hand-off has already been lost**: ticket 08's resolution declares a
`Clear-Site-Data` origin-scoping note under its own "25 (handover)" line, and that item never reached ticket 25's
body. Prose hand-off is therefore retired. Any ticket concluding that a deployer or operator must **do, configure,
know, or periodically repeat something outside the application** must declare it in its own `## Answer` under a
heading spelled exactly:

`### Handover items (ticket 25)`

One bullet per item, each carrying four things: the obligation; the requirement or control ID it discharges, **with
its level**; whether the application can enforce any part of it; and how a reader could prove it was done. Ticket 25
assembles that document by **extraction over that heading**, so an item declared anywhere else is an item that will
be lost. Back-filling the already-resolved tickets is ticket 25's own work, not yours — you only owe the heading in
what you resolve from here.

**Mechanism/constants seam rule (settled in "Decide the observability signals and monitoring surface",
after three instances; a fourth and fifth arrived with ticket 09's reopening — the escalating-lockout ladder
constants and the cardinality axis's `k`. *Corrected by ticket 16: the earlier text here said both were "named with their property keys and binding test at source", which was false. Ticket 09 named no key for either, nor for its budgets or NIST cap, and gave k only as "≈5". The keys and `k = 5` are now named in ticket 09's amendment from ticket 16.*):** where one ticket owns a mechanism whose constants another ticket owns — ticket 13's
severity column, ticket 09's lockout thresholds, ticket 09's per-IP budget as an input to disk sizing — the
owning ticket **names the property keys and their binding test**, and the dependency is recorded in the
deferral register as a **named owed input**, never left as a missing value. Two instances is a convention;
an unwritten convention is how an untracked dependency appears on the fourth.

**Verification rule (settled in the same ticket, the hard way):** an argument that rests on an external fact —
a framework default, a property name, a CVE precondition, a clause in a normative standard — is not recordable
until that fact is checked against a primary source. Across that ticket, **five of eleven external facts were
wrong, every one of them in the direction that favoured the conclusion being argued**. Verified facts live in
`research/` and are cited from the ticket, not restated in it.

**Extended by ticket 09's reopening: the rule applies to facts about *this repository* too, and that is where it
paid most.** Three positions in that round were reversed, and the two that changed the design were overturned by
reading resolved tickets rather than by argument — a recovery channel ticket 13 had already confined to `dev`, and
a lock-release step that does not exist. So *before recording a claim about another ticket's decision, read that
ticket*; a remembered decision is an unverified fact with a citation attached. Same round, same direction of bias:
every reversed claim favoured the conclusion being argued.

**Extended again by [ticket 28](issues/28-out-of-band-privileged-channels.md): check the ticket's own premises before
answering the question it poses, and treat your own drafts as premises too.** That ticket asked which output channel to
use. Four of the five findings that shaped its answer came from checking what the question took for granted. The tool
it asked about could never have run, and the standards citation behind its design was inverted. **Three of the
premises that failed were drafted during that session's own grilling.** A resolver's draft gets no exemption from this
rule. The resolver has the most reason to believe it and the least distance from it.

## Decisions so far

<!-- one line per resolved ticket: gist + link. Detail lives in the ticket, never here. -->

- [Extract the IM8 and ARC controls that bind this app](issues/01-im8-arc-applicable-controls.md):
  **IM8 ac-2 mandates MFA for privileged access and has no N/A branch — the PRD's MFA exclusion is a
  genuine conflict, now [ticket 19](issues/19-mfa-scope-conflict.md).** IM8 does *not* require a durable
  audit store, so log-lines-only survives. Two further unacknowledged FAILs: **as-9** (no CSP anywhere
  in the PRD) and **lm-16** (no monitoring). ARC is N/A as a product requirement; its `op` controls
  apply to our AI-assisted workflow. `im8-review` is a code audit and cannot read this plan; it also
  targets Boot 3.4 / Security 6.4 config spellings, so our 4.1 config may read as absent.
- [Verify the App Standard's controls are still current practice](issues/02-verify-standard-currency.md):
  the standard is **behind on four of seven** items. Breached-password screening is a NIST `SHALL` and
  is absent entirely; lockout-as-primary-control is superseded by throttling; `SameSite=Lax` should be
  `Strict`; composition rules are discouraged. Two defects found in the standard itself: lockout has
  **no observation window**, and locking at 5 failures makes the 10/min per-account rate limit
  unreachable dead code. **NIST requires a 15-character minimum for single-factor auth, so the PRD's 12
  is below the floor** unless MFA lands.
- [Extract the binding structured logging and audit schema](issues/03-logging-schema-extraction.md):
  `event.action` is a **closed enum** and every PRD event maps onto it, though enable/disable, role
  change, and delete all collapse onto `user-administration`, so `event.type` carries the semantics.
  **No target field exists** for the second party in an admin action — `user.target.id` is a documented
  schema extension we must declare. A custom `StructuredLogEncoder` subclass is effectively mandatory
  (the ECS formatter seals `error.*`), on a **non-public Spring extension point**. A dedicated audit
  appender is an Enforced Constraint. Unresolved contradiction in the sources over whether `source.ip`
  may be logged.
- [Inventory the prescribed recipes](issues/04-prescribed-recipe-inventory.md): **every recipe already
  targets Boot 4.x / Security 7.x** — the version risk we feared does not exist. The recipes largely
  settle the CSRF bootstrap and the RBAC config format, and confirm `ImmutableSecurityHandler` guards
  role *definitions* only, so **Story 10's admin role-change endpoint is permitted**. Two gaps found:
  the recipes assume a single origin and so **never address CSP for the SPA origin**, and the RBAC
  recipe registers guards before the whitelist under first-match-wins ordering.
- [Pin down the Spring Security 7 config surface](issues/05-spring-security-7-config-surface.md):
  Security **7.1.1**, Spring Session **4.1.1**, new `spring-boot-starter-session-jdbc`. Own the session
  DDL in Flyway with `initialize-schema=never` (the `EMBEDDED` default silently creates nothing on a
  real database and `continue-on-error=true` masks the failure). **`csrf.spa()` is built on
  `CookieCsrfTokenRepository` and is therefore prohibited for us** — the likeliest wrong turn. **No
  absolute session lifetime exists**; it must be built on an auth-instant attribute, and *not* on
  `getCreationTime()`, which `changeSessionId()` preserves from before login. `__Host-` works but
  forces a per-profile cookie name. **BCrypt is asymmetric: encoding >72 bytes throws, verification
  does not short-circuit** — that asymmetry *is* the CVE-2025-22234 fix, so validate length at
  registration and change, never on the login path.
- [Resolve the MFA scope conflict raised by IM8 ac-2](issues/19-mfa-scope-conflict.md): **MFA is in
  scope — TOTP on the admin surface, enforced through Spring Security 7's native factor authorities,
  plus a 15-character password floor for everyone.** Three of the ticket's premises were wrong:
  step-up-at-action does **not** pass ac-2 (`im8-review`'s own example report grades that exact design
  WARN/Medium); **Spring Security 7 has first-class MFA** via timestamped `FactorGrantedAuthority` and
  `validDuration`, which nothing on this map knew; and the prescribed `MFA_Critical_Transaction` layer
  exhausts TOTP codes during ordinary admin work. So `MFA_Core` is kept as prescribed and **only the
  enforcement layer is replaced**, by two rules — admin reads require the factor unbounded, admin
  mutations re-verify every 10 minutes — closing both halves of ac-2 with one mechanism. Secret encrypted
  AES-GCM under an **environment-supplied** key, overriding the standard's instruction to store it in the
  database; `Encryptors.stronger()` and `AesBytesEncryptor` are prohibited (CVE-2026-47842). Lost
  authenticator is admin-resets-admin plus a **two-enrolled-admins invariant**; recovery codes deferred.
  Enrolment is admin-only and the challenge is eager, so the reviewer sees a real two-step admin login.
  Still demoable on two ports: `localhost:5173` and `localhost:8080` are cross-origin but **same-site**,
  so `SameSite=Strict` survives and only CORS-with-credentials is needed. Six ADRs owed.
- [Decide the API error envelope and the enumeration-safe response contract](issues/06-error-envelope-and-enumeration-contract.md):
  **RFC 9457 `application/problem+json`, with a SCREAMING_SNAKE `code` extension as the single branch point
  and a closed enum of 14 codes.** §5 demands conformance to a "documented schema" that **does not exist** —
  zero hits for `9457`/`ProblemDetail` anywhere in the user pillar — while the File and MFA pillars both
  mandate it, so 9457 is org-consistent rather than invented. The §3.2 prose strings are demoted to `title`
  and log reasons; **`account locked` never reaches the wire**, resolving §3.2's self-cancelling bullet that
  names it *and* requires it be indistinguishable. "Identical" is defined field by field: `instance` must be
  **pinned to a constant**, because Spring's request-URI default differs across login/reset/registration —
  a live defect on exactly the responses the rule protects. Timing is the framework's dummy-hash mitigation
  plus two ordering rules and **no artificial floor**. The `user exist` tension splits: specific for
  admin-initiated creation, **uniform 202 + activation token** for self-registration, which grows ticket 10.
  Four independent envelope producers exist because Spring Security never routes through
  `@RestControllerAdvice`; one `ProblemDetailWriter` serves all four and **`sendError` is prohibited**.
  Two of this ticket's own premises were wrong: `SETUP_REQUIRED` is nowhere in the MFA corpus, and 403 there
  means insufficient privileges. Five ADRs owed.
- [Extract the MFA_Core and MFA_Frontend/Standalone recipes at implementation fidelity](issues/22-mfa-core-recipe-extraction.md):
  **there is no provisioning recipe** — `generateSecretKey` and `generateQRCode` exist as static utilities with
  no caller, no endpoint, no status, no response shape, deferred to the MCC corpus we do not follow. So the
  QR-versus-`PENDING_TOTP`-write ordering is ticket 23's decision, not an extraction. Recipes 10 and 12 **do
  not compile as printed**, and Recipe 12 mutates three lockout columns its own entity lacks.
  `EncryptionService` is a name and one `decrypt(byte[])` call — no interface, no `encrypt`, no algorithm, no
  key source — so ticket 19's AES-GCM design drops in with zero conflict and zero guidance. Better news than
  expected on the deviations: **TOTP-only is explicitly sanctioned** (§4.1 "a deployment decision"), the
  router never put `MFA_Critical_Transaction` on the login path, `MFA_Core` mandates **no AOP at all**, and the
  recipes' own notes license the `AuthenticationProvider` re-homing — **so ADR 1 is mis-framed**. The real
  deviation is §4.1's enforced "**on each request** the enforcement layer iterates all providers": our
  session-scoped factor is per-session, and ticket 19's admin-**read** rule is unbounded, which is the edge a
  reviewer finds first. Ticket 06 is vindicated — RFC 9457 + SCREAMING_SNAKE `code` is what Recipe 7 already
  does — and **`412` is prescribed in prose but produced by no code in the corpus**, the mandated
  `"User Details not found."` string is unproducible from any recipe exception, and not-enrolled is a two-way
  contradiction (412 vs 422), not three. Third standards defect on this map: the "1-hour **sliding** window"
  is a staleness reset on the most recent failure only, so paced attempts still lock a known admin username
  with no self-unlock — and **there is no TOTP unlock path anywhere**, Recipe 11 being PIN-only. Skew
  tolerance is overstated: the replay rule burns `counter-1` and `counter` on first success, leaving **+1
  window only**. Frontend: the Generate-QR contradiction resolves against §3.2, the prescribed object-URL
  cleanup **breaks under React 19 StrictMode**, code entry is bare `useState` not RHF+Zod, every mount check
  fails open, and of 31 prescribed test cases **the two components we keep have zero**. No manual-entry secret
  fallback exists, which is a functional a11y blocker for an admin-only factor.
- [Decide the password policy and hashing parameters](issues/07-password-policy-and-hashing.md): **BCrypt cost
  12 behind `DelegatingPasswordEncoder`, band 15 characters to 72 bytes, no composition rules, and two
  rejection controls — a breach-corpus blocklist plus a zxcvbn score gate at 3.** The gate is **deliberately
  stricter than NIST**, and reaching it required correcting three misreadings of §3.1.1.2 that the ticket now
  records so they are not re-derived: NIST is a floor, not a ceiling; "the entire password SHALL be compared,
  not substrings" constrains the *blocklist comparison* and is not a bar on pattern-based checks; and
  Appendix A.3's "no additional requirements are imposed" is NIST describing its own scope, not binding
  implementers. Without the gate `aaaaaaaaaaaaaaaaaaaa` is **accepted** — length alone does not imply entropy,
  and an earlier blocklist sizing was wrong because it filtered a top-100k list rather than a breach corpus.
  `CompromisedPasswordChecker` is implemented but **deliberately not registered as a bean**, because
  `DaoAuthenticationProvider` picks such a bean up and would make a correct-but-breached password
  distinguishable from ticket 06's uniform 401. The seam is **"set a password", not "validate a password"**:
  one `PasswordService` owns `encode()` with no call-site exceptions, because the corpus shows a shared
  validator simply not being called — its self-service change command validates nothing at all. The prescribed
  12-character admin generator **fails our own 15-character floor** and becomes 20. On complexity, the
  **binding Standard never required it** on user-chosen passwords — §3.5 scopes the four character classes to
  admin-generated passwords only, §6 defines strength as "(minimum length)", and the corpus's own Q12
  recommends against mandatory character types, so the regex is the *recipe's* alone and contradicts the
  questionnaire beside it. Running quotas *as well* was rejected on security grounds too, not just compliance:
  `Password123!@#$` is predictable and clears all four classes, `my neighbour keeps unusual bees` is strong and
  clears none, so quotas reject no weak password the gate misses and their only marginal effect is false
  rejection of passphrases. The
  pepper/pre-hash escape from the 72-byte ceiling is now **possible** — ticket 19 built the key facility ticket
  02 said was missing — and still declined on new grounds: **a peppered hash cannot be rotated**, a constraint
  ticket 24 inherits. Seven ADRs owed.
- [Decide the origin topology: two origins, or one behind a reverse proxy](issues/20-deployment-origin-topology.md):
  **two origins as the PRD specifies — `localhost:5173` + `localhost:8080` — with `SameSite=Strict`,
  `__Host-SESSION` outside dev, and a three-layer document CSP we own.** Production is settled as a
  **documented requirement, not an environment**: nobody terminates TLS, no prod origin is ever named, so
  every prod-only value is asserted by a test and never executed. Four of this ticket's own premises were
  wrong: the recipes do **not** assume one origin (headers recipe Step 4 is an explicit credentialed CORS
  policy, and Q27 offers cross-origin as a first-class branch); `SameSite` **was never broken** under two
  origins, since `localhost:5173` and `:8080` are already same-site, which collapses "two independent
  arguments" to one (CSP); `localhost:3000` is hedged "e.g." and non-binding; and **as-9 accepts a meta tag
  in `index.html`**, so the IM8 FAIL never required a topology change. The option the ticket missed —
  **Boot serves the bundle**, one origin with no proxy or infra — lost on PRD fidelity (line 119 is binding),
  on making **two prescribed CORS tests unimplementable**, and because its dev story is still Vite, so the
  divergence merely relocates. Its one unique advantage, **a per-request CSP nonce**, is an advantage we have
  no use for: a Vite build emits no inline script, so `script-src 'self'` is already exactly as strict as a
  nonce, and `'strict-dynamic'` buys nothing against an allowlist of `'self'`. **`'unsafe-inline'` is
  dev-server-only** (React Refresh preamble + HMR `<style>` injection), confined to two directives in
  `.env.development`; the other seven stay identical to production so feature-work violations are caught
  while developing, making the dev policy a **lint, not a control**. CSP **intersection semantics** kill the
  idea of an invariant meta floor — a meta `default-src 'self'` without the API origin in `connect-src`
  blocks every API call — so the meta tag is templated via Vite's `%VITE_CSP%`. `frame-ancestors` is
  header-only (ignored in meta), and **`vite preview` is the verification surface**. `Strict` fails the
  standard's own prescribed test at line 499 **by design**. Four constraints to ticket 14, including Base UI
  `CSPProvider` with `disableStyleElements` — and the good news that client-side JS style-setting is outside
  CSP entirely, so Floating UI positioning needs no relaxation. Reopening trigger recorded: any inline script
  in the production document forces the topology back open. Seven ADRs owed, plus two glossary terms.
- [Decide session management and the CSRF contract](issues/08-session-and-csrf-contract.md): **15 min idle /
  8 h absolute / 1 concurrent session with the new login winning, and CSRF session-bound, header-only, and
  rotated on login and logout** — all three lifecycle numbers are the standard's own defaults, so only the
  mechanisms and deviations cost an ADR. Three things in the inherited plan were broken. **Ticket 05's Route C
  composite silently omits `CsrfAuthenticationStrategy`**, so as drafted the token never rotates at login, a
  planted pre-login token survives authentication, and ticket 06's re-bootstrap-and-retry loses its trigger
  entirely; the default handler **accepts the token as a `_csrf` query parameter**, creating exactly the log
  leak §3.4 prohibits, so resolution is narrowed to the header alone; and the `[Enforced Constraint]` pre-login
  session existed **only as a side effect of Jackson calling a getter** on the returned `CsrfToken`, so it is
  now created deliberately. Defects four, five and six in the standard: **Failure Path 1's invalidate-on-failed-login
  is an unauthenticated DoS** (anyone knowing a username can kill that user's session) and is deviated from;
  **§5:446's "previously redeemed CSRF tokens"** has no meaning under the repository §3.1:238 mandates, and is
  reinterpreted as superseded-session rejection; **§5:499's "CSRF cookie" attributes** describe a cookie that
  cannot exist, and become a negative assertion that none is ever set. Invalidation is **actor-relative** — all
  *other* sessions only where actor and subject are the same principal, which is just self-service change and
  forced-change completion, and the recipe's own `revokeOtherSessions` deleting the caller's session is the
  evidence the authors meant it; **four triggers the standard omits are added** (admin disable, role change,
  soft-delete, lockout), since authorities live in the session and a role downgrade otherwise leaves a live
  privileged session. Two invariants worth more than the decisions they protect: **`AuthInstantStampingStrategy`
  is wired in exactly one place**, because the same silent 8-hour-window reset appeared three times (factor
  grant, 10-minute re-verification, password-change rotation); and the **absolute filter must precede
  `CsrfFilter`**, or an expired session gets a misleading 403 and a pointless retry before the 401. Two more
  envelope producers found beyond ticket 06's four (`sessionInformationExpiredStrategy`, `invalidSessionStrategy`),
  and **`sendError` appears in two inherited code samples** that ticket 06 prohibits. `FactorGrantedAuthority`
  verified `Serializable`, so ticket 19's factor survives JDBC sessions; its `getIssuedAt()` *is* the
  `validDuration` clock, so re-verification replaces the `Authentication` rather than flipping a flag. Two of
  this ticket's own drafted claims were wrong and were corrected before recording: `Clear-Site-Data` **is**
  exercisable on `http://localhost` (so the origin-scoping gap is testable, not assumed — `cookies` reaches the
  domain, `cache`/`storage` do not reach the SPA), and **Spring Security does know which factor is missing** and
  redirects to where it is obtained, so ticket 23 inherits a redirect-to-JSON conversion rather than an
  invention job. Eight ADRs owed, plus three glossary terms.
- [Decide the admin module, role model, and initial admin bootstrap](issues/11-admin-module-role-model-and-bootstrap.md):
  **eight endpoints behind one factor-gated `/api/admin/**` prefix, two roles whose definitions live in YAML but are
  persisted read-only behind a foreign key, one central guard holding both the self-action rule and the two-admin
  invariant under a pessimistic row lock, and a bootstrap that validates during context refresh and seeds in a
  runner.** Four of this ticket's premises were wrong, all in the same direction — **the corpus has no list-users
  endpoint, no enable/disable endpoint, no unlock endpoint and no working immutability guard**; all are prose, and
  `ImmutableSecurityHandler` is outright *inert* here because its `SecurityResource` type is never defined and
  `@RepositoryEventHandler` needs Spring Data REST we do not use. So Story 8's "never password hashes" is ours to
  build: as printed an exported repository serialises the hash, and the fix is **never serialising entities at all**
  plus an API-wide assertion, not `@JsonIgnore`, which protects nothing against projections, error bodies or
  actuator. Story 10 is permitted, but the blocker was never the handler — it is §3.5:384's "or others", which the
  admin recipe's own `setRoles` **flatly contradicts**, resolved as barring self-assignment only. The stated
  two-enrolled-admins invariant **was not an invariant**: two admins demoting two different admins concurrently
  each read "2 remain" and both commit, leaving zero, so it now takes a pessimistic lock — and **H2 rejects
  `FOR UPDATE` on aggregate queries**, so the guard selects admin *rows* and counts in Java, a constraint that
  would otherwise have been found in `/do-work`. The bootstrap had two hidden failure modes: an `ApplicationRunner`
  **cannot fail fast**, because Boot starts the web server during refresh and runs runners afterwards, so validation
  moved to a `@Validated @ConfigurationProperties` bean while seeding stayed in the runner; and the forced-change
  filter would have **bricked every fresh deploy** one layer above the problem Q7 was solving, since the corpus's
  two prescribed allowlists are **mutually incompatible** (one omits `/logout`, trapping the user; the other omits
  `/csrf`, making the change impossible under session-bound CSRF) — ours is the union of five paths, with enrolment
  deliberately *after* the password change. Flyway was rejected for the seed on **checksum immutability**, not on
  the false claim that a migration cannot reach `PasswordEncoder`. **The 30-day deadline the map had twice conceded
  as unachievable now lands**: `credentialIssuedAt` checked lazily at login behind a generic 401, no scheduler, no
  conflict with the hygiene-jobs deferral. Soft delete blocks **both** username and email reuse — the corpus checks
  tombstones for username only, which is a recovery-channel hijack, not an asymmetry — with the email stored as a
  keyed HMAC on PDPA grounds rather than IM8's silence, and the honest limit recorded that a keyed hash is
  pseudonymisation, not anonymisation. Canonicalisation (NFC, trim, lowercase, no dot or `+tag` folding) is
  mandatory rather than advisory, and **not a cost of hashing**: H2 has no expression indexes and its
  `VARCHAR_IGNORECASE` is proprietary, so a stored canonical value is the only portable route for plaintext too.
  Usernames reject `@`, without which the plaintext-username split is cosmetic. `spring.mvc.servlet.path` is
  **prohibited** — CVE-2026-22753 silently disabled the entire filter chain in exactly that configuration, on
  exactly our endpoint. Ticket 01's last open item closes: **all six population-dependent controls resolved by
  declaring users external**, with a named reopening trigger if admins are ever internal officers. Batch reset
  declined, which **removes a member from ticket 06's closed enum**. Fifteen ADRs owed, four glossary terms, and
  amendments made to five other tickets — including that **the map's NIST claim was vindicated** against a
  plausible correction: the final SP 800-63B-4 makes 15 characters a `SHALL` for single-factor passwords, and
  because MFA covers admins only it is the *least* privileged population that forces the floor.

- **Reopened by [ticket 21](issues/21-observability-signals.md) and now re-resolved** — see §R of the ticket.
  **The NIST §3.2.2 cap is implemented at 100 consecutive failures, so this map no longer declines any NIST
  `SHALL` on the password axis**, and ADR 3 flips from a declined requirement to an implemented one — retiring
  the only deviation here whose compensating control (ticket 21's detection, itself ticket 01's lm-16 FAIL) was
  a known gap. The detection commitment is **kept anyway**, because reset-on-success makes the cap blind to a
  compromised-but-active account. The reopening's own framing was right about the misreading and wrong about
  what it cost: compliance was never "one integer and a flag". **The correction exposed a single-source mass
  permanent-lockout primitive** — `3600 ÷ 100 = 36` permanent admin-surface disables per hour from one host at
  its permitted rate, with the usernames *confirmed* rather than guessed through ticket 10's deliberate
  `USERNAME_UNAVAILABLE`, and with §10's 240× headroom turning out to be the same arithmetic all along. Bounding
  it took a **third limiter axis** (distinct accounts a source has driven into lockout, not accounts attempted,
  which would trip on every shared NAT), and the honest ceiling is a **7× reduction, defeated by IP rotation**.
  Four of the nine owed items were forced rather than open, and three drafted positions were **reversed by
  reading this repository rather than by argument**: the sole-admin recovery story rested on a reset link
  ticket 13 had already confined to `dev`, so outside `dev` there is **no deliverable artefact at all** and the
  cap needed an operator rebinding entrypoint built before it could be enforced honestly; "release the row lock,
  then kill sessions" rested on a lock behaviour that does not exist, and Spring Session's `REQUIRES_NEW` means
  session kills can **never** be atomic with the state change that triggers them — which moves the dispatch rule
  and a reconciliation sweep to **ticket 08 for all five of its triggers** and yields a rule anyone can follow:
  *session rows only after commit, never inside the lock*. The declined `429` progressive delay is **taken**, as
  an escalating lockout **duration** (20/40/60) rather than a header, because a delay derived from a per-account
  counter puts account state on the wire and headers are outside ticket 06's uniformity rule — buying ~10 hours
  of lead time instead of 3.3, at the cost of leaving WSTG's 5-to-30 band on NIST's authority. The cap's refusal
  sits in **`preAuthenticationChecks`**, which is the decision rather than a detail: the post-auth slot fires
  **only when the password was correct**, so hosting it there would be a password oracle in the audit stream —
  and that same fact is a **live defect in ticket 11's 30-day expiry**, which ticket 13's "enumeration protection
  was already spent" argument did not merely miss but *swept in*, since `grace-expired` is in its own list.
  Both new readers of the client IP depend on the Route C converter setting `WebAuthenticationDetails`, absent by
  default, and they fail in **opposite** directions — §12 quietly open, the new axis loudly closed. 100 is
  the threshold not because NIST permits it but because **requests-per-disable *is* the threshold**, so halving
  it would double the primitive's throughput. Ticket 11's bootstrap survives with ADR 13 narrowed; ASVS 6.1.1
  is re-argued as pass-with-note **conditional on** the entrypoint and the ladder, cited to its final clause.
  **Nine new ADRs plus four amended** (stated as two figures deliberately — this map's other entries say
  "*n* ADRs owed", which reads as *n* new files and is how ticket 17's population came to be overstated), six
  glossary terms, five register entries, an eight-test delta, and amendments to nine tickets.
  [Verification asset](research/boot-4.1-actuator-observability-and-nist-throttling-verification.md) §§8, 12–14,
  18–20.

- [Decide lockout and dual rate limiting semantics](issues/09-lockout-and-dual-rate-limiting.md) — *the original
  resolution, kept for the route actually walked; four of its claims are superseded by the entry above and are
  flagged in the ticket at the point of contradiction: the flat 20-minute duration, the single window-equals-duration
  value, §3.2.2's cap being unimplemented, and the unqualified decline of progressive backoff.* **Lock at 5
  consecutive failures inside a 20-minute observation window for 20 minutes with automatic lift; throttle per
  source IP in a pre-authentication filter and per submitted username inside the authentication converter.** The
  split is not a preference — ticket 05 had already recorded that the Route C JSON converter consumes the body,
  so a pre-auth filter needing the username would have to buffer every login body, which is the amplifier ticket
  07 warned about. Asking which limiter *needs* pre-auth placement (only the per-IP one) removed the buffering,
  made ticket 06's submitted-string rule **structural rather than remembered**, and kept the envelope producers
  at six because the per-account 429 is thrown as an `AuthenticationException` and written by the failure handler
  that already exists. Cost recorded: that handler now branches on exception type inside the one component whose
  job is uniformity, so a later "always 401" simplification would delete the limiter's only observable behaviour
  and break nothing but §5:452 — hence a comment at the branch and a test. **One window value fixes two bugs**:
  the standard has no observation window (ticket 02's defect) *and*, with lazy expiry, the counter stays at 5
  after auto-lift so the first wrong password re-locks immediately — a window equal to the duration catches both.
  **Ticket 02's other defect is half wrong and the correction changes no number**: a rate limiter counts all
  attempts while lockout counts consecutive failures, so 10/min is reachable on mixed traffic; its real
  justification is the third job nobody had named — it bounds **write contention on one user row**, without which
  60/min/IP across N sources is a lock convoy. Counting takes a **pessimistic row lock, ticket 11's idiom**, with
  three consequences pinned: the listener fails open on counting but **never on responding** (a lock-timeout 500
  on a contended account against 401 everywhere else is a state oracle), H2's 1-second `LOCK_TIMEOUT` is pinned on
  the JDBC URL rather than inherited, and **user rows lock before session rows** now that three tickets take row
  locks. Every leak edge collapses onto Spring's exception-to-event mapping, so unknown / disabled / locked /
  credential-expired all increment nothing — and locked-with-correct-password is not even a choice, since
  `performPreCheck` discards the password result. **`alwaysPerformAdditionalChecksOnUser` must stay `true`**: it is
  the CVE-2026-22746 mitigation, `false` reads as a free optimisation, and it is declared on
  `AbstractUserDetailsAuthenticationProvider`, not the subclass the advisory names. `framework` forwarding is
  **prohibited** — `ForwardedHeaderFilter` validates nothing, and a spoofable key does not weaken the limiter but
  nullifies it — while a `@NotEmpty` check on Tomcat's `internal-proxies` would have been a **no-op**, since Boot
  ships a non-empty CIDR default that trusts every RFC 1918 neighbour; so we bind our own property with no default
  and derive both `internal-proxies` *and* the strategy from it, because `source=proxy` with the strategy unset
  gives the whole internet one shared bucket. Two NIST positions taken honestly: §3.2.2's cumulative cap is **not
  implemented**, justified on calibration (100 guesses is a six-digit-OTP number; ours is ~12 guesses/hour against
  a 15-char zxcvbn-3 credential, ~105,000/year, negligible) and on failure mode, with the borrowed claim that we
  implement NIST's alternatives **dropped** — we implement none of the three and decline one — and with the
  uncomfortable part said: the spirit is "there must be a ceiling" and ours is "there is no ceiling", replaced by
  a detection commitment that is **itself ticket 01's lm-16 FAIL**. PRD Story 3's lockout-prevention criterion is
  **unachievable** at 240× headroom, so ASVS 6.1.1 is closed as **satisfied-with-documented-residual, not a
  partial** — it is a documentation requirement and nothing in it demands malicious lockout be impossible. Which
  yields the load-bearing sentence: **the 20-minute auto-lift is unremovable by three independent arguments** —
  ticket 11's bootstrap, the NIST deviation, and WSTG-ATHN-03's own precondition that a tier-3 admin-unlock
  control needs tier-1 as the administrator's own recovery path. Progressive backoff declined on **thread-pool
  exhaustion**, not on strength (they are alternatives, not additions); CAPTCHA declined on scope with WSTG
  agreeing it must not replace lockout; `RateLimit` headers declined as a live Internet-Draft. Ten ADRs owed, five
  glossary terms, and amendments to seven other tickets.

- [Decide the credential flows: registration, password reset, self-service change](issues/10-credential-flows.md):
  **the credential moves out of registration and out of the administrator's hands — registration mints only a hashed
  single-use token, the password is set at redemption, and admin-create and admin-reset both become token issuance.**
  Three premises were wrong, one fatally: **there is no token machinery in the corpus to reuse** — no token entity, no
  expiry, no single-use flag, no `SecureRandom`, only a References link — so the gating choice's stated economy was
  fictional, and the admin-reset *recipe* implements a 12-character plaintext password rather than a token. That is the
  **seventh standards defect**: §2:62–64 mandates the token model and §3.5:357 mandates the generated-password model,
  and one action cannot be both. Three more defects follow — SHA-256 for reset tokens is normative only for *activation*
  tokens in the Questions file, the 30-minute expiry is stated in four incompatible modalities, and per-account limiting
  on redemption is circular, which **vindicates ticket 09's per-IP key as forced rather than preferred**. The settled
  shape also had a takeover in it: with a password at registration the overwrite rule reverses, because the attacker
  registers *after* the victim and the fresh link still lands in the victim's inbox — [CVE-2026-48117](https://www.sentinelone.com/vulnerability-database/cve-2026-48117/)
  shipped exactly this. Moving the credential to redemption closes it **and deletes a 50–100× timing oracle** against
  ASVS 6.3.8 (L3), because BCrypt-12 at 300–400ms against a no-op is a loud enumeration channel behind a uniform 202
  and ticket 06's dummy hash is login-path-only; a decoy hash would have been a permanent tax on an anonymous endpoint
  to hide something removable. It also removes the unauthenticated-BCrypt resource lever. **The username stays at
  registration and its conflicts are specific**, because the squat is unavoidable under every option (no reaping, and
  ticket 11's tombstone blocks reuse forever, so admin deletion makes it worse) — so the only question is whether the
  victim is told, and a silent 202 leaves them with no account and no explanation. `USERNAME_UNAVAILABLE` sits **inside**
  ticket 06's split rather than as an exception: username availability is submitted-value quality, email existence is
  account state, so the axes separate cleanly and nothing joins the closed 13-code enum. **Admin-create becomes an
  invite token**, which deletes ticket 07's 20-character generator, deletes admin-create's forced-change branch,
  converges admin-create with self-registration onto one path, and satisfies ASVS 6.4.6 (L3) — with the trap that an
  invited-but-unredeemed admin must not satisfy ticket 11's two-admin invariant, or one real admin plus one pending
  invite reads as two and the real admin can demote themselves to zero. One `credential_tokens` table with
  **domain-separated hashing** (`SHA-256(type || ":" || token)`), so cross-type redemption fails cryptographically
  rather than because a `WHERE` clause was written correctly, and a **conditional update** for single-use so token rows
  never enter the lock ordering at all. **Redemption clears the password lockout but never any TOTP state** — which is
  named as WSTG-ATHN-03 tier 2 arriving through a channel we already had, superseding ticket 09's malicious-lockout
  residual, and is the sentence that keeps the log-leak containment claim true. The **link origin is never
  request-derived**: three negative assertions against `Host`, `X-Forwarded-*` and a caller-supplied body field, the
  last being [CVE-2026-55207](https://github.com/pimcore/pimcore/security/advisories/GHSA-h854-c3m3-mh5v). And the
  honest headline for the handover: **the stubbed transport logs reset links, so the leak is total for ordinary users
  and partial for admins, contained only by ticket 19's TOTP** — making ticket 19 load-bearing for a risk it was never
  scoped against, confidentiality-only, with the URL fragment buying nothing against it. No entropy floor is claimed
  from ASVS, because there is none for reset tokens; 256 bits is first-principles and 6.5.2's 112-bit line is cited by
  analogy for the hash choice alone. Eight ADRs owed, seven glossary terms, amendments to seven other tickets.

- [Decide the TOTP enrolment, step-up, and factor-reset flows](issues/23-totp-enrolment-stepup-and-reset-flows.md):
  **three endpoints, three authentication pathways, two filters, two authorization rules composed role-first, and a
  two-tier factor lockout.** The load-bearing find is that the composition ticket 19 assumed is **inverted**:
  `DefaultAuthorizationManagerFactory` wraps every rule as `allOf(deny, additionalAuthorization, manager)` and
  `allOf` returns the *first* non-granted result, so the factor check runs **before** `hasRole` and the role check
  never executes — an ordinary USER hitting `/api/admin/**` would be told to **enrol MFA for a surface they must
  never reach** (an ASVS 6.3.8 (L3) disclosure), and because `getFactorGrantedAuthorities` returns an empty list for
  an unauthenticated request, **anonymous gets 412 instead of 401**. So `AuthorizationManagerFactories` is abandoned
  along with `@EnableMultiFactorAuthentication` (global-only, cannot hold two `validDuration`s, would demand the
  factor of regular users), the rules are hand-composed role-first, and the **two-argument** `allOf` is mandatory
  because the one-argument form defaults to **grant** on all-abstain. Abandoning the annotation then disarms
  `setMfaEnabled`, so the framework's own authority merge silently vanishes and a verified admin **412s forever** —
  one of **two** silent-failure traps, the other being the filter's own `SecurityContextRepository`, which defaults to
  request-attribute scope, so the grant would survive exactly one request. Both are set explicitly beside
  `addFilterBefore`, along with ticket 08's minimal session strategy (a configurer would inject the login composite
  and reset the 8-hour window on every 10-minute re-verification) and non-redirecting handlers. **`shouldPerformMfa`
  is not a reachability gate** — it runs *after* `attemptAuthentication` and gates only the merge — and because the
  filter consumes the request, an `authorizeHttpRequests` rule on its path is **dead code**, so the precondition
  lives in the provider, keyed on `AuthenticationTrustResolver.isAnonymous` (an `AnonymousAuthenticationToken`
  reports `isAuthenticated() == true`) with the **username taken from the `SecurityContext`, never the body** and no
  counter increment on precondition failure. That precondition is what makes **every guess cost a password**, which
  is the sentence the whole lockout argument rests on. Ticket 09's auto-lift **cannot be copied** to a 10⁶ space —
  that was ticket 09's own calibration argument — because 10 failures per 20-minute cycle is 720/day and
  **≈55%/year** against a ±1 window, so a monotonic **100-failure cap** is added, cleared only by admin reset, which
  *is* NIST's rebinding; residual **0.03%**, with the honest notes that the 55% is conditional on prior password
  compromise, that NIST's cap is *consecutive* so our cumulative reading is **stricter than the SHALL**, and that the
  per-IP throttle never engages because tier 1 is the sole rate ceiling. NIST's multi-authenticator SHALL is
  satisfied by a conjunction rule rather than declined. Provisioning returns **JSON carrying the QR *and* the Base32
  secret**, fixing ticket 22's functional a11y blocker while leaving ticket 20's CSP untouched (base64 → Blob →
  `blob:` URL). Two premises of ticket 19 were wrong: the envelope is **69 bytes, not 48** (`AesGcmBytesEncryptor`
  uses a 16-byte IV), and **AAD is unreachable** through `BytesEncryptor`, replaced by a fixed-width context prefix
  inside the plaintext. Three standards results worth more than the decisions they support: **NIST §4.1.2.1 endorses
  password-only first enrolment** (bind at the *lower* of available and target AAL), which converts the
  first-enroller race from a finding into a sanctioned decision and kills the temptation to add a control;
  **SP 800-38D is satisfied, not deviated from** (the 96-bit figure is a non-normative interoperability
  recommendation in §5.2.1.1, a random 128-bit IV is the shape §8.2.2 itself recommends, and §8.3's 2³² cap is nine
  orders of magnitude away), which **removes** a register entry; and **ASVS 6.4.4 is N/A, not satisfied**, because
  it asks for identity-proofing evidence and we perform none. `validDuration` on the admin-read rule is kept but
  **reframed** — it is a fail-closed type guard, not a time bound, because a non-null duration is the only thing
  that routes a degraded authority into `createExpired`, and `getFactorGrantedAuthorities` returns *all* authorities
  despite its javadoc. Eleven ADRs owed, five glossary terms, twelve register entries, and amendments to eight other
  tickets.

- [Reconcile the data model and the Flyway migration set](issues/12-data-model-reconciliation.md): **seven tables, the
  UUID as every primary key, Hibernate's default type mappings taken rather than pinned, and `ON DELETE CASCADE` as the
  single deletion mechanism — with both rules this ticket inherited restated, because neither survived verification.**
  "Vendor-neutral DDL" is unachievable and becomes a **closed six-entry seam register**, held to ticket 20's standard
  and for the same reason: no Postgres or MySQL runtime is in scope, so every portability claim is asserted by review
  and never executed. Its sixth entry is the one invisible in the DDL text and the one that justifies the register —
  MySQL's default `utf8mb4_0900_ai_ci` makes every unique character index case- **and accent**-insensitive, and since
  **NFC does not fold accents**, `josé@…` and `jose@…` collide there but not on H2, so MySQL's uniqueness rule would be
  *stricter than the canonicalisation contract* and override a principle ticket 11 chose deliberately. Second,
  **`ddl-auto: validate` is demoted from the gate to a typo-catcher**: it checks table and column existence and type
  compatibility and nothing else — no nullability, length, defaults, foreign keys or check constraints — so **a
  48-byte `totp_key` passes silently**, which is the exact truncation ticket 23 derived 69 bytes against three wrong
  figures to prevent. Index and unique-key validation exist from Hibernate 7.3 but **default to `NONE`**, and `NAMED`
  **silently skips any index named with a leading uppercase `IDX`**, Hibernate's own source conceding the check is weak
  — so the convention is `ux_`/`ix_`, and the real gate is a five-item negative-test set whose first job is proving
  validation runs at all. Third, **ticket 03's prescribed `uuid` mapping does not run on our only runtime**:
  `gen_random_uuid()` is PostgreSQL's and exists in H2 only under `MODE=PostgreSQL`, `columnDefinition = "UUID"` has no
  MySQL spelling, and `insertable = false` needs read-back — so the **UUID becomes the sole primary key**, on four
  arguments, of which two are new: identity-column syntax is itself a three-way portability break, so deleting the
  bigint *removes* a seam, and `@UuidGenerator` assigns **before execution**, so registration inserts a user and its
  token in one transaction with no flush ordering, which a `BIGINT IDENTITY` could not. It also makes ticket 11's
  never-return-the-PK rule **vacuous rather than enforced**, which is the point. **UUIDv7 is rejected deliberately**,
  because a sortable identifier on the wire discloses account creation time. The tombstone then forces two negative
  facts nobody had written: ticket 11's delete is a **real row delete** across two repositories, so **neither
  `user_id` nor `deleted_by_id` can be a foreign key** — the deleting admin may themselves be deleted — and the
  password-history purge is **forced by referential integrity**, not merely chosen on retention grounds, while the
  cascade makes **ticket 10's `used_at` stamp on delete redundant**, taking its five invalidation triggers to four.
  Sessions deliberately do **not** cascade, since `PRINCIPAL_NAME` is a string and not an FK. Enrolment is **derived
  from row existence**, refusing the `im8-review`-pleasing flag on ticket 09's `account_non_locked` precedent, which
  forces the two-admin guard to lock **both** tables on **all four** paths — because the cascade changes the
  enrolled-admin count through a table the delete statement never names — with the honest caveat that H2 has no gap
  locking, so the guard protects against **decrements, not phantoms**. Eviction turns out free (the reuse check already
  loads the rows, avoiding a `LIMIT`-in-`IN` that MySQL still rejects), and a tie needs two password changes inside one
  microsecond, which BCrypt-12 forbids — so no sequence column. Two things declined with better reasons than scale:
  the **reservations table**, because its email row would move *live* registration onto the non-rotatable tombstone key
  and a username-only version would fix the cheap half while leaving the recovery-channel hijack open; and a
  **reservations table** (above). On the tombstone HMAC key this ticket was **overruled by
  [ticket 24](issues/24-secrets-and-configuration-handling.md), resolved concurrently**, and the disagreement is
  recorded rather than left standing: 12 rejected forward-only key versioning as "add a key, not rotation", which does
  not survive 24's argument that accumulating versions gives **a leaked key a forward response where there was none**
  and moves ASVS 11.2.2 (L2) from a clean failure to a partial. **The schema consequence is nil, and the reason
  generalises: an HMAC used for equality search needs no version column** — you compute the candidate under every live
  version and look each up, never needing to know which produced a stored row, unlike the TOTP secret where the stored
  version is what selects the decryption key. So 12 had conflated two decisions and argued the column to settle the
  policy. What 12 does contribute on that key is the **justification** its non-rotatability always lacked — a
  **blinding key for a pseudonymised reuse index, not a confidentiality key**, one bit per address — with the asymmetry
  against ticket 07 stated so the map's one rule stops looking like two answers, and HKDF pre-empted. 24 also lands the
  sharpest input to the gate above: Boot's **embedded-datasource fallback** means an unset URL starts on an ephemeral
  in-memory H2 that **Flyway migrates and `validate` passes**, so the negative-test set proves validation *runs* and
  can never prove *what it ran against*. The session DDL is copied verbatim (blob
  `be6e515720a5434a898bcb6d186f42d7b4766006`), which brings ticket 08's load-bearing `PRINCIPAL_NAME` index free and
  preserves the cascade that is the **only** reclaimer of attribute rows — and "verbatim" is **enforced**, one copy
  plus a git-blob-hash test, because `eol=lf` catches CRLF **silently** while only the hash catches a BOM. And one finding about the
  tooling rather than the standards: `LONGVARBINARY` is **undocumented in H2 2.x** yet ships inside that script, sitting in
  the same file as an `OCTET_LENGTH` check that rests on a documented function. Seventeen ADRs owed, two glossary
  terms, six register entries, four reopening triggers, and amendments to seven other tickets.

- [Decide secrets and configuration handling for non-local environments](issues/24-secrets-and-configuration-handling.md):
  **three keys not two, every secret mandatory in every profile with no default anywhere, and the policy made
  structural — no secret's property name may appear in any committed config, so a default has nowhere to be
  written.** Four of the six inventory items were settled elsewhere and one of them, a session `hash` key, **does
  not exist**; the genuinely open work was two property names and the rules. The corpus has **no secrets standard
  at all**, and its one pattern is a fail-open — MCC Recipe 9's `${VAR:dev-default}` with a SIT profile that
  silently runs on dev credentials — while the normative contract four standards cite for how secrets reach the
  application, `Appfw-Project-Bootstrap/...#36-profile-configuration-contract`, **does not exist in this
  repository**, nor does `ProfileDotenvPostProcessor`, the loader it names: **eighth standards defect**. The
  ticket's own opening rule then decided its hardest question against an inherited one: ticket 09 had routed
  `source.ip.hash` through ticket 11's tombstone key claiming "no new key and no new argument", but **one key
  cannot rotate for the IP stream and stay frozen for tombstones** — domain prefixes buy cross-correlation
  resistance, not independent rotation — so the keys split, on **SP 800-57 Part 1 Rev 5 §5.2 Key Usage** reached
  through ASVS 11.1.1 (L2) and *not* through its "not overshared" parenthetical, which bounds how many entities
  hold a key and was a misreading. Ticket 11's "cannot rotate, at all" is **corrected to forward-only**: versions
  accumulate under the idiom tickets 19 and 23 built and can never retire, because we hold no plaintext to
  re-derive them — which turns ASVS 11.2.2 (L2) from a clean failure into a partial and gives a leaked key a
  forward response where there was none. Deterministic AEAD would have satisfied 11.2.2 outright and **fails at
  L1**: Appendix C's approved AEAD list contains **no SIV of any kind**, NIST files AES-GCM-SIV under *Proposed
  Modes* whose own page disclaims endorsement, and it would mean BouncyCastle or Tink on the one path ticket 19
  kept inside Spring Security after CVE-2026-47842 — so ticket 11's PDPA argument is the second reason, not the
  first. Two Boot behaviours inverted decisions rather than informing them: **OS environment variables override
  imported config data**, so a leftover env var silently beats the mounted file you just rotated and the
  application starts on the old key with nothing failing — which makes the startup key fingerprint part of the
  fail-fast design rather than an ornament; and **Boot's `@ConfigurationProperties` failure report prints the
  rejected value and its origin**, so a length constraint expressed as Bean Validation would echo a wrong key to
  stdout, and a validating constructor does not escape it either — value-dependent checks move out to the `@Bean`
  factory, leaving `SecretKeyMaterial` holding redaction and copying only. `configtree:` replaces a mounted
  properties file because a Kubernetes Secret is a **directory of files**, which also gives the one human-typed
  credential a file route instead of forcing it onto the very channel the file preference exists to avoid.
  **32 bytes is a compliance floor, not a preference** — Appendix C grades AES-128 **Legacy**. Nothing guaranteed
  the key was *random*, so 11.5.1 (L2) is satisfied-by-procedure with one enforced check — **reject an
  all-printable-ASCII key**, which catches Base64-encoding a string at a ~10⁻¹⁴ false-positive rate — while a
  known-bad vector list was **declined** as a control that could not hold. Datasource least privilege was
  attempted and **declined on [h2database#2846](https://github.com/h2database/h2database/issues/2846)**, where DML
  grants confer DROP and only admins may own a schema, because asserting 13.2.2 on that would be ticket 11's inert
  `ImmutableSecurityHandler` again; and Boot's **embedded-datasource fallback** means an unset URL starts cleanly
  on an ephemeral in-memory H2 that **Flyway migrates and `ddl-auto: validate` passes**, so absence must be
  converted into failure. Nine prohibited-configuration entries consolidate into one refresh-phase validator,
  which **collided with ticket 20's never-executed prod profile** and narrowed. Frontend origins bake at build,
  so **build-once-deploy-many is invalid and the build machine is part of the security configuration surface**.
  Two L2 failures added (13.3.1; 13.2.1 with 13.2.2) as one deferral with named IDs, 11.1.2 rescued from an
  overclaim by adding an algorithm inventory, and **ASVS 13.4.1 (L1) found with no owner anywhere on this map**.
  Eight ADRs owed, three glossary terms, and amendments to eight other tickets — including that
  `app.cors.allowed-origins` is dead, and that the name-absence assertion must match **four spellings**, because
  Boot binds kebab, camel and underscore in files and **removes dashes** when mapping to environment variables.

- [Build the audit event catalogue](issues/13-audit-event-catalogue.md): **forty rows, `user.id` on resolved
  login failures, three custom fields rather than five, and the catalogue compiled rather than written down.**
  The find that mattered most is that **ticket 09's only compensating control for a declined NIST `SHALL` was
  not computable from the log stream ticket 09 itself prescribed** — the 50-failures-in-24-hours alert needs an
  account key and §12 had specified that failure rows carry none. It is computable **verbatim** now, and an
  earlier restatement as "≥10 lockout transitions" was dropped because it would have missed the exact attacker
  ticket 09 admits it cannot stop, the paced one who never locks. The authority is the Standalone standard's
  §3.4 **Privacy clause** — a mandate to use `user.id` with a carve-out only where the UUID is unresolved — and
  *not* §3.4's Log Levels parenthetical, which grammatically attaches to lockout transitions; but the decisive
  argument is that **ticket 06 already routes the internal failure reason to the audit log**, so the recipes'
  enumeration protection was spent and withholding the UUID was cost without benefit. Deviated from in **four
  places**, including an inverted printed test assertion at `Centralising_Audit_Logging_With_A_Typed_Module.md:305`
  — this map's sharpest departure from the corpus, hence its own ADR. Scope is **three rows, one change**: the
  lockout row already carried `user.id`, and the per-account 429 stays keyless **by construction, not policy**,
  because ticket 09's converter throws before any repository lookup, so both rows fall under the *same* carve-out
  — with the invariant recorded where it will be violated, that **adding a lookup to make them consistent** would
  put an existence check in front of ticket 06's timing mitigation and destroy what placement bought. Second
  find: **`source.ip.hash`, carried since ticket 09, would have been rejected at ingest** — ECS types `source.ip`
  as `ip` and our schema as `string`, so a sub-field makes it an object where every other platform producer emits
  a scalar. Fixed to **`source.ip_hash`**, and rotation is **no longer free**: it breaks source correlation across
  the boundary, invisibly, so the interval is pinned at ≥ the investigation window. Third: **an audit row can leak
  the submitted password with no call site naming it**, because `.setCause(e)` auto-populates `error.message` and
  `error.stack_trace` — closed structurally by an emitter that **exposes no throwable parameter**, which also
  closes the general form (constraint, validation, any message carrying request content). The Jackson instance was
  investigated and **downgraded from blocking**: jackson-core #991 is the *fix*, not the report — 2.16 flipped the
  default off and #1039 renamed the marker to `REDACTED` — and CVE-2025-49128 is a byte-array-offset mechanism that
  is not our servlet path; so the pin earns its place as defence in depth on a default we could not confirm carries
  into Jackson 3, asserted by outcome (`REDACTED` present, password absent) rather than by flag, and
  `spring.jackson.use-jackson2-defaults` was checked and **excluded** because it aligns to Boot 3's Jackson 2,
  which is already post-2.16. The extension package **shrinks**: `user.target.*` is a documented ECS reuse point
  and `user.roles` a real ECS field, so two of five are "our schema is behind ECS" rather than inventions, leaving
  `user.target.unlock_reason` (renamed from ticket 11's camelCase), `user.target.count` and `source.ip_hash` —
  with `labels.*` rejected for count because ECS stores every label as **keyword**, which would strip a numeric
  count that is half a control. Two of ticket 03's conflicts **reverse** under a precedence rule now stated once
  rather than re-argued per question — governing user standard > logging standard > recipes: **lockout is WARN**
  (§3.4 twice, plus §3.3's own definition of ERROR, against the recipe's `atError`), and **no cleartext IP ever**.
  C4 also **widens**: §3.3 requires path and method on *every* audit event, and `url.path` must be the **matched
  route pattern**, because five admin endpoints carry a UUID and the standard **externalises the base path**, so
  any path-keyed saved query breaks on redeployment — which makes the static `message` the primary discriminator
  and, pleasantly, **deletes the injection surface on every handler-scoped row**, leaving it only where no pattern
  exists. Four rows existed nowhere on this map: **CSRF rejection** (§3.3 demands bypass attempts; ticket 08
  specified six envelope producers and no audit row, so the control guarding every state-changing endpoint was
  silent), **lockout-cleared** (§3.3 requires "unlocked" and the lazy auto-lift had no producer), **failed
  administrative attempt** (§3.4 requires it), and the **authentication session-start** row that ticket 08's
  handover implied — which turns out to need **no new field**, since rotation happens inside the login request so
  pre- and post-rotation hashes join on one `trace.id`, provided the audit strategy sits **after
  `ConcurrentSessionControl` and before `ChangeSessionId`**; placed last, the join evaporates with no test failing.
  **Idle expiry cannot be produced**: Spring Session JDBC publishes no session events and reaps by bulk DELETE, so
  it is producible only via a custom reaper and **declined on scope**, leaving §3.3's three session endings as two
  producible rows plus one that cannot be told from a forged cookie. The emitter is **data-driven** — a closed
  `AuditEvent` enum, one `emit`, typed context **records** rather than a map (or the masking recipe's commonest
  defect reopens), **unknown-key rejection** as well as missing-field checking (a required-field check still lets
  `user.email` onto a row), fail soft at runtime and hard at test time per ticket 09's never-fail-open-on-responding
  rule, a **completeness** test mapping every §3.3 required event to a member including three N/A negative
  assertions, and the **ASVS 16.1.1 inventory generated from the enum as a snapshot** so the document cannot drift
  from the code. This catalogue **is** that inventory, so it owes destination, retention and who-can-read columns.
  ASVS is graded honestly: **V16 contains no L1 requirement at all**, so nothing here touches the L1 claim, and at
  L2 **16.4.2 and 16.4.3 are F with deployer obligations and no compensating control** — a file the application owns
  and rewrites is neither tamper-proof nor separate. An earlier draft of this ticket proposed dropping the console
  copy outside dev and was **wrong**, contradicting an Enforced Constraint it had quoted itself: stdout is the
  ingestion path and the only route to 16.4.3, so it stays in every profile and 16.2.3 is satisfied by documenting
  it. Retention is **answered**: 90 days locally via `max-history`, with **no `total-size-cap`** because a cap
  deletes the oldest archives while every value still reads compliant. NIST's framing is corrected in ticket 09's
  favour — reset-on-success is the only sanctioned reset and there is **no time-based one**, so the deviation is
  **the remedy, not the ceiling**, and NIST's own rationale for choosing 100 cites the account-recovery burden that
  *is* ticket 09's auto-lift argument; progressive delays are **additions, not alternatives**, correcting ticket
  09's parenthetical. One upside claimed: identity-present versus identity-absent failures from one
  `source.ip_hash` is a **username-enumeration signature**, a signal nothing on this map could produce before, so
  the same field is both a log-reader oracle and a defender's signal. And a rule that retires four precedents:
  **amend a resolved ticket unless the amendment weakens a compensating control standing in for a declined
  `SHALL`, in which case reopen it.** Nine ADRs owed, five glossary terms, eleven register entries, four reopening
  triggers, and amendments to seven other tickets.

- [Decide the observability signals and monitoring surface](issues/21-observability-signals.md): **Actuator
  for the meters and nothing for the reader — `health` is the only exposed endpoint, metrics are pushed over
  OTLP or not collected at all, and lm-16 is confirmed as a narrowed High rather than closed.** The collision
  this ticket was created to resolve **does not exist**: Boot 4.1 already defaults
  `exposure.include` to `health`, so as-13 and lm-16 never conflicted — though as-13's check greps the literal
  property and states no verdict for absence, so every value is written explicitly anyway. Three findings
  outrank the decisions. **Ticket 03 has been exporting metrics to `localhost:4318` every 60 seconds since it
  chose the OpenTelemetry starter**, because the OTLP *metrics* `ConnectionDetails` bean — unlike the tracing
  and logging ones — carries no `@ConditionalOnProperty` and falls through to a default Micrometer hardcodes
  one layer below Boot's blank appendix entry: traces need a property, logs need a property and a hand-installed
  appender, **metrics need nothing**, so the starter was adopted for the one signal requiring configuration and
  silently acquired the one requiring none. The kill switch is `enabled: false`, and a property with **no value
  does not work** — an unresolved `${VAR}` binds as the *literal string* because
  `PropertySourcesPlaceholdersResolver` is lenient where `@Value` and `Environment.getProperty` are strict,
  which defeats `@NotBlank` and is worse than loopback because it reads as configured; a general caveat on
  every fail-fast claim resting on `@ConfigurationProperties`. **The alert taxonomy cannot route off
  `error_follow_up_action`**: the recipe sets it `true` on the lockout row, ticket 13 never assigned it at all
  (one occurrence, no column — this ticket's own handover sentence was false), and the encoder injects it into
  the sealed `error` object so it cannot reach a success row. The discriminator is *does clearing this state
  require an action outside the normal flow*, read off row 4's reason enum and ticket 23's tier model, giving
  **three** classes — per-event, rate-above, and a rate-below class nothing had a home for. **Rows 5 and 6 were
  a log-amplification path**: unannotated where rows 3 and 40 say "once per transition", so one audit row per
  rejected request, aimed at the file whose failure is §3.4's audit-failure condition in a design that declined
  `total-size-cap` — and the disk detector this ticket adds would have existed to catch an attack the audit
  logger enabled. Transition-keying alone does not bound it (`maximumSize` caps memory, not transitions), so
  ticket 13 gains a **per-window distinct-source cap with the truncation recorded** as row 46, on ticket 11's
  `user.target.count` precedent — which is what makes disk sizing computable and what keeps this ticket's fog to
  one patch. Health is **ping + diskspace**: `db` disabled on consumer-absence (not cost — it runs *no SQL* by
  default, just `isValid(0)`), `diskspace` kept as the only in-process detector of the condition that breaks
  the audit appender, with size and threshold as **different figures** (`90 × daily` versus
  `daily × lead_days`) because deriving the threshold from retention parks health permanently DOWN. Two
  authorization rules instead of three, and `show-details: never` is the interlock that makes subtree-matching
  `permitAll` safe. Both actuator CVE guards were **aimed at the wrong subtree** — 22731 needs a health-group
  `additional-path` on a server root and 22733 is `/cloudfoundryapplication`, so an `/actuator/**` predicate
  catches neither, and "we are not on CloudFoundry" is not among 22733's preconditions. `info` stays unexposed
  on a **no-benefit** reason after the Boot-4.1 `workingDirectory` argument turned out to be wrong. Probes
  disabled and asserted by **outcome**, since `livenessstate.enabled` is not a second lever but an explicit
  group declaration is. The strongest thing this ticket bought is verification posture: disabling the exporter
  brings `SimpleMeterRegistry` back, so **every meter claim is a unit assertion** rather than an argument.
  Nine register entries, eight ADRs, five glossary terms; ticket 09 **reopened** (since re-resolved — the cap is
  implemented, and the reopening turned out to cost far more than the "one integer and a flag" it predicted);
  amendments to tickets 03, 09, 13, 24, 25.
  [Verification asset](research/boot-4.1-actuator-observability-and-nist-throttling-verification.md).

- [Design the frontend architecture](issues/14-frontend-architecture.md): **one rule for where the app
  may be, one rule for what it does when refused, a queue the server cannot keep, and a word for a state
  that had none.** The rule that generalises across all three state axes is that **the self-read drives
  navigation and the envelope `code` is the authority** — and it is the server's own assumption, since
  ticket 23 built `422` precisely for the stale-belief case, so a client trusting its cache would make
  that reply dead code. The find that matters most is a **loop nobody had seen, and it was encoded in this
  ticket's own prototype**: ticket 12 §8 derives enrolment from row existence, tier 2 disables the factor
  without deleting the row, so a tier-2 admin is **row-present and permanently unverifiable** — enrolled,
  so never sent to set-up; never verifiable, so never let past — and because ticket 19 made the challenge
  eager they never reach an admin route at all. `423 FACTOR_DISABLED` is therefore owed on **three**
  surfaces, not one response: the `factors` object (or the client can only discover it by refusal), the
  **entry point's row-read**, which is the load-bearing half since without it the server keeps inviting a
  challenge that cannot succeed, and every verification refusal. "One response, the hundredth failure" dies
  on ticket 23's own amendment §4, which routes the next login through forced password change and then
  resumes the loop with no response left to carry the code; **there is no path from tier-2-disabled to
  enrolment except an admin deleting the row.** `enrolled: false` is the tempting wrong fix — it routes to
  enrolment, which 409s off row existence, and hands the admin a self-service path around a control that
  exists to force rebinding. 409 lost to **423** because a 409 on `GET /api/admin/users` conflicts with
  nothing, so ticket 23 §12 goes to **three** recorded deviations; the enum is stated as **15 rows / 16
  identifiers** rather than inherited, because ticket 06 contradicts itself at lines 394 and 436. Tier 1 is
  `429` + `Retry-After` on ticket 06 §11's **carve-out quoted rather than re-derived**, which is also why
  ticket 09's refusal of that header on the password axis does not transfer — and it costs two things the
  first draft called free: `LOCKED` is a **new member of a sealed vocabulary**, and the discriminator is a
  **negative assertion about a different envelope producer**, since ticket 09's 429 comes from a filter
  registered before `SecurityContextHolderFilter`. Also closed: `AdminActionGuard`'s two refusals had no
  codes at all, and the copy needs **three** conditions because ticket 10 tightened the predicate — redeem,
  promote, enrol. The step-up **queue, prompt and replay all belong to the client** (`NullRequestCache` plus
  a no-content success handler), single-flight, with **re-bootstrap before replay** or every step-up
  manufactures the same 403 ticket 08 refused for login. Sign-out is terminal, but **narrower than drafted**:
  ticket 08 §12's filter ordering already sends absolute expiry to 401, so the mint-a-session-to-sign-out-of-it
  path is **idle expiry and eviction only** — and ticket 06 §10 assigns that 403 **no code**, so the
  assignment is owed too. Five of this ticket's own premises were wrong, four in the direction that made a
  chosen answer look hard-won: **ticket 11 contains no client-caching instruction**, so a three-option
  tension was scaffolding around an invented sentence; **`retry: false` was not forced** by tickets 09 or 21,
  neither of which reaches the endpoint, and a status-aware predicate is strictly better — but the real
  finding is bigger and survives any client policy, that **`GET /api/profile` is audited on every call and
  throttled on none**, an unbounded input to ticket 21's `daily`; ticket 22's stack mismatch is **false on
  the OTP half** (shadcn/ui on Base UI ships Input OTP) and merely **guidance-now-exists** on the dialog
  half, where re-hosting is real work; the `input-otp` CSP interaction is **silent, not warned**, so a
  console-quietness assertion passes in exactly the blocked case; and the tier-2 gap is **unacknowledged**,
  not conceded. The QR **stays a Blob**: the draft's three arguments for client-side SVG were two that
  ticket 23 had already weighed in those words and one new one, and its escape clause has an **unmet
  precondition**, so the amendment surface is zero and the remedy is structural — one effect creating and
  revoking, keyed on the Blob, which collapses ticket 22's two separate leaks into one cleanup, asserted as
  **revoke-count equals create-count**. React 19.3 double-invoking effects after **Fast Refresh** is the one
  genuinely new fact and it raises the cost of that effect rather than relocating server work. `Referrer-Policy`
  "on both pages" is **unsatisfiable in a single-document SPA** and becomes a document-wide meta placed first
  in `<head>`, with the split against ticket 05's `SAME_ORIGIN` named so a grep does not read two answers as a
  defect. Ticket 20's four constraints are verified with three corrections, `style-src` is left unsplit on the
  `as-9` grep reason, and the body's "`%VITE_X%` since 4.2" claim is **withdrawn as unverifiable**. Nine ADRs,
  six glossary terms, four register entries, an eleven-test delta, amendments to six tickets and handbacks to
  four — and **no new fog**: every consequence landed as an amendment or a handback on a ticket that already
  owns it, and the accessibility patch narrowed rather than grew.
  [Prototype](prototypes/14-auth-interceptor-state-machine.prototype.html),
  [verification asset](research/frontend-stack-and-browser-platform-verification.md).

- [Decide the contents and owner of the operational handover document](issues/25-operational-handover-document.md):
  **one generated inventory rendered twice behind a `verify`-phase gate, a three-field row schema, deployment-sequence
  ordering, and a break-glass path that stops being permanently unenumerated.** The ticket was framed against "six
  contents from five tickets"; an exhaustive extraction found **47 verdicts** resting on this document or on deployer
  action, across five distinct **L1** IDs (6.1.1, 6.3.1, 6.3.2, 6.4.1, 13.4.1), plus **nine obligations with no owner
  anywhere**. Ticket 17's register and this document are two renderings of the **same 47 rows**, so Deliverable 1
  stops being a document to write and becomes the compliance rendering of an extracted table, with a per-row anchor
  into the operational one — which is how ticket 17's no-reassembly constraint survives the split. **A generated
  inventory with no gate is a transcription with extra steps**, so the extractor runs in `verify` on the
  Dependency-Check precedent. The binding argument for generating it is **not** house style and the first version was
  too broad: **6.3.1 (L1) is not a general drift detector** — verbatim it is scoped to credential-stuffing and
  brute-force controls, so it binds ticket 09's items specifically — and the generalisation is five *textual* IDs
  (6.3.1 L1, 6.1.2, 6.2.11, 16.2.3, 16.3.3), with 13.2.4/13.2.5 demoted to **enforcement companions** because
  counting them lets a reviewer conclude the family was padded. Schema is **three** fields not four classes (a
  four-item ordered list reads as a severity ranking, which is the misreading the limitation class existed to
  prevent) and not two (FedRAMP pairs Responsible Role with Implementation Status **and** Control Origination, holding
  severity in the POA&M — ranking does not belong in this rendering); `status` is **scoped explicitly to the
  application's own enforcement**, without which `unmitigated` + `deployer` says one thing twice. **Vacancies are
  unmet acceptance checks, never a fourth responsibility value** — the extraction's sharpest result being that the
  most repeated gap is a missing *actor*, not a missing procedure. Four worked rows resolve as a **schema spike**,
  each having broken a previous design; the 47-row pass is deliberately **not** here, because doing it would
  hand-transcribe the table the gate exists to generate. **Seven external facts were checked and four were wrong in
  the direction favouring the argument**: the Appendix C key-wrapping strengthening is **declined**, because its
  approved modes are **KW and KWP only** while our TOTP secret uses AES-GCM, so invoking it to obtain a number we
  already have would volunteer a *mode* finding on the one path ticket 19 kept inside Spring Security after
  CVE-2026-47842 — and AES-192 is **Approved**, so ticket 24's Legacy row implies a false floor; the `git.properties`
  exposure path **does not exist**, because ticket 21 already generates neither file — but four sentences later that
  same ticket calls adding one "mild", so **the trigger is written into the argument it invalidates**, and since a
  property validator cannot see a Maven plugin the enforcement surface is the 13.4.1 jar test, not a prohibited-config
  entry; **13.4.1 is disjunctive**, rendered conjunctively in two tickets; and the **15-character floor is a SHALL
  barred two independent ways** (regular users are password-only, and the password is the §4.2.2.2 companion), a floor
  a CSP may raise since higher length is not a composition rule. On break-glass, an earlier draft **banked both limbs
  of one clause** — routing through §4.2.1's application-specific-method MAY while filing §4.2.1 against §4.2.2.1 as a
  contradiction — and is withdrawn: **Reading B is better supported at ~70%**, so nothing is filed against ticket 02
  and the adopted reading is recorded *because the alternative would have produced a compliance route*. The route runs
  inside the enumeration instead: **a password qualifies as §4.2.2.2's "single-factor authenticator bound to the
  subscriber account"**, no exclusion exists, and §4.2's own introduction contemplates "an authenticator that is still
  available to the subscriber". Ticket 09's runner already mints the right artefact — **what takes it out of class 2 is
  printing it to `System.out` instead of delivering it** — and the channel is explicitly carved out by §3.1.3.1, which
  is also why the binding reading is wrong: §4.1.2.2 **prohibits** email for binding codes, so B forbids the channel
  that makes the remedy possible. Recovery contacts were **rejected** (§4.2.1.3 requires subscriber-nominated trusted
  associates with a management surface; ticket 19's second admin is system-designated and nominated by nobody), saved
  codes **deferred to ticket 19 as a reopening trigger** (ticket 25 owns a document, not the auth design), and
  **option 2 plus a distinct confirmed recovery address taken**, converting §4.2.1.2's already-owed two-address SHALL
  into the mitigation. Grade: invalidation half satisfied under §4.3/§4.5, access-restoration half a **conditional pass
  with mail transport as its single named prerequisite**, §4.2.3's notification SHALL failed on that same one cause.
  **Declining recovery codes is not a NIST failure** — §4.2.1.1's issuance clause is a SHOULD on an optional method.
  Adopted: 13.4.1 (L1) and **13.1.1 (L2)**, whose SSRF limb ticket 10's three negative assertions already discharge;
  refused: pm-6's diagram and ac-3/ac-4's scheduled halves. Nine register entries, six ADRs, four reopening triggers,
  two glossary terms, and amendments to nine tickets — plus a **handover-item declaration rule written into this map's
  Notes mid-resolution**, because ticket 14 was claimed and live, and a convention it never sees is not a convention.

- [Run the threat model against the design](issues/15-threat-model.md): **fourteen threats over three diagrams, and
  the two graded High are both a control enforced correctly at a layer the threat does not pass through.** The honest
  headline is that **the eight flows this ticket named produced almost nothing** — login, registration, reset,
  self-service change and the admin surface are argued, priced and tested, and the report records each as clean and
  says why. Everything new came from three places: **composition** (six of fourteen, including both Highs — each
  ticket saw its neighbours, none saw the whole), **the DFD itself**, and **what arrived after the blockers closed**.
  TM-01: Spring Security's ordering puts exploit protection before authentication *and before routing*, so
  `CsrfFilter` fires on **every path including unmatched ones**, emitting ticket 13's row 13 with a `url.path` of raw
  client bytes — on a route with **no bucket, because ticket 09's budget table is a route allowlist whose default no
  ticket states**, and rows 12/14 arrive the same way through deliberately-unthrottled `/api/admin/**`. All three sit
  in ticket 21's rate-above class and **row 46 caps only `RATE_LIMITED_SOURCE`**, so this is the amplification ticket
  13 closed for rows 5 and 6 arriving through a door row 46 does not cover, aimed at the file that deliberately has
  no `total-size-cap` — and it is High because **ticket 21's `90 × daily` and `daily × lead_days` are both computed
  from a quantity an unauthenticated attacker controls**. TM-12: ticket 24 routed the rebinding token to
  `System.out` *rather than a logger*, but ticket 13 emits audit NDJSON to **stdout in every profile** and ticket 25
  sequences a forwarder over it — **the same file descriptor**, and a collector does not distinguish who wrote the
  bytes, so the control binds the application and not the platform, for the credential of last resort on a channel
  present in every profile by design; ticket 25's §4 grades that item `enforced` and its step-7 acceptance check is
  **unpassable as written**. Ten more: `trace.id` is **caller-supplied** (W3C §4.3 adopts a valid inbound
  `traceparent`, §7.2 names forged collisions as an attack) and ticket 13 made it the correlation **join key**; the
  **tier-2 trip inverts the pinned lock order**, writing `users` while holding the TOTP lock against
  `AdminActionGuard`'s opposite acquisition, and the two sides fail asymmetrically because ticket 23 deliberately does
  not fail open — so contention rejects a legitimate admin's **correct** code; the **factor gate is single-layered
  while the role gate is double-layered**, *because* ticket 23 correctly abandoned the global annotation, so a
  mis-written matcher has nothing behind it; `GET /api/hello`, **PRD Story 5's only endpoint, has no owner** and fails
  closed on `denyAll()`; session attributes are **JDK-deserialised with an unverified filter posture**, where ticket
  24 conceded read-means-hijack and write is a gadget path; **no global bulkhead** exists, and ticket 10's
  unauthenticated-BCrypt claim holds for registration only; **any admin can take over any other admin** by composing
  reset issuance with factor reset, which nobody had stated as a whole; the tombstone **records no role**, so
  privileged deprovisioning loses its evidence first; **row 46's `N` has no value, key or test** although ticket 21
  invoked the seam rule for it; and activating ticket 25's recovery-code route against the `dev` stub would **invert
  ticket 10 §12's containment** from partial to total for administrators, recorded as an amendment rather than a
  reopening because the weakening is contingent on a trigger that has not fired. Ticket 09's residual arithmetic was
  stress-tested first as instructed and **holds unchanged**. Three new tickets ([26](issues/26-unbudgeted-routes-and-audit-volume.md),
  [27](issues/27-inbound-trace-context.md), [28](issues/28-out-of-band-privileged-channels.md)), six amendments
  across four tickets, eight build-phase assertions, four ADRs plus two amended, six accepted risks with **"no MFA"
  withdrawn** because ticket 19 closed it, seven handover items, and **no new fog**.
  [Model](threat-model/secured-hello-world.json), [report](threat-model/report.md),
  [verification asset](research/threat-model-external-fact-verification.md).

- [Decide the rate-limit default for unbudgeted routes, and bound pre-routing audit volume](issues/26-unbudgeted-routes-and-audit-volume.md):
  **the budget table stays an allowlist for request rate, a second budget meters session-store misses, and the
  audit bound moves to the emitter in two tiers keyed on two key spaces.** The load-bearing sentence is that
  **per-source rate and distinct-source count are orthogonal and volume is their product** — so row 46's cap
  applied to un-keyed rows bounds nothing, `N` distinct sources times unbounded rows each still being unbounded,
  and ticket 13's own finding for rows 5 and 6 inverts: a budget buys a factor of itself and nothing more. Three
  of this ticket's premises were wrong. **The unauthenticated pair is 11 and 13, not 12, 13 and 14** — rows 12 and
  14 carry a resolved actor, because anonymous fails `hasRole` and becomes 401 under ticket 23's role-first
  ordering, so they cost a credential, while **row 11 is reachable on any safe method with a fabricated cookie and
  appears in no finding and no alert class.** **The flood is the database as well as the disk, and it is
  method-agnostic**: `SessionManagementFilter` evaluates `containsContext` *first*, that opens with
  `getSession(false)`, and `DelegatingSecurityContextRepository` polls the HttpSession delegate before the cheap
  one — so every request bearing a Base64-decodable cookie costs one `LEFT JOIN` with **no id-format validation**
  under `PROPAGATION_REQUIRES_NEW`, on any path, including `/actuator/health`. `CsrfFilter` is *not* that path: it
  returns before dereferencing on safe methods, and a first draft's claim that actuator was "exempt without needing
  an exemption" was right about `permitAll` and `CsrfFilter` and wrong about which component mattered. And
  **`StrictHttpFirewall` imposes no URI length limit**, so ticket 13's numberless raw-URI cap is a 15× term against
  Tomcat's 8 KB combined budget — now 256 characters with a truncation marker. Default-deny was refused rather than
  overridden: a limiter before `SecurityContextHolderFilter` cannot tell an admin from an attacker, so any catch-all
  it applies to `/api/admin/**` is exactly what `09:358-360` declined. Rejecting unmatched paths early was declined
  too — it makes a route oracle of `denyAll()`, and its mapped-set union could not be verified as excluded from
  `getHandlerMethods()`, so it becomes the hand-written list it exists to avoid. **"An admin consumes zero tokens,
  ever" was drafted and is false**: ticket 08 omits `Max-Age`, and Spring Session never clears a non-resolving
  cookie because `isInvalidateClientSession` needs `requestedSessionInvalidated`, which only `invalidate()` sets —
  so the honest property is *live session pays nothing, expired session pays about two, self-healed by the lazy
  CSRF bootstrap*, with the fan-out being **browser session restore and tabs**, not in-page parallelism, since
  `14:207` puts `/api/csrf` off the load path entirely. The sharpest find is the fix's own fail-open:
  **`resolveSessionIds` returns a list and the loop runs to exhaustion on a miss**, so ~130 duplicate cookies fit
  the 8 KB budget and one token would be charged for ~130 transactions — a budget cannot be correct while the cost
  per request is attacker-set — and capping the list to one element **requires registering a `CookieSerializer`
  bean as well**, because Boot's `DefaultCookieSerializerCondition` silently stops applying every
  `server.servlet.session.cookie.*` property once a non-`CookieHttpSessionIdResolver` resolver bean exists, which
  would unwind `__Host-SESSION`, `SameSite=Strict` and the omitted `Max-Age` with nothing failing. The ordering
  residual is **inherited, not introduced** — `getRequestedSession()` already assigns from the first element before
  any validity check, and RFC 6265 §4.2.2 names the duplicate-name case — and its closure is `__Host-`, which is
  ticket 08's, rests on an Internet-Draft ticket 09 declined `RateLimit` headers over, and does not stop same-host
  duplicates or isolate ports. On the emitter, **tier 2's population turned out bounded by a control this ticket can
  name** — registration stores no credential and outside `dev` the activation token reaches nobody — so the drafted
  7,200-accounts-a-day objection is refuted, but the cap is taken anyway because **the bound is the absence of a
  mail transport the map plans to add**, and that is its reopening trigger. **One integer and one timestamp replaced
  a 10,000-entry set**: every keyed row carries the count it replaces and the window's first-seen, which answers
  16.2.1 on magnitude, gives row 35 a reason to exist after keying, and lets row 46 carry **two exact numbers**
  (`source.distinct_count`, `events.untracked_count`) bracketing the truth at `[N, N + untracked]` — closing a
  defect neither 13 nor 21 saw, that `source.distinct_count` was not computable under any bounded structure. Row 35
  is **keyed, not deleted**: no standard mandates it and 16.3.2 puts all-decisions logging at L3, but two recipes
  emit it and one puts it on the *audit* logger, so deletion was the only option with no textual cover. **`daily`
  lands as a formula** with one deployer input and a measured `bytes_per_row` — ≈27 MB/day at 100 users, so
  `90 × daily ≈ 2.4 GB` and `threshold ≈ 54 MB`, five times the default ticket 21 overrode — which hands the fog
  patch its precedent. Coverage is **enumerated from `getHandlerMethods()`**, with its unverified exclusions stated;
  it closes the endpoint gap while §2 and §4 close the band gap, which is ticket 15's transferable finding twice
  over. Standards position corrected in both directions: **every hook is L2 against a declared L1 target**, so
  2.4.1, 2.1.3, 2.3.2, 15.1.3, 15.2.2, 15.3.4, 16.1.1 and 16.3.3 are all adopted-because-cheap; **16.3.1 is not
  engaged at all** because nothing keyed is an authentication operation, and saying so beats conceding it; ASVS is
  **silent** on aggregation rather than supportive, so the licence is the corpus's `:221` and `:214`; and a drafted
  "correction" that V16 has no L1 requirement was **retracted**, the fact already sitting in six places on this map
  with no ticket ever having mis-tagged it — this map's verification rule catching its own answer. Twelve ADRs,
  seven glossary terms, six register entries, four reopening triggers, twelve tests (two of which exist because a
  citation cannot prove a claim about our assembled chain), four handover items, amendments owed to nine tickets,
  and **one new ticket**: [ticket 29](issues/29-anonymous-session-row-growth.md), because the read side is now
  bounded and the write side is not — `/api/csrf` mints ~450 live anonymous rows per source, attacker-multipliable
  by source count, against an H2 file **no instrument watches at all**, and every cheap remedy collides with a
  resolved decision.

- [Decide the rebinding runner's output channel and accountability, given that stdout is collected](issues/28-out-of-band-privileged-channels.md):
  **the runner runs offline as a planned outage. Credentials go in and nothing secret comes out. Every destructive
  run is plan-then-apply, bound to a digest of account state.** The channel question was dissolved, not answered.
  The finding underneath it was that **the runner had never been able to run**: ticket 24 mandates H2-file,
  embedded file mode is single-writer, and a second JVM cannot open the database the application holds. That had
  been true for the entire documented life of the rehearsal meant to prove it. The fix is the same jar with
  `web-application-type=none` and the app stopped, which makes sole-admin recovery a **planned outage** nothing had
  priced. It buys two things: no concurrent `AdminActionGuard`, and a quiescent database between plan and apply.
  An `AUTO_SERVER` socket and a loopback TCP listener were rejected on the record. H2's own Spring example passes
  `-tcpAllowOthers`, and its admin can `CREATE ALIAS` onto arbitrary Java, which our datasource user *is*.
  The offline model **does not** make stdout uncollected: a Job is PID 1 and a one-shot unit goes to the journal.
  So the channel was **inverted**: the operator supplies the password on stdin, never as an argument (CWE-214). It
  goes through `PasswordService` with no exception and forced change, and nothing secret crosses any stream.
  Ticket 09 §R.3 had argued that shape was barred at L1, and **it had 6.4.1 and 6.4.6 inverted**: 6.4.1 (L1)
  governs system-generated secrets, and the requirement forbidding operator choice is 6.4.6 (L3). That pass had
  been *adopted* (it decided ticket 10's invite token), so it is withdrawn **scoped to the runner path only**, where
  the token design had already made it notional. The batch leg mints nothing, which degrades a mass-lockout event
  to an **uncosted RTO** rather than making it "unrecoverable". The offline fix opened a **tool that reports success
  and changes nothing**. Ticket 24's validator already catches an unset URL, but a wrong *file* path auto-creates an
  empty database that Flyway migrates, and ticket 11's seeder then runs in the runner process. It is closed by six
  preconditions, the most important being a lazily attached audit appender, since Logback is single-writer and
  the H2 lock guards only the database. The first-drafted existence check **refused the tool's primary use case**,
  the lost authenticator, so loop protection moved to the state-bound confirm digest instead: a leftover invocation
  fires **at most once**. A stale copy passes every check and stays `procedural`. `FILE_LOCK=NO` joins the validator
  because it silently disables the interlock. Accountability is `labels.operator_claimed_id`, ECS's own shape, so
  there is no fourth custom field and no exception to ticket 13's key-absence rule. It carries a stated PDPA trade
  and is corroborated by the OS user, never `user.name`. Intent and outcome rows make the post-commit window
  detectable, and ticket 21 inherits an absence-detection class **with its cost attached**. Ticket 25's TM-12
  placeholder was edited in place: the item is now "no secret is emitted", `asserted-by-test` with a named test.
  Step 7 gains negative cases and a **recurrence** that includes JDK, Boot and H2 upgrades. **6.1.1 (L1) is pass-with-note pending
  first rehearsal, and F if it comes back red.** Three premises that failed were drafted during this ticket's own
  grilling; **four of the five findings that shaped it came from checking premises, not from answering the
  question as posed.** Five ADRs plus two amended, seven register lines, eight handover items, twelve tests to
  ticket 16, amendments to nine tickets, and one graduation:
  [ticket 30](issues/30-sole-admin-bootstrap-premise.md), because ticket 11's "one admin, not two" rests on a
  premise ticket 09 removed. [Verification asset](research/rebinding-runner-channel-and-process-model-verification.md).

- [Decide inbound trace-context handling](issues/27-inbound-trace-context.md): **every inbound trace is restarted
  at the boundary, so `trace.id` is server-generated again and ticket 13's correlation join costs nothing.**
  - **Mechanism.** A header-stripping wrapper at `HIGHEST_PRECEDENCE` runs before the observation filter, which
    itself runs before Spring Security. It strips a fixed list plus `fields()`, matching case-insensitively and
    hiding the headers from all three lookup methods.
  - **Formats.** Boot accepts **three** formats by default, not one (W3C, `b3`, `X-B3-*`), plus `tracestate` and
    `baggage`. W3C §3.4 sanctions the restart.
  - **Cost.** Nothing: no gateway, no browser tracing, no outbound call, no exporter.
  - **Rejected.** Gating on authentication is possible, but it is custom code and cannot protect the
    pre-authentication login path. Accepting was priced and rejected.
  - **Also decided.** Baggage is off, with a behavioural test. `correlation.id` is dropped. Ticket 16 row 4 becomes
    "no inbound trace header ever sets `trace.id`", with a pinned-value **collision** case and a `0.0`-probability
    sampling case. "Not parsed, not logged" replaces header validation, scoped to application logs with the Tomcat
    access log disabled.
  - **Corrections.** Ticket 15's W3C §4.3 citation is non-normative. Its proxy handover item cited lm-16 where lm-4
    is the right control, and the item is retired and replaced. Browser pinning was always blocked by Fetch plus
    ticket 05's `allowedHeaders`, **not** by `SameSite`.
  - **Owed.** One ADR, carrying its reopening triggers; one glossary term; two handover items; a tripwire at
    ticket 05 line 784; amendments to 03, 05, 06, 08, 13, 15, 16, 17.
  - [Verification asset](research/inbound-trace-context-propagation-verification.md).

- [Decide what bounds anonymous session-row growth, and what watches the H2 file](issues/29-anonymous-session-row-growth.md):
  **only one route creates anonymous sessions, anonymous expiry is fixed at creation + W, and new anonymous
  sessions are refused (shed) at a count cap or when free space falls below a reserve that scales with the live row
  count.** Live `SPRING_SESSION` rows never exceed `N_max` = 100,000, whatever the number of sources.
  - **Two premises were wrong, both in the direction that hid the problem.** `CsrfFilter` creates a session on
    **every** unsafe request that has none, on any path, before its 403. And any request with a live cookie keeps
    an anonymous row alive, so ~450 per source was never a ceiling.
  - **Per source:** 480 unexpired, 510 physically present.
  - **The pin deviates from `08:118-121`** with ticket 08's reasoning answered. A negative interval must never be
    saved, because such a row can't be deleted by any code path.
  - **A `k × F` reserve was rejected because it latches**: the file keeps its high-water mark while open.
  - **The count cap is recorded as a deviation from shedding's own rationale.** It denies new logins at about 196
    sources, regardless of disk size, because the per-row cost was measured only up to 100k rows.
  - **Instrumentation:** a custom `h2Data` indicator that shares one cached COUNT with the shed check and never runs
    its own, plus two gauges. This reverses ticket 21's decline on verified facts.
  - **Disk sizing nearly triples, to about 6.96 GB.**
  - **Admin recourse while shedding:** wait, or the edge per-source limit. The trusted-range exemption and an
    offline purge verb were rejected on the record.
  - **Owed:** five ADRs plus one amended, and one graduation:
    [ticket 31](issues/31-ipv6-source-keying.md), because every per-source key on the map uses the full address.
  - [Verification asset](research/anonymous-session-growth-and-h2-file-verification.md).

- [Decide IPv6 source keying for every per-source limiter](issues/31-ipv6-source-keying.md): **one source key for
  every per-source control — IPv4 /32, IPv6 masked to `app.security.client-ip.ipv6-prefix-length` (default 64,
  validated to [48, 128]) — derived from parsed bytes by one `SourceKeyResolver`, with `source.ip_hash` hashing the key
  under a pinned input format.** A /64 stops one host rotating, not one subscriber: RIPE-690's /56-per-home does not
  carry to APNIC, which permits /64 to /48 per site, Singapore ISP sizes are unverified, APNIC Whois has no RFC 9977
  `prefixlen:`, and one AWS VPC supplies 256 /64s. The load-bearing finds were in ticket 09, not IPv6. **§R.6's "7×"
  was a units error** (disables compared with accounts locked): the axis gives about 100× per bucket, the ceiling
  assumed one bucket per attacker, and it had already failed under IPv4 at about 20 keys (`P ÷ k`). **The ladder
  carried a fencepost:** 19 locks, not 20, so 840 / 260 / 580 min, not 900 / 300 / 600. The ladder is the only bound
  that doesn't depend on source count, so it gets a **startup floor** derived from the threshold, cap, alert and
  rungs. A second /56 key and a global lockout cap are declined on it. R.6's refusal is specified as reading (B),
  with the expiry pinned to first insertion, because Caffeine's `expireAfterWrite` would otherwise let five re-locked
  members hold an egress at 429 for 14 h. The unparseable-token bucket, the no-DNS parse test, and the ban on
  `getRemoteAddr()` / `getRemoteAddress()` outside the resolver close the raw paths in our code only. **The user's own
  reset cannot clear a disable outside `dev`**, since no mail transport exists, so in this build every disable needs
  the `--rebind` runner and the 9.7 h window is the only time to act without it. Two ADRs plus three amended, one
  glossary term, eleven register entries, five handover items, twelve tests, and amendments to ten tickets.
  [Verification asset](research/ipv6-source-keying-verification.md).
- [Decide whether ticket 11's one-admin bootstrap survives the removal of its premise](issues/30-sole-admin-bootstrap-premise.md):
  **seed one admin, conditional on a second *enrolled* admin invited before go-live; factor reset exempt from the
  two-admin count; ADR 13 restated as three routes keyed on the single `authenticable` predicate.** The premise had
  already been replaced once, by "the runner closes the cap path", and it was the replacement that weakened when
  ticket 28 made the runner a planned outage. A second *seeded* admin closes nothing, because a targeted cap takes
  ≈14 h per account whatever the source count, so the seed-one decision stands. It stands only because a second
  admin makes the everyday cases (forgotten password, lost phone) recoverable in-app instead of by outage. The finding
  that changed the design: **at exactly two admins the guard refused A resetting B's TOTP**, so ticket 19's
  compensating control refused its own reset. Exempting factor reset is safe on **who can reverse it**. After a factor
  reset the subject re-enrols alone. After a disable or demote only another admin can restore the count, and at two
  that is the actor who removed B. "The other paths are permanent" was wrong: only delete is. Cost: TM-08 widens
  to full takeover at exactly two admins. Routes:
  - Auto-expiry for lockout.
  - In-app reset when another authenticable admin exists. If that admin is only locked out, this waits and does
    not fall through to the runner.
  - The runner, pending first rehearsal, when no other authenticable admin exists.

  "Nobody invited the second admin" is detectable out of the box only by the step-7 attestation. The gauge needs the
  deployer to enable export, which ships off. One ADR plus two amended, four handover items, seven tests plus a gauge
  check, and amendments to ten tickets and both threat-model artefacts. No graduations.
- [Decide the test plan: which test proves which control](issues/16-test-plan.md): **one table of stable test
  IDs behind a `@Proves` traceability gate. No Spring slices. A forward-only clock that starts at real time, with
  `Ticker` and `TimeMeter` adapters as production design. Timing verified by counting `matches()` calls rather than
  by stopwatch. Playwright in, narrow.**
  - **What the checking found:**
    - Ticket 24 rejects in-memory H2 in every profile, so every test context runs on a file.
    - A `test` profile would count as production, so the stub is replaced by a capture bean instead.
    - Nothing on the map named ticket 09's constants. The keys and `k = 5` are now named, and this map's own
      seam-rule claim is corrected.
    - Ticket 05's cleanup-cron snippet contradicted ticket 29's arithmetic.
    - Failsafe 3.6.0 stopped honouring `-DskipTests`, so both plugins are pinned and any bump reopens it.
    - Cost-4 BCrypt would have let the lock race test pass with the lock removed.
    - TM-13's routes have no path, so their test is structural.
  - **§5 rows decided:** five rows no ticket had decided, among them a new 16 KiB body cap. Plus a two-part
    fidelity list separating harness limits from features not built.
  - **Graduated** [ticket 32](issues/32-test-plan-table-transcription.md), which transcribes the rows with exact
    per-source counts. It blocks 17, 18 and the `/to-spec` handoff.
  - **Owed:** six ADRs, three glossary terms, three handover items, and amendments to eleven tickets.
- [Transcribe the test-plan table](issues/32-test-plan-table-transcription.md): **349 rows in
  [`docs/test-plan/test-plan.md`](../../docs/test-plan/test-plan.md).** 460 owed tests came from 30 tickets, and the
  standards added 33 rows. 146 duplicates were merged into the owning ticket's row and 6 were retired. Eight rows were
  minted for controls the map enforces but no ticket tested.
  - **Every prescribed test is a T-ID or a register row.** That covers Standard §5, the PRD, MFA_Core §5 and
    Logging §5, 122 items. Nine of those register rows are now owed by ticket 17.
  - **200 source lists end in a `superseded by T-…` pointer.** The pointers are appended to existing lines, so every
    `ticket:line` citation on the map still resolves.
  - **What the checking found:**
    - Two decided enum codes never reached ticket 06: `FACTOR_ALREADY_ENROLLED` and `FACTOR_DISABLED`.
    - Ticket 24's validator table is missing the reset-link logger entry that 13 and 25 rely on.
    - 16's canary list still named runner tokens that ticket 28 removed.
    - Four rows used ticket 05's pre-`/api` paths.
  - **Audit trail** in [`test-plan/transcription/`](test-plan/transcription/reconciliation.md). Ticket 32 no longer
    blocks 17, 18 or the `/to-spec` handoff.
- [Produce the deferral register and ADR set](issues/17-deferral-register-and-adrs.md): **resolved as its own sizing
  decision. It split into six tickets and authors nothing.**
  - **The count:** a mechanical extraction of every owed item across all 32 tickets found 855 raw rows. Live among
    them: 198 new-ADR claims, 91 amendments, 325 register rows, 79 glossary terms, 64 triggers and 49 handover
    items. The extraction is in [`deferral-register/inventory/`](deferral-register/inventory/).
  - **Three of the ticket's own premises failed:**
    - The "47 rows" is ticket 25's handover slice, not the register.
    - The handover population is itself more than 47.
    - Standards-defect ordinals collide across 10, 11 and 24.
  - **The ADR rule changed.** Every decision is routed by where it survives: register, test-plan row plus
    rationale, handover, spec, or ADR. A decision becomes an ADR only if a maintainer reading the code and spec
    would plausibly undo it, with one decision per ADR and no target count.
  - **PRD deviations move to the register.** The conflict rule above is amended to match.
  - **The survival rule** (above) bars any `docs/` artefact from citing `.scratch/`.
  - **Children:**
    - [Build the canonical register and handover table](issues/33-canonical-register-and-handover-table.md)
    - [Route the ADR candidates and write the rejection log](issues/34-adr-candidate-list.md)
    - [Write the authentication ADRs](issues/35-adrs-authentication.md)
    - [Write the platform ADRs](issues/36-adrs-platform.md)
    - [Write `CONTEXT.md`](issues/37-context-glossary.md)
    - [Make `docs/` stand alone](issues/38-de-scratch-docs.md)
  - The compliance review gate now blocks on all six.
- [Write `CONTEXT.md`](issues/37-context-glossary.md): **81 entries in [`CONTEXT.md`](../../CONTEXT.md) at the repo
  root, with no ticket or line citations.** Every one of the 81 inventory rows is accounted for.
  - **Dispositions:** 64 defined as named, 5 renamed, 5 merged, 4 split and 3 dropped. The three dropped are the two
    withdrawn candidates and *current user*, which no ticket ever pinned.
  - **Collisions:** the four listed collisions are resolved in writing.
    - *Rebinding* becomes an umbrella with a password case and a factor case.
    - *Factor freshness* stays with 08, and 23's policy words get their own entries.
    - *Client IP* is the input and *source key* the key.
    - *Disable* merges into *authenticator disable*, with *account disable* holding the admin sense.
  - **Four more collisions were found and named apart:**
    - *tier* (factor lock levels vs audit keying tiers);
    - *discriminator*;
    - *pin*;
    - *authentication pathway* (10 counts seven routes, 23 three; 23's sense is kept, and the rest are
      credential-setting routes).
  - **The reconciliation stays in the ticket**, per 17 §0.
- [Route the ADR candidates and write the rejection log](issues/34-adr-candidate-list.md): **the filter produces
  74 ADRs (owner 35: 28; owner 36: 46) and a 92-line rejection log.** Every one of the 311 ADR-kind inventory rows
  ends in a decision, an attached amendment, a rejection line or a cited drop.
  - **IDs are fixed.** ADR-001 to ADR-074 are reserved, and the rejection log is written, in
    [`docs/adr/README.md`](../../docs/adr/README.md). Rejection lines carry a new surviving prefix, `REJ-nnn`.
  - **The filter reading:** "plausibly undo" means *undo and get away with it*. A reversal that a named test fails
    with a one-sentence rationale goes to the test plan, not an ADR. That is how 17 §1's own two examples were
    routed, and it sends 26 candidates to a `rationale` column.
  - **Every PRD deviation was filtered.** Thirteen pass and have an ADR. One fails: the Story 3 AC3 verdict. The
    beyond-PRD additions are register rows only.
  - **Neither owner's share fit one session**, so ADR writing is now seven tickets:
    - [35](issues/35-adrs-authentication.md), passwords and credential tokens;
    - [39](issues/39-adrs-lockout-and-throttling.md), lockout and throttling;
    - [40](issues/40-adrs-mfa-factor.md), the MFA factor;
    - [36](issues/36-adrs-platform.md), errors, sessions and CSRF;
    - [41](issues/41-adrs-admin-and-data-model.md), the admin module and data model;
    - [42](issues/42-adrs-logging-observability-config.md), logging, observability and configuration;
    - [43](issues/43-adrs-harness-handover-runner.md), the test harness, handover design and runner.

    The compliance review gate blocks on all of them.
  - **Detail** is in [`adr-routing/routing.md`](adr-routing/routing.md): register and handover inputs for ticket 33,
    rationale sentences and the ticket-local-ADR map for ticket 38.
- [Build the canonical register and handover table](issues/33-canonical-register-and-handover-table.md): **324 rows
  in [`docs/register/register.md`](../../docs/register/register.md), one source for both renderings, with no ticket
  or line citations.**
  - **How:** 424 rows were staged from every register, handover and trigger item. 100 of them merged into the row of
    the ticket that owns the decision. The build validates the schema on every row and fails on any violation.
  - **Handover population, computed:** 54 pre-rule + 41 from heading items − 26 overlap + 26 register-origin = **95**
    operational rows. Ticket 25 had stated 47 + 9; that extraction was never recorded.
  - **Defects:** 54 standards defects, renumbered R-STD-001 onward. The old ordinals survive only in the
    reconciliation.
  - **Pointers:** 808 source lines now carry a consolidation pointer, and no line numbers moved.
  - **Found:** a reopen trigger that had already fired, with nobody holding it. The forced-change expiry's audit
    reason confirms a correct password. It is R-AUD-018, and it graduated as
    [ticket 44](issues/44-forced-change-expiry-password-oracle.md). Two unowned FAILs were handed to 18: IM8 dp-8
    (R-FE-002) and ASVS 16.3.3's second limb (R-AUD-019).
  - **Audit trail:** [`deferral-register/consolidation/`](deferral-register/consolidation/reconciliation.md).
- [Write the platform ADRs: test harness, threat model, handover design and recovery runner](issues/43-adrs-harness-handover-runner.md):
  **ADR-064 to ADR-074 are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.**
  - **Verification added two things the sources lacked:**
    - the clock-built Caffeine ticker is a real monotonicity regression from Caffeine's default, not parity;
    - IM8 pm-6's "up-to-date" clause gives the threat model an update trigger, so leaving it un-updated fails pm-6
      as surely as deleting it.
  - **One inherited argument dropped as unverified:** ticket 25's §4.4 contrast.
  - **Residuals are named in words** until ticket 33 mints `R-` IDs. The ADR-to-residual list is in the ticket.
- [Write the platform ADRs: error envelope, sessions and CSRF](issues/36-adrs-platform.md): **ADR-029 to ADR-041
  are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.** Every external fact
  was checked at source. No decision reversed, but the checking changed what several ADRs say.
  - **ADR-030's sketch premise was half wrong.** Indexed Redis *can* find sessions by principal; only Boot's default
    Redis repository cannot. What rules Redis out is the second datastore.
  - **Lockout ends sessions, so ADR-034 removes the one-request kill, not the power.** Five failures still end a
    session. No ticket had joined these two.
  - **Three invariants have no test row:** the automatic tier-2 session kill, header-only CSRF resolution, and the
    sweep for triggers other than the cap.
  - The sweep runs at startup. The scheduled hosting that ticket 08 proposed is unverified.
  - ASVS 2.3.3 (L2) is a new register input for 33.
- [Decide where the 30-day forced-change expiry is checked, given its audit reason confirms a correct password](issues/44-forced-change-expiry-password-oracle.md):
  **the check moves to `preAuthenticationChecks` and throws Spring's `CredentialsExpiredException`.
  `isCredentialsNonExpired()` is hard-wired `true`. The wire stays a uniform 401, and the lazy expiry survives.**
  R-AUD-018 is regraded `fail` → `pass`.
  - `performPreCheck` rethrows the pre-check exception after a discarded `matches()`. So the reason, the event and
    the single BCrypt verify are identical for a correct and a wrong password.
  - A disguised wrong-password reason was rejected because the failure counter would still reveal a correct guess.
  - Guesses on an expired credential move neither counter, so R-LCK-001 is rewritten. T-ADM-015 is amended and
    T-ADM-030 is added.
  - ADR-046 was still `reserved`: its routing row and title are amended, and ticket 41 is briefed.
- [Write the authentication ADRs: the MFA factor](issues/40-adrs-mfa-factor.md): **ADR-021 to ADR-028 are written in
  `docs/adr/` and marked accepted in the index, as routed, with no regrouping.** The Spring Security 7.1.x and
  `AesGcmBytesEncryptor` claims were re-checked at source, and all held.
  - **ADR-024's routing filter sentence was too broad.** Only *issued* (emailed) codes invert containment, not the
    *saved* codes it defers.
  - **`Encryptors.stronger()` is not exposed to CVE-2026-47842, but it is deprecated and password-derived**, so it
    stays excluded on those grounds.
  - **Tier 2 is also cleared by the offline runner**, not only by the admin factor reset.
  - No new handover items or register rows.
- [Write the authentication ADRs: passwords, hashing and credential tokens](issues/35-adrs-authentication.md):
  **ADR-001 to ADR-009 are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.**
  No decision reversed. Two routing filter sentences failed against the Standard's text, and one inherited argument
  had a gap.
  - **The Standard does provide a self-service change endpoint** (Happy Path 13, §3.1, §5), so R-CRED-013's `fail` is
    a misreading. ADR-008 defends the forced-change rule and the direct `matches()` check instead.
  - **The Standard is not silent on the lock at admin reset.** It says the account stays locked. Clearing it at
    redemption is a deviation, argued in ADR-009 on the cap. R-STD-024 understates it.
  - **"A pepper cannot rotate" holds only for keyed hashes.** An encryption-layer pepper can rotate. ADR-004 declines
    it on weaker, stated grounds. This argument is new and open to challenge.
  - **Measured, not estimated:** BCrypt cost 12 takes 208 ms (a change request is about 1.04 s, not 1.5–2 s).
    zxcvbn4j scores are run, and `SecuredHelloWorld2026!` passes the gate, so only `CONTEXT_TERM` stops it.
  - Three register corrections handed to ticket 18, since ticket 33 is resolved. No new handover items.
- [Write the authentication ADRs: lockout, throttling and source keying](issues/39-adrs-lockout-and-throttling.md):
  **ADR-010 to ADR-020 are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.**
  The attached amendments are folded in: the 840 / 260 / 580 ladder with its startup floor, reading (B) with the
  first-insertion pin, `k = 5`, and source-key units.
  - **The observation-window citation is the OWASP Authentication Cheat Sheet**, not the Blocking Brute Force Attacks
    page that ticket 09 implied.
  - **A capped password is recoverable in-app while another authenticable admin exists.** The runner is needed only
    when none does. A draft that said otherwise was corrected.
  - **For ticket 18:** no test-plan row asserts that a tier-2 factor disable sets `force_password_change`.
- [Write the platform ADRs: logging, observability, configuration and topology](issues/42-adrs-logging-observability-config.md):
  **ADR-054 to ADR-063 are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.**
  No decision reversed. Three routing filter sentences failed at source: **IM8 as-8 names no `@Value`** (only
  `im8-review` does), `health`-only exposure is already Boot's default, and `Log_Schema.md` names no key rather than
  prescribing plain SHA-256.
  - **ADR-059's trigger was wrong:** a hash source allows a fixed inline script from a static host, so only
    per-response inline script reopens the topology. Any `script-src` change still reopens the enrolment re-auth decline.
  - **IM8 as-9's text asks for a header**; the meta tag satisfies only the tool.
  - **For ticket 18:** nine missing or broken test-plan rows (including T-HDR-003's unworkable preview setup) and
    register corrections to R-CFG-007, R-CFG-012, R-CFG-020, R-HDR-003 and R-SES-004. No new handover items.
- [Write the platform ADRs: admin module and data model](issues/41-adrs-admin-and-data-model.md): **ADR-042 to
  ADR-053 are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.** ADR-048's
  eight amendments and ADR-046's pre-authentication brief are folded in. No decision reversed.
  - **ADR-046 carries one open item:** nobody decided whether completing a forced change clears
    `credential_issued_at`. If it does not, a later tier-2 disable meets a stale issue time and locks the admin out at
    once. Owed to the spec; ticket 18 should check it.
  - **ADR-048:** the cascade is covered by the uniform lock set, not a blind channel. Break-glass and the runner are
    one channel today.
  - **ADR-051:** Hibernate's `ALL` value was never weighed; the ADR says why `NAMED` suffices.
  - **For ticket 18:** R-ADM-002 still says `uuid` (now `user_id`). R-ADM-008 and R-ADM-015 blur the four-path lock
    set with the three-path count. No new handover items.
- [Make `docs/` stand alone](issues/38-de-scratch-docs.md): **nothing under `docs/` or in `CONTEXT.md` points into
  `.scratch/` any more.** The only grep hits left are RFC section numbers, one recipe line reference and
  audit-catalogue row numbers, each listed with its reason.
  - **Test plan:** `source` is removed and `rationale` added in its place, so the table stays at 10 columns. 288
    `clause` cells were rewritten to primary clauses plus `ADR-`/`REJ-`/`R-` IDs, and none of the cited IDs dangles.
    30 rationale sentences were added, each with its primary citation. T-BLD-008 was amended by ID to fix the header.
  - **Threat model:** moved to `docs/threat-model/` and de-scratched.
  - **Register:** R-AUTH-003 was rewritten because the error contract is a build artefact.
  - **New deviation found, R-AUTH-005:** a 401 without `WWW-Authenticate` breaks RFC 9110 §15.5.2's MUST.
  - **For ticket 18:** one rationale claim is still uncited (T-AUD-011's Logback arithmetic). The threat-model
    rewrite was checked by grep and ID resolution only, not line by line.
- [Compliance review gate](issues/18-compliance-review-gate.md): **passes; nothing reopens and `/to-spec` can run.** Rescoped from a five-skill
  audit to a triage gate, because no spec exists and the design review had already happened ticket by ticket.
  - **Inbound items fixed at source:** 11 register corrections, 5 owed register rows, 9 test rows added and 4
    amended, and ADR-046's open item decided (a user-chosen password clears `credential_issued_at`, redemption
    included).
  - **Declared:** IM8 Low Risk. WCAG 2.2 AA with a manual pass. ASVS 16.3.3's second limb accepted as an L2
    partial.
  - **IM8 ac-3 is a recorded FAIL**, under an amended gate rule: a FAIL outside PRD scope may stand as a stated
    residual.
  - **Three premises failed on checking:** dp-8 is N/A for public-facing apps (reversing an agreed label
    decision), ac-7 had been read as ac-8, and ck-1/2/4's N/A went stale once the app held keys.
  - **Ten consolidation staging IDs** had leaked into the register and are replaced by their `R-` IDs.
  - **Fog cleared:** notifications out of scope, the accessibility target decided, spikes and ratios left as a
    deployer threshold.

## Not yet specified

In scope, but not yet sharp enough to ticket. Graduates as the frontier advances.

- ~~**Whether rate-above thresholds are expressible as formulas rather than integers.**~~ Answered as a formula
  for every keyed and capped row by [ticket 26](issues/26-unbudgeted-routes-and-audit-volume.md); the remainder,
  spikes and ratios that need a baseline this application cannot learn, was cleared by [Compliance review gate](issues/18-compliance-review-gate.md): it is a
  deployer-owned threshold held in R-OBS-004.

- ~~**Response-time normalisation.**~~ Resolved inside
  [Decide the API error envelope and the enumeration-safe response contract](issues/06-error-envelope-and-enumeration-contract.md)
  rather than becoming its own ticket: the framework's dummy-hash mitigation is the baseline, an artificial
  response-time floor is rejected with reasons, and the two ordering rules that stop our own code defeating
  the baseline are now **constraints ticket 09 inherits** (lockout checked inside/after authentication, never
  as a pre-auth branch on account existence; no `PasswordEncoder` fast path). The same ticket closed an
  unwritten sibling leak — the per-account 429 as an existence oracle — by keying the limiter on the
  submitted username rather than a resolved account.
- ~~**Owner notification mechanics.**~~ Ruled out of scope by [Compliance review gate](issues/18-compliance-review-gate.md); see Out of scope.
- ~~**Accessibility conformance target.**~~ Decided in [Compliance review gate](issues/18-compliance-review-gate.md): WCAG 2.2 AA, verified by a recorded manual
  keyboard and screen-reader pass before the `browser-test` gate (R-FE-005).
- ~~**Secrets and configuration handling for non-local environments.**~~ Graduated and now **resolved**:
  [Decide secrets and configuration handling for non-local environments](issues/24-secrets-and-configuration-handling.md).
  The TOTP secret encryption key gave the patch a second hard requirement — a key that must exist, must
  not live in the database, and must rotate yearly — which pushed it past deciding incidentally inside
  another ticket. It graduated no new fog: every consequence landed as an amendment on a ticket that
  already owns it.
- ~~**Password reset token redemption rate limits.**~~ Resolved inside
  [Decide lockout and dual rate limiting semantics](issues/09-lockout-and-dual-rate-limiting.md) as a
  ten-row budget table with an explicit **axis per row**, covering everything this patch had accumulated —
  reset issuance and redemption, ticket 07's self-registration, ticket 08's `/csrf`, and one endpoint the
  patch never knew about because ticket 06 created it afterwards (**activation-token redemption**). Two
  numbers moved: `/csrf` is **30/min, not 10**, because 10 breaks a developer reloading the SPA, which takes
  ticket 08's live anonymous session rows from ~150 to **~450 per source**; and the reset-request endpoint
  gained a **per-submitted-identifier** budget of 3/hour, because per-IP bounds nothing per address — 5/min
  across N sources is unbounded mail to one victim. `GET /csrf` having a persistent side effect is recorded
  as an HTTP safe-method oddity that **cannot be fixed**, since §3.1:238 requires the token be session-bound.
- ~~**Authorization matrix file format.**~~ Resolved: [Inventory the prescribed recipes](issues/04-prescribed-recipe-inventory.md)
  found `Common_Role-Based_Access_Control_Configuration.md` prescribes it, so it collapses into the
  admin module ticket as inherited config rather than becoming its own.
- ~~**HTTPS/HSTS handover notes for whoever deploys this.**~~ Graduated:
  [Decide the contents and owner of the operational handover document](issues/25-operational-handover-document.md).
  Ticket 20 gave the patch its third and decisive shape — the same-site constraint is deployment-blocking,
  the static host owes a header set including `frame-ancestors`, and nobody is nominated to terminate TLS —
  which took the total to six required contents owed from five tickets, four of them compensating controls
  for deferrals we have already justified on the grounds that this document exists.
- ~~**Password blocklist maintenance** and the **break-glass runbook for a TOTP-locked sole admin**.~~
  Graduated with the patch above, into
  [Decide the contents and owner of the operational handover document](issues/25-operational-handover-document.md),
  which now carries both as named required contents. Ticket 07 fixed the blocklist *source* (breach corpus
  sliced at ≥15 characters, version-pinned classpath resource) but not the refresh obligation — a blocklist
  that is never refreshed decays as a control while continuing to look like one. `MFA_Core` prescribes no
  unlock path for the TOTP table at all (Recipe 11 clears `PIN_USER_DETAILS` only) while mandating `lockedAt`
  be "cleared by admin", and ticket 19 named that runbook line as a compensating control for deferring
  recovery codes. Both were unowned prose; they now have a ticket that must decide which of them can be
  converted into an enforced check rather than staying prose.

## Out of scope

Ruled beyond this destination. Does not graduate.

- **Application code.** This map plans; `/do-work` builds.
- **Testcontainers, Docker-based tests, and any real Postgres/MySQL runtime** — user decision;
  PRD defers portability, so H2 serves dev and test. Accepted gap, stated plainly: H2 in Postgres
  compatibility mode does not prove Postgres portability.
- **Account hygiene scheduled jobs** (90-day inactivity disablement, 180-day role revocation,
  ShedLock serialisation). The standard mandates these; we defer them with written justification as
  operational lifecycle controls orthogonal to the PRD's auth scope. The single largest deferral. IM8 **ac-3** is recorded as an IM8 FAIL on this reason ([Compliance review gate](issues/18-compliance-review-gate.md), R-ADM-011).
- **MFA recovery and backup codes.** MFA itself is now *in* scope — see
  [Resolve the MFA scope conflict raised by IM8 ac-2](issues/19-mfa-scope-conflict.md) — but recovery
  codes are deferred with justification. The corpus never mentions them, and for a single-tenant
  reference app they would duplicate the admin-resets-admin path. Compensating controls: the
  two-enabled-enrolled-admins invariant and a break-glass runbook line.
- **MFA for regular USER accounts, including opt-in enrolment.** The factor gates the admin surface
  only. `/settings/mfa` is ADMIN-only and the prescribed advisory `MFAPrompt` is not built: an opt-in
  factor no authorization rule ever demands secures nothing while still carrying enrolment endpoints, a
  reset path, and tests for every user in the system. Deviation from `MFA_Frontend/Standalone`, recorded
  as an ADR. Returns only if ac-2's optional "all accounts" reading is later imposed.
- **PIN and OTP factors.** TOTP only. A PIN is "something you know", the same category as the password,
  so it fails ac-2's bar; email OTP is prohibited by NIST SP 800-63B-4 §3.1.3.1 for out-of-band
  authentication, would land the second factor in the application log given the stubbed `EmailService`,
  and would share a single point of failure with the account-recovery path. `MFA_Core`'s
  `PIN_USER_DETAILS` table and its PIN recipes are therefore not implemented.
- **Remember-me / persistent login.** The standard's Questions Q10 offers it as a decision point and
  recommends against it for high-security applications; the PRD never asks for it. Ruled out in
  [Decide session management and the CSRF contract](issues/08-session-and-csrf-contract.md), which also
  declined the cookie `Max-Age` hint on the same grounds — a persistent session cookie is remember-me
  by another name, and a long-lived token that mints fresh sessions structurally defeats both the 8-hour
  absolute lifetime and the single-concurrent-session cap.
- **CI/CD pipelines, containerization, hosting infra** — PRD out of scope.
- **JWT implementation** — PRD appendix only, documented alternative.
- **Local HTTPS setup** — PRD documents this as an accepted gap.
- **SSO / OAuth2 / OIDC** (`App-Standards/.../User_SSO/`) — different standard, not this app.
- **Batch password reset** (`PATCH /users/batchResetPassword`, cap 5000). Prescribed by the admin recipe, never
  asked for by the PRD, and declined in
  [Decide the admin module, role model, and initial admin bootstrap](issues/11-admin-module-role-model-and-bootstrap.md):
  it multiplies the "plaintext token returned exactly once, never logged" problem by up to 5000 in one response
  body, and its own recipe cannot decide whether the over-cap status is 413 or 400 where the standard prescribes
  400. Consequence recorded rather than left dangling: `BATCH_TOO_LARGE` is **removed** from ticket 06's closed
  enum, which is now 13 codes.
- **The RBAC privilege layer, role hierarchy, and startup role synchroniser.** Ruled out in the same ticket. The
  privilege indirection and `role-hierarchy` map one-to-one onto themselves with two roles; the synchroniser is
  actively dangerous, since `syncDBUsersBasedOnDefinedUsers()` performs `userRepository.deleteAll()` gated by a
  **flag rather than a profile**. Role definitions still come from configuration, so the principle survives
  without the machinery. No dev seed accounts of any kind.
- **A scheduled account-access review pipeline** (the compare-and-revoke half of IM8 **ac-4**). The *declared
  baseline* half is built — the authorization matrix is the baseline — so this is a narrowed deferral with
  written justification rather than the flat FAIL ticket 01 found.
- **Owner notification mechanics** (channels, retry, delivery failure) for every account-security event. Ruled out
  in [Compliance review gate](issues/18-compliance-review-gate.md): nothing can be delivered without a mail transport, which is out of scope. The SHALL failures it
  leaves are register rows R-CRED-022, R-CRED-024 and R-MFA-019, and R-CRED-026 is the trigger that reopens it
  when a transport enters scope.
