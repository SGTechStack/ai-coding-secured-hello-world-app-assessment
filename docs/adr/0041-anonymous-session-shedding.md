---
status: accepted
---

# ADR-041: Anonymous-session shedding: a count line and a free-space reserve

When the session table or the disk it lives on gets too full, the application refuses to create new anonymous
sessions. This is called a shed episode. It trips on either of two lines: a **count line** on live session rows, and
a **free-space line** whose reserve grows with the row count. Shedding denies new logins while it lasts, and the count
line looks redundant beside the disk reserve. A maintainer could remove either line, or add an allowlist for admins.
Each of those undoes something deliberate.

## Context

- Per-source budgets bound how fast one source creates anonymous sessions (ADR-040), but sources are cheap. Under
  IPv6 one host holds many /64s (ADR-020). The total number of live anonymous rows is therefore unbounded unless
  something caps the aggregate.
- Sessions, accounts and audit data share one H2 file. Disk-full on that volume is an availability failure of the
  whole application. H2 2.4's MVStore fails stop, and un-flushed commits are at risk.
- The H2 file keeps its high-water mark while the database is open. It reuses freed space internally, but it only
  shrinks when the tail of the file is free.
- The per-row cost `b` was measured only up to 100,000 rows. It grows faster than the row count (about 1.7 KB per
  row for inserts at 20,000 rows, 6.9 KB at 100,000), and nothing above 100,000 was measured.

## Decision

| Line | Trips when | Clears when |
| --- | --- | --- |
| Count | `N ≥ N_max` | `N ≤ 0.9 × N_max` |
| Free space | `free < k × N × b + floor` | `free ≥ 1.1 × (k × N × b + floor)` |
| Either | the check cannot be evaluated | — |

- **Planning values:** `N_max = 100,000`, `k = 1.5`, `b = 17 KB`, `floor = 256 MB`. `N` is an unfiltered
  `COUNT(*)` of `SPRING_SESSION`, cached for one second and single-flight.
- **Minimum dwell of 60 seconds.** An episode holds for at least that long whatever the lines say. The hysteresis
  bands reduce flapping, and the dwell bounds it. Rows can be deleted in bulk on demand, because every row created in
  one second expires in the same second.
- **Where and what.** Only a session-less `GET /api/csrf` is refused, before `getSession(true)`, with
  `429 TOO_MANY_REQUESTS` and a constant `Retry-After: 60`. A caller that already has a session is never refused. A
  constant is used because a computed value would reveal disk state.
- **A check that cannot be evaluated sheds.** After an H2 panic the store is closed and every login fails anyway.
- One health indicator and the shed check share one component and one cached count. The indicator never runs its
  own count, because anyone can reach `/actuator/health`.

## Considered options

- **A reserve proportional to the file size (`k × F`). Rejected: it latches.** The file keeps its high-water mark, so
  after one burst the reserve never falls back. Shedding would start a minute after an attack and stay on until a
  restart. A reserve of `k × N × b` falls as cleanup removes rows.
- **The free-space line alone, with no count cap.** Shedding was chosen because it should never trip under normal
  load. A count cap brings back a fixed login-denial threshold: about 196 sources at 510 rows each, whatever the disk
  size. **It is taken anyway, on an epistemic argument:** the row-cost model is trusted only inside the range it was
  measured over, so that range is the cap. The lever that raises `N_max` is measuring further, not configuring
  it higher.
- **Exempting trusted admin source ranges from shedding. Rejected.** A source address is only as trustworthy as the
  proxy configuration behind it, and an exempt range creates rows with no limit, which is the resource being
  protected.
- **An offline purge command on the recovery runner (ADR-072). Rejected.** It would clear shedding for seconds against
  an attacker who is still running, and the runner is an offline tool that mints no session.
- **`503 Service Unavailable`. Rejected** in favour of `429`, which adds no code to the closed enum and reuses the
  SPA's existing 429 handling.
- **Shedding on audit-disk fill as well. Rejected.** On an undersized audit disk with no attack running, sessions are
  not what fills the disk. Refusing them would barely slow the fill and would deny logins days early.

## Consequences

- **While an episode lasts, an admin with no live session cannot sign in.** An admin with a live session is
  unaffected. The episode clears within about the idle interval plus two minutes of the attack stopping. While the
  attack continues, the only remedy is a per-source limit at the infrastructure edge. A restart does not clear it,
  because the rows are in the database.
- The volume must be sized to the sizing line, about 11.5 GB with the measured audit row size and base file size (R-AUD-030; R-RL-009; about 6.96 GB at the planning values), of which the session term is
  about 4.25 GB. The handover document carries the formula, and the register carries `k`, `b` and the unmeasured range.
- An episode start is one audit row and its end another, at most one episode a minute.
- Reopening triggers: a measurement of `b` beyond 100,000 rows; an H2 upgrade that changes MVStore retention or
  compaction; any change that sets `AUTH_INSTANT` or the principal name anywhere other than password sign-in.
- Tests: T-RL-024 (trip and clear on both lines with both bands, a failed count sheds, the dwell holds, a request
  with a session is never refused, a refused request creates no row, `429` with `Retry-After: 60`).

## Sources

- H2 2.4.240 documentation: Features, Compacting a Database; MVStore, Log Structured Storage and File Format;
  Advanced, Limits and Limitations. H2 source: `mvstore/FileStore`, `command/query/Select` (quick-aggregate
  `COUNT(*)`).
- Spring Session 4.1.1 source: `JdbcIndexedSessionRepository` (expiry on lookup, cleanup predicate).
- Spring Boot 4.1 reference, Actuator health (`DiskSpaceHealthIndicator` takes a fixed threshold, one path).
- OWASP ASVS 5.0: 2.4.1 (L2) anti-automation against resource exhaustion, 15.1.3 (L2), 2.1.3 (L2) documented limits.
  Adopted because they were cheap, against the L1 target.
