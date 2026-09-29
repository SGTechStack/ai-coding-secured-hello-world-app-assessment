# 22: Forced password change on admin-issued credentials

**What to build:** A user holding a credential they did not choose must change it before they can
do anything else. Ticket 16 makes the bootstrap admin password externally supplied, unlogged and
fail-fast — good provenance. But provenance is not rotation: that credential is known to whoever
set it, it survives indefinitely, and ticket 16 only *documents* an expectation that it be
changed. IM8 `ac-6` requires the change be **enforced**, not expected.

This is the one control the PRD's stories imply but never state. Story 12 seeds an admin from
configuration, which is by definition an admin-issued credential, so `ac-6` attaches the moment
that story is built.

The enforcement is a gate, not a nudge: while the flag is set, every endpoint except
password-change and logout is refused. A banner the user can dismiss does not satisfy this.

Covers IM8 `ac-6`, arising from PRD Story 12.

**Blocked by:** 16, 07.

**Status:** ready-for-agent

**IM8 controls:** `ac-6` Default Credentials (primary); `as-5` Password Requirements;
`as-6` Password Salting and Hashing; `as-7` Access Control Check Enforcement;
`as-11` Session Management; `lm-4` Audit Logging. *ASVS: V2.3 Authenticator Lifecycle,
V4 Access Control, V7 Logging.*

- [ ] A `force_password_change` boolean column is added to `users`, defaulting to `false` for
      self-registered accounts
- [ ] The bootstrap admin seeded in ticket 16 is created with `force_password_change = true`
- [ ] A servlet filter, ordered **after** authentication so the principal is resolved, checks the
      flag on every authenticated request
- [ ] While the flag is set, the filter returns **403** with a machine-readable body code
      `PASSWORD_CHANGE_REQUIRED` for every endpoint except the password-change endpoint and logout
- [ ] The exemption list is a closed allow-list of exactly those two endpoints — not a prefix
      match, and not an exclusion list that a newly added endpoint silently joins
- [ ] `POST /api/account/password` accepts a current password and a new password, requires an
      authenticated session, and verifies the current password before making any change
- [ ] The new password must satisfy the same strength policy as registration, and must **differ**
      from the current one — rotating a temporary credential to itself is rejected
- [ ] The flag is cleared **only** on a successful change, in the same transaction as the hash
      update, so a partial failure cannot leave the account both changed and still gated
- [ ] The new hash is produced by the same `PasswordEncoder` bean as every other account
- [ ] All other sessions for that user are invalidated on success, matching the password-reset
      behaviour in ticket 14
- [ ] The frontend handles `PASSWORD_CHANGE_REQUIRED` with a **dedicated forced-change page**, not
      a generic error toast, and not the ordinary change-password form reached through settings
- [ ] A route guard prevents navigating away from that page while the flag is set — including
      direct URL entry, not just in-app links
- [ ] Audit events record that a forced change was required and that it completed, with the actor;
      neither event contains a password
- [ ] Any future path that issues a credential the user did not choose — an admin-initiated reset,
      an account unlock that assigns a temporary password — must set this flag. Documented as a
      standing rule so it is not rediscovered later.
- [ ] Test: the freshly seeded admin receives 403 `PASSWORD_CHANGE_REQUIRED` from `GET /api/hello`
      and from `GET /api/admin/users`, proving the gate precedes role authorization
- [ ] Test: the same admin can reach logout and the password-change endpoint while gated
- [ ] Test: submitting the current password as the new password is rejected and the flag stays set
- [ ] Test: submitting a wrong current password is rejected and the flag stays set
- [ ] Test: after a successful change the admin reaches `GET /api/admin/users` normally, and a
      session captured before the change is rejected
- [ ] Test: a self-registered `USER` is never gated
