# Hello World Auth App — Ticket Breakdown

Vertical-slice tickets derived from `prd/assessment-prd.md`, numbered in dependency
order (blockers first). Each slice cuts a complete path through schema → API → UI → tests
and is verifiable on its own. IM8 findings from the spec-compliance run
(`artifacts/spec-compliance/spec-compliance-hello-world-auth-app-react-spring-boot-2026-09-28-1559.html`)
are folded into the relevant tickets as acceptance criteria.

## Dependency order

| # | Ticket | Blocked by | PRD |
| --- | --- | --- | --- |
| 01 | Project scaffold & security baseline (prefactor) | — | NFRs / Overview |
| 02 | Visitor registration | 01 | Story 1 |
| 03 | User login & session establishment | 02 | Story 2 |
| 04 | Account lockout & IP-level throttling | 03 | Story 3 |
| 05 | Logout & session invalidation | 03 | Story 4 |
| 06 | Protected personalized greeting | 03 | Story 5 |
| 07 | Password reset request | 02 | Story 6 |
| 08 | Password reset confirm | 07, 03 | Story 7 |
| 09 | Admin — list all users | 03 | Story 8 |
| 10 | Admin — enable/disable a user | 09, 03 | Story 9 |
| 11 | Admin — change a user's role | 09 | Story 10 |
| 12 | Admin — delete a user | 09 | Story 11 |
| 13 | Admin bootstrap seed + privileged-account hardening | 02, 03, 10, 11, 12 | Story 12 + IM8 as-8/ac-2/ac-6 |
| 14 | System documentation & residual-control register | 01 | IM8 pm-6/as-15/ac-3/ac-4/lm-16 |

## Compliance linkage (IM8) — post-waiver

Spec updated 2026-09-28 16:11; re-audit verdict **PASS** (0 FAIL, 0 AUTO-FIX, 0 DEFERRED).
Report: `artifacts/spec-compliance/spec-compliance-hello-world-auth-app-react-spring-boot-2026-09-28-1611.html`.

- **In-scope, enforced (PASS):** as-8 (13), ac-6 (13), as-3 (02, 06), as-9 (01), as-11 (01), as-15 (14), pm-6 (14 + spec System Documentation section).
- **Waived (out of scope, N/A with compensating controls):** ac-2 MFA, ac-3 inactivity disable, ac-4 access review, lm-16 RED/USE metrics → recorded in the spec's Compliance Waivers section + tickets 13/14.
- **ARC:** all 88 controls N/A — not an agentic AI system.

## Prefactor note

Ticket 01 is the prefactor ("make the change easy, then make the easy change"):
CORS, CSRF, CSP, HSTS, structured logging, default-deny authz, session config, and
persistence are established once so every later slice inherits the baseline instead
of re-deriving it.
