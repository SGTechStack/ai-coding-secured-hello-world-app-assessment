---
status: accepted
---

# ADR-009: Reset redemption clears the password lockout, never TOTP state

A successful password-reset redemption clears every piece of password-lockout state: the failure counter, the
temporary lock and the NIST cap's disabled state. It never touches any TOTP counter, lock or enrolment. The standard
does not say this, and in one case says the opposite. A maintainer would plausibly clear both axes for symmetry, or
neither to follow the standard's admin-reset sentence.

## Context

- **The lock protects a credential that redemption replaces.** Once a new password is set, a lock on the old one
  protects nothing and only costs the owner access. The OWASP Forgot Password Cheat Sheet agrees from both sides:
  accounts should not be locked in response to a forgotten-password attack, and nothing about the account should
  change until a valid token is presented.
- **WSTG-ATHN-03** names three ways to unlock an account: after a set time, through a self-service unlock, or by an
  administrator. Redemption, gated on mailbox control, is the self-service route, and it arrives through a channel
  the build already has.
- **Redemption is NIST rebinding.** NIST SP 800-63B-4 §3.2.2 requires a disabled authenticator to be rebound, and
  §4.2 names account recovery as the route when no other authenticator is available. Setting a new password rebinds
  the password authenticator, so it must also clear the cap's disabled state (ADR-013).
- **The standard's §2 Decision Logic addresses one case directly.** An administrator-initiated reset of a locked
  account is permitted, the lock is not cleared, and the account "remains locked until the lock expires or the
  administrator explicitly unlocks it". Read literally, a user who redeems an admin-issued token stays locked.
- **ASVS 5.0 6.4.3 (L2)** requires that resetting a forgotten password not bypass any enabled multi-factor mechanism.
  The TOTP factor has its own failure window (ADR-027).

## Considered options

- **Clear nothing, as the standard's admin-reset sentence reads.** A user who redeems a token while locked holds a
  new password they cannot use. Worse, the cap is permanent. Unlock does not clear it, because unlock destroys no
  credential and so is not rebinding. A capped account could then never be restored by a reset, and the in-app
  admin-reset recovery route (ADR-048) would not work.
- **Clear both the password and the TOTP state.** An email-gated reset would lift an MFA lockout. Anyone reading
  reset links could then retry TOTP against an administrator freely. Fails 6.4.3.
- **Clear the password axis only (chosen).**

## Decision

- **Redemption clears:** `failed_login_attempts`, `last_failed_at`, `locked_until`,
  `consecutive_failures_since_success` and `password_disabled_at`. It also clears the forced-change flag and
  `credential_issued_at`, since the user has just chosen their own password (ADR-046).
- **Redemption never clears, resets or re-issues:** any TOTP failure counter, TOTP lock, factor disable or TOTP
  enrolment. The administrator factor-reset path remains the only route for those.
- **Issuance clears nothing**, whether the user or an administrator issues the token (REJ-016). Unlock is a separate,
  audited administrator action.
- The redemption and reset-request paths never go through the `AuthenticationManager`. If they did, a disabled
  password authenticator would block the only operation that can clear it.
- The admin-issued case is a **deliberate deviation** from the standard's Decision Logic sentence: the lock is not
  cleared at issuance, as it says, but it is cleared at redemption, which it forbids.

## Consequences

- The malicious-lockout residual shrinks. A user locked out by someone else can recover through reset, bounded by
  the per-identifier reset budget (ADR-010). Outside `dev` no reset link is delivered (R-CRED-021), so today this
  helps only where mail reaches the user.
- It does not weaken the lockout's automatic lift (ADR-011). Those arguments concern an administrator's own recovery,
  and an administrator without their authenticator cannot use an email-gated route either.
- Only the password is recoverable by email. A log reader who resets an administrator's password still lacks the TOTP
  factor, which is why the `dev` reset-link leak is only partial for administrators (R-CRED-020).
- Tests: T-LCK-017 (redemption clears the five password fields and leaves all TOTP state unchanged); T-CRED-024 (reset
  request and redemption succeed while the password authenticator is disabled, and clear it); T-CRED-009 (the reset
  paths never depend on the `AuthenticationManager`).

## Sources

- Standalone User Access Control Application Standard §2 Decision Logic (administrator-initiated reset of a locked
  account).
- NIST SP 800-63B-4 §3.2.2 Rate Limiting (Throttling); §4.2 Account Recovery.
- OWASP ASVS 5.0, V6.4: 6.4.3 (L2).
- OWASP Web Security Testing Guide, WSTG-ATHN-03 Testing for Weak Lock Out Mechanism.
- OWASP Forgot Password Cheat Sheet.
