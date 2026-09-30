---
status: accepted
---

# ADR-037: Sessions are invalidated on admin disable, role change, delete, lockout and cap disable

Every event that changes what an account may do ends that account's live sessions. The governing standard names only
the credential events (reset, self-service change, forced change) and the scheduled hygiene job's disable. This
application adds the events the standard omits: an administrator disabling, re-roling or deleting the account, the
account locking, and the password authenticator being disabled by the failure cap. Each extra row looks optional to
a maintainer. None of them is.

## Context

- Spring Security serialises the user's authorities into the session. A role downgrade that leaves the session alive
  leaves a privileged session alive until idle or absolute expiry. PRD Story 10's role change is permitted, so this
  is reachable.
- The standard covers disable only on the **batch** path (§2 Failure Path 22, the scheduled hygiene job). Hygiene
  jobs are not built here, so as written the clause never applies, and the manual admin disable of PRD Story 9 has no
  clause at all. Story 9 says a disabled user "can no longer log in", which says nothing about a session they
  already hold.
- OWASP ASVS 5.0 **7.4.2 (L1)** requires terminating all active sessions when an account is disabled or deleted.
- A lockout that leaves an existing session alive does nothing against an attacker who is already signed in.

## Decision

| Trigger | Sessions ended | Source of the rule |
| --- | --- | --- |
| Password reset redemption | all | standard; PRD Story 7 |
| Self-service change, forced-change completion | all others | standard, narrowed by ADR-035 |
| Admin reset-token issuance | all of the subject's | added with the admin reset flow (ADR-006) |
| Admin disable | all of the subject's | **added**; ASVS 7.4.2 (L1) |
| Admin role change | all of the subject's | **added** |
| Admin deletion | all of the subject's | **added**; ASVS 7.4.2 (L1) |
| Trusted-device lockout (ADR-075) | all | **added** |
| Untrusted-lane lockout (ADR-075) | none | **amended 2026-09-30**: anyone can cause one |
| Password authenticator disabled by the failure cap (ADR-013) | all | **added**; ASVS 7.4.2 (L1) |
| Admin factor reset | all of the subject's | added with the second factor |
| Automatic tier-2 factor disable (ADR-027) | all | added with the second factor |
| Activation or invite redemption | none | the account has no sessions yet |

- **One seam.** A single `SessionTerminationService`, over `SpringSessionBackedSessionRegistry` (ADR-030), makes
  every call. The failure the recipes show is a shared helper that simply never gets called.
- **Order.** The state change is persisted first, and sessions are ended after it commits (ADR-039). A failed kill
  therefore never leaves a user unable to log in, and the kill is never inside a row lock.
- **Once per transition.** Lockout and the cap are detected inside the pessimistic row lock that counts failures, so
  the kill fires once when the state changes, not on every failure past the threshold.
- The two rows that end nothing are written down so their absence is not read as an oversight.

## Consequences

- Lockout ending sessions means an attacker who knows a username can end that user's session by driving the account
  into lockout. That costs five failures and is audited. ADR-034 explains why one wrong password alone does not.
- Every row above except the last depends on the `PRINCIPAL_NAME` index being populated. Without it every trigger is
  a silent no-op (T-SES-009).
- A new state that restricts an account joins this table. The table is the rule, not a list of examples.
- The additions beyond the standard are recorded in the deferral register.
- Tests: one replay test per row. Each captures the subject's raw cookie, fires the trigger, replays the cookie
  against the real session store and asserts 401 and the row gone: T-SES-003 (disable), T-SES-004 (role change),
  T-SES-016 (deletion), T-SES-017 (lockout), T-SES-021 (cap), T-SES-015 (reset issuance), T-SES-018 (factor reset),
  T-SES-012 to T-SES-014 (credential rows). T-SES-019 and T-SES-020 assert that the two redemption rows end nothing.
  The automatic tier-2 row needs a replay test of the same shape.

## Amendment (2026-09-30): the untrusted lane's lock ends no session (ADR-075)

The first consequence above, that driving an account into lockout ends its sessions for five requests, was a session
kill any attacker who knew the username could use. Since ADR-075 the account's lock is the untrusted lane's, which
anyone can cause and which no longer refuses the owner's trusted browsers, so it ends no session (T-SES-038). The lock
of a trusted device still ends every session (T-SES-017): only a client holding that device's cookie can cause it. The
cap's disable and every admin action are unchanged.

## Sources

- PRD Stories 7, 9 and 10.
- Standalone User Access Control Application Standard §2 Happy Path steps 11 and 13, Failure Path 22, Decision
  Logic; §5 Password Reset Tests and Self-Service Password Change Tests.
- OWASP ASVS 5.0, V7.4: 7.4.2 (L1).
- NIST SP 800-63B-4 §3.2.2 (the authenticator failure cap behind the cap row).
