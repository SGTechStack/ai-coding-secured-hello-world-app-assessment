# 21: Admin delete account with self-deletion guard

**What to build:** An admin removes an account that should no longer exist. An admin cannot
delete **themselves**, via the same shared guard from ticket 19.

Deletion is the one admin operation with dependents: a user may own password reset tokens and
active sessions, and both have to go with them. A dangling reset token for a deleted user is a
live credential for an account that no longer exists.

Covers PRD Story 11. The dependency on ticket 13 exists because the reset-token cleanup cannot
be written before the reset-token table does.

**Blocked by:** 18, 13.

**Status:** ready-for-agent

**IM8 controls:** `ac-1` Principle of Least Privilege; `as-7` Access Control Check Enforcement;
`as-11` Session Management; `lm-4` Audit Logging. *ASVS: V4.1 General Access Control, V8 Data
Protection, V11 Business Logic, V7 Logging.*

- [ ] An admin targeting another user's account removes that account
- [ ] An admin targeting **their own** account is rejected — an admin cannot delete themselves
- [ ] The self-action guard is the **same reusable rule** from ticket 19
- [ ] The deleted user's password reset tokens are removed with them, leaving no token that could
      be presented against a nonexistent account
- [ ] The deleted user's active sessions are invalidated, so deletion takes effect immediately
      rather than leaving a ghost session authenticating as a deleted principal
- [ ] Deletion does not fail on a foreign-key constraint — the dependent data is handled
      explicitly rather than relying on database cascade behaviour that may not survive the
      documented move from H2 to Postgres/MySQL
- [ ] Deleting a nonexistent account is handled cleanly and does not reveal whether that account
      ever existed
- [ ] The operation is CSRF-protected
- [ ] An audit event records the actor and the target, retaining enough identifying detail to be
      meaningful **after** the target row is gone
- [ ] The frontend requires an explicit confirmation before deleting, and disables the control on
      the admin's own row
- [ ] Test: deleting an account removes it and it can no longer log in
- [ ] Test: deleting an account with outstanding reset tokens succeeds and leaves no orphan tokens
- [ ] Test: deleting an account invalidates that user's active session
- [ ] Test: an admin deleting their own account is rejected and the account remains
- [ ] Test: the audit event still names the deleted target after deletion
