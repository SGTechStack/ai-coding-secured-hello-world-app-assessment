# PRD coverage check

This file checks each acceptance criterion (AC) in `prd/assessment-prd.md` against `docs/test-plan/test-plan.md` and `docs/register/register.md`. It covers the PRD only. IM8, ASVS and Std rows are left out unless one changes a PRD outcome. The check reads the plan documents. It does not run any tests.

Key: ✅ met and tested · 🟡 met, but no test asserts it · ⚠️ knowingly partial, recorded in the register · ➕ met, with extra behaviour added beyond the PRD

## PRD §Testing minimum: every required item is covered

| Required test | Rows |
|---|---|
| Login: success, wrong password, unknown username (identical error), locked | T-SES-005, T-AUTH-006, T-LCK-004 |
| Lockout: N failures lock; login after cooldown resets counter; IP throttle independent | T-LCK-008, T-LCK-012, T-LCK-002, T-LCK-006, T-RL-001 |
| Logout: replayed cookie rejected | T-SES-007 |
| Reset: single-use, expiry, sessions invalidated | T-CRED-014, T-CRED-013, T-SES-014 |
| Admin self-action guard (disable, delete, demote) | T-ADM-003 |
| USER gets 403 on every `/api/admin/**` route | T-ADM-013 |

## Stories

| Story | AC | Status | Evidence / note |
|---|---|---|---|
| 1 Register | 1 create USER, enabled, BCrypt | ⚠️ | R-CRED-009: registration takes a username and email only. The password is set, and the account enabled, when the user redeems the activation link. This closes a pre-account hijack. R-CRED-023: the minimum length is 15 (NIST), not 12. |
| | 2 conflict gives a clear error | ⚠️ | R-CRED-018: a username conflict gets a specific error. An email conflict gets the same 202 as a new email (T-AUTH-014), because the PRD's own enumeration-resistance requirement conflicts with this AC. |
| | 3 weak password rejected | ✅ | T-CRED-004, T-CRED-001 |
| | 4 plaintext never logged or stored | ✅ | T-AUD-018, T-AUD-013 |
| 2 Login | 1 session, cookie, counter reset | ✅ | T-SES-005, T-LCK-006 |
| | 2 generic error, counter increments | ✅ | T-AUTH-006, T-LCK-008 |
| | 3 locked account refuses the correct password | ✅➕ | T-LCK-004. R-LCK-004: after 100 consecutive failures the password stays disabled until the user resets it. |
| 3 Lockout | 1 lock after 5 failures | ✅➕ | T-LCK-012. R-LCK-007: the lock lasts 20, then 40, then 60 minutes, instead of the PRD's example 15. |
| | 2 cooldown elapses, then login succeeds | ✅ | T-LCK-002, T-LCK-006 |
| | 3 IP throttle independent; attacker cannot lock out a user | ⚠️ | R-LCK-002: the IP throttle is independent of account lockout, as required. The PRD's promise that one source can't lock out a user isn't achievable while per-account lockout exists, because a few failures per lock period keep a user locked. |
| 4 Logout | 1, 2 | ✅ | T-SES-007 |
| 5 Hello | 1 body is `Hello, <username>` | 🟡 | T-ADM-013 asserts only that the call returns 200. No test checks the body. |
| | 2 no session gets 401 | ✅ | T-AUTH-009, T-SES-007 |
| 6 Reset request | 1 generic response | ✅ | T-AUTH-006 |
| | 2 hashed token, expiry, EmailService called | ✅ | T-CRED-012, T-CRED-013, T-AUD-030. R-CRED-021: the stub logs the link in dev only, so self-service reset works in dev only. That follows from the PRD putting SMTP out of scope. |
| 7 Reset confirm | 1 password updated, token used, sessions ended | ✅ | T-SES-014, T-CRED-014, T-CRED-015 |
| | 2 expired token rejected | ✅ | T-CRED-013 |
| | 3 reused token rejected | ✅ | T-CRED-014 |
| 8 List users | 1 fields listed, never hashes | 🟡 | T-ADM-008 and T-ADM-016 cover "never hashes". No test checks that username, email, role, enabled and created-at are present. |
| | 2 non-admin gets 403 | ✅ | T-ADM-013 |
| 9 Enable/disable | 1 flag updated; disabled user can't log in | ✅➕ | T-SES-003, T-AUTH-006. R-ADM-008: when exactly two admins are enrolled, disabling either one is refused with 409. |
| | 2 no self-disable | ✅ | T-ADM-003 |
| 10 Role change | 1 role updated | ✅➕ | T-SES-004 (from the session side), with the same two-admin note (R-ADM-008) |
| | 2 no self-demote | ✅ | T-ADM-003 |
| 11 Delete | 1 account removed | ✅➕ | T-ADM-004. R-ADM-002: deletion also writes a tombstone. |
| | 2 no self-delete | ✅ | T-ADM-003 |
| 12 Bootstrap | 1 seed when no ADMIN exists | 🟡 | R-ADM-007 describes the seed. T-ADM-022 and T-ADM-023 only test refusal of bad configuration. No test starts on an empty database and asserts that the admin is created and hashed. |
| | 2 no duplicate seed | ✅ | T-ADM-024, T-ADM-025 |

## Non-functional requirements

| Requirement | Rows |
|---|---|
| BCrypt | T-CRED-025, T-CRED-005 |
| Cookie attributes, fixation, invalidation | T-SES-002, T-SES-005, T-SES-007, T-SES-014 |
| CSRF | T-CSRF-003, T-CSRF-006 |
| CORS allow-list with credentials | T-HDR-007, T-HDR-008, T-E2E-003 |
| Enumeration resistance | T-AUTH-006 |
| Audit logging, no passwords | T-AUD-001–045 (catalogue: T-AUD-007, T-AUD-014) |
| Server-side role checks | T-ADM-013, T-FE-002 |
| HTTPS in deployment | R-OPS-003 (a deployment obligation; the PRD accepts HTTP for local dev) |

## Deliberate departures from the PRD's scope

- MFA is required for admins, even though the PRD lists MFA as out of scope. The register justifies this with IM8 ac-2 (R-MFA-008). It is the largest scope addition.
- Other additions required by the governing standard (R-AUTH-002): one session per user, idle and absolute session timeouts, password history, self-service password change, email activation, tombstones, an admin unlock endpoint, and admin invites.
- Data model: one typed `credential_tokens` table (R-DATA-002), and roles held as a foreign key to a roles table (R-DATA-003).

## Open actions to close the PRD

1. Add three small tests: the Story 5 body, the Story 8 list fields, and the Story 12 seed on an empty database.
2. Sign off the three ⚠️ rows (1.1, 1.2, 3.3) as accepted PRD deviations. Each one already has a recorded reason.
