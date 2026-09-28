# Spec-Compliance Findings — Hello World Auth App (React + Spring Boot)

- **Run 1:** 2026-09-28 15:59 → Overall FAIL (3 FAIL, 5 AUTO-FIX)
- **Run 2 (fixes):** 2026-09-28 16:06 → PASS (0 FAIL, 0 AUTO-FIX, 3 DEFERRED)
- **Run 3 (waivers):** 2026-09-28 16:11 → **Overall PASS** (0 FAIL, 0 AUTO-FIX, 0 DEFERRED; 4 controls waived out of scope)
- **Current report:** `artifacts/spec-compliance/spec-compliance-hello-world-auth-app-react-spring-boot-2026-09-28-1611.html`
- **Source spec:** `prd/assessment-prd.md` (updated; new "Compliance Waivers" section)
- **Policies audited:** IM8 (36) + ARC (88) = 124

## Post-waiver verdict counts

| Verdict | Count |
| --- | --- |
| PASS | 22 |
| AUTO-FIX | 0 |
| FAIL | 0 |
| DEFERRED | 0 |
| N/A | 103 (88 ARC + 15 IM8, incl. 4 waived) |
| **Total** | **124** |

## Waived out of scope (per user instruction) — recorded in spec Compliance Waivers

- **ac-2 MFA** — waived for all users incl. ADMIN. Compensating: lockout, IP throttling, forced first-login change, secure sessions, audit logging. Residual single-factor risk accepted for non-prod demo.
- **ac-3 inactivity disable** — waived; admin manual disable (Story 9) + `last_login_at`.
- **ac-4 access review** — waived; admin user list (Story 8) supports ad-hoc review.
- **lm-16 RED/USE metrics** — waived; monitoring infra out of scope; audit logging retained.

## Still enforced in scope (PASS)

as-1, as-2, as-3, as-4, as-5, as-6, as-7, as-8 (secrets), as-9 (CSP), as-10, as-11, as-13, as-14, as-15, ac-1, ac-6 (first-login change), dp-3, lm-4, lm-15, lm-19, pm-6.

## Data-model note
`users` retains `must_change_password` and `last_login_at`; the earlier `mfa_enrolled`/`mfa_secret` fields were removed when MFA was waived.

