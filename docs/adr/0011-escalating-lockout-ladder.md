---
status: accepted
---

# ADR-011: Escalating lockout ladder 20/40/60 minutes instead of the PRD's flat 15

After 5 failures inside the observation window (ADR-012), an account locks for 20 minutes. The first five locks
last 20 minutes, the next five last 40, and every lock after that lasts 60. Each lock lifts by itself. A maintainer
holding PRD Story 3 ("e.g. 15 minutes") would put a flat 15 back. That would break a startup check. It would also
remove the only NIST additional technique this application can implement, and cut the warning time before the
permanent password disable (ADR-013) by about two-thirds.

## Context

- PRD Story 3 gives 15 minutes as an example. The governing standard's §3.5 default is 20 minutes with automatic
  lift. Where the two disagree on how a control behaves, the standard governs, so the base rung is 20 (R-LCK-007).
- NIST SP 800-63B-4 §3.2.2 lists three optional techniques that reduce the chance an attacker locks out the
  legitimate subscriber. They are additions to the 100-attempt limit, not replacements. One is a wait after a failed
  attempt that increases as the account approaches its maximum, with the example "30 seconds up to an hour". Bot
  detection is out of scope here. Risk-based signals need a behavioural baseline this application cannot learn. So
  the increasing wait is the only one of the three we can build.
- A flat lock is not that technique. The technique is defined by a wait that grows. It matters because the capped
  state (ADR-013) is permanent until rebinding, and outside `dev` rebinding needs the offline runner (ADR-072).
- The wait could be expressed in two ways: as a `Retry-After` delay derived from the account's counter, or as a
  longer `locked_until`. A per-account `Retry-After` puts account state in a response header. The enumeration
  contract (ADR-033) makes status, body and timing uniform, but it does not cover headers. A third party probing a
  username would learn that the account exists and is under attack, which ASVS 6.3.8 (L3) forbids.
- Q15 in the standard's decision questions offers a permanent lock that only an admin can clear. That was rejected.
  With no self-service unlock and one seeded admin, a mistyped password would become an unrecoverable deployment.

## Decision

- **The escalation is expressed as lockout duration, never as a header.** The response during and after a lock is
  the same uniform `401` either way. Only the account owner can observe the longer wait, and that friction is what
  the technique consists of. It needs no extra thread, header or state: `locked_until` is simply set further out.
- **The rung is read from `consecutive_failures_since_success`** (ADR-013's counter), not from the windowed counter,
  which resets on staleness and would never climb the ladder.
- **Constants:** `app.security.lockout.threshold` = 5, `app.security.lockout.ladder.rungs` = 20m, 40m, 60m,
  `app.security.lockout.ladder.cycles-per-rung` = 5.
- **The arithmetic, which a startup check enforces.** Attempts during a lock raise `LockedException` and do not
  advance the cap. So with threshold t = 5, cap C = 100 and alert threshold A = 50:
  - locks before the cap = floor((C − 1) / t) = 19;
  - locks before the alert = floor((A − 1) / t) = 9;
  - time to the permanent disable = 5×20 + 5×40 + 9×60 = 840 minutes, about 14 hours;
  - time to the alert = 5×20 + 4×40 = 260 minutes;
  - warning between them = 580 minutes, about 9.7 hours.
  A flat 20 minutes would give 380 minutes to the cap and 180 to the alert, about 3.3 hours of warning.
- **Startup floor.** A `@Validated @ConfigurationProperties` check derives both lock counts from the threshold, cap
  and alert threshold, then sums the configured rungs over them. It fails the context refresh if the time to disable
  is under 840 minutes or the warning is under 580. Deployers may make the ladder slower, never faster. Checking only
  the rungs would not be enough: raising the threshold to 10 halves the lock count while every rung still passes.

## Consequences

- **The 60-minute rung sits outside OWASP WSTG-ATHN-03's 5-to-30-minute band.** NIST's "up to an hour" is the
  governing citation, and WSTG's band is descriptive.
- **The malicious-lockout residual grows.** It becomes 20 minutes at first, and up to an hour under sustained attack
  (R-LCK-003). An hour is still a delay that lifts by itself, not an admin-only lock, so the bootstrap argument above
  still holds.
- **The ladder adds delay, not a throughput limit.** Per-disable cost is fixed at 100 requests, so disables per hour
  from one unlimited source stay at 36 (ADR-013). What the ladder changes is when those requests land, and that makes
  a campaign last longer and easier to see. It is the one bound that does not depend on how many sources the attacker
  holds (R-LCK-005, ADR-015).
- A legitimate user who is locked repeatedly waits longer than the PRD's example.
- Any change the floor would reject is a reopening trigger, not a configuration edit (R-LCK-014).
- Tests: T-LCK-010 (rungs binding), T-LCK-014 (cycles per rung), T-LCK-012 (threshold), T-LCK-011 (startup floor,
  including the raised-threshold case), T-LCK-020 (no disable before 840 minutes, and the alert fires first).

## Sources

- PRD `prd/assessment-prd.md`, Story 3, first acceptance criterion.
- Standalone User Access Control Application Standard §3.5 Security Contract (Account Lockout: 5 failures,
  20 minutes, automatic lift); Standard Questions Q15 (lockout unlock strategy).
- NIST SP 800-63B-4 (July 2025) §3.2.2, additional techniques (increasing wait, "30 seconds up to an hour").
- OWASP Web Security Testing Guide, WSTG-ATHN-03 (Testing for Weak Lock Out Mechanism).
- OWASP ASVS 5.0, 6.3.8 (L3).
