---
status: accepted
---

# ADR-015: A per-source cardinality axis caps distinct accounts driven into lockout

Each source key keeps a small set of the accounts it has driven into a timed lockout. When the set holds 5, logins
from that source for any other username get `429`. A third limiter, with membership state and an expiry pinned to
first insertion, looks like over-engineering beside the per-source and per-username buckets. A maintainer could
plausibly delete it or fold it into the budget table. Either change would remove the only width bound on the
mass-disable primitive that ADR-013 creates.

## Context

- The NIST cap (ADR-013) makes a permanent password disable cost about 100 requests. The per-source login budget
  allows 3,600 an hour, so one source can disable 36 accounts an hour. Neither existing limiter engages. Each
  account's track is far inside the per-source budget, and 40 times inside the per-username bucket.
- Targets are cheap to find. Registration reports username conflicts specifically (`USERNAME_UNAVAILABLE`), so a
  list of usernames can be confirmed rather than guessed.
- The ladder (ADR-011) bounds time per account. Nothing bounded how many accounts one source works at once.
- Three obvious formulations fail:
  - **Distinct accounts attempted.** A hundred staff behind one NAT are a hundred distinct accounts on an ordinary
    morning, so this trips on every shared egress. Counting lockouts instead reverses that. Legitimate NAT traffic
    produces very few lockouts, while the attack produces one per account per cycle.
  - **A token bucket.** A bucket counts events, and the question here is how many distinct accounts. That needs
    membership state, which Bucket4j does not provide.
  - **A row in the budget table.** The table's columns are burst and sustained rate, which a cardinality limit does
    not have. A reader treating the table as a schema would build it as a bucket.

## Decision

- **Axis:** source key (ADR-020) → the set of distinct accounts this source has driven into a timed lockout.
  `app.security.rate-limit.lockout-cardinality.k` = 5, `app.security.rate-limit.lockout-cardinality.window` = 1h.
- **Recorded at the lockout transition**, in the failure listener, not at the attempt. The listener has no request,
  so the converter sets `SourceKeyAuthenticationDetails` on the token, and the listener reads the source key from
  there.
- **Checked in the converter**, beside the per-username bucket. It uses the same `429` branch, before BCrypt.
- **What a full set refuses: non-member usernames only.** Logins for accounts already in the set proceed.
- **Expiry runs from first insertion.** A re-lock of a member tests membership and never writes. The set is written
  with `asMap().putIfAbsent` on a first lock only. Caffeine's `expireAfterWrite` resets on every write, so without
  this, an attacker re-locking its 5 members would keep the set full for the whole 14 hours of each chain. So the
  "hour" is a fixed hour from each member's first lock, not a sliding window.
- **State:** a bounded Caffeine cache per source with its own `maximumSize`. Eviction here is a bypass of the
  control, not a memory saving, so the size is set so that it does not evict within a window.

## Consequences

- **The honest ceiling.** One bucket carries about 5 disable chains at once. That is about 0.36 disables an hour,
  against 36 from an unlimited source, so roughly a hundredfold reduction. It binds only below P ÷ k source keys,
  about 20 for the planning population of 100 accounts. That number was already cheap under IPv4, and it is free under
  IPv6 for anyone holding more than one /64. Above it, the ladder is the bound: no account is disabled in under about
  14 hours (R-RL-002, R-LCK-005).
- **Rotating sources restores throughput.** This is the standard's own objection to per-IP limiting, and it is
  recorded as a property of this axis, not denied.
- **Shared NAT pays.** A full set refuses every non-member on that egress, which on a shared NAT is nearly everyone,
  for about an hour after the fifth first lock. Refusing members too would stop the attacker's own chains, but it
  would cost the NAT the same and gain nothing on price, because the ladder binds at about 20 keys either way
  (R-RL-003).
- **Fails closed if the details are missing.** If the converter stops setting the details, every lockout is
  attributed to one key. The set fills at once, and the axis `429`s everyone. T-RL-005 pins the converter.
- A coarser second key (/56) for this axis was declined (R-RL-016). A global lockout-rate cap was declined because it
  would let an attacker switch off the NIST limit for everyone (R-LCK-013). A change to k, or growth well past 100
  accounts, reopens the pricing (R-RL-022).
- Tests: T-RL-006 (the `429` on a full set), T-RL-007 (keyed on lockout transitions, not attempts), T-RL-008 and
  T-RL-009 (bindings), T-RL-030 (members proceed, non-members refused), T-RL-031 (expiry pinned to first insertion),
  T-RL-005 (details set by the converter).

## Sources

- NIST SP 800-63B-4 (July 2025) §3.2.2: the 100-attempt SHALL, which this axis protects from being turned against
  users. IP address appears only as an optional risk-based signal.
- OWASP ASVS 5.0, 6.1.1 (L1) (how controls prevent malicious account lockout); 15.3.4 (L2) (real client IP for
  security decisions, with the stated caveat that it may be unreliable).
- Standalone User Access Control Application Standard §2 Decision Logic (per-account counting to prevent bypass by
  IP rotation); Standard Questions Q16.
- Caffeine documentation, `expireAfterWrite` (the timer resets on each write).
