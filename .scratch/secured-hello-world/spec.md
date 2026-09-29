# Spec: Secured Hello World App

**Source of truth:** [`prd/assessment-prd.md`](../../prd/assessment-prd.md). This file is an index
and a dependency map over that PRD, not a replacement for it. Where this file and the PRD
disagree, the PRD wins.

## What we're building

A React + Spring Boot reference app demonstrating username/password login, built as a
**production-grade security baseline** rather than a shortcut demo.

- Frontend: React, own origin (e.g. `localhost:3000`)
- Backend: Spring Boot REST API, own origin (e.g. `localhost:8080`)
- Cross-origin: CORS allow-list on the backend, credentials (cookies) enabled
- Persistence: Spring Data JPA over H2 (dev profile), schema portable to Postgres/MySQL
- Auth: server-side session via a secure HttpOnly cookie (Spring Session)

Because auth is **cookie-based**, CSRF protection is mandatory on every state-changing
endpoint. This is the single design fact that drives the most tickets.

## Roles

| Role | Authenticated | Capabilities |
| --- | --- | --- |
| Visitor | No | Register, log in, request password reset |
| User | Yes (`USER`) | View personalised greeting, log out, reset own password |
| Admin | Yes (`ADMIN`) | Everything a User can do, plus list/enable/disable/re-role/delete other accounts |

This table is also the **declared per-account permission baseline** that IM8 `ac-4` access review
is conducted against. There are exactly two privilege levels; anything finer is out of scope.

## Out of scope

Not built, and no ticket may quietly smuggle them back in:

- JWT implementation (appendix design only)
- MFA / 2FA
- Real SMTP or email delivery (stubbed `EmailService` logs instead)
- Containerization, CI/CD, hosting infrastructure
- Local HTTPS (dev runs over HTTP as a documented, accepted gap)
- Granular per-resource authorization beyond the USER/ADMIN check on admin endpoints

Two of these shape the build rather than just being absent:

1. **No local HTTPS** → the `Secure` cookie attribute must be profile-conditional (ticket 01),
   and the gap recorded as an accepted deviation with risk rationale (ticket 27).
2. **No CI/CD** → the dependency vulnerability scan is a point-in-time snapshot (ticket 26),
   not a pipeline stage.

**Every one of the six** waives or constrains a control that would otherwise apply, which is
different from a control that does not apply at all. All six are enumerated in the waiver register
below and each one needs an entry in the deviation register (ticket 27). A waiver without a
recorded rationale is itself an audit finding.

## IM8 compliance approach

Controls are cited from the **IM8 Reform Control Catalog** — 36 controls across Application
Security (`as`), Access Control (`ac`), Data Protection (`dp`), Logging and Monitoring (`lm`),
Cryptography (`ck`), Generative AI (`ga`), Project Management (`pm`) and Security Testing (`st`).
Every ticket's `IM8 controls` line cites real control IDs. ASVS 4.0.3 chapters are retained as a
secondary mapping because they give each control a *testable* form, which is useful when writing
the integration tests, but IM8 is the authority.

The audits live in [`artifacts/spec-compliance/`](../../artifacts/spec-compliance/). Two runs so
far:

1. **PRD phase** (`…-2157`) — 3 PASS, 15 AUTO-FIX, 3 FAIL, 3 DEFERRED, 12 N/A. The three FAIL
   findings are what tickets 22, 23 and 24 exist to resolve.
2. **Post-decomposition** (`…-2227`) — 18 PASS, 4 AUTO-FIX, **0 FAIL**, 3 DEFERRED, 11 N/A. All
   three FAILs confirmed closed by tickets 22, 23 and 24. The four AUTO-FIX findings
   (`ac-1`, `as-15`, `lm-19`, `pm-6`) were all waiver-register completeness gaps rather than
   control failures, and were resolved in that run.

The table below shows verdicts **after** those four fixes were applied, which is why no AUTO-FIX
row appears in it. The report retains the as-audited verdicts; this table is the living state.

### Risk classification

This system is assessed as **Low Risk (LR)**. Two reasons:

1. It is a non-production reference application holding only usernames, email addresses and
   password hashes — no sensitive or classified data.
2. Wherever the catalogued LR and MR levels differ (`as-1`, `as-3`, `as-7`, `as-8`, `lm-4`,
   `lm-19`, `ck-1`, `ck-2`), **LR is the higher of the two**. Choosing LR therefore cannot
   under-call a severity, which makes it the safe classification rather than merely the
   plausible one.

Severity follows from the control's risk level and its status: Level 2 FAIL is Critical and WARN
is High; Level 1 FAIL is High and WARN is Medium; Level 0 FAIL is Medium and WARN is Low.
Controls with no catalogued level — all of `ac`, `dp`, `pm`, `st` — default to Level 1.

### Control mapping

Verdicts are **post-decomposition**: they record whether a ticket now mandates the control in
specific, testable form. The PRD-phase column shows where each control stood before the tickets
existed, so the two audits can be reconciled rather than silently replacing one another. A PASS
here still means *the spec mandates it*, never that code implements it.

| Control | Level | PRD phase | Verdict | Ticket(s) |
| --- | :---: | :---: | --- | --- |
| `as-1` Input Validation | 1 | AUTO-FIX | **PASS** | 05, 02 |
| `as-2` Parameterised Interfaces | 1 | AUTO-FIX | **PASS** | 01 |
| `as-3` Output Sanitisation | 1 | AUTO-FIX | **PASS** | 02, 07 |
| `as-4` Auth Rate-Limiting | 1 | AUTO-FIX | **PASS** | 11, 12, 13 |
| `as-5` Password Requirements | 1 | AUTO-FIX | **PASS** | 05, 22 |
| `as-6` Password Salting and Hashing | 1 | PASS | **PASS** | 01, 04, 16, 22 |
| `as-7` Access Control Enforcement | 1 | AUTO-FIX | **PASS** | 01, 17, 23 |
| `as-8` Secrets Management | 1 | AUTO-FIX | **PASS** | 16 |
| `as-9` Content Security Policy | 1 | AUTO-FIX | **PASS** | 01 |
| `as-10` HSTS | **2** | AUTO-FIX | **PASS** | 01 |
| `as-11` Session Management | 1 | AUTO-FIX | **PASS** | 06, 09, 10, 14 |
| `as-12` Malware Scanning of Uploads | 2 | N/A | N/A — no upload capability | — |
| `as-13` Exposure of Internal Details | **2** | PASS | **PASS** | 01, 23 |
| `as-14` Secure Cryptographic Libraries | **2** | AUTO-FIX | **PASS** | 01, 13, 26 |
| `as-15` Password Change | — | N/A | N/A — mechanism exists (22), no detection signal | 22, 14, 19 |
| `ac-1` Least Privilege | 1 | PASS | **PASS** — granularity ceiling waived (6) | 01, 17, 19–21 |
| `ac-2` MFA Enforcement | 1 | N/A | **WAIVED** (1) — applies, excluded by PRD | 27 |
| `ac-3` Inactive and Expired Accounts | 1 | **FAIL** | **PASS** — resolved by **24** | 24 |
| `ac-4` Access Review | 1 | DEFERRED | DEFERRED — baseline richness waived (6) | 18, 24, 27 |
| `ac-6` Default Credentials | 1 | **FAIL** | **PASS** — resolved by **22** | 22 |
| `ac-7` Singpass/Corppass | 1 | N/A | N/A — not a government service | — |
| `ac-8` Automated Account Lifecycle | 1 | N/A | **WAIVED** (2) — no IdP in scope | 27 |
| `ac-12` SSO for Internal Services | 1 | N/A | **WAIVED** (2) — applies, excluded by PRD | 27 |
| `dp-3` Data in Transit Encryption | 1 | DEFERRED | DEFERRED (3) — prod TLS remains binding | 01, 27 |
| `dp-8` Data Classification Disclosure | 1 | N/A | N/A — internal applications only | — |
| `lm-4` Audit Logging | 1 | AUTO-FIX | **PASS** | 03, 25 |
| `lm-15` Structured Log Formatting | **2** | AUTO-FIX | **PASS** | 03, 25 |
| `lm-16` Key Signals Monitoring | **2** | **FAIL** | **PASS** — resolved by **23**; alerting waived (5) | 23 |
| `lm-18` WOGAA | — | N/A | N/A — not a public government service | — |
| `lm-19` Log Sanitisation | **2** | AUTO-FIX | **PASS** — dev reset-link emission waived (4) | 03, 13, 25 |
| `ck-1` Key Establishment | 2 | N/A | N/A — no keys; **contingent on JWT not being built** | 27 |
| `ck-2` Key Rotation | 2 | N/A | N/A — no keys; **contingent on JWT not being built** | 27 |
| `ck-4` Key Storage | — | N/A | N/A — no keys; **contingent on JWT not being built** | 27 |
| `ga-8` GenAI Risks | — | N/A | N/A — no GenAI feature | — |
| `pm-6` System Documentation | 1 | AUTO-FIX | **PASS** — continuous scanning waived (5) | 26, 27 |
| `st-3` Public Vulnerability Disclosure | 1 | DEFERRED | DEFERRED — needs a public deployment | 27 |

Bracketed numbers point at the waiver-register row below that governs the gap. A control marked
**WAIVED** is one that *applies* to an application of this shape and is absent only by PRD
decision — distinct from N/A, where there is nothing for the control to attach to.

The **ARC Framework** (88 controls) is N/A in full. Every ARC control presupposes an LLM, an MCP
server, an agent system prompt, agent memory, inter-agent messaging, or agent-generated code.
This application has none of those, so there is no component for any ARC control to attach to.

### Waiver register

Controls that **do** apply to an application of this shape and are excluded or constrained only by
an explicit PRD decision. Each needs a deviation-register entry in ticket 27 with its compensating
controls. The register is keyed to the PRD's **Out of Scope** list so that every scope exclusion
has a traceable control disposition — an out-of-scope item with no row here is the gap this
register exists to prevent.

| # | PRD out-of-scope item | Control waived or constrained | Residual risk | Compensating controls |
| :---: | --- | --- | --- | --- |
| 1 | MFA / 2FA | `ac-2` MFA Enforcement | A single authentication factor is the only barrier to account takeover; a phished or stuffed credential is sufficient | Account lockout (11), IP throttling (12), audit logging of every privileged action (03, 25), self-action guards (19–21), externalised admin credential (16), forced rotation (22) |
| 2 | Local username/password auth is the declared subject of the PRD | `ac-12` SSO for Internal Services, and `ac-8` Automated Account Lifecycle following from it | No central identity, no leaver feed, so deprovisioning is manual and depends on an admin noticing | Server-side session with HttpOnly cookie; manual lifecycle via admin enable/disable/delete (19, 21) plus inactivity deactivation (24). `ac-12` would be a blocking FAIL if this were fielded as an internal agency service. |
| 3 | Local HTTPS setup | `dp-3` Data in Transit Encryption | Session cookie and credentials travel in clear over the loopback interface in `dev` | Held as **DEFERRED, not waived**: prod-profile TLS, a TLS 1.2 protocol floor, and the profile-conditional `Secure` cookie all remain binding (01, 27) |
| 4 | Real SMTP / email delivery — stubbed `EmailService` logs instead | `lm-19` Log Sanitisation, partially | The stub writes a **live plaintext reset token** to a log file; read access to logs is full account takeover. A **deployment blocker**, not a cosmetic stub | Emission fenced to the `dev` profile only, at DEBUG only, through a non-audit logger, and unreachable under the prod profile (13). Redaction of tokens from the audit stream is enforced in the seam (03, 25) |
| 5 | Containerization / CI/CD / hosting infra | `pm-6` System Documentation, as to *continuous* vulnerability management; `lm-16` as to alert routing | Dependency scanning is point-in-time and superseded the day after it runs; nothing re-runs it and no alerting stack consumes the metrics | CycloneDX SBOM plus dated scan snapshot with resolved versions, re-run documented as a pre-deployment step (26); application-side metrics exposed so alerting is a wiring task not a discovery task (23) |
| 6 | Granular per-resource authorization beyond USER/ADMIN | `ac-1` Least Privilege, as to granularity; `ac-4` Access Review, as to baseline richness | Exactly two privilege levels, so an admin has every admin power; no finer separation of duties is expressible | Default-deny filter chain with method-level `@PreAuthorize` as an independent second check (01, 17), self-action guards (19–21), the Roles table above as the declared `ac-4` baseline, `last_login_at` on the admin list as the review surface (18, 24) |

The sixth PRD exclusion, **JWT implementation**, waives no live control — the session-cookie
design is the PRD's chosen primary and is fully built. It is recorded as a deviation in ticket 27
for a different reason: `ck-1`, `ck-2` and `ck-4` are N/A **because** no JWT signing key exists.
Their N/A is contingent on that architectural choice, not on the application's nature, and all
three become live the moment the appendix is built.

Not a PRD exclusion but recorded in the same register, because it is a deliberate narrowing of a
requirement rather than an omission: **registration conflict errors intentionally reveal that a
username or email is taken** (05). The PRD requires a clear validation error and a registration
form cannot function otherwise, so this is a bounded exception to the enumeration resistance that
governs login and password reset — which remain strict.

Controls added beyond what the PRD states, because IM8 requires them:

- Forced password change on the admin-issued seed credential — `ac-6` (ticket 22)
- Key signals monitoring — `lm-16` (ticket 23)
- Inactive account deactivation and `last_login_at` — `ac-3`, `ac-4` (ticket 24)
- Externalised admin seed credential, no default or committed password — `as-8` (ticket 16)
- Idle and absolute session timeouts — `as-11` (ticket 10)
- CSP, HSTS and sanitised error responses — `as-9`, `as-10`, `as-13` (ticket 01)
- Audit-log redaction and ECS structured formatting — `lm-15`, `lm-19` (tickets 03, 25)
- Dependency vulnerability scanning and SBOM — `pm-6` (ticket 26)
- Formal recording of accepted deviations — the waiver register above (ticket 27)

## Ticket index

Tickets live in [`issues/`](issues/), numbered in dependency order (blockers first).

| # | Ticket | Blocked by |
| --- | --- | --- |
| 01 | Backend skeleton and security configuration baseline | None |
| 02 | Frontend skeleton and credentialed API client | 01 |
| 03 | Audit logging seam and redaction policy | 01 |
| 04 | Registration happy path | 02, 03 |
| 05 | Registration validation and conflict handling | 04 |
| 06 | Login and session establishment | 04 |
| 07 | Protected personalised greeting | 06 |
| 08 | Login failure handling and enumeration resistance | 06 |
| 09 | Logout and session invalidation | 07 |
| 10 | Idle and absolute session timeouts | 09 |
| 11 | Account lockout after repeated failures | 08 |
| 12 | IP-level login throttling | 08 |
| 13 | Password reset request | 04 |
| 14 | Password reset confirmation | 13, 05, 06 |
| 15 | Reset token expiry and single-use enforcement | 14 |
| 16 | Admin bootstrap seeding with externalised credential | 04 |
| 17 | Admin endpoint role enforcement | 16, 06 |
| 18 | Admin user list | 17 |
| 19 | Admin enable/disable account with self-action guard | 18 |
| 20 | Admin role change with self-demotion guard | 18 |
| 21 | Admin delete account with self-deletion guard | 18, 13 |
| 22 | **Forced password change on admin-issued credentials** | 16, 07 |
| 23 | **Key signals monitoring** | 01 |
| 24 | **Inactive account deactivation** | 06, 19 |
| 25 | Audit log conformance verification | 09, 11, 12, 14, 19, 20, 21, 22, 24 |
| 26 | Dependency vulnerability scan snapshot | 02 |
| 27 | IM8 control mapping dossier and accepted deviations | 23, 25, 26 |

Tickets 22–24 are new, added to resolve the three FAIL findings. The three aggregator tickets
that were previously 22–24 are now 25–27, so that aggregators still sort last and the numbering
stays in dependency order.

## Dependency shape

```
01 ─┬─ 02 ── 04 ─┬─ 06 ─┬─ 07 ── 09 ── 10 ─────────────────┐
    │            │      │                                   │
    │            │      ├─ 08 ─┬─ 11 ─────────────────────┤
    │            │      │      └─ 12 ─────────────────────┤
    │            │      └─ 24 ──────────────────────────┐ │
    ├─ 03 ───────┘                                       │ │
    │            ├─ 05 ── 14 ── 15                        │ │
    │            ├─ 13 ─┘                                 │ │
    │            └─ 16 ─┬─ 17 ── 18 ─┬─ 19 ──────────────┤ │
    │                   │            ├─ 20 ──────────────┤ │
    │                   │            └─ 21 ──────────────┤ │
    │                   └─ 22 ────────────────────────────┤ │
    │                                                  25 ─┴─┤
    └─ 23 ──────────────────────────────────────────────────┤
                                          02 ── 26 ─────────┴─ 27
```

Note that 24 needs both 06 (to write `last_login_at` on login) and 19 (it reuses the `enabled`
flag and its audit event), so it sits across two chains.

Four independent fronts open up after ticket 04: the **login/session** chain (06→07→09→10,
08→11/12), the **password reset** chain (13→14→15), the **admin** chain
(16→17→18→19/20/21, 16→22), and the **inactivity** work (24, once 06 and 19 land). Ticket 23
depends only on 01 and can be worked at any time. They can all be worked in parallel.

## Design decisions (resolved)

These four points are where the PRD is silent and the tickets had to choose. They are **decided**,
not open — an implementing agent follows them rather than re-deciding or pausing to ask.

- **Ticket 14:** a password reset **does** also clear `failed_login_attempts` and `locked_until`.
  The PRD does not say, but a user who reset their password *because* they were locked out would
  otherwise still be locked out, which makes the recovery path useless in the case that most needs
  it.
- **Tickets 19-21:** the self-action guard is **one shared rule applied three times**, not three
  independent checks. Implement it once in 19 and reuse it in 20 and 21. Three hand-written copies
  of a privilege check is three chances for one of them to drift.
- **Ticket 22:** the forced-change gate is ordered **before** role authorization, so a gated admin
  gets `PASSWORD_CHANGE_REQUIRED` rather than a 403 from the admin matcher. Both are 403s, so the
  body code is the only thing distinguishing them — which is why the ordering is specified rather
  than left to whichever filter happens to register first.
- **Ticket 23:** the metrics endpoint is **either** authenticated **or** bound to a separate
  management port — the implementer picks one, and either satisfies `as-7`. What the ticket does
  not allow is leaving it open: the choice must be recorded, because an unstated decision here is
  indistinguishable from an unsecured actuator surface.
