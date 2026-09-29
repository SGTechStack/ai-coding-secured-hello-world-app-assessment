# 20: Admin role change with self-demotion guard

**What to build:** An admin promotes a user to `ADMIN` or demotes an admin back to `USER`, so
privileges can be granted and revoked without touching the database. An admin cannot demote
**themselves** — the same shared guard ticket 19 established.

Privilege escalation is the highest-consequence operation in this application, so the tests here
carry more weight than the line count suggests.

Covers PRD Story 10. Independent of tickets 19 and 21; can be worked in parallel.

**Blocked by:** 18.

**Status:** ready-for-agent

**IM8 controls:** `ac-1` Principle of Least Privilege; `as-7` Access Control Check Enforcement;
`as-1` Input Validation; `lm-4` Audit Logging. *ASVS: V4.1 General Access Control, V4.3 Other
Access Control Considerations, V11 Business Logic, V7 Logging.*

- [ ] An admin targeting another user's account with a valid role updates that account's role
- [ ] Only the two roles the PRD defines are accepted; any other value is rejected as invalid
      rather than persisted or coerced
- [ ] An admin targeting **their own** account is rejected — an admin cannot demote themselves
- [ ] The self-action guard is the **same reusable rule** from ticket 19, not a second copy
- [ ] A demotion takes effect on the demoted user's **next authorization decision** — a demoted
      admin must not continue passing admin checks on an existing session because of a cached
      authority. This is the sharpest failure mode in this ticket and needs its own test.
- [ ] Conversely, a promotion takes effect without requiring the promoted user to log out and
      back in, or the delay is documented as intended behaviour
- [ ] The operation is CSRF-protected
- [ ] An audit event records the actor, the target, the previous role and the new role — role
      changes are the events an auditor will look for first
- [ ] The frontend offers the role control in the user list and disables it on the admin's own row
- [ ] Test: promoting a `USER` to `ADMIN` grants them access to admin endpoints
- [ ] Test: demoting an `ADMIN` revokes their admin access on their existing session, without
      requiring re-login
- [ ] Test: an admin demoting themselves is rejected and their role is unchanged
- [ ] Test: an invalid role value is rejected and the account's role is unchanged
