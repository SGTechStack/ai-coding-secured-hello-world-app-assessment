# 07 — Account lockout and IP throttling

Type: grilling
Status: open
Blocked by: 02
Map: [Secured Login App](../map.md)

## Question

What is the account lockout policy, and by what mechanism is IP-based rate limiting enforced?

Answer `Q15` (account lockout policy and admin unlock capability) and `Q16` (IP-based rate limiting strategy) of the standard's question set.

The PRD's Story 3 sets the shape and names the reason it matters: **account lockout and IP throttling must be independent**, "so an attacker cannot lock out a legitimate user merely by failing that user's password from one source" (`prd/assessment-prd.md:56`). Two counters, two policies.

- **Account lockout** — the PRD suggests 5 attempts / 15 minutes and gives `failed_login_attempts` + `locked_until` columns. Confirm or override the thresholds, decide whether the attempt window is sliding or absolute, and settle `Q15`'s **admin unlock** capability, which the PRD never mentions (no unlock endpoint exists in Stories 8–11 — if unlock is required, that is a new endpoint and a new authorization-matrix row for 08).
- **IP throttling** — the PRD specifies behaviour but no mechanism. Decide the store (in-memory versus shared, which matters only if more than one instance ever runs), the library or hand-rolled filter, the threshold and window, the response (429 versus the generic login error), and where it sits in the Spring Security filter chain. Note that the enumeration-resistance requirement constrains what the throttled response may reveal.

Blocked on 02 because the counter store and the session/datasource decision are the same decision for the IP limiter.

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md); this ticket is now unblocked.** 02 settled the half of this question it was gating — the counter store — leaving `Q15`'s and `Q16`'s policy questions untouched.

- **Account-lockout state is durable and transactional**, held in the `users` columns the PRD's data model already names (`failed_login_attempts`, `locked_until`). 02's column rulings apply: `locked_until` is `TIMESTAMP WITH TIME ZONE` stored UTC, compared against an injectable `Clock`.
- **IP-throttle state is in-memory** — Caffeine or Bucket4j; **the library choice is this ticket's**. 02's reasoning: a JDBC write per login *attempt* is a self-inflicted write amplification on precisely the endpoint under attack.
- **Two consequences to carry into the answer rather than discover later:** throttle state does **not** survive a restart, and it is **not** multi-instance-safe. Both are honest under the single-instance topology 01 fixed, and both are among the first things that break if topology is ever revisited.
- Still wholly open here: thresholds, window shape (sliding versus absolute), the `429`-versus-generic-error response under the enumeration-resistance constraint, filter-chain position, and `Q15`'s admin-unlock capability — which, if required, adds an endpoint to 01's inventory and a row to 08's matrix.

**Amended by [18 — Client IP in logs](18-client-ip-in-logs.md).** No change to the counter store; two constraints on its key and one new obligation.

- **The counter keys on the verbatim `ServletRequest.getRemoteAddr()` string** — no IPv6 normalization, no `X-Forwarded-For`. 18 rejected `X-Forwarded-For` outright: single-instance with no gateway (01) means a client-supplied header is attacker-controlled, and honouring it would let an attacker rotate it to evade this throttle entirely. The key and the audit log's `source.ip` must be the *same* string or the two cannot be correlated, which is why "verbatim" is a constraint here rather than a detail.
- **Still in-memory, and 18 imposes no persistence obligation.** The counter's lifetime is the throttle window — shorter retention than the audit log's — so it is not the sensitive-data long pole; the cleartext IP already exists in the audit appender.
- **New obligation: the throttle-rejection event is audited and carries `source.ip`.** It is one of 18's six `source.ip` classes. **No recipe templates this event** — its field set is [12 — Audit and logging contract](12-audit-and-logging-contract.md)'s to define — but the decision it serves is this ticket's: whether rejection returns `429` or a generic error determines what `event.outcome` and error fields the audit line can carry without leaking the throttle's existence to an attacker probing it. Settle the response shape with that coupling in view.
- **A restart consequence now has an audit-trail edge, not just a throttle edge:** because the counter does not survive a restart, the audit log will show throttle-rejection events for an IP with no preceding accumulation after a bounce. Note it so it is not read as a bug during 14's testing.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** 15 closed the dependency set but left **one slot deliberately empty: the in-memory rate-limit cache**, which is this ticket's pick. The two candidates are not the same kind of thing — **Caffeine** is a cache with TTL eviction (you build the counter yourself), **Bucket4j** is a purpose-built token-bucket rate limiter. If this ticket forms no preference, 15 records Caffeine as the default. Whatever is chosen becomes a POM entry; nothing else in 15 constrains the algorithm. Note also 15's ArchUnit row banning `@Scheduled` anywhere under `com.assessment.auth` — an IP-throttle counter that wants periodic sweeping must evict lazily or via cache TTL, not via a scheduled task, or it is a scope change to raise rather than an implementation detail.
