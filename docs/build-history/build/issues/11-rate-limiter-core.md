# 11: Rate limiter core

**What to build:** `AuthRateLimiter` is the one component for every per-source and per-submitted-value budget, keyed through `SourceKeyResolver` (ADR-010; ADR-017). It uses Bucket4j with a `TimeMeter` built from the `Clock`, and in-memory state asserted single-instance (REJ-018). Each budget row binds `app.security.rate-limit.<route>.<axis>.burst` and `.refill-period` (T-RL-010). This ticket lands the rows for:
- `POST /api/login`: source 60 / 1 per second, username 10 / 1 per 6 s;
- `GET /api/csrf`: source 30 / 1 per 2 s.

**Every later route ticket adds its own row** from the spec's budget table.

Refusals are 429 `TOO_MANY_REQUESTS` with an integer `Retry-After`, and they never move an account counter. There is no server-side delay (ADR-014). The per-username 429 is thrown before any repository lookup (T-RL-017). Every request is metered on session-store misses: 300 per 15 minutes per source. A 16384-byte body cap, counted in bytes, applies before the login converter reads the body (T-RL-011; T-RL-019).

Throttle audit rows are *transition-keyed* and *keyed rows*. Tier 1 is keyed by source and tier 2 by account, capped at 20 sources and 500 users per 15-minute keying window. Each row carries its count and first-seen time, and a truncation row carries exact counts (ADR-019; REJ-078; REJ-079; REJ-080; REJ-082).

**Blocked by:** 10

**Status:** done. Proven here: T-RL-001 to 003, T-RL-011 to 023, T-AUD-033, 034, 036 and 037, and T-CFG-029 (clustering properties refused: the single-instance assertion). Left on the ledger: T-RL-010 (the table test covers the rows built so far; route tickets 12 to 15 and 17 to 19 add theirs), T-AUD-035 (the tier-2 cap is unit-tested, but rows 12, 14 and 35 do not exist yet), and T-AUTH-003 and T-AUTH-006 (their limiter cases are tested; the lockout, cap, reset and registration cases are not built yet). Row 13 (CSRF) is now a tier-1 keyed row.

- [x] The 61st login from one source in a burst gets 429 with an integer `Retry-After`, and the refill follows the `Clock`.
- [x] The username-axis 429 happens with no repository call, identically for real and unknown usernames.
- [x] A 16385-byte body is refused before the credentials are parsed.
- [x] The session-miss budget refuses the 301st miss in 15 minutes.
- [x] Keyed rows and the truncation row behave at the source and user caps.
- [x] Each budget binds to its property, and changing the property changes the behaviour.
