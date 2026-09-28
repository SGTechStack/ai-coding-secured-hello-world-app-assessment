---
status: accepted
---

# ADR-013: NIST SP 800-63B-4 §3.2.2 cap: the password authenticator is disabled after 100 consecutive failures

The password authenticator has a second counter, `consecutive_failures_since_success`. It counts every failed
password with no time window, and only a successful password resets it. At 100 it sets `password_disabled_at`.
From then on the password is refused until it is rebound, whatever any lockout says. Two things look like mistakes
here, and a maintainer could "fix" either. A limit of 100 beside a lockout at 5 looks redundant. And this counter
resets on success, while the factor's tier-2 counter (ADR-027) never resets, which looks inconsistent.

## Context

- NIST SP 800-63B-4 §3.2.2 is a SHALL: the verifier "SHALL limit consecutive failed authentication attempts using a
  specific authenticator on a single subscriber account to no more than 100 by disabling that authenticator".
  Disabled authenticators "SHALL be required to rebind". The text has no time window anywhere, and 100 is an upper
  bound that agencies MAY lower. §3.1.1.2 applies §3.2.2 to passwords.
- The timed lockout does not satisfy it. A lock that lifts by itself is not a disable, so a paced attacker gets
  unlimited guesses over time.
- The windowed lockout counter (ADR-012) cannot host the cap, because its staleness reset means it could never
  reach 100.
- §3.2.2 has two SHOULDs on reset. The verifier should disregard earlier failures "for the authenticators used in
  the successful authentication", and should reset the retry count "of the authenticators that were used". So scoping
  the reset to the authenticator involved is NIST's own rule. The AAL clause permits it here: the authenticator being
  reset may not exceed the AAL of the session resetting it, and a password is the lowest-AAL authenticator.
- The factor's tier 2 counts cumulatively on purpose. It guards a freshly bound, admin-only, cheaply rebindable
  secret in a space of 10⁶, and a daily successful login would otherwise zero it. A password is held by every account
  for its whole life. A cumulative count would eventually disable it with no attacker present.

## Decision

- **The cap is implemented at 100 consecutive failures** (`app.security.lockout.nist.cap`), with an alert at 50
  (`app.security.lockout.nist.alert-threshold`). Each fires once, on its transition.
- **100 is chosen because it minimises attack throughput, not only because it is the maximum allowed.** One
  disable costs 100 requests, so disables per hour from one source equal the per-source login budget divided by
  the cap: 3,600 ÷ 100 = 36. Halving the cap to 50 would double that to 72. It would also shorten the defender's lead
  time between the alert and the permanent state.
- **Counting is per authenticator.** Only a successful password resets the counter. Both login stages publish
  `InteractiveAuthenticationSuccessEvent`, so the listener tells them apart by `Authentication` type or source
  filter, not by event type. A factor success never resets the password cap, and the password never resets the
  factor's counters. Attempts during a timed lock raise `LockedException` and do not advance the cap.
- **Its own state, not `enabled = false`.** An admin re-enable must not clear a NIST disable, and the user list must
  tell "disabled by an admin" apart from "disabled by the cap" (REJ-020).
- **The refusal runs as a pre-authentication check** that throws its own exception. That keeps the audit stream
  from confirming a correct password, and it inherits the provider's constant-cost `matches()` call (REJ-019).
- **Only rebinding clears it:** reset-token redemption (ADR-009) or the offline recovery runner (ADR-072). Admin
  unlock does not clear it, and neither does admin issuance of a reset token. The reset and redemption paths never
  go through `AuthenticationManager`. If they did, the disabled password would block the only operation that can
  clear it.
- **No enforcement gate on "is recovery available?"** §3.2.2's note about recovery burden is the reason for choosing
  100. It is not a condition on the SHALL. Outside `dev`, before mail transport exists, that condition would be false,
  so the cap would never engage.
- **Detection is kept alongside the cap.** Reset-on-success makes the cap blind to an account that is compromised
  but still active. A per-account ratio of failed to successful logins covers that shape, and it is a different signal
  from the per-source enumeration signature.

## Consequences

- **A mass permanent-lockout primitive exists, and it is priced rather than hidden.** About 100 unauthenticated
  requests disable one account's password. One unlimited source can do 36 an hour. The lockout-cardinality axis cuts
  that to about 0.36 an hour per limited source (ADR-015). The ladder means no account is disabled in under about
  14 hours, however many sources the attacker holds (ADR-011, R-LCK-005, R-LCK-011).
- **PRD Story 2's third criterion is exceeded, not met as written.** A capped password stays refused after the lock
  expires, until it is rebound (R-LCK-004).
- **Recovery depends on who is left.** Outside `dev`, a self-service reset request produces no deliverable link until
  a mail transport exists. So a disabled user recovers through a reset token issued by another authenticable admin
  (ADR-006) and redeemed by the user. When no other authenticable admin exists, recovery is the offline runner,
  which needs deploy-level access (ADR-072, R-RUN-003).
- **A cap disable ends the account's sessions.** The disable is one of the session-invalidation triggers (ADR-037).
  Sessions are killed after commit, and a reconciliation sweep repairs a crash in between (ADR-039).
- **No global cap on lockout rate.** A global cap would let an attacker switch the SHALL off for everyone
  (R-LCK-013).
- Tests: T-LCK-016 (cap binding), T-LCK-015 (alert threshold), T-LCK-018 (column invariants), T-LCK-009 (refusal
  publishes no event), T-AUTH-003 (one `matches()` call per request), T-CRED-024 (reset succeeds while disabled),
  T-CRED-009 (reset paths never depend on `AuthenticationManager`), T-SES-022 (sweep), T-LCK-020 (time to cap).

## Sources

- NIST SP 800-63B-4 (July 2025) §3.2.2 Rate Limiting (Throttling): the consecutive-failure SHALL, the MAY on lower
  limits, the two reset SHOULDs, the AAL constraint on reset, and the rebinding SHALL. §3.1.1.2 (passwords are
  subject to §3.2.2), §4.1 (rebinding), §4.2 (account recovery).
- Spring Security 7.1.x reference: `AbstractAuthenticationProcessingFilter.successfulAuthentication` publishes
  `InteractiveAuthenticationSuccessEvent` on every successful filter authentication.
- OWASP ASVS 5.0, 6.1.1 (L1) (documenting how controls prevent malicious account lockout), 7.4.2 (L1) (terminate
  sessions when an account is disabled).
