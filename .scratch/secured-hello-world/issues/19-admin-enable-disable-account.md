# 19: Admin enable/disable account with self-action guard

**What to build:** An admin suspends someone's access without destroying their data by toggling
the account's enabled flag — and a disabled user can genuinely no longer log in. An admin
cannot disable **themselves**, which would be an own-goal lockout of the admin module.

This ticket also establishes the **shared self-action guard** that tickets 20 and 21 reuse. It
is one rule — "an admin may not apply this operation to their own account" — applied three
times, not three separately written checks. Implement it once here as a reusable rule.

Covers PRD Story 9.

**Blocked by:** 18.

**Status:** ready-for-agent

**IM8 controls:** `ac-1` Principle of Least Privilege; `as-7` Access Control Check
Enforcement; `as-11` Session Management; `lm-4` Audit Logging; `ac-3` Inactive and Expired
Accounts. *ASVS: V4.1 General Access Control, V4.2 Operation Level Access Control, V11 Business
Logic, V7 Logging.*

- [ ] An admin targeting another user's account updates that account's enabled flag
- [ ] A disabled user can no longer log in, even with the correct password
- [ ] A disabled user's **existing sessions are invalidated**, so disabling takes effect
      immediately rather than at their next login. *(Beyond the PRD's literal wording, but
      "suspend access" that leaves an active session alive does not suspend access.)*
- [ ] An admin targeting **their own** account is rejected — an admin cannot disable themselves
- [ ] The self-action guard is implemented once, as a reusable rule, ready for tickets 20 and 21
- [ ] The guard compares against the **session principal's** identity, not an identifier
      supplied in the request body
- [ ] The operation is CSRF-protected and is not reachable by a cross-site GET
- [ ] Targeting a nonexistent account is handled cleanly rather than surfacing a server error
- [ ] An audit event records the **actor** and the **target** and the new status
- [ ] The frontend offers the toggle in the user list and disables the control on the admin's
      own row
- [ ] Test: disabling another account prevents that user from logging in
- [ ] Test: disabling another account invalidates that user's active session
- [ ] Test: an admin disabling their own account is rejected and the account stays enabled
- [ ] Test: re-enabling a disabled account restores login
- [ ] Test: the audit event for the change names both actor and target
