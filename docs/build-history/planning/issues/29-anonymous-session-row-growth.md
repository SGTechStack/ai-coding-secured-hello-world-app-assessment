# 29 — Decide what bounds anonymous session-row growth, and what watches the H2 file

Type: grilling
Status: resolved
Graduated: 31 (IPv6 source keying for every per-source limiter)
Blocked by: 08, 09, 12, 21, 26

## Question

What bounds the **total** number of live anonymous `SPRING_SESSION` rows, and what instrument observes the H2
database file — the resource that holds them and that nothing on this map currently watches?

Graduated from [ticket 26](26-unbudgeted-routes-and-audit-volume.md), which bounded the *read* side of the same
attack and found the *write* side unbounded and uninstrumented.

## Why this is a decision and not a correction

The number is not new. It has been carried forward four times and never owned:

- **Ticket 08** established the shape and the arithmetic: "The real control on unauthenticated session growth is
  **rate limiting, not the cleanup cron**" — `spring.session.jdbc.cleanup-cron` deletes only rows whose
  `EXPIRY_TIME` has passed, so live rows are untouched, and live anonymous rows ≈ request rate × idle window
  (`08:120-129`). It computed ~150 live rows per source at 10/min and handed the number to ticket 09.
- **Ticket 09** raised `/api/csrf` to 30 burst / 1 per 2 s per source IP (`09:311`) because 10/min breaks a
  developer reloading the SPA, and recorded the consequence: live anonymous rows rise "from roughly 150 to
  roughly **450 per source**" (`09:342-348`). Flagged onward to ticket 12.
- **Ticket 12** received it as a *sizing note*, twice (`12:128-131`, `12:800-802`), calling 450 "a live-row
  ceiling set by the limiter, not by the cron" — a per-source ceiling, and **no ticket multiplies it by the
  number of sources.**
- **Ticket 21** declined the only instrument that would see it: "a session-row-count gauge: it needs a scheduled
  query, and scheduled jobs are the map's largest deferral. The bound is already enforced by ticket 09's
  budgets" (`21:401-402`).

So four tickets each hold a piece and none owns the total. The claim that closes the loop — "the bound is
already enforced by ticket 09's budgets" — is true **per source** and false in aggregate, because the budget is
keyed on source and source is attacker-chosen. That is the identical defect ticket 26 found for audit rows,
arriving on a different resource.

## The finding

`GET /api/csrf` is unauthenticated and has a persistent side effect by design: §3.1:238 requires the CSRF token
be session-bound, so a pre-login session is structural and ticket 08 recorded the remedy "do not persist a
session for anonymous callers" as **unavailable**, not deferred (`08:490-494`).

Consequences neither bounded nor observed:

1. **Aggregate live anonymous rows are `450 × distinct sources`**, with distinct sources attacker-chosen. There
   is no global cap, no aggregate limiter, and the cleanup cron reclaims nothing that has not already expired.
2. **The H2 file has no instrument at all.** Ticket 21's `DiskSpaceHealthIndicator` is pointed at
   `${app.audit.log-dir}` (`21:482-485`) and `management.health.db` is deliberately disabled (`21:462`). The one
   file holding both the application tables and `SPRING_SESSION` is unwatched, while the log directory — the
   resource ticket 26 just bounded — has the map's only disk signal.
3. **The write path is a second cost with no budget on it.** Ticket 26's miss budget meters session-store
   *lookups*; it meters no INSERTs. A source rotating IPs mints rows at its full per-source budget indefinitely.

## Why the obvious remedies are blocked

This is the reason it is a ticket rather than a handover line: **every cheap answer collides with a resolved
decision.**

- **A shorter anonymous idle timeout** was considered and rejected by ticket 08 in terms:
  "**Pre-login sessions get the same 15 minutes — deliberately no second timer.** A shorter unauthenticated
  window was considered and rejected: it adds a second clock to reason about, and the failure it prevents is
  already handled" (`08:118-121`). Reopening that is a decision, not a correction, and the ticket's reasoning has
  to be answered rather than overridden.
- **Not minting a session** is barred by §3.1:238 (`08:490-494`).
- **A session-row gauge** was declined by ticket 21 on the scheduled-jobs deferral (`21:401-402`), which is the
  map's largest.
- **A `Max-Age` on the cookie** is barred by ticket 08 on remember-me grounds (`08:143-148`).

## What to decide

- **The bound.** A global cap on live anonymous rows, a shorter anonymous expiry (reopening `08:118-121`), an
  aggregate limiter, or accept-and-instrument. Whichever wins, state it as a property rather than a per-source
  figure, since the per-source figure is exactly what four tickets already have.
- **The instrument.** What observes the H2 file, given `management.health.db` is off, no scheduler exists, and a
  second `DiskSpaceHealthIndicator` on the database path is the cheapest candidate. Ticket 21's decline was
  about a *row-count* gauge needing a query; a *file* indicator needs none, which may be the gap in its
  reasoning.
- **The arithmetic, in ticket 26 §7's shape.** A published formula over quantities this map owns plus a named
  deployer input, so the precedent transfers rather than being re-argued. Terms to include: the `/api/csrf`
  contributor, the four credential-flow routes ticket 10 added (`08:515`), and ticket 23's `NullRequestCache`
  contributor (`08:566-570`) — already fixed, but still part of the sum and worth carrying so the sum is
  auditable.
- **Whether `450` survives.** It is `budget × idle_window`. If either changes, so does it — and ticket 26 made
  the keying window a property, so a reader will reasonably ask whether this window should be one too.
- **Whether the H2 file and the audit mount are one resource or two.** On a single-mount deployment they share
  free space, so ticket 26's `90 × daily` sizing and this ticket's session-row growth compete for the same
  bytes and neither arithmetic knows about the other.

## Done when

The aggregate bound on live anonymous session rows is stated as a property with published arithmetic; the H2
file has an instrument or a recorded reason it has none; the interaction between this arithmetic and ticket 26's
disk sizing is resolved either way; and any reopening of `08:118-121` is recorded as a deviation with the
original reasoning answered.

---

## Answer

**Exactly one route creates an anonymous session. Every anonymous session expires at a fixed 15 minutes after it
was created, whatever traffic it sees. New anonymous sessions are refused (shed) on two lines: a count line, and a
free-space line whose reserve scales with the live row count, not with file size. A dedicated health indicator and
two gauges watch the H2 file.** The aggregate bound is a property: **live `SPRING_SESSION` rows never exceed
`N_max` (100,000) plus the rows in flight, whatever the number of sources.** Per source, the ceiling is
**480 unexpired and 510 physically present**.

The verified facts behind this are in the
[verification asset](../research/anonymous-session-growth-and-h2-file-verification.md) §§A–F and are cited, not
restated. Two premises of this ticket were wrong, and both made the problem worse than the ticket said:

- **`/api/csrf` was never the only source of new sessions (§F.1).** Every unsafe method without a session writes a
  row before its 403, on any path, because `CsrfFilter` runs before authorization. Ticket 26 saw the `get()` call
  (`26:142`) and priced it as a lookup cost only.
- **450 per source was never a ceiling (§F.3).** Any request with a live cookie extends that row by a full idle
  window. So a source can keep old rows alive with pings on unlisted routes while it creates new ones, and its row
  count grows without limit.

### 1. Only one route creates anonymous sessions

- **Property:** *only `GET /api/csrf` may create an anonymous session.*
- **Mechanism:** a delegating wrapper around `HttpSessionCsrfTokenRepository`.
  - **A non-null `saveToken` is skipped when `request.getSession(false) == null`.** A null `saveToken` always passes
    through, because logout and login rotation use it and it never creates a session (§F.1).
  - The request that was skipped compares against a token that was never stored, so it gets 403 `CSRF_MISSING`, as
    before. `isGenerated()` is still true.
- **The wrapper cannot tell the filter's call from the controller's (§F.2).** It works only because the bootstrap
  controller calls `request.getSession(true)` *before* it resolves the token. That is ticket 08 §7's "create it
  deliberately" (`08:284`), and it is now **load-bearing**. Without it, `/api/csrf` stops creating sessions and
  nothing fails. The controller comment has to say so.
- **The wrapper must not forward `loadDeferredToken`.** It must inherit the interface default. The delegate is
  `final` and doesn't override it. Forwarding it binds the deferred token to the inner repository and bypasses the
  wrapper entirely (§F.2).
- **The terms that are now zero, each pinned by the test in §9:**
  - the four credential-flow routes (`08:515`);
  - ticket 23's `NullRequestCache` term (`08:566-570`);
  - unsafe requests without a session on any mapped or unmapped path.

### 2. The pin: anonymous expiry fixed at creation + W (a recorded deviation from `08:118-121`)

**Mechanism.** On every access to a session with **no `AUTH_INSTANT` attribute**, `AbsoluteSessionLifetimeFilter`'s
second branch computes `remaining = CREATION_TIME + W − now`:

- If `remaining > 0`, it calls `setMaxInactiveInterval(remaining)`, so `EXPIRY_TIME` never moves past
  `CREATION_TIME + W` and the existing cleanup reclaims the row on time.
- If `remaining ≤ 0`, it calls **`invalidate()` and never the setter.** A negative interval that is ever saved makes
  the row immortal: cleanup never matches it, and `deleteById`, which invalidation uses, excludes it
  (`MAX_INACTIVE_INTERVAL >= 0`, §F.4). One bad write would leave a row that nothing in the application can remove.

**Why this placement.** It reuses the anonymous test ticket 08 §12 already uses to skip anonymous sessions
(`08:420`). So there is one filter, one ordering invariant (after `SecurityContextHolderFilter`, before `CsrfFilter`),
and one test that tells the two branches apart. `CREATION_TIME` is a safe anchor here, because no
`changeSessionId` has happened yet. The gauge's `PRINCIPAL_NAME IS NULL` agrees with "no `AUTH_INSTANT`" **only
because both are set at password login**. If either ever moves, the two definitions of anonymous come apart. *Consolidated into the register (ticket 33): R-SES-010. Amend the table by ID, not this list.*

**`W` is the repository's resolved interval, not a new key.** The test reads it from a freshly created session's
`getMaxInactiveInterval()`, which comes from `defaultMaxInactiveInterval` (`SS-JDBC:470`). It does not read the raw
`spring.session.timeout` key, so the mismatch ticket 08 recorded with `server.servlet.session.timeout` can't come
back here.

**Login resets the interval.** Inside the login composite, login sets `maxInactiveInterval` back to `W`. Without
that, the authenticated session inherits the shortened interval and the user is logged out early. That failure is
visible, unlike the one the `AUTH_INSTANT` invariant guards against. The reset is idempotent, so running it more
than once is harmless. Keeping it separate means keeping it out of `AuthInstantStampingStrategy`, not confining it to
one call site.

**Recorded as a deviation.** Ticket 08 rejected "a second clock to reason about". This adds a second **anchor**: it
reuses the absolute-lifetime model (anchor plus duration) with the idle value. Ticket 08's recovery argument still
holds unchanged: an expired pre-login token gets 403, and ticket 06's single retry recovers it. What ticket 08 did
not consider was pings keeping the row alive, and that is what the pin closes. This is an **amendment** under ticket
13's rule: it strengthens a control and weakens none. *Consolidated into the register (ticket 33): R-SES-009. Amend the table by ID, not this list.*

### 3. Shedding: two lines, a dwell, and nothing on the audit axis

| Line | Trips when | Clears when |
| --- | --- | --- |
| Count | `N ≥ N_max` | `N ≤ 0.9 × N_max` |
| Free space | `free < k × N × b + floor` | `free ≥ 1.1 × (k × N × b + floor)` |
| Either | the check can't be evaluated (a failed or timed-out COUNT) | — |

- **Minimum dwell `D = 60 s`.** Once shedding starts it holds for at least `D`, whatever the two lines say.
- **`N`** is `SELECT COUNT(*) FROM SPRING_SESSION`, unfiltered, which takes H2's quick-aggregate path (§F.6).
  - It counts authenticated rows too. That is conservative, because cleanup deletes them and inflates the file the
    same way.
  - It is cached for 1 s, **single-flight**: one refresher runs and the others wait up to the query timeout.
  - Its cost under concurrent uncommitted writes is not verified (§F.6). It is bounded anyway, at `N_max` plus the
    rows in flight, once per second. *Consolidated into the register (ticket 33): R-RL-013. Amend the table by ID, not this list.*
- **Why the reserve scales with `N` and not with the file.** A reserve of `k × F` latches: the file keeps its
  high-water mark while the database is open (§C.1). After cleanup inflates the file, that reserve never falls back,
  so any burst above about a third of the trip point turned shedding on a minute *after* the attack and kept it on
  until a restart. With a reserve of `k × N × b`, `N` falls after cleanup, and so does the reserve. Internal space
  reuse means new rows fill freed pages before the file grows, so a free-space reading still errs on the safe side.
- **Why an unevaluable check sheds.** After an H2 panic the store is closed (§C.4) and every login fails anyway.
- **Constants and properties.**
  - The count ratios **0.8 (alert), 0.9 (clear) and 1.0 (trip), and the 1.5 alert multiplier, are code constants**,
    not properties. So they need no ordering test: an ordering assertion on constants can't fail.
  - `floor` and `lead_days` are validated non-negative by the startup validator.
- **Why the dwell carries the bound, not the hysteresis bands.** Cleanup is not the only thing that deletes rows in
  bulk. `findById` deletes an expired row whenever it is looked up (`SS-JDBC` 493–496), and the pin puts every row
  created in the same second into the same expiry second. So an attacker can delete a block of rows on demand by
  touching them.
  - Those touches **are metered**. They return null, so ticket 26's miss budget counts them (`26:199-202`), at 300
    per source per 15 minutes. Sources are free under IPv6, though.
  - The bands reduce flapping; the dwell bounds it.
- **Where the refusal happens.** In the `/api/csrf` controller, before `getSession(true)`, and **only for requests
  with no session**. A stale cookie counts as no session. A caller re-fetching a token on a live anonymous session
  creates no row and is never refused.
  - The response is 429 `TOO_MANY_REQUESTS` with `Retry-After: 60` (`06:265`). No new enum member is added.
  - **What `Retry-After: 60` means.** At the start of an episode it is the earliest possible clear. After that it is
    an overstatement, not a promise. It is a constant because a computed value would reveal disk state.
  - The frontend change is nil: ticket 26 already made ticket 14's interceptor handle 429.
- **Why A is not on the shed line (this is a decision, recorded).** On an undersized audit disk with no attack
  running, sessions are not what is filling the disk. Refusing them hardly slows the fill, and would deny logins two
  days early. So shedding acts only on what it controls. Audit fill still gets its lead time through the alert line
  in §4.

### 4. The instrument

- **A custom `h2Data` health indicator.** It is not a second `DiskSpaceHealthIndicator`, which takes a fixed
  `DataSize` and can't follow a reserve that moves.
  - **DOWN when `free < 1.5 × (k × N × b + floor) + A`, or when `N ≥ 0.8 × N_max`.**
  - The free-space alert line is above the shed line for every `N ≥ 0`: the difference is
    `0.5 × (k × N × b + floor) + A`. The count branch exists because otherwise a deployer who sizes generously
    reaches `N_max` with health UP and shedding on, which breaks Q9's rule that health goes DOWN before shedding.
  - **The indicator and the shed check share one component**, which computes the reserve and holds the
    single-flight cached COUNT. **The indicator must never run its own COUNT.** That is the obvious shortcut when
    writing it, and it would let anyone who can hit `/actuator/health` (which is `permitAll`) set the database's
    COUNT rate.
  - `show-details: never` still applies, because the details would include an absolute path.
  - **The 1.5× line gives lead time only for slow growth, meaning audit fill.** An attack ramp crosses both lines
    within minutes. Nobody should read it as warning of an attack.
- **Gauges, evaluated at publish on the OTLP registry's own thread (§F.5):**
  - **anonymous rows**: `COUNT(*) … WHERE PRINCIPAL_NAME IS NULL`, a range scan over the `PRINCIPAL_NAME` index
    (IX3). Its state object is a strongly held bean, or the gauge reads `NaN`. It is the only signal that separates
    session growth from audit growth on one mount;
  - **H2 file bytes**: `Files.size` of `<data-dir>/<name>.mv.db`. It is documented JDK API, and unlike the COUNT it
    keeps working after a panic. H2's own `info.FILE_SIZE` is declined because it is undocumented (§C.2).
- **Ticket 21's decline at `21:401-402` is reversed on verified facts.** Both of its reasons were false: Spring
  Session already runs its own scheduler (§A.3), and a gauge needs none (§F.5).
- **Both disk indicators stay, always.** Ticket 21's `diskSpace` indicator on the audit directory keeps its
  binding test and covers the separate-mount case. On a shared mount `h2Data` goes DOWN first, and that is expected.
- **The data directory is a property.** `app.db.data-dir`; a test asserts that the resolved
  `spring.datasource.url` file path lies under it. This is ticket 21's one-property, asserted-equal rule.
- **Shared mount or not: always assume shared.** `A` is always included. The result of *Consolidated into the register (ticket 33): R-OBS-019. Amend the table by ID, not this list.*
  `Files.getFileStore` equality is written to the startup fingerprint for information only, because it fails in the
  unsafe direction: two bind mounts of one volume compare not equal. If the mounts really are separate, the cost is
  `A` bytes of extra headroom. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-016. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-OBS-020. Amend the table by ID, not this list.*

### 5. Audit

- **Row 5 gains reason `DISK_RESERVE_SHED`**, emitted once when an episode starts. Identity is empty; `url.path` is
  `/api/csrf`, per ticket 13's rule. It is **per-event** in ticket 21's class model, split by (row, reason) on row
  34's precedent. The end is not a denial, so it does not go on a `["denied"]` row.
- **New row 47, "Anonymous session shedding cleared".** `access-control` / `["change"]`, INFO / low, no identity,
  carrying the episode's duration. Class rate-above.
- **Bound, derived from the dwell:** at most one episode per 60 s, so at most 2 rows per minute, about 2,880 per day.
  That is negligible against ticket 26's roughly 27 MB/day.
- **A restart ends an episode without a row 47.** So a trail showing start, start (a re-trip after reboot) is
  expected, not a defect.

### 6. The cap is a deviation from the reasoning that chose shedding

Shedding was chosen because it "never trips under normal load and is costly to trip". The count line brings back
Q3(a)'s outcome: a count limit that denies every new login, at a known price of **about 196 sources** (100,000 ÷ 510),
**regardless of disk size**. So a deployer with a large volume cannot buy more headroom against it.

It is taken anyway on an epistemic argument. The per-row cost `b` grows faster than the row count: inserts cost 1.7
KB per row at 20k rows and 6.9 KB at 100k; the file after the delete costs 5.5 KB and 17 KB per row at the same
sizes. Nothing above 100k was measured. **The model is trusted only inside the range it was measured over, so the
range is the cap.** Details:

- **`b` = 17 KB was read after `CHECKPOINT`.** The brief peak *during* the DELETE was never sampled. `k = 1.5` is a
  judgement meant to cover that transient, not a measurement of it. *Consolidated into the register (ticket 33): R-RL-012. Amend the table by ID, not this list.*
- **The lever that raises `N_max` is extending the measurement to 1M rows.** Raising it without new measurements
  leaves the measured range. *Consolidated into the register (ticket 33): R-RL-011, R-RL-014. Amend the table by ID, not this list.*

### 7. The arithmetic (ticket 26 §7's shape)

```
per_source_unexpired = burst + rate × W                     = 30 + 30 × 15   = 480
per_source_present   = burst + rate × (W + cron_period)     = 30 + 30 × 16   = 510   ← disk terms use this
shed    when  N ≥ N_max  or  free < k·N·b + floor  (N cached 1 s, single-flight; unevaluable ⇒ shed; dwell D)
clear   when  N ≤ 0.9·N_max  and  free ≥ 1.1·(k·N·b + floor)  and  dwell elapsed
alert   when  N ≥ 0.8·N_max  or  free < 1.5·(k·N·b + floor) + A
N_trip ≥ min( N_max , (V − L − F_base − floor) / ((k + 1)·b) )           (conservative: F(N) ≤ N·b)
sizing:  V ≥ 90 × daily + F_base + (k + 1) × N_max × b + floor + A        (V is the one deployer input)
```

**Planning values:** `k = 1.5`, `b = 17 KB`, `N_max = 100,000`, `floor = 256 MB`, `D = 60 s`. *Consolidated into the register (ticket 33): R-RL-012. Amend the table by ID, not this list.*
`A = daily × lead_days ≈ 54 MB`, and `L ≤ 90 × daily ≈ 2.4 GB`, both from ticket 26.
`F_base` is the H2 file's normal size at `P = 100`, bound by a test that pins an upper bound, following ticket 26's
`bytes_per_row` precedent. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-017. Amend the table by ID, not this list.*

**What the numbers give:**

- **The H2 term** is `(k + 1) × N_max × b` ≈ **4.25 GB**, 1.8× ticket 26's audit figure. The **total volume nearly
  triples**: from 2.4 GB to about 6.96 GB with `floor` and `A`, a factor of about 2.9.
- A volume sized to the sizing line trips the free-space line only at `N_max`, so on a correctly sized disk the
  published trip point is the count cap.
- If the `(k + 1)` factor is dropped, the trip point falls to about 0.6 × `N_max`.
- **Does 450 survive?** No. It is corrected to **480 unexpired / 510 present**. It is a correction, not a reopening,
  of the figure tickets 08, 09 and 12 carried: the ceiling is reached the same way, and only the burst and the cron
  lag were missing. The per-source figure matters less now, because the aggregate is bounded by `N_max`. `S` is
  attacker-chosen and free under IPv6 (§10).

### 8. What an admin can do while shedding is on

- **An admin with a live session is unaffected.** Shedding refuses only session-less `/api/csrf`.
- **An admin without a live session cannot log in** except in a gap after the dwell and before the next trip. Their
  recourse:
  1. **Wait.** With the pin and the dwell, an episode clears within about `W + 60 s + D` of the attacker stopping.
  2. **The deployer's infrastructure-layer per-source limit** (ticket 26's handover item). This is the only lever
     that works while the attack continues.
- **Rejected on the record:**
  - **Ticket 28's runner is not a route back in.** It runs offline and mints no session (`28:112`, `28:161`,
    `28:212`), and the shedding state survives the restart, because the rows live in JDBC.
  - **An offline purge verb on that runner.** It would clear shedding only for seconds against an attacker who is
    still running, and it would reopen ticket 28's accepted scope and its plan-then-apply process.
  - **Exempting trusted source ranges from shedding.** A configured admin network range that bypasses the shed check
    is the only in-app way for an admin to log in during a sustained attack. It is rejected because the source IP is
    only as trustworthy as ticket 20's and ticket 09's proxy configuration (`framework` forwarding is prohibited;
    `internal-proxies` is derived from our own property). An exempt range would also create rows with no limit,
    which is the resource this ticket exists to protect. Recorded so that "why not allowlist the admins?" does not
    get re-litigated.

### 9. Tests owed to ticket 16

1. **Exactly one site creates anonymous sessions.** With no cookie, a fabricated cookie and an expired cookie, send
   every safe and unsafe method to every mapped route (enumerated via `getHandlerMethods()`, following ticket 26),
   one unmapped path, `/api/admin/**` and actuator. Assert **zero** `SPRING_SESSION` rows, **and** that
   `GET /api/csrf` with no session creates **exactly one** row. Without the second assertion the test passes on a
   system that creates no sessions at all.
2. Removing `getSession(true)` from the controller makes (1)'s positive case fail. This pins §1's load-bearing
   sentence.
3. The wrapper inherits `loadDeferredToken`: a CSRF-less unsafe request with no session still yields
   `CSRF_MISSING`, and still no row.
4. The pin keeps `EXPIRY_TIME ≤ CREATION_TIME + W` across pings. A ping after `W` invalidates the session and
   deletes its row.
5. **Negative-interval guard.** A clock stepped past `W` produces `invalidate()`, never
   `setMaxInactiveInterval(≤ 0)`. Assert that no row with `MAX_INACTIVE_INTERVAL < 0` ever exists (§F.4).
6. The pin's `W` equals a fresh session's `getMaxInactiveInterval()`.
7. Login resets `maxInactiveInterval` to `W` and does not re-stamp `AUTH_INSTANT`. Running the reset twice leaves
   the state unchanged.
8. The two branches of the absolute filter are distinguished: anonymous sessions are pinned, and authenticated ones
   keep ticket 08's `AUTH_INSTANT` behaviour.
9. Shedding, with a stubbed `FileStore`, file size and count: it trips and clears at the computed lines, with both
   hysteresis bands. A count failure sheds. The dwell holds a clear for `D`. A request that already has a session
   is never refused. A refused request creates no row. The response is 429 with `Retry-After: 60`.
10. The single-flight cache: N parallel expiries cause one COUNT. `/actuator/health` never causes a COUNT of its own.
11. The shed episode yields exactly one row 5 `DISK_RESERVE_SHED` and one row 47, however many requests are refused.
12. `app.db.data-dir` contains the resolved datasource file path. The `Files.size` gauge equals the file length. The
    anonymous-row gauge is non-`NaN` after a GC, which proves its state object is strongly held.
13. `F_base` has an upper bound pinned at `P = 100`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-026, T-SES-027, T-CSRF-008, T-SES-032, T-SES-024, T-SES-033, T-SES-034, T-SES-035, T-RL-024, T-OBS-006, T-AUD-039, T-OBS-016, T-OBS-017. Amend the table by ID, not this list.*

### Handover items (ticket 25)

- **Size the database volume to §7's sizing line, not to ticket 26's audit figure alone.**
  - **Obligation:** `V ≥ 90 × daily + F_base + (k + 1) × N_max × b + floor + A`, about **6.96 GB** at the planning
    values. The H2 term alone is about 4.25 GB. Recompute when `N_max`, `k`, `b` or the mount changes.
  - **Discharges:** ASVS **2.1.3 (L2)** and **15.1.3 (L2)** (documented limits and resource strategy), and **2.4.1
    (L2)** (anti-automation against resource exhaustion). This is adopted because it is cheap, against the L1
    target.
  - **Application enforcement:** partial. Shedding prevents an attack from filling the disk; it cannot make the
    volume big enough.
  - **Fields:** `responsibility: shared` · `status: asserted-by-test` (the application's half, which is shedding,
    §9 test 9) · `priority: required`.
  - **Acceptance check:** the volume's total size, read from `h2Data`'s startup fingerprint entry, meets the line. *Consolidated into the register (ticket 33): R-RL-009. Amend the table by ID, not this list.*
- **Know that anonymous-session shedding denies new logins, and what clears it.**
  - **Obligation:** row 5 `DISK_RESERVE_SHED` is a per-event alert. While it is active, admins without a live
    session cannot log in. It clears on its own within about `W + 60 s + D` of the attack stopping. While the attack
    continues, the only remedy is the infrastructure-layer per-source limit. **A restart does not clear it**, because
    the rows live in JDBC, and a restart leaves the episode without a row 47. `Retry-After: 60` is the earliest
    possible clear, not a promise.
  - **Discharges:** IM8 **lm-16** Key Signals Monitoring (MUST, LR 2 | MR 2; `01:193`), specifically its alerting
    half, which ticket 21 left as a High. Also ASVS **2.4.1 (L2)**.
  - **Application enforcement:** detection and the audit row, yes. The remedy while an attack continues, no.
  - **Fields:** `responsibility: deployer` · `status: asserted-by-test` (detection and the audit row, §9 tests 9
    and 11) · `priority: required`.
  - **Acceptance check:** the runbook names row 5 `DISK_RESERVE_SHED`, the self-clearing bound, and the edge
    per-source limit as the remedy for an attack that is still running. *Consolidated into the register (ticket 33): R-RL-010. Amend the table by ID, not this list.*
- **Keep the H2 data directory as `app.db.data-dir`, and don't split it from the datasource URL.**
  - **Obligation:** the datasource URL's file path must lie under `app.db.data-dir`. Otherwise `h2Data` and the file
    gauge watch the wrong mount.
  - **Discharges:** IM8 **lm-16** Key Signals Monitoring (MUST, LR 2 | MR 2; `01:193`), specifically its
    saturation coverage.
  - **Application enforcement:** yes; the startup validator refuses a mismatch.
  - **Fields:** `responsibility: deployer` · `status: enforced` · `priority: required`.
  - **Acceptance check:** the application starts, and the fingerprint shows both paths. *Consolidated into the register (ticket 33): R-OBS-019. Amend the table by ID, not this list.*

### 10. Graduated: one new ticket

**[Decide IPv6 source keying for every per-source limiter](31-ipv6-source-keying.md).** Nothing on the map mentions
IPv6. Every per-IP key on ticket 09's table, the third axis at §R.6, and ticket 26's miss budget key on the full
address, so one host with a /64 controls about 2⁶⁴ sources. That reopens ticket 09's keys for more than this
ticket's resource, so it is its own ticket. Here it is recorded only as "`S` is free", which is why the aggregate is
bounded by `N_max` and not by sources.

### 11. ADRs, glossary, register

**Five new ADRs:**
1. Only one route creates anonymous sessions: the CSRF repository wrapper, its `loadDeferredToken` trap, and the
   load-bearing `getSession(true)`. *Consolidated into the ADR routing (ticket 34): ADR-040. Amend by ID, not this list.*
2. Anonymous expiry pinned at creation + W: a deviation from `08:118-121`, with the original reasoning answered, the
   invalidate-don't-set rule, and the login reset. *Consolidated into the ADR routing (ticket 34): REJ-091. Amend by ID, not this list.*
3. Shedding on a count line and on a free-space reserve that scales with `N`: the rejected `k × F` latch, the
   dwell, and the unevaluable-means-shed rule. *Consolidated into the ADR routing (ticket 34): ADR-041. Amend by ID, not this list.*
4. The `N_max` count cap, as a deviation from ADR 3's own rationale, with the measured-range argument and the lever. *Consolidated into the ADR routing (ticket 34): ADR-041. Amend by ID, not this list.*
5. Rejected recourse: the trusted-range exemption, the offline purge verb, and 503. *Consolidated into the ADR routing (ticket 34): ADR-041. Amend by ID, not this list.*

**One amended ADR:** ticket 21's "declined session-row gauge", reversed on §A.3 and §F.5. *Consolidated into the ADR routing (ticket 34): REJ-063 (attached amendment). Amend by ID, not this list.*

**Glossary terms:**
- **Anonymous session:** a session with no `AUTH_INSTANT`.
- **Pin / pinned expiry:** an anonymous session's expiry fixed at creation + W.
- **Shedding / shed episode:** a period during which new anonymous sessions are refused.
- **Reserve:** the free space the shed line demands.

**Register entries:**
1. `08:118-121` deviated (second anchor). *Consolidated into the register (ticket 33): R-SES-009. Amend the table by ID, not this list.*
2. `N_max` as a login-denial primitive at about 196 sources, independent of disk size. *Consolidated into the register (ticket 33): R-RL-011. Amend the table by ID, not this list.*
3. `k = 1.5` and `b = 17 KB` as planning values, with the DELETE peak unsampled. *Consolidated into the register (ticket 33): R-RL-012. Amend the table by ID, not this list.*
4. §F.6's COUNT cost under concurrent writes, unverified and bounded by `N_max`. *Consolidated into the register (ticket 33): R-RL-013. Amend the table by ID, not this list.*
5. Mount-sharing detection, informational only. *Consolidated into the register (ticket 33): R-OBS-020. Amend the table by ID, not this list.*

**Reopening triggers:**
- A measurement that extends `b` past 100k rows (raises `N_max`). *Consolidated into the register (ticket 33): R-RL-014. Amend the table by ID, not this list.*
- Any change that sets `AUTH_INSTANT` or `PRINCIPAL_NAME` anywhere other than password login. *Consolidated into the register (ticket 33): R-SES-010. Amend the table by ID, not this list.*
- An H2 upgrade that changes MVStore retention or compaction. *Consolidated into the register (ticket 33): R-RL-015. Amend the table by ID, not this list.*

### 12. Amendments made

Written into tickets 08, 12, 13, 21, 26, 16 and 17 below each ticket's existing content. Ticket 09 receives only a
pointer to the new ticket 31.

Status: resolved.

## Amendment from ticket 31 (IPv6 source keying)

- **§10's "`S` is free" is now priced.** Sources are source keys (IPv4 /32, IPv6 /64). The 480 / 510 per-source
  figures hold **per source key**, unchanged.
- **Register entry 2 (login denial at about 196 sources) is an improvement, not a new cost.** Under the old /128 key
  one host supplied 196 keys for free; at /64 it takes 196 LANs. The cheapest supply is **one AWS VPC**, which holds
  256 /64s for the cost of creating subnets, in any region including `ap-southeast-1`. A RIPE-conformant home /56 is
  the other ([ticket 31](31-ipv6-source-keying.md) §8, research §6.1 addendum). `N_max` is not a compensating control
  for a declined `SHALL`, so this is an amendment, not a reopening.
- **Your edge per-source limit handover item (`29:372-381`) is tightened**: the edge must aggregate IPv6 at a *Consolidated into the register (ticket 33): R-RL-011. Amend the table by ID, not this list.*
  deployer-chosen prefix, because that is the only lever that works while shedding persists. *Consolidated into the register (ticket 33): R-OPS-006, R-RL-010. Amend the table by ID, not this list.*

---

## Amendment from ticket 16 (test plan)

- **This ticket now owns `spring.session.jdbc.cleanup-cron`, pinned at `0 * * * * *`.** That is Spring Session's `DEFAULT_CLEANUP_CRON`, and the 1-minute `cron_period` 29:282's `510 = 30 + 30 × 16` already assumes.
  - Ticket 05's `0 */5 * * * *` snippet is superseded. At five minutes the figure would be 630. *Consolidated into the register (ticket 33): R-SES-011. Amend the table by ID, not this list.*
  - Any change to the cron is a reopening trigger for this ticket's per-source figure and disk sizing. *Consolidated into the register (ticket 33): R-SES-011. Amend the table by ID, not this list.*
- **Binding test** (ticket 16): it reads the production configuration (`ctx-nondev` or the property file). It never reads a shared test context, because those set the cron to `-` (`Scheduled.CRON_DISABLED`), so idle-expiry tests are not satisfied by the reaper instead.
- **Test isolation.** Test 1 ("exactly one row") and the anonymous-row gauge count across the whole database, so they assert **before/after deltas**. One dedicated test runs with the cron enabled. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-028, T-SES-023. Amend the table by ID, not this list.*
