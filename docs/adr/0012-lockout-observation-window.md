---
status: accepted
---

# ADR-012: Failures count only inside an observation window

The lockout counter (`failed_login_attempts`) counts a failure only if the previous failure is less than 20 minutes
old. Otherwise it restarts at 1. The governing standard defines no window, so a maintainer would plausibly remove
this as an unrequested addition. That would bring back two bugs: five typos spread over months lock the account,
and the first wrong password after a lock lifts re-locks it at once.

## Context

- The standard's §3.5 locks after "5 consecutive failed logins", and its counter resets only on success. With no
  window, failures accumulate indefinitely. Five mistyped passwords across a year are enough.
- The OWASP Authentication Cheat Sheet names three factors to weigh for a lockout policy: the lockout threshold,
  the observation window and the lockout duration. The standard sets two of them.
- The lock is derived, not stored: an account is locked while `locked_until` is in the future, so the lock lifts
  with no scheduler. But the counter still reads 5 after the lift, so without a window the next failure re-locks at
  once, and the user gets one attempt, not five.
- The factor lockout in the MFA corpus uses the same staleness-reset mechanism, and it has a known limit. The reset
  looks only at the most recent failure, so an attacker who spaces out attempts still locks a known username.

## Decision

- **Mechanism: a staleness reset on `last_failed_at`.** On a failure, if `last_failed_at` is older than the window,
  set the counter to 1. Otherwise, increment it. A success resets the counter to 0, as the standard requires.
- **Window and duration are two values, with `duration ≥ window`.** The window is
  `app.security.lockout.observation-window` = 20m. The duration is the ladder's rung (ADR-011), which is at least
  20 minutes. The constraint is what fixes the re-lock bug. When a lock has just lifted, `last_failed_at` is at least
  one duration old, so it is also at least one window old, and the next failure starts a fresh count.
- **The windowed counter is not the cap counter.** Because the window resets it, a NIST limit built on it could
  never reach 100. The cap has its own counter, `consecutive_failures_since_success` (ADR-013). The two
  similar-looking columns have different jobs.

## Consequences

- The limit is stated honestly: the window protects legitimate users from their own typos spread over days. It does
  not stop a paced attacker. No counter tied to one account can prevent a targeted lock (R-LCK-002, REJ-013).
- A shortened duration would break `duration ≥ window` and bring back the re-lock bug. The ladder's startup floor
  (ADR-011) already refuses a faster ladder, so the current rungs cannot drop below the window.
- `last_failed_at` is a column the PRD's data model does not have. It is added because it is what makes a window
  implementable.
- Tests: T-LCK-001 (failures spaced wider than the window do not accumulate, including right after an auto-lift),
  T-LCK-013 (window binding), T-LCK-006 (success resets the counter), T-LCK-021 (the window's limit since the
  amendment below).

## Amendment (2026-09-29): the window forgives only up to the consecutive threshold

The window protects legitimate users from typos spread over days, but it also let a paced attacker stay under the
threshold forever while the cap counter climbed to the permanent disable, with no lock and no cardinality entry
(ADR-011 amendment). The window's forgiveness is now bounded: once `app.security.lockout.consecutive-threshold` (10)
failures have accrued since the last success, the 10th locks and every 5th after it locks, whatever their spacing.
The staleness reset of `failed_login_attempts` is unchanged, and so is `duration ≥ window`, which the floor model
relies on: a lock always restarts the window.

The "limit stated honestly" consequence above is narrowed. The window still does not stop a paced attacker from
locking a known username (R-LCK-002), but a paced attacker can no longer reach the cap faster than a steady one.
Five typos over a year still lock nothing; ten with no successful sign-in between them now do.


## Sources

- Standalone User Access Control Application Standard §3.5 Security Contract (Account Lockout: 5 consecutive
  failures, counter resets on success).
- OWASP Cheat Sheet Series, Authentication Cheat Sheet, "Account Lockout" (lockout threshold, observation window,
  lockout duration).
- NIST SP 800-63B-4 (July 2025) §3.2.2: the mandatory limit has no time window, and no time-based reset is
  sanctioned. That is why the cap counter (ADR-013) is kept separate from this windowed counter.
