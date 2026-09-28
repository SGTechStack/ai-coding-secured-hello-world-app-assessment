---
status: accepted
---

# ADR-027: Two-tier TOTP lockout: tier 1 auto-lifts, tier 2 disables cumulatively

Failed TOTP codes feed two counters. **Tier 1:** 10 consecutive failures inside a 20-minute observation window lock
the factor for 20 minutes, and the lock lifts by itself. **Tier 2:** 100 cumulative failures disable the factor.
The tier-2 counter never resets on success, and only rebinding clears it. MFA_Core prescribes one lock, lifted only
by administrative review. A maintainer would plausibly collapse the tiers back to that, or "harmonise" tier 2 with
the password axis's consecutive cap. Both reopen an attack this ADR prices.

## Context

- **MFA_Core §3.4 (Per-User Attempt Limits)**, enforced constraint: after "10 cumulative failures within a 1-hour
  window" the user is removed from the session and "the account locked until administrative review". The counter
  resets on successful verification. It has three defects:
  - **The window does not slide.** The recipe resets the counter only when the single most recent failure is older
    than an hour. One attempt every 59 minutes still locks the account after about 9 hours. With no self-unlock and
    a username-only key, that is a denial-of-service lever against a known admin username (R-STD-042).
  - **The counter has three readings:** "cumulative" in §3.4, "consecutive" in §3.3 and §4.2, and
    consecutive-since-last-success-or-one-hour-gap in the recipe (R-STD-044).
  - **No TOTP unlock path exists anywhere.** The admin unlock recipe clears the PIN table only.
- "Until administrative review" cannot work here. It would brick a fresh deployment with one seeded admin, the
  reviewer may be the locked party in a two-admin population, and OWASP WSTG-ATHN-03 expects a self-recovery tier
  beneath any administrator-only control.
- **NIST SP 800-63B-4 §3.2.2:** the verifier "SHALL limit consecutive failed authentication attempts using a
  specific authenticator on a single subscriber account to no more than 100 by disabling that authenticator", and
  "Authenticators that have been disabled SHALL be required to rebind". 100 is an upper bound, and lower limits are
  permitted.
- **A six-digit code is a 10⁶ space**, not a passphrase. The password axis's auto-lifting ladder is calibrated for a
  15-character credential (ADR-011, ADR-013). Copied onto a TOTP factor, auto-lift alone gives:

  > 10 attempts per 20-minute cycle is 720 a day. Against the ±1 window each guess matches with probability at most
  > about 3/10⁶, so over a year the chance of success is 1 − exp(−262 800 × 3×10⁻⁶), **about 55%**.
  >
  > With a cumulative cap of 100 it is 100 × 3×10⁻⁶, **about 0.03% over the life of the secret**.

## Decision

- **Tier 1:** 10 consecutive failures inside a 20-minute window, then a 20-minute lock that lifts automatically.
  Same three columns and derived predicate as the password axis (`locked_until` null or past), no scheduler. It
  **resets on success**, as §3.2.2 recommends. A locked factor answers `429` with the factor member and an integer
  `Retry-After` (ADR-033).
- **Tier 2:** 100 **cumulative** failures disable the factor. The counter **does not reset on success**. The factor
  answers `423 FACTOR_DISABLED` until it is rebound (ADR-033).
- **Tier 2 clears only by rebinding:** the admin factor reset, which deletes the secret and forces re-enrolment, or
  the offline runner's `totp` scope (ADR-072). The admin unlock clears the password lock and tier 1, **never tier 2**
  (REJ-072). Password-reset redemption never touches either tier (ADR-009).
- **A tier-2 disable also forces password rebinding at the next sign-in.** Every tier-2 increment cost a valid
  password, so 100 factor failures are 100 confirmations that the password is known. It uses the forced-change flag,
  which is evaluated after a successful authentication and so carries no oracle. It does not use the 30-day
  forced-change expiry, and it does not stamp the credential-issued time. This is the middle path under the narrow
  reading of §3.2.2's multi-authenticator sentence (ADR-016).
- **Conjunction rule.** The password and factor counters never touch each other. If both are over threshold at
  once, both lock. That meets §3.2.2's "both authenticators SHALL be disabled" with one predicate.
- **Every guess costs the password.** The verification endpoint refuses an anonymous caller in the **provider**,
  before any comparison, and takes the username from the security context, never the request body. A precondition
  failure increments nothing, and neither does an empty code. The filter's `shouldPerformMfa` check is not this gate: it only decides whether
  authorities are merged, after authentication has already run.
- **Locking and ordering.** The counters and `lastUsedCounter` are updated under one pessimistic lock on the TOTP
  row, held across load, decrypt, compare and write, so replay rejection is atomic. A lock timeout on this path is a
  failed authentication, not fail-open, because losing the lock loses replay rejection. The tier-2 trip takes the
  `users` row **before** the TOTP row, keeping the order used everywhere else (users, then TOTP rows, then
  sessions). The trip ends the target's sessions **after commit** (ADR-037, ADR-039).
- **Provisioning never resets a counter.** It writes the pending row only, so re-provisioning cannot launder a lock.
  NIST §3.1.3.2 states this for out-of-band verifiers, and we adopt it by analogy.

## Considered options

- **One lock until administrative review, as MFA_Core §3.4 prescribes.** Rejected on the three grounds above.
- **Tier 1 alone, copying the password axis.** Rejected: about 55% a year for an attacker who holds the password.
- **Tier 2 counted consecutively, resetting on success, as §3.2.2's wording reads.** Rejected. A legitimate morning
  sign-in would zero the counter, tier 2 would never fire against an active admin, and the 55% returns. Our
  cumulative reading is **stricter than the SHALL**, which §3.2.2 permits.
- **The wide reading of §3.2.2, disabling both authenticators on a tier-2 trip.** Rejected. Combined with a
  cumulative counter that never resets, it would guarantee that a legitimate admin eventually destroys both
  authenticators with no attacker present. That arithmetic, from the counting rule rather than the text, is the
  strongest argument for the narrow reading.
- **Escalating backoff.** Declined. NIST offers it as a MAY, and a sleeping request holds a thread (ADR-014).

## Consequences

- **The 55% is conditional on prior password compromise.** Every guess costs that admin's password, which is the
  scenario a second factor exists for. The precondition justifies tier 2 rather than weakening it.
- **The per-source throttle does not bound this.** At tier 1's pace a source sends about 0.5 requests a minute
  against a 20-a-minute budget, so tier 1 is the only rate ceiling. That is why a cumulative cap is needed, not
  redundant.
- **The same number, 100, has two counting rules.** The password cap is consecutive and resets on success, because
  every account holds a password for its whole life and a cumulative count there would fire with no attacker
  present (ADR-013). The factor cap is cumulative. A reviewer comparing the two sees one control unless told.
- **NIST §3.2.2 is met at the cap.** Tier 1's auto-lift is not rebinding, and that is not a deviation: the rebinding
  SHALL attaches to the disablement at the cap, and tier 1 is an additional control below it (R-LCK-008).
- **Availability.** Burning an admin's factor costs that admin's password, and burning both costs two passwords. An
  admin can burn their own counter, and the other admin resets them. If both do, the break-glass runner is the
  route, and a tier-2 disable is its second trigger after a lost authenticator. The malicious-lockout residual is
  recorded under ASVS 6.1.1 (L1) (R-LCK-003).
- **The audit stream distinguishes an automatic trip from an operator action**, because the break-glass procedure
  keys off that difference. A tier-2 disable logs at ERROR, and a tier-1 lock at WARN, once per transition.
- Tests: T-MFA-020 (tier-1 lock, auto-lift, reset on success), T-MFA-021 (100 cumulative failures interleaved with
  successes and tier-1 lifts disable the factor; unlock does not clear it and factor reset does), T-MFA-007 (the
  tier-2 trip lock order), T-MFA-015 (an empty code is not counted).

## Sources

- Unified MFA Application Standard (`Appfw-Mfa-Standards/MFA_Core`) §3.3, §3.4 Per-User Attempt Limits, §4.2; MFA
  recipes (factor verification and admin unlock); Base Standalone Application Standard Questions, Q15 to Q17.
- NIST SP 800-63B-4 §3.2.2 (failed-attempt limit, rebinding, multiple authenticators, reset on success, AAL of the
  reset); §3.1.3.2 (new secret does not reset the failure count).
- OWASP Web Security Testing Guide, WSTG-ATHN-03 (Testing for Weak Lock Out Mechanism).
- OWASP ASVS 5.0: 6.1.1 (L1), 6.5.1 (L2), 7.4.2 (L1).
- RFC 6238 §5.2.
