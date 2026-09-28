---
status: accepted
---

# ADR-016: Narrow multi-authenticator reading of NIST §3.2.2: a tier-2 factor disable forces password rebinding

When the TOTP factor reaches its tier-2 disable (ADR-027), the password is **not** disabled. Instead the account is
flagged for a forced password change at its next successful login, and its sessions end. This rests on a reading of
NIST SP 800-63B-4 §3.2.2's multi-authenticator sentence. The wider reading is the one a reviewer reaches first, and
under it the password would be disabled too. A maintainer who adopts it would, on this counting design, eventually
disable both authenticators of a legitimate admin with no attacker involved.

## Context

- §3.2.2: "If more than one authenticator is involved with an excessive number of authentication attempts (e.g.,
  single-factor cryptographic authenticator and centrally verified password), both authenticators SHALL be disabled."
  The text does not say how this applies to a staged flow, where the factor is reachable only after a correct
  password.
- **Wide reading:** an attempt that reaches the factor stage involved both authenticators, so a tier-2 disable must
  disable the password as well. That escalates straight to §4.2 account recovery.
- **Narrow reading:** stage-1 failures never involve the factor. Stage-2 failures involved a password that
  *succeeded*, so the password did not fail. The limit's own wording is "failed authentication attempts using a
  specific authenticator", and the parenthetical describes one ceremony using two authenticators, not two counters
  exhausted separately. This is the stronger reading on the text's own words, but it is an interpretation, not a
  quote.
- The genuine simultaneous case is covered separately. Counters stay independent for counting, and if both
  authenticators are over their thresholds at the same time, both lock.
- **The wide reading's best argument is real.** Every tier-2 increment cost a valid password. So 100 factor failures
  are 100 confirmations that someone holds the password.
- **The strongest argument against it comes from the counting rule, not the text.** Tier 2 is cumulative and never
  resets (ADR-027). Combined with the wide reading, every legitimate admin's ordinary mistyped codes would add up,
  over the account's life, to disabling both authenticators with no attacker present.

## Decision

- **Narrow reading adopted.** A tier-2 factor disable never disables the password authenticator.
- **Middle path for the wide reading's signal.** A tier-2 disable is treated as a sign the password may be
  compromised. It sets the account's `force_password_change` flag and ends its sessions.
- **Hosted on the forced-change flag, never on the 30-day forced-change expiry.** The flag is evaluated after a
  successful authentication, and the only thing that slot may reveal is something success already implies. The
  expiry refuses a login, and a refusal must never sit behind the password check, or it confirms a correct password
  (ADR-046). `credential_issued_at` is not stamped, so no expiry clock starts.

## Consequences

- **The forced change is deferred, not immediate.** A tier-2-disabled admin cannot complete the factor stage, so they
  cannot reach the forced change until their factor is reset and they re-enrol. The reset comes from another admin,
  or from the offline runner's `totp` scope (ADR-027, ADR-072). This is not relief for
  someone already locked out, and it must not be described as keeping their session alive.
- A reviewer taking the wide reading will grade this as a deviation. The answer is this ADR, and the factor-axis
  grading in the register (R-LCK-008).
- No test-plan row yet asserts that a tier-2 disable sets `force_password_change`. T-MFA-021 covers the disable
  itself, and T-ADM-005 covers what the flag does once set.
- Reopen if the factor's tier 2 stops being cumulative. The argument against the wide reading rests on that counting
  rule.

## Sources

- NIST SP 800-63B-4 (July 2025) §3.2.2 Rate Limiting (Throttling): the per-authenticator limit, the
  multi-authenticator SHALL and the rebinding SHALL; §4.2 account recovery.
- OWASP ASVS 5.0, 7.4.2 (L1) (terminate sessions when an account is disabled).
