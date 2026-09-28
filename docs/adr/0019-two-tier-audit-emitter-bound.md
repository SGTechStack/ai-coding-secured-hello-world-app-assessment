---
status: accepted
---

# ADR-019: A two-tier emitter bound caps audit volume, because a limiter cannot

Audit rows that anyone can trigger without an account are emitted at most once per (key, row, reason, window), and
the number of distinct keys per window is capped. The single audit emitter enforces this, not the rate limiter. A
maintainer seeing unbounded audit volume would reach for the limiter that already exists. That fails, because
volume is rate multiplied by distinct sources, and a limiter only bounds the first factor.

## Context

- **Audit volume is per-source rate times distinct-source count.** A per-source limiter bounds rows per source. The
  attacker chooses how many sources, so the product stays unbounded. The limiter buys a constant factor and nothing
  more.
- **The threat is rows on disk.** Several rows cost an attacker nothing:
  - the session-end row for an unknown or expired session id, reachable on any safe method with a made-up cookie;
  - the CSRF-failure row, reachable on any unsafe method;
  - the rate-limit row itself.
  None of them needs an account, so their keys come from an unbounded space.
- **Other rows need a resolved user:** role denials, missing or expired factor, and the profile self-read. Their key
  space is the user population. That population is bounded today because registration stores no credential, and
  outside `dev` no activation or reset link is delivered. The bound is the absence of a mail transport, which is
  planned to change.
- **The standards say different things.** The organisation's Structured Logging Application Standard allows a
  counter-based gate (§3.3), and warns that a per-request evaluation log "will flood the log under load" (§2). ASVS 5.0 is silent
  on aggregating, sampling or truncating security log records. Silence is not permission, and this ADR does not claim
  it.

## Decision

- **Tier 1: unbounded key space, keyed on the source key (ADR-020).** Rows: rate-limit (`RATE_LIMITED_SOURCE`,
  `RATE_LIMITED_SOURCE_MISSES`), session-end (`UNKNOWN_OR_EXPIRED`, `DUPLICATE_SESSION_COOKIE`) and CSRF
  (`CSRF_MISSING`, `CSRF_INVALID`). Each is emitted once per (source, row, reason, window). At most
  `app.audit.truncation.distinct-sources` = 20 sources are tracked per window.
- **Tier 2: bounded key space, keyed on `user.id`.** Rows: `INSUFFICIENT_ROLE`, `FACTOR_MISSING`, `FACTOR_EXPIRED`,
  and the profile self-read. Each is emitted once per (user, row, reason, window), with at most
  `app.audit.truncation.distinct-users` = 500 users per window. The user cap is insurance against the mail transport
  arriving, and it carries that reopening trigger.
- **Tier 3: unchanged.** Authentication rows, lockout transitions and every row behind a budgeted route or an account
  counter stay per event.
- **One window for both tiers:** `app.audit.keying.window` = 15 minutes.
- **Nothing is silently lost.** Every keyed row carries the number of occurrences it stands for and the window's
  first-seen time (REJ-078). A single truncation row reports two exact numbers: `source.distinct_count`, the sources
  tracked, and `events.untracked_count`, the emissions from sources beyond the cap. It also names which row was
  truncated (REJ-079). The true distinct count therefore lies between N and N plus the untracked count.
- **No eviction inside a window.** The per-window membership set holds at most N entries, and source N+1 is never
  admitted. Evicting an entry would let that source emit again, which bypasses the bound. So the evidence bound and
  the memory bound are the same number.

## Consequences

- ASVS 16.2.1 (L2) asks that each entry carry enough to reconstruct a timeline. A keyed row keeps who, where and
  what, and loses only the individual timestamps inside the window, which the count and first-seen fields make up
  for (R-AUD-028). The truncation row is where source identity is actually lost (R-AUD-029).
- ASVS 16.3.1 (L2), which requires logging all authentication operations, is not engaged. No keyed row records an
  authentication operation. Those rows are tier 3 and stay per event.
- Any new audit row emitted before routing must join tier 1 or tier 2. Otherwise it reopens the volume arithmetic
  (R-AUD-031).
- The stream's consumer must alert on the truncation row per event, and treat the count fields as magnitude signals.
  The application emits them and cannot alert on them (R-OBS-013). The log inventory must record the keyed and
  truncated forms, not only the event list (R-AUD-027).
- Disk sizing is a formula with one deployer input, not a table (REJ-084). The 20, 500 and 15-minute constants are
  tunable, with a recomputation trigger (REJ-080).
- Tests: T-AUD-033 (one window for both tiers), T-AUD-034 and T-AUD-035 (the two caps), T-AUD-036 (exact counts on
  the truncation row), T-AUD-037 (occurrence count on a keyed row).

## Sources

- Appfw Logging Standards, Structured Logging Application Standard §3.3 Logging Contract (counter-based gate) and
  §2 Conditional Logic and Decision Points (the per-request flood warning).
- OWASP ASVS 5.0 (`v5.0.0_release`), V16: 16.1.1 (L2), 16.2.1 (L2), 16.3.1 (L2), 16.3.3 (L2). None constrains
  aggregation or truncation.
