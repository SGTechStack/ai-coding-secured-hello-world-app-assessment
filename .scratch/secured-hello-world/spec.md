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
   and the gap recorded as an accepted deviation with risk rationale (waiver register below).
2. **No CI/CD** → the dependency vulnerability scan is a point-in-time, manually-run snapshot,
   not a pipeline stage (waiver register below).

**All six** waive or constrain a control that would otherwise apply, which is different from a
control that does not apply at all — every one of the six has its own row in the **PRD
out-of-scope waivers** table below, in this list's order, with no exceptions. A waiver without a
recorded rationale is itself an audit finding.

## IM8 compliance approach

Controls are cited from the **IM8 Reform Control Catalog** — 36 controls across Application
Security (`as`), Access Control (`ac`), Data Protection (`dp`), Logging and Monitoring (`lm`),
Cryptography (`ck`), Generative AI (`ga`), Project Management (`pm`) and Security Testing (`st`).
Every ticket's `IM8 controls` line cites real control IDs. ASVS 4.0.3 chapters are retained as a
secondary mapping because they give each control a *testable* form, which is useful when writing
the integration tests, but IM8 is the authority.

The audits live in [`artifacts/spec-compliance/`](../../artifacts/spec-compliance/).

**2026-09-30 re-decomposition:** the ticket set was consolidated from 27 tickets down to 5
(tickets 01–05 below), on explicit instruction to minimize ticket count. Every control previously
resolved by a dedicated ticket (22–24, plus the metrics hook from 23) was folded into an
acceptance criterion on one of the 5 vertical slices instead of dropped; where a control's *full*
remediation (e.g. automated inactive-account deactivation, continuous dependency scanning) would
have required scope the PRD doesn't ask for, it is recorded as a **deferred deviation** below
rather than silently disappearing. See the re-run in `artifacts/spec-compliance/` for the verdict
against this 5-ticket set.

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
| `as-1` Input Validation | 1 | AUTO-FIX | **PASS** | 01, 04 |
| `as-2` Parameterised Interfaces | 1 | AUTO-FIX | **PASS** | 01 |
| `as-3` Output Sanitisation | 1 | AUTO-FIX | **PASS** | 04 |
| `as-4` Auth Rate-Limiting | 1 | AUTO-FIX | **PASS** | 03 |
| `as-5` Password Requirements | 1 | AUTO-FIX | **PASS** | 01 |
| `as-6` Password Salting and Hashing | 1 | PASS | **PASS** | 01 |
| `as-7` Access Control Enforcement | 1 | AUTO-FIX | **PASS** | 01, 02, 05 |
| `as-8` Secrets Management | 1 | AUTO-FIX | **PASS** | 01 |
| `as-9` Content Security Policy | 1 | AUTO-FIX | **PASS** | 01 |
| `as-10` HSTS | **2** | AUTO-FIX | **PASS** | 01 |
| `as-11` Session Management | 1 | AUTO-FIX | **PASS** | 01, 02 |
| `as-12` Malware Scanning of Uploads | 2 | N/A | N/A — no upload capability | — |
| `as-13` Exposure of Internal Details | **2** | PASS | **PASS** | 01 |
| `as-14` Secure Cryptographic Libraries | **2** | AUTO-FIX | **PASS** | 01 |
| `as-15` Password Change | — | N/A | N/A — mechanism exists (04), no detection signal | 04 |
| `ac-1` Least Privilege | 1 | PASS | **PASS** — granularity ceiling waived (A6) | 01, 02, 05 |
| `ac-2` MFA Enforcement | 1 | N/A | **WAIVED** (A2) — applies, excluded by PRD | — |
| `ac-3` Inactive and Expired Accounts | 1 | **FAIL** | **DEFERRED** (C) — `last_login_at` captured, automatic deactivation not built | 02, 05 |
| `ac-4` Access Review | 1 | DEFERRED | DEFERRED — baseline richness waived (A6); `last_login_at` is the review surface | 05 |
| `ac-6` Default Credentials | 1 | **FAIL** | **PASS** — forced password change on seeded credential | 05 |
| `ac-7` Singpass/Corppass | 1 | N/A | N/A — not a government service | — |
| `ac-8` Automated Account Lifecycle | 1 | N/A | **WAIVED** (B) — no IdP in scope | — |
| `ac-12` SSO for Internal Services | 1 | N/A | **WAIVED** (B) — applies, excluded by PRD | — |
| `dp-3` Data in Transit Encryption | 1 | DEFERRED | DEFERRED (A5) — prod TLS remains binding | 01 |
| `dp-8` Data Classification Disclosure | 1 | N/A | N/A — internal applications only | — |
| `lm-4` Audit Logging | 1 | AUTO-FIX | **PASS** | 01, 02, 03, 04, 05 |
| `lm-15` Structured Log Formatting | **2** | AUTO-FIX | **PASS** | 01 |
| `lm-16` Key Signals Monitoring | **2** | **FAIL** | **PASS** — metrics hook exposed; alerting waived (A4) | 01 |
| `lm-18` WOGAA | — | N/A | N/A — not a public government service | — |
| `lm-19` Log Sanitisation | **2** | AUTO-FIX | **PASS** — dev reset-link emission waived (A3) | 01, 04 |
| `ck-1` Key Establishment | 2 | N/A | N/A — no keys (A1); **contingent on JWT not being built** | — |
| `ck-2` Key Rotation | 2 | N/A | N/A — no keys (A1); **contingent on JWT not being built** | — |
| `ck-4` Key Storage | — | N/A | N/A — no keys (A1); **contingent on JWT not being built** | — |
| `ga-8` GenAI Risks | — | N/A | N/A — no GenAI feature | — |
| `pm-6` System Documentation | 1 | AUTO-FIX | **DEFERRED** (A4) — no dedicated scan ticket; run as a pre-deployment step | — |
| `st-3` Public Vulnerability Disclosure | 1 | DEFERRED | DEFERRED — needs a public deployment | — |

Bracketed codes point at the waiver-register section/row above that governs the gap (`A1`–`A6` =
PRD out-of-scope waivers table, in PRD list order; `B` = architecture-driven waivers; `C` =
consolidation deviation). A control marked **WAIVED** is one that *applies* to an application of
this shape and is absent only by an explicit decision — distinct from N/A, where there is nothing
for the control to attach to.

The **ARC Framework** (88 controls) is N/A in full. Every ARC control presupposes an LLM, an MCP
server, an agent system prompt, agent memory, inter-agent messaging, or agent-generated code.
This application has none of those, so there is no component for any ARC control to attach to.

### Waiver register

**A. PRD out-of-scope waivers.** One row per bullet in the PRD's **Out of Scope** list, in that
list's exact order — all six, no exceptions, so an excluded item with no row here would be the
gap this register exists to prevent. There is no such gap: every bullet below has a disposition.

| # | PRD out-of-scope item (verbatim) | Control(s) affected | Disposition | Residual risk | Compensating controls |
| :---: | --- | --- | --- | --- | --- |
| 1 | JWT implementation (appendix design only) | `ck-1`, `ck-2`, `ck-4` | **No live control waived** — N/A, contingent on JWT not being built | None while unbuilt; all three become live the moment the appendix design is implemented | Session-cookie mechanism (the PRD's chosen primary) is fully built (01, 02); the JWT design itself is fully documented in the PRD appendix, satisfying "documented, not built" |
| 2 | MFA / 2FA | `ac-2` MFA Enforcement | **Waived** — applies to an app of this shape, excluded by explicit PRD decision | A single authentication factor is the only barrier to account takeover; a phished or stuffed credential is sufficient | Account lockout (03), IP throttling (03), audit logging of every privileged action (01–05), self-action guards (05), externalised + forced-rotation admin credential (01, 05) |
| 3 | Real SMTP / email delivery (stubbed `EmailService` logs instead) | `lm-19` Log Sanitisation, partially | **Constrained** — a deployment blocker if left unfenced, not a cosmetic gap | The stub writes a **live plaintext reset token** to a log file; read access to logs is full account takeover | Emission fenced to the `dev` profile only, DEBUG-only, through a non-audit logger, unreachable under prod (04); redaction of tokens from the audit stream is enforced in the seam (01, 04) |
| 4 | Containerization / CI/CD / hosting infra | `pm-6` System Documentation (continuous scanning), `lm-16` (alert routing) | **Deferred** — no pipeline exists to re-run scans or route alerts | Dependency scanning is point-in-time and stale the day after it runs; no alerting stack consumes metrics | Manual pre-deployment dependency-vulnerability scan (SBOM + resolved versions) as a documented step, not a ticket; the metrics hook in 01 makes alerting a future wiring task, not a discovery task |
| 5 | Local HTTPS setup (dev runs over HTTP) | `dp-3` Data in Transit Encryption | **Deferred, not waived** — accepted only for local `dev`; remains binding elsewhere | Session cookie and credentials travel in clear over the loopback interface in `dev` | Profile-conditional `Secure` cookie attribute (01); prod-profile TLS and a TLS 1.2 floor remain binding on any real deployment |
| 6 | Granular per-resource authorization beyond the USER/ADMIN role check | `ac-1` Least Privilege (granularity), `ac-4` Access Review (baseline richness) | **Waived** — exactly two privilege levels is the PRD's declared model | An admin has every admin power; no finer separation of duties is expressible | Default-deny filter chain with method-level `@PreAuthorize` as an independent second check (01, 02, 05); self-action guards (05); the Roles table above as the declared `ac-4` baseline; `last_login_at` on the admin list as the review surface (02, 05) |

**B. Architecture-driven waivers.** Not literal PRD out-of-scope bullets, but controls that apply
to an app of this shape and are excluded only because the PRD chose local username/password
session-cookie auth as the sole built mechanism, rather than SSO:

| Control | Disposition | Residual risk | Compensating controls |
| --- | --- | --- | --- |
| `ac-12` SSO for Internal Services | **Waived** — would be a blocking FAIL if this were fielded as a real internal agency service | No central identity provider | Server-side session with HttpOnly cookie (01, 02) is the PRD's chosen mechanism |
| `ac-8` Automated Account Lifecycle | **Waived** — follows from having no IdP/SCIM feed | No leaver feed, so deprovisioning is manual and depends on an admin noticing | Manual lifecycle via admin enable/disable/delete (05) plus the `last_login_at` review signal (02, 05) |

**C. Consolidation deviation.** Trimmed when the ticket set was minimized from 27 to 5 — not a PRD
exclusion at all, but recorded with the same rigor:

| Control | Disposition | Residual risk | Compensating controls |
| --- | --- | --- | --- |
| `ac-3` Inactive and Expired Accounts (automatic enforcement) | **Deferred** — folded to a data-capture AC instead of a dedicated deactivation ticket | A dormant, still-enabled account with a valid password remains usable indefinitely | `last_login_at` captured on every login (02) and surfaced on the admin list (05) so an admin can manually disable a dormant account; automatic deactivation is documented future work (a 6th ticket blocked by 02 and 05), not silently dropped |

Not a waiver but recorded in the same register, because it is a deliberate narrowing of a
requirement rather than an omission: **registration conflict errors intentionally reveal that a
username or email is taken** (01). The PRD requires a clear validation error and a registration
form cannot function otherwise, so this is a bounded exception to the enumeration resistance that
governs login and password reset — which remain strict.

Controls folded in as acceptance criteria beyond what the PRD states in prose, because IM8
requires them, without spawning a dedicated ticket:

- Forced password change on the admin-issued seed credential — `ac-6` (ticket 05)
- Key signals monitoring hook — `lm-16` (ticket 01)
- `last_login_at` capture and admin-list surfacing — `ac-3`, `ac-4` (tickets 02, 05)
- Externalised admin seed credential, no default or committed password — `as-8` (ticket 01)
- Idle and absolute session timeouts — `as-11` (ticket 02)
- CSP, HSTS and sanitised error responses — `as-9`, `as-10`, `as-13` (ticket 01)
- Audit-log redaction and structured formatting — `lm-15`, `lm-19` (ticket 01, reused everywhere)

## Ticket index

Tickets live in [`issues/`](issues/), numbered in dependency order (blockers first). This is a
**consolidated 5-ticket set**, deliberately minimized from an earlier 27-ticket draft of the same
PRD — each ticket is a full vertical slice (schema → API → UI → tests) rather than a single layer
or endpoint, with the IM8-driven controls folded in as acceptance criteria (see the waiver register
above for the handful that were trimmed to fit rather than fully built out).

| # | Ticket | Blocked by | Status |
| --- | --- | --- | --- |
| 01 | App skeleton, security baseline & registration | None | done |
| 02 | Login, session, logout & protected greeting | 01 | done |
| 03 | Brute-force protection: account lockout & IP throttling | 02 | done |
| 04 | Password reset (request & confirm) | 01, 02 | done |
| 05 | Admin user management (bootstrap, list, enable/disable, role change, delete) | 02 | done |

All 5 tickets are implemented, tested, and merged to `chnglipkuang` (see each ticket file's own
`## Comments` section in [`issues/`](issues/) for the exact commits). 27 backend integration tests
pass; the full register → login → hello → logout, password-reset, lockout/throttle, and admin
bootstrap → forced-password-change → user-management flows were also verified against a real
running server, not just MockMvc.

## Dependency shape

```
01 ── 02 ─┬─ 03
          ├─ 04 (also needs 01, directly)
          └─ 05
```

Ticket 01 is the only hard prerequisite for everything else. Once 02 (login/session) lands, three
independent fronts open in parallel: **brute-force protection** (03), **password reset** (04, which
also touches the registration/session surface from 01 directly), and **admin management** (05).

## Design decisions (resolved)

These are points where the PRD is silent, or where consolidating to 5 tickets required a call, and
the tickets had to choose. They are **decided**, not open — an implementing agent follows them
rather than re-deciding or pausing to ask.

- **Ticket 04:** a password reset **does** also clear `failed_login_attempts` and `locked_until`.
  The PRD does not say, but a user who reset their password *because* they were locked out would
  otherwise still be locked out, which makes the recovery path useless in the case that most needs
  it.
- **Ticket 05:** the self-action guard is **one shared rule applied three times** (disable, demote,
  delete), not three independent checks. Three hand-written copies of a privilege check is three
  chances for one of them to drift.
- **Ticket 05:** the forced-change gate on the seeded admin credential is ordered **before** role
  authorization, so a gated admin gets a distinct "password change required" response rather than
  an indistinguishable 403 from the admin matcher.
- **Ticket 01:** the metrics/health hook is **either** authenticated **or** bound to a separate
  management port — the implementer picks one, and either satisfies `as-7`. What isn't allowed is
  leaving it open: the choice must be recorded, because an unstated decision here is
  indistinguishable from an unsecured actuator surface.
- **Scope trim (waiver register C):** automatic deactivation of inactive accounts (`ac-3`/`ac-4` in
  full) is not built. Capturing `last_login_at` and surfacing it to admins (02, 05) is the
  compensating control, and the gap is recorded rather than silently dropped. If this ever needs to
  be closed, it's a 6th ticket blocked by 02 and 05, not a change to either of them.
- **Scope trim (waiver register A4):** dependency vulnerability scanning (`pm-6`) is a manual
  pre-deployment step, not a ticket, because the PRD puts CI/CD out of scope and a one-off scan with
  no pipeline to re-run it would be a stale artifact by the next commit.
