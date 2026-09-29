# 14: Password reset confirmation

**What to build:** A user with a valid reset token sets a new password and regains access. The
security-critical part is the cleanup: the token is consumed, and **every existing session for
that user is invalidated**. If someone reset their password because an attacker had their old
one, leaving the attacker's session alive would defeat the entire exercise.

Covers PRD Story 7, success path. Token expiry and replay rejection are ticket 15.

**Blocked by:** 13, 05, 06.

**Status:** ready-for-agent

**IM8 controls:** `as-15` Password Change; `as-11` Session Management; `as-6` Password Salting
and Hashing; `as-5` Password Requirements; `as-14` Secure Cryptographic Libraries; `lm-4` Audit
Logging. *ASVS: V2.5 Credential Recovery, V2.1 Password Security, V3.3 Session Termination, V7
Logging.*

- [ ] A valid, unexpired, unused token plus a new password meeting the policy updates the
      password, stored as a BCrypt hash like any other
- [ ] The new password is validated against the **same** reusable strength policy validator from
      ticket 05 — not a second copy of the rule
- [ ] The token is looked up by comparing the hash of the presented token against the stored
      hash; the plaintext is never stored to enable the lookup
- [ ] The token is marked used at the moment it is consumed
- [ ] **All existing sessions for that user are invalidated**, including sessions on other
      devices and any session an attacker may hold
- [ ] `failed_login_attempts` is reset and any lock expiry cleared, so a user who reset
      *because* they were locked out is not left locked out. *(This goes beyond the PRD, which is
      silent on it — flagged as a design decision in the spec.)*
- [ ] The new password takes effect immediately: the old password no longer authenticates
- [ ] A reset-completed audit event is emitted naming the account, with no token or credential
      material
- [ ] The frontend has a reset-confirmation form that accepts the token from the link and the
      new password, and routes to login on success
- [ ] Test: after a reset, a session established before the reset is rejected by a protected
      endpoint
- [ ] Test: after a reset, the old password fails and the new password succeeds
- [ ] Test: a new password failing the strength policy is rejected and the password is unchanged
- [ ] Test: the consumed token is marked used
