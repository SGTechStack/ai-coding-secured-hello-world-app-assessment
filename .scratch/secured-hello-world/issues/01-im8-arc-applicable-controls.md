# 01 — Extract the IM8 and ARC controls that bind this app

Type: research
Status: resolved
Blocked by: —

## Question

Which IM8 controls apply to this app, what exactly do they require, and does IM8 force anything the
PRD has declared out of scope — MFA for privileged access above all?

This plan is formally assessed against IM8 and ARC, so these controls are design constraints that
shape the other tickets, not a review at the end. This ticket blocks every design ticket on the map.

## What to find

Read `.kiro/skills/policies/references/im8-reform-app-policy.md` and
`.kiro/skills/policies/references/arc-framework-policy.md`, plus `.kiro/skills/im8-review/SKILL.md`
and `.kiro/skills/spec-compliance/SKILL.md` and anything they reference.

Report, grouped by design area — authentication and credentials, password policy, session
management, cryptography, audit logging, input validation, data protection at rest, error handling
and information disclosure, access control and least privilege, secrets and configuration:

1. The exact control, its file path and heading, and whether it is MUST or SHOULD.
2. Which of this map's design tickets it constrains.
3. Which controls the PRD as written would **fail**. The PRD's declared gaps are: no MFA, no HTTPS
   in local dev, stubbed email delivery, no CI/CD, no authorization granularity beyond USER/ADMIN,
   and audit logging as structured log lines with no audit table.

Then answer specifically:

- **Does IM8 mandate MFA for privileged or administrative access?** If yes, "MFA" moves off the
  map's Out of scope list and this becomes a scope conflict needing the user's decision. This is the
  highest-value question on the ticket.
- **Does IM8 require a durable audit store** (a table or external sink) rather than log lines? The
  App Standard demands 90-day retention; the PRD says log lines only. If IM8 requires durability,
  the PRD's approach fails.
- **Does ARC bind at all?** ARC covers agentic AI governance and capability boundaries; this app has
  no AI features. State plainly whether it is N/A, or whether some part still applies because the
  code is AI-generated.
- **What input format do `spec-compliance` and `im8-review` expect?** The plan must produce an
  artifact those skills can actually consume, which shapes what "Produce the deferral register and
  ADR set" writes.

## Done when

The applicable control list is recorded in the answer, each mapped to the ticket it constrains, with
the MFA and audit-durability questions answered yes or no rather than hedged.

## Answer

### Sources actually read

| Path | Read | Note |
| --- | --- | --- |
| `.kiro/skills/policies/references/im8-reform-app-policy.md` | yes | 36 controls, one line each. The *what*. |
| `.kiro/skills/policies/references/arc-framework-policy.md` | yes | 88 controls, all agentic-AI scoped. |
| `.kiro/skills/policies/SKILL.md` | yes | Triage → audit → fix → append summary. |
| `.kiro/skills/im8-review/SKILL.md` | yes (all 1501 lines) | The *how*: per-control applicability, checks, FAIL conditions, risk levels. This is the operative document. |
| `.kiro/skills/im8-review/output/im8-compliance-report.md` | yes | A worked example against a different app (EMR demo). Shows the output shape and how strictly the evidence rule bites. |
| `.kiro/skills/spec-compliance/SKILL.md` + `HTML-REPORT.md` | yes | Input/output contract for the spec-phase audit. |
| `App-Standards/**` (the map's governing standard, incl. `Appfw-Logging-Standards/`) | **not read by this ticket** | ~~does not exist in this workspace~~ — **corrected.** It does exist, at `c:\Users\zlimweil\Desktop\SGTechStack\App-Standards\`, outside the assessment repo; the original claim was an artefact of a sandbox scoped to the repo, not a fact. Tickets 03 and 04 read those files successfully and cite them by line number. This ticket's scope is IM8/ARC only, so nothing below depends on them — but the 90-day retention claim in this ticket's own wording is still **not** an IM8 requirement, which stands independently. See Q2, and see ticket 03 for what the logging standard actually requires. |

**Terminology caveat, load-bearing:** neither IM8 file uses the words MUST or SHOULD. `im8-reform-app-policy.md` states controls imperatively; `im8-review/SKILL.md` grades them with `**Risk Level:** LR: X | MR: X` and explicit **FAIL conditions**. So in the tables below I read **MUST** = the control applies to us and `im8-review` defines a FAIL condition for it; **SHOULD** = a recommended sub-item or a "Manual review flag". ARC *does* label levels explicitly (`## Control Levels`: Level 0 Cardinal "MUST be implemented, cannot be waived", Level 1 SHOULD, Level 2 MAY).

**Two things the plan must declare before any audit can even run:**

1. **Risk classification (Low or Medium).** `im8-review/SKILL.md` step 3 derives every finding's severity from the `LR`/`MR` column. With no declared classification, severity is underivable. Recommend declaring **Low Risk** and saying so in the Spec.
2. **User population: internal, public, or both.** Six controls (`ac-7`, `ac-8`, `ac-12`, `dp-8`, `lm-18`, `st-3`) switch between N/A and binding purely on this. The PRD never says. See "Scope conflict 2".

---

### The four direct questions

#### Q1. Does IM8 mandate MFA for privileged or administrative access? **YES.**

- `im8-reform-app-policy.md` → `## Access Control (ac)` → **ac-2**: "Require MFA for privileged account logins and privileged actions (step-up authentication)."
- `im8-review/SKILL.md` → `### ac-2: Multi-Factor Authentication (MFA) Enforcement` → **Applicability:** "Applies to privileged account logins and privileged actions (and optionally all accounts, per policy)."
- Same heading defines privileged accounts to include "`ROLE_ADMIN` … or any role that can manage users, modify system configuration, or access sensitive data at scale."

Our `ADMIN` role exists solely to manage users (PRD Stories 8–11). It is squarely a privileged account under that definition.

Critically, **ac-2 has no N/A escape hatch.** Compare the controls that do: `ac-6` ("If the application delegates authentication entirely to an external IdP … this control is N/A"), `ac-7`, `ac-8`, `as-12`, `ck-1/2/4`, `ga-8`, `lm-18`, `st-3`, `dp-8` all carry an explicit not-applicable branch. ac-2's applicability clause offers only "privileged accounts only" vs "all accounts" — never "not at all". The only way ac-2 goes away is if the app has no privileged accounts, and the PRD's entire admin module is privileged.

The PRD as written fails every ac-2 backend check: no MFA state fields on `users`, no second-factor endpoint, no two-step login flow, no step-up gate on the admin mutations.

**Consequence: this is a scope conflict. "MFA" must come off the map's Out of scope list and go to the user as a decision.** The map's current justification — that `Appfw-Mfa-Standards/index.md` reads as a feature router rather than a blanket mandate — may well be true of the App Standard, but IM8 ac-2 is an independent and unconditional source, and the map itself made this conditional: "if IM8 forces MFA for privileged access, this returns as a fresh effort." It does.

Cheapest compliant options, for the user to choose between:
- **TOTP step-up on `/api/admin/**` mutations only** (not at login). `im8-review` accepts step-up-at-action as satisfying ac-2, via "a Spring Security filter, `@PreAuthorize` expression … or custom annotation processor that gates the endpoint on a valid step-up token". Scope: one `totpSecret` column (encrypted at rest — ac-2 requires this explicitly), one enrol endpoint, one verify endpoint, one guard. Smallest change that closes ac-2.
- **TOTP at login for ADMIN accounts only** — two-step login, partial-auth state that cannot reach protected resources.
- **Accept the FAIL and register it as a deferral** with written justification in ticket 17. Defensible for a reference app, but it will surface as a High/Critical-band `ac-2` FAIL in every future assessment, and `spec-compliance` Step 4 requires the user to explicitly sign off on FAILs before decomposition.

#### Q2. Does IM8 require a durable audit store rather than structured log lines? **NO.**

- `im8-reform-app-policy.md` → `## Logging and Monitoring (lm)` → **lm-4**: "Log management and audit events (authentication, authorization failures, data access)." Nothing about persistence, a table, a sink, immutability, or retention.
- `im8-review/SKILL.md` → `### lm-4: Audit Logging` — the backend checks look for "audit logging framework: Spring Boot Actuator audit events, `@EventListener(AuditApplicationEvent.class)`, custom audit interceptors/aspects", plus JPA `@CreatedBy`/`@LastModifiedBy`, plus coverage of "authentication events, authorization failures, data access". Every one of those is satisfiable by emitting log lines. No check inspects a datastore.
- `### lm-15: Structured Log Formatting` goes further and makes **log lines the required medium**: PASS conditions are `logging.structured.format.file: ecs` (Spring Boot 3.4+) **or** a Logback `EcsEncoder`/`LogstashEncoder`/`JsonEncoder`.

Explicit negative findings, stated rather than inferred:
- The string "retention" appears **nowhere in the IM8 sources** — not in `im8-reform-app-policy.md`, not in `im8-review/SKILL.md`, not in `policies/SKILL.md`. I grepped `.kiro/**`: the only two hits are `spring-logging-review/SKILL.md` line 53 ("sensible rolling/retention policies", a recommendation about async appenders, not an IM8 control and not a duration) and `teach/SKILL.md` (pedagogy, unrelated). **No IM8 control sets an audit-log retention period.**
- The only **log**-retention-adjacent "90 days" in the IM8 sources is `im8-review/SKILL.md` line 1050, `### ac-3: Inactive and Expired Accounts` — **90 days of account inactivity**, not log retention. (`90 days` also appears in `im8-review/output/im8-compliance-report.md` restating that same ac-3 rule, and in `dependency-orchestrator/artifacts/*.json`, which are an unrelated app's user stories.) The ticket's premise that "the App Standard demands 90-day retention" is not traceable to IM8; if it is real it belongs to `App-Standards/Appfw-Logging-Standards/`, which is absent from this workspace. **Ticket 03 must settle it against that standard; IM8 does not settle it either way.**
- No IM8 control in these sources requires encryption of data at rest, or an append-only/immutable audit trail.

**So the PRD's "structured log lines, no dedicated table" survives IM8 intact.** Three conditions attach, and they are design constraints on tickets 03 and 13:

1. **Pin the format.** "Structured log lines" is not enough for lm-15 — which sits in the **LR: 2 | MR: 2** band, the highest severity tier. Choose ECS (`logging.structured.format.file: ecs`) or a named Logback JSON encoder, in the Spec, by name.
2. **Give lm-4 a named component to find.** The checks hunt for a framework, listener, interceptor, or aspect. Scattered `log.info(...)` calls inside service methods will read as WARN, not PASS — the worked example marked a real app's lm-4 as WARN for exactly this shape of ambiguity. Design a single audit emitter (an `AuditEvent` type → one structured logger) so there is one thing to point at.
3. **Sanitise.** `lm-19` (LR: 2 | MR: 1) FAILs on logging `exception.getMessage()` where the message carries PII, and on entities with `@Data` that log PII. Our reset tokens, session IDs, and emails all qualify. This couples ticket 03 to ticket 06's error envelope.

#### Q3. Does ARC bind? **Not to the application. Possibly to our own workflow — and that is a decision, not a fact I can read off the page.**

`arc-framework-policy.md` is entirely scoped to agentic AI systems: LLM, Tools, Instructions, Memory, inter-agent Architecture, agent Roles, agent Monitoring, and Cognitive/Interaction capabilities of agents. This app has no LLM, no tools, no agents. Those ~74 controls are **N/A**.

The honest complication is `## Capability — Operational (op)`, which governs **agent-generated code and agent database access**, and several are Level 0 (cannot be waived):

- CTRL-0068 (L0) code linters on generated code; CTRL-0070 (L0) review all agent-generated code before execution; CTRL-0071 (L0) static analysers; CTRL-0074 (L0) CVE scanning, block High/Critical; CTRL-0069 (L0) isolated compute, network blocked by default; CTRL-0073 (L0) command denylist; CTRL-0076 (L1) human approval for destructive DB changes; CTRL-0075 (L1) no agent write access unless necessary.

This plan and its eventual code are AI-generated, so these *read* as applicable to the development process. But `arc-framework-policy.md` contains **no applicability section and no statement of its own trigger** — it never says whether it governs AI-built software or only AI-featuring software. I will not invent that boundary. State it plainly in the Spec as a declared interpretation.

Recommended position, for the user to confirm: **ARC is N/A as a product requirement; the `op` code-generation controls are claimed as workflow controls.** Two of them we already satisfy by accident — the map's "OWASP Dependency-Check bound to the Maven `verify` phase with a CVSS failure threshold" is CTRL-0074, and it works without CI, which is why the no-CI/CD gap does not sink it. CTRL-0068/0071 want a linter and a static analyser (ESLint strict + Semgrep or SpotBugs on `verify`); CTRL-0070 is the human review gate we already run per ticket.

Practical consequence for ticket 18: `spec-compliance` Step 2 demands a verdict row for **every** control in `/policies` — that is 36 IM8 + 88 ARC = **124 rows**. The ARC block needs one blanket N/A rationale that can be stamped across ~74 rows, plus individual rows for the `op` controls we claim.

#### Q4. What input format do `spec-compliance` and `im8-review` expect?

They expect **different artefacts at different times, and only one of them can run now.**

**`spec-compliance`** — `### Step 1: Locate the Spec`: "Identify the Spec or TRD content in the current conversation", and halt if missing. Input is therefore **one Spec/TRD document loaded into the conversation** — not 18 ticket files, not a directory. `### Step 2: Audit against Policies` assigns each control one of `PASS` / `AUTO-FIX` / `FAIL` / `DEFERRED` / `N/A`, with the completion criterion that "Every control in `/policies` has a dedicated row in the audit table with an explicit verdict and a specific, logical reason." Output is HTML at `artifacts/spec-compliance/spec-compliance-{spec-slug}-{YYYY-MM-DD-HHmm}.html`; `HTML-REPORT.md` → `## Verdict table` fixes the columns at **Control | Verdict | Reason**. `### Step 4` then requires every AUTO-FIX and FAIL to be resolved with explicit user guidance before decomposition.

This pins down what ticket 17 ("Produce the deferral register and ADR set") must actually write:

- **Keyed by control ID**, one row per control, all 124. A register listing only our deviations will fail Step 2's completion criterion.
- Each row carries **verdict + a self-contained one-sentence reason**, because that text lands verbatim in the Reason column with no surrounding context.
- `DEFERRED` is a first-class verdict — "not applicable at the Spec phase and must be handled during implementation" — which is exactly the right slot for the map's account-hygiene deferrals. Use it; don't call them N/A.
- `policies/SKILL.md` warns: "**Never auto-fix:** scope decisions, security boundaries, permission models — mark FAIL." So MFA, the user-population question, and the role model cannot be quietly tightened in wording; they go to the user as FAILs.

**`im8-review`** — Step 1: "Identify the target codebase". The skill is explicitly a code audit ("Run this skill against a React 19 (TypeScript) + Spring Boot 3.4 (Spring Security 6.4) codebase"), and `### Evidence standard — framework delegation rule` insists "Evidence must come from the application's own code and declared dependencies", with "**'Delegated to framework' is WARN, not PASS**". Output is markdown at `artifacts/im8-compliance-report-YYYYMMDD-HHmm.md` per `## Report Format`.

**It therefore cannot consume this plan at all.** Ticket 18's gate can run `spec-compliance` now and must schedule `im8-review` post-build. Two knock-ons:

- **Version mismatch.** `im8-review` targets Boot 3.4 / Security 6.4; our baseline is Boot 4.1 / Security 7. The checks are literal — they grep for `server.servlet.session.timeout`, `.headers(h -> h.contentSecurityPolicy(...))`, `bucket4j.filters`, `logging.structured.format.file: ecs`, `@EnableMethodSecurity`. **Ticket 05 should prefer the config spellings `im8-review` recognises wherever Boot 4.1 still supports them**, or ticket 17 must pre-write the mapping note, or the audit will read compliant config as absent.
- **Ticket 16's test plan gets a free specification.** `im8-review`'s PASS conditions and FAIL conditions are the de facto acceptance criteria. Asserting them in tests (CSP header present, HSTS max-age, session timeout value, 429 on the 6th attempt, generic error body) converts the audit from an inspection into a regression suite.

---

### Applicable controls by design area

Every row cites `im8-reform-app-policy.md` for the control text and `im8-review/SKILL.md` `### <id>: <name>` for the checks. "Risk" is the `LR | MR` annotation. PRD verdicts are against the PRD *as written*.

#### Authentication and credentials

| Control | Force | Risk | What it actually requires | Constrains | PRD as written |
| --- | --- | --- | --- | --- | --- |
| **ac-2** MFA Enforcement | MUST | (none; defaults to 1) | Second factor for privileged logins **or** step-up on privileged actions; MFA secret encrypted at rest; partial-auth state cannot reach protected resources | **11**, new MFA effort | **FAIL** — see Q1. Scope conflict. |
| **as-4** Auth Rate-Limiting | MUST | 1 \| 1 | Both halves: `failedAttempts`+`locked` fields with a lockout listener, **and** `bucket4j-spring-boot-starter` with a `bucket4j.filters` block keyed on `getRemoteAddr()` over the login URL | **09** | PASS on intent (dual limiting is already settled). Note the check names bucket4j and the YAML shape specifically. |
| **as-15** Password Change on suspected compromise | MUST | n/a → 1 | A `forcePasswordChange` flag; **the unlock path must set it**; a filter blocking every endpoint except password-change and logout | **09**, **10**, **12** | **FAIL** — PRD Story 3 unlocks by letting `locked_until` expire, with no forced reset and no flag in the data model. |
| **ac-6** Default Credentials | MUST | (none → 1) | Same flag, set on admin-created accounts, admin resets, forgot-password temp credentials, and unlock; new password must differ from the temporary one | **11**, **12** | **FAIL** — Story 12 seeds an admin from config with no forced change. |
| **ac-12** SSO for Internal Services | conditional | (none → 1) | Internal users authenticate via the org IdP; **FAIL condition: "Frontend login page accepts username/password for internal users without an SSO redirect"** | **11**, **14** | **Conditional FAIL — see Scope conflict 2.** |
| **ac-7** Singpass/Corppass | conditional | — | N/A unless external public users or corporate users **and** high-risk transactions | — | N/A, but must be asserted with a reason. |
| **ac-8** Automated Account Lifecycle | conditional | — | SCIM/push or SSO JIT provisioning; "internal user accounts only"; N/A if no internal users | **11** | Conditional **FAIL** if internal — PRD has manual admin CRUD only. |

#### Password policy

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **as-5** Password Requirements | MUST | 1 \| 1 | Min length (check: `>= 8`); complexity; **requirements displayed before/during input**, not only on submit failure; confirm-field match; `type="password"` | **07**, **14** | PASS — ≥12 clears the bar. Frontend obligations are new. |
| **as-6** Salting and Hashing | MUST | 1 \| 1 | `BCryptPasswordEncoder` is on the accepted PASS list; FAIL on `NoOpPasswordEncoder`, raw MD5/SHA, no encoder bean, or passwords in web storage. **No client-side hashing.** | **07**, **14** | PASS. Note the catalog line names Argon2/scrypt as examples but `im8-review` explicitly accepts BCrypt — the settled BCrypt decision is safe. |

#### Session management

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **as-11** Session Management | MUST | 1 \| 1 | `server.servlet.session.timeout` set and **≤ 15 minutes**; session-fixation protection; `maximumSessions`; frontend idle timeout ≤ backend timeout; logout clears all client auth state | **08**, **14** | Underspecified → must pin a value ≤15 min and decide concurrent-session policy. |
| **as-10** HSTS | MUST | **2 \| 2** | max-age ≥ 31536000; PASS requires "Spring Security default (not explicitly disabled) **+ app serves over HTTPS**" | **08**, HTTPS handover note | **WARN/FAIL in the top severity band** under the local-HTTP concession. Never explicitly disable HSTS. |
| **dp-3** Data in Transit | MUST | (none → 1) | TLS enabled; no SSLv3/TLSv1.0/1.1; no cert/hostname validation bypass; flags hardcoded `http://` in config and REST clients | **08**, **14** | WARN. The Vite proxy to `http://localhost:8080` is exactly what the check flags; the worked example accepted it as dev-only. The manual-review flags let a documented TLS-terminating-proxy topology carry the burden — **so the map's "HTTPS/HSTS handover notes" item is load-bearing compliance evidence, not a nicety.** Also set `server.forward-headers-strategy`. |

#### Cryptography

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **as-14** Secure Crypto Libraries | MUST | **2 \| 2** | `SecureRandom` not `java.util.Random` (FAIL condition); no DES/RC4/MD5/SHA-1; no hardcoded IVs/keys; frontend must not use `Math.random()` for anything security-sensitive | **10**, **14** | Unspecified → the reset token's generator must be named as `SecureRandom` in the Spec. Cheap now, a FAIL later. |
| **ck-1**, **ck-2**, **ck-4** | N/A | — | Each carries an explicit skip: applies "only if the application manages its own keys". `ck-4` adds that an infra-level TLS keystore with no app-level key material is N/A. | — | N/A. Assert with reasons. Still: no `.pem`/`.key`/`.p12`/`.jks` in the source tree (ck-4 FAIL condition). |

#### Audit logging

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **lm-4** Audit Logging | MUST | 1 \| 0 | A recognisable audit mechanism covering authentication events, authorization failures, data access. **No durability requirement.** | **13** | PASS on approach (Q2), but needs a named emitter component rather than scattered log calls. |
| **lm-15** Structured Log Formatting | MUST | **2 \| 2** | ECS via `logging.structured.format.file: ecs`, or a Logback ECS/JSON encoder | **03** | Underspecified — "structured log lines" must become a named format. |
| **lm-19** Log Sanitisation | MUST | **2 \| 1** | Masking layout/filter; `@ToString.Exclude` on sensitive fields; FAILs on logging PII-bearing `exception.getMessage()` | **03**, **06**, **12**, **13** | Partial — PRD covers passwords only. Add reset tokens, session IDs, email, and the error-envelope path. |
| **lm-16** Key Signals Monitoring | MUST | **2 \| 2** | Actuator **or** custom Micrometer meters + a `MeterRegistry`; coverage of latency, traffic, errors, **and saturation**; absence of both paths is FAIL | currently map's "Not yet specified: Observability and alerting" | **FAIL — and this one is not on the PRD's declared-gap list.** Top severity band. The map's plan to defer observability until the audit catalogue exists is fine as sequencing, but lm-16 must land in the Spec. |
| **lm-18** WOGAA | N/A | — | Public-facing government services only | — | N/A (internal/demo). Assert. |

#### Input validation

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **as-1** Input Validation | MUST | 1 \| 0 | `@Valid`/`@Validated` on every `@RequestBody` **at the controller boundary** (service-layer-only validation is explicitly WARN); Bean Validation on DTOs; validated `@PathVariable`/`@RequestParam`. Frontend: Zod + resolver, **runtime validation of API responses**, validated `useParams`/`useSearchParams`, no `as`/`any` on user input | **06**, **12**, **14** | PASS in spirit; the map's Zod + RHF choice aligns. The response-side and URL-param obligations are easy to miss. |
| **as-2** Parameterised Interfaces | MUST | 1 \| 1 | No raw `Statement`; `nativeQuery` must use placeholders; frontend must `encodeURIComponent` user input in URLs | **12** | PASS — JPA + Flyway. Spring Session JDBC's own SQL is framework-internal. |
| **as-3** Output Sanitisation | MUST | 1 \| 0 | No `dangerouslySetInnerHTML` without DOMPurify; no `innerHTML`/`eval`/`new Function`; no `javascript:` hrefs; API responses not `text/html` | **14** | PASS by default — React auto-escapes. Keep the escape hatches out. |
| **as-9** CSP | MUST | 1 \| 1 | Minimally permissive CSP; no `unsafe-inline`/`unsafe-eval` in `script-src`; no wildcards. **"If no CSP found in either backend headers or frontend meta tag, FAIL."** | **08**, **14** | **FAIL — not on the declared-gap list.** The PRD never mentions CSP. **And the two-origin split makes this a real design problem: a CSP header on API responses does nothing to protect the SPA's own origin.** The SPA origin needs its own CSP (meta tag in `index.html`, or headers from whatever serves the built assets). Ticket 14 must own this. |
| **as-12** Malware Scanning | N/A | — | File uploads only | — | N/A — no uploads. Assert. |

#### Data protection at rest

**Stated explicitly: there is no IM8 control in these sources requiring encryption of data at rest.** The `## Data Protection (dp)` section contains exactly two controls, dp-3 (transit) and dp-8 (classification labels). Password-at-rest is covered by as-6. If MFA is built, ac-2 adds "Verify the stored secret is encrypted at rest".

| Control | Force | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- |
| **dp-8** Data Classification Disclosure | conditional MUST | A classification label **persistently visible next to every input field**. "**A one-time popup dialog or dismissible banner does NOT satisfy this control.**" FAIL if any input field lacks one. | **14** | **Conditional FAIL — see Scope conflict 2.** If the app is classed internal, every field (username, email, password, all admin forms) needs an adjacent label. Not a trivial retrofit on an already-designed form layer. |

#### Error handling and information disclosure

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **as-13** Exposure of Internal System Details | MUST | **2 \| 2** | `server.error.include-stacktrace`/`-message`/`-binding-errors` = `never` (Boot default counts as PASS; explicit `always` is FAIL); a `@ControllerAdvice` returning generic errors; no `e.getMessage()` in bodies; **Actuator `exposure.include` must list exact IDs — wildcard `*` is FAIL** and the base path must be authenticated. Frontend: an Error Boundary with a generic fallback, no `{error.message}` rendered, console stripped in prod, `build.sourcemap` `false`/`'hidden'`. | **06**, **14**, and lm-16's Actuator config | Underspecified. Note the collision: lm-16 pushes us to add Actuator, as-13 requires keeping its exposure to `health,info` and authenticating it. Decide both in one place. |

**Enumeration resistance is not an IM8 control** in these sources. The PRD's and the App Standard's identical-response requirements stand on their own; `as-13` is about internal details, not account existence. Ticket 06 should not cite IM8 as its authority for the enumeration contract.

#### Access control and least privilege

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **ac-1** Principle of Least Privilege | MUST | (none → 1) | Explicit `requestMatchers` role rules; **a default-deny catch-all**; correct matcher ordering (first match wins); `@EnableMethodSecurity` + `@PreAuthorize` on privileged methods; **ownership/tenancy checks** (`#id == authentication.principal.id` style); chain rules and annotations must not disagree. Frontend privilege UI must derive from backend-sourced permissions, not hardcoded client-side role logic. | **05**, **11**, **14** | Mostly PASS. Two sharp edges: (a) the admin self-action guards (cannot disable/demote/delete self, Stories 9–11) **are** ac-1 ownership checks and must be server-side; (b) the frontend must not hardcode role logic — the worked example took a WARN for exactly that. |
| **as-7** Access Control Check Enforcement | MUST | 1 \| 0 | Every controller method covered by an annotation **or** a URL rule; flags `anyRequest().permitAll()` | **05**, **11** | PASS if the default-deny catch-all lands. |
| **ac-3** Inactive and Expired Accounts | MUST | (none → 1) | Disable within 5 days of last authorised use; auto-disable after 90 days inactivity; a **daily** job; disabling must invalidate active sessions | **17** (deferral), **12** | **FAIL — knowingly deferred by the map.** Worth noting: the PRD data model has no `lastLoginAt` and no `accountExpiresAt`, so even the *deferral* has a ticket-12 consequence — add the columns now or accept a later migration. |
| **ac-4** Access Review | MUST | (none → 1) | A **declared per-account permission baseline** as a source of truth, plus a scheduled compare-and-revoke pipeline running often enough to revoke within 5 days. N/A only with zero app-managed accounts. The skill warns explicitly that inactivity-based jobs satisfy ac-3, **not** ac-4. | **11**, **17** | **FAIL.** Split it: the baseline half is cheap (two roles, declarable in the authorization matrix ticket 11 already plans); the scheduled-revoke half is the genuine deferral. Deferring both when half is nearly free is a weak position at review. |

#### Secrets and configuration

| Control | Force | Risk | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- | --- |
| **as-8** Secrets Management | MUST | 1 \| 0 | No hardcoded secrets in **any** profile; `@Value("${...}")` from env; `.env` gitignored; **no auth tokens in `localStorage`** | **11**, **12**, **14** | Conditional. Story 12's `app.admin.password` must be env-injected and must never appear as a literal in a committed yml — the worked example logged a finding for dev-profile credentials specifically. Our HttpOnly-cookie session design already satisfies the no-web-storage half cleanly. |

#### Documentation and testing

| Control | Force | Requires | Constrains | PRD |
| --- | --- | --- | --- | --- |
| **pm-6** System Documentation | MUST | Architecture docs (`ARCHITECTURE.md` or `docs/architecture/`); network topology/diagram; an ADR directory (`/adr` or `/docs/adr`); dependency manifests; **a committed OpenAPI spec** — "A runtime-only OpenAPI endpoint … does not count"; data-flow documentation. "A basic README with only setup/build instructions is NOT sufficient." | **15**, **17**, **18** | Achievable and cheap **if planned now**. The worked example took a High FAIL here. Commit to a version-controlled `openapi.yaml`, an `/adr` directory (ticket 17 produces this anyway), and a data-flow diagram. |
| **st-3** Public Vulnerability Disclosure | conditional | Internal-only → N/A. Otherwise `security.txt` at `/.well-known/` with a `Contact` and a **future** `Expires`, or the footer link. | **14**, **17** | N/A under an internal classification; otherwise a trivially avoidable FAIL. **Warning: the skill's required frontend snippet contains a typo — `herf` instead of `href`. Do not copy it verbatim into real code; flag the discrepancy in ticket 17 if the footer route is chosen.** |
| **ga-8** GenAI Risks | N/A | GenAI features only | — | N/A. Assert. |

---

### Scope conflicts requiring a user decision

**Scope conflict 1 — MFA (ac-2).** Established above. The map's Out of scope entry is now void on its own stated condition. Decision needed: TOTP step-up on admin mutations (recommended, smallest surface), TOTP at admin login, or an accepted FAIL registered as a deferral with written justification.

**Scope conflict 2 — the undeclared user population.** The PRD never says whether users are internal public officers, external public users, or neither. Six controls hinge on it, and the two branches lead to very different applications:

- **If classed "internal":** `ac-12` FAILs outright — internal users must authenticate via the organisational IdP, and its FAIL condition names a local username/password login page. **That would invalidate the PRD's entire premise.** `ac-8` also FAILs (no SCIM/JIT provisioning), and `dp-8` FAILs (no per-field classification labels).
- **If classed "external public / demo, serving neither internal officers nor real citizens":** `ac-12`, `ac-8`, `dp-8` all go N/A, and `lm-18` (WOGAA) and `st-3` need explicit N/A or cheap compliance.

Recommendation: declare in the Spec that this is **a standalone reference application serving neither internal public officers nor production public users**, which makes `ac-7`, `ac-8`, `ac-12`, `dp-8`, `lm-18`, `st-3` N/A on a single stated basis. This is a scope declaration, not a technical fix — `policies/SKILL.md` forbids auto-fixing scope decisions, so it must come from the user, and it must be written down where the auditor will read it.

**Scope conflict 3 — ARC's `op` controls.** Whether AI-generated code for a non-AI app inherits ARC's Level 0 code-generation controls. The source does not say. Needs a declared interpretation (recommendation in Q3).

---

### PRD verdicts, consolidated

**Declared gaps, reassessed:**

| PRD declared gap | IM8 verdict |
| --- | --- |
| No MFA | **ac-2 FAIL.** Hard conflict. |
| No local HTTPS | **as-10 WARN/FAIL (2\|2), dp-3 WARN.** Survivable only via documented TLS-terminating topology — which makes the HTTPS handover note real evidence. |
| Stubbed email | **No IM8 control failed.** Nothing in these sources requires email delivery. The stub is safe. |
| No CI/CD | **No IM8 control failed directly.** Touches pm-6's infra inventory; ARC CTRL-0074 is already covered by Dependency-Check on `verify`. |
| USER/ADMIN only | **Acceptable for ac-1's URL-role mapping**, but the self-action guards must be implemented as ac-1 ownership checks, and ac-4 still wants a declared baseline. |
| Audit log lines, no table | **lm-4 PASSES on durability.** Must add a named ECS/JSON format (lm-15) and masking (lm-19). |

**Failures the PRD did *not* declare — the ones that matter most, because nobody has budgeted for them:**

1. **as-9 CSP — FAIL.** Not mentioned anywhere in the PRD; explicit FAIL condition; complicated by the two-origin split.
2. **lm-16 Key Signals Monitoring — FAIL** in the 2\|2 band. Not mentioned; absence of both metrics paths is a stated FAIL.
3. **ac-6 + as-15 forced password change — FAIL.** No `forcePasswordChange` field in the data model; no forced reset after unlock; seeded admin credentials never forced to rotate. **Direct consequence for ticket 12.**
4. **ac-4 Access Review — FAIL**, and materially separate from the ac-3 deferral the map already made.
5. **as-11 session timeout ≤ 15 minutes — underspecified**, and the threshold is concrete.
6. **as-13 Actuator exposure, source maps, console stripping — unspecified**, in the 2\|2 band.
7. **as-14 `SecureRandom`** for the reset token — unspecified, one sentence to fix now.
8. **pm-6** — needs a committed OpenAPI spec, an ADR directory, architecture and data-flow docs. Cheap now, a High FAIL if left.
9. **Missing data-model columns** implied by the above: `forcePasswordChange`, `lastLoginAt`, and (if MFA lands) `mfaEnabled` + an encrypted `totpSecret`. **Ticket 12 should reconcile these before the Flyway set is written.**
10. **No declared risk classification**, without which no severity can be derived at all.

---

### Ticket-by-ticket constraint index

| Ticket | Controls it must satisfy |
| --- | --- |
| **03** Logging/audit schema | lm-15 (name ECS or a JSON encoder), lm-19 (masking), lm-4 (a findable emitter). **Also: settle the 90-day retention claim against the App Standard — IM8 does not require it.** |
| **05** Spring Security 7 config surface | ac-1 (default-deny catch-all, matcher order, `@EnableMethodSecurity`), as-7. **Prefer config keys `im8-review` recognises, or pre-write the Boot 4.1 → 3.4 mapping note.** |
| **06** Error envelope | as-13 (generic bodies, no `e.getMessage()`), as-1, lm-19. Enumeration resistance comes from the standard/PRD, **not** IM8. |
| **07** Password policy and hashing | as-5, as-6 (BCrypt explicitly accepted). |
| **08** Session and CSRF | as-11 (≤15 min, fixation, `maximumSessions`), as-10, dp-3, as-9 (API-side headers). |
| **09** Lockout and dual rate limiting | as-4 (both halves, bucket4j shape), as-15 (forced reset on unlock). |
| **10** Credential flows | as-14 (`SecureRandom`), as-15, ac-6, lm-19. |
| **11** Admin module, role model, bootstrap | **ac-2 (MFA — blocked on the user decision)**, ac-1 ownership checks, as-7, ac-6 (bootstrap credential), as-8 (env-injected), ac-4 (declared baseline), ac-12/ac-8 (pending the population decision). |
| **12** Data model and Flyway | **New columns: `forcePasswordChange`, `lastLoginAt`; conditionally `mfaEnabled`/`totpSecret` encrypted**; as-2, as-1, lm-19 (`@ToString.Exclude`). |
| **13** Audit event catalogue | lm-4 (auth events, authorization failures, data access), lm-19, and lm-16's signal list. |
| **14** Frontend architecture | **as-9 (CSP on the SPA origin — the sharpest new constraint)**, as-3, as-1 (response + URL-param validation), as-5 (requirements shown before submit), as-13 (Error Boundary, sourcemaps, console), as-11 (idle timeout), ac-1 (no hardcoded role logic), as-8, dp-3, conditionally dp-8 and st-3. |
| **15** Threat model | pm-6 (data-flow documentation doubles as pm-6 evidence). |
| **16** Test plan | Assert `im8-review`'s PASS conditions as tests: CSP header, HSTS max-age, session timeout, 429 threshold, generic error bodies, 403 on role violation. |
| **17** Deferral register and ADRs | **124 rows keyed by control ID** (36 IM8 + 88 ARC), each with a verdict from `spec-compliance`'s five-value set and a self-contained reason. `DEFERRED` for ac-3/ac-4 with written justification. Plus the declared risk classification, the population declaration, the ARC interpretation, the Boot-version mapping note, and the `herf` typo warning. |
| **18** Compliance review gate | Run `spec-compliance` against **one consolidated Spec/TRD in-conversation**; resolve every FAIL/AUTO-FIX with the user per Step 4. **`im8-review` cannot run here — it needs a codebase; schedule it post-build.** |

### What I could not determine

- **Log retention (incl. the 90-day figure).** Absent from the IM8 sources entirely; the only `.kiro` mention of retention is an unrelated Logback rolling-policy recommendation in `spring-logging-review/SKILL.md`. Belongs to `App-Standards/Appfw-Logging-Standards/`, which is not in this workspace. Ticket 03 owns it.
- **The app's official risk classification and user population.** Not stated in any source I read. Both are user decisions with large control consequences.
- **ARC's own applicability trigger.** `arc-framework-policy.md` has no applicability section. The `op`-section question is a judgement call, flagged rather than silently resolved.
