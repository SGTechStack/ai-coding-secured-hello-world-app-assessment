# 07 — Account lockout and IP throttling

Type: grilling
Status: resolved
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

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** 08 closed without waiting on this ticket, by pre-specifying the unlock row conditionally rather than leaving a hole. Nothing here is decided for you: `Q15`'s admin-unlock capability is still this ticket's call.

- **If unlock is ruled in**, the row is already written: `PATCH ${api.base-path}/users/{userId}/unlock`, `hasRole:USER_MANAGER`, slotted among 08's rows 12–15. It needs no new matrix reasoning — it is covered by 03's broad self-action guard (`Standalone_User_Access_Control_Application_Standard.md:101` names unlocking your own account as a self-action rejection, so `403`), it sits inside 08's `/users/**` backstop, and it is blocked by the tier-0 forced-change filter, so a flagged manager cannot unlock anyone until they have changed their own password. **If unlock is ruled out**, this paragraph is the whole consequence and 08 needs no edit either way.
- **Two 08 rulings constrain the `429` decision this ticket still owns.** First, an undeclared method+path combination returns `403`, not `405`, because each method is independently authorized (`:427`) and `405` enumerates the surface — so a `429` on the login path is the *only* status that distinguishes throttled from unthrottled, and choosing the generic login error instead makes the throttle invisible to a prober by construction rather than by effort. Second, **no non-authentication rejection on an authenticated path may use `401` or `403`**, because `Std:437`'s global SPA interceptor treats both as a logout signal. The login path is unauthenticated so the constraint does not bind there, but it does bind on any throttle this ticket might extend to the password-reset endpoints while a session exists.
- **The IP-throttle filter sits outside the matrix entirely.** It must run before authentication to protect the login endpoint, so it is ahead of both the tier-0 forced-change filter and `AuthorizationFilter`. Its rejection is therefore never an authorization decision and never reaches 08's `accessDeniedHandler` — it writes its own response, the same way 08 requires of the forced-change filter, and for the same reason: `response.sendError` triggers an `ERROR` dispatch that is itself authorized (`filterErrorDispatch=true`, verified in `spring-security-web-7.0.6`).

## Answer

Resolved by grilling, one round, six questions. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**Three counters, not two. Account lockout at 5 consecutive / 20 minutes, DB-backed, no window. Per-account rate limit at 10/min. Per-IP throttle at 50/min. All three reject with `429` + `Retry-After`. Bucket4j over Caffeine. Admin unlock is required and ruled in.**

### The ticket counts two counters; the standard mandates three

The ticket's framing — "**account lockout and IP throttling must be independent**. Two counters, two policies" — is the PRD's framing, and it is short by one. `Standalone_User_Access_Control_Application_Standard_Questions.md:400` opens `Q16` with:

> **Already required:** Per-account rate limiting (10 attempts/account/minute)

This is a third mechanism, distinct from both. It is `Std:378` ("Rate limit on `/login`: `10` attempts per account per minute… return `429 Too Many Requests` with a `Retry-After` header"), `Std:124` ("Rate limiting on authentication endpoints is applied **per account**"), and `Std:452` makes it an acceptance test. It appears nowhere in the PRD, nowhere in this ticket, and nowhere in the map before now.

**Accepted.** It closes a real gap that lockout alone leaves open: a patient attacker who stops at 4 consecutive failures per account, resetting by succeeding elsewhere or simply waiting, never trips lockout and is never slowed. The honest cost is that one endpoint now carries three independent rejection paths, and 14's test matrix grows to match.

**A fourth application, which is 11's not ours:** `Std:379` — "Password reset endpoints (token issuance and token redemption) must also enforce rate limiting" — with `Std:113` fixing `429` + `Retry-After` there. Handed to 11 with the mechanism this ticket builds; 11 owns the keying problem, which is genuinely harder (an unknown-email request has no account to key on, so `Std:124`'s per-account rule cannot apply).

### The three counters, side by side

| | Account lockout | Per-account rate limit | Per-IP throttle |
|---|---|---|---|
| Keyed on | username | username | `getRemoteAddr()` verbatim (18) |
| Threshold | 5 consecutive failures | 10 attempts/min | 50 attempts/min |
| Window | **none** — consecutive counter | sliding, 1 min | sliding, 1 min |
| Effect | locked 20 min | `429` | `429` |
| Store | `users` columns, DB, transactional (02) | in-memory (02) | in-memory (02) |
| Source | `Std:366-367` | `Std:378` | `Qs:415` |
| Survives restart | **yes** (`Std:451`) | no | no |

### The window is neither sliding nor absolute (`Q13`)

The ticket asks the question as a binary. **The standard uses neither word.** Exhaustive search finds only "**consecutive** failed logins" — `Std:32` (glossary: "A counter tracking **consecutive** failed login attempts"), `:33`, `:366`, `:450`, `:609` — and `Qs:374` states the decay rule outright: "Failed-login counter: **Resets only on successful login**."

So: **a pure consecutive counter that never decays.** No time window at all. Reset only on successful authentication (`Std:48`, `:368`). The PRD's "within a window (e.g. 5 attempts)" (`prd:52`) has no counterpart in the standard and is dropped.

This is *stricter* than the PRD — four failures a day apart still leave the account one failure from lockout — and much simpler to implement and to test: no timestamp arithmetic on the counter, one integer column.

**Thresholds: 5 attempts, 20-minute lockout, automatic lift.** The 5 matches PRD and `Std:366`. The **20 minutes takes `Std:367` over the PRD's 15** per the map's authority order; the divergence is small and the standard is explicit ("Default lockout duration: `20` minutes with automatic lift").

### `429` is mandated, not chosen (`Q12`)

The ticket asks whether rejection returns `429` or the generic login error, and frames it as an enumeration trade-off. **There is no choice.** `Std:84`, `:113`, `:146`, `:260`, `:378` and `:452` all require `429`, and `:260` is unambiguous: "The response **must** include the `Retry-After` header." Nothing anywhere forbids it.

**The apparent conflict with `Std:247` resolves the way 04 resolved `Std:110` versus `:247`.** `:247` scopes itself precisely: "All **authentication-related failure responses** (invalid credentials, locked account, non-existent user, disabled account) must return identical HTTP status codes, response bodies, and response timing." A rate-limit rejection is none of those four — it is a **pre-authentication** rejection that never reaches credential evaluation. The document does leave the two mandates sitting side by side without reconciling them, so this reading is recorded as an interpretation, with the literal alternative (everything on `/login` returns an identical `401`) written down and rejected because it would make `Std:260`'s own `Retry-After` mandate unsatisfiable.

**The consequence to hold onto:** a locked account still returns `401` with the byte-identical generic body (`Std:258` — "Responses must be identical to invalid credential responses"). So lockout and throttling deliberately produce **different statuses**, and that is the standard's intent rather than an inconsistency for 14 to flag.

**All three rejections carry `Retry-After`** and are written by 21's `ProblemDetailWriter`, never `sendError` — 08's chain-wide finding.

### Admin unlock is required (`Q14`)

The ticket treats `Q15`'s admin-unlock capability as genuinely open. It is not:

- `Std:367` — "Administrator-initiated unlock is also allowed through **the existing admin unlock endpoint**" — presumes it exists.
- `Std:451` — an acceptance test: lockout "can be manually lifted by an administrator via **the dedicated unlock endpoint**."
- `Std:282`, `:324`, `:513`, `:542` — unlock is an audited, logged and monitored admin operation.
- `Std:100` and `:473` — locking (and by symmetry unlocking) through the generic update is explicitly rejected; a dedicated endpoint is required.

**But no standard or recipe gives it a path, method or role** — `grep -rn unlock` over the four Standalone recipes returns **zero hits**. The only concrete unlock endpoint in the whole tree is in the MFA standards, a different product.

**So: 08's pre-written row is taken as-is** — `PATCH ${api.base-path}/users/{userId}/unlock`, `hasRole:USER_MANAGER`, slotted among rows 12–15. Self-unlock is `403` via 03's self-action guard, which `Std:101` and `:266` independently require. It sits inside 08's `/users/**` backstop and behind the tier-0 forced-change filter.

**`Qs:384` ("admin manual unlock") over `Qs:386` ("with mandatory reason").** The reason variant buys accountability we already get from the audit line's actor attribution (`Std:282`), and costs a request field, a column, validation and a UI control — for an application with one administrator. `Qs:394`'s own trade-off note calls it "more friction"; that friction is not earned here.

**This adds an endpoint to 01's inventory.** It is the only endpoint any ticket has added since 08.

### Library: Bucket4j for the algorithm, Caffeine for the store (`Q15`)

**The standards name no rate-limit library** — zero hits for `bucket4j` or `resilience4j` anywhere in `Appfw-User-Standards/`. But `Standalone_Session_Login_with_CSRF_Bootstrap.md:94-105` writes:

```java
Bucket bucket = buckets.computeIfAbsent(username, k -> Bucket.builder()
    .addLimit(Limit.of(10, Refill.intervally(10, Duration.ofMinutes(1))))
    .build());
return bucket.tryConsume(1);
```

That is Bucket4j's API verbatim — `Bucket`, `Limit`, `Refill`, `tryConsume` — with **no import, no `<dependency>` block, and no eviction** on its backing `ConcurrentHashMap`. The recipe ships an unbounded map.

**The standards name the fix themselves**, in the SSO recipe that hits the identical problem: `SSO_OAuth2_OIDC_Session_Translation.md:194` — "The `ConcurrentHashMap<String, Bucket>` grows unbounded under a distributed attack with many source IPs … replace with a TTL-evicting cache (e.g. **Caffeine** `Cache.expireAfterAccess`)." That is the only appearance of Caffeine in the tree, and it is a cache recommendation, not a rate limiter.

**So both, with distinct jobs:** Bucket4j supplies the token-bucket algorithm (adopting it *is* copying the recipe, which is why it beats hand-rolling); Caffeine supplies the `expireAfterAccess` map underneath both limiters, fixing the leak the recipe ships. This **overrides 15's "Caffeine as the default if 07 forms no preference"** — it is two dependencies, not one.

`expireAfterAccess` evicts **lazily, on access**, so 15's ArchUnit row banning `@Scheduled` under `com.assessment.auth` holds with no special handling. That ban was the constraint most likely to bite here and it does not.

**The per-IP limiter follows the SSO recipe's filter shape** (`SSO…:155-186`): a `OncePerRequestFilter`, **not** `@Component`-annotated, registered explicitly via `addFilterBefore` — `SSO…:152-153` says why: an annotated filter double-executes. Position 2 in 09's chain order, before `CsrfFilter` and before authentication.

### IP throttling contradicts `Q16`'s own recommendation, and ships anyway (`Q16`)

`Qs:413` marks "**No IP-based limiting** — Per-account limiting is sufficient (**recommended for internal applications**)" and `Qs:407` is blunter: "❌ Do NOT implement if: Internal behind corporate proxy, VPN/NAT gateway, cloud shared egress IPs, small user base." 01 settled this application as **internal-enterprise**, so the standard's own guidance is against it.

**Implemented regardless, at 50 attempts/IP/minute** (the low end of `Qs:415`'s "recommend: 50-100"). Three reasons: PRD Story 3 makes it an explicit deliverable with acceptance criteria (`prd:52-54`); `Q16` is a questionnaire recommendation, not a clause or an `[Enforced Constraint]`, so this is a documented choice rather than a deviation from binding text; and the PRD's stated rationale is sound — account lockout alone lets one source lock out a legitimate user, which is a denial-of-service the standard itself worries about at `Std:83`.

**The risk `Qs:407` is warning about is real and is recorded, not waved off.** Behind a corporate NAT or shared egress, every user shares one source IP, so **50 attempts/minute is the whole office's budget** and one fumbling user can throttle everyone. 18 banned `X-Forwarded-For` outright, so there is no way to see past the NAT — the two decisions compound. This goes to **15's deployment assumptions** beside the `X-Forwarded-For` expiry and 09's same-registrable-domain constraint. If this application were ever deployed behind a shared egress, the right response is to raise the IP threshold or drop the limiter, not to start trusting a proxy header.

### Carried from 02 and 18, unchanged

- **Lockout state is DB-backed and durable** — `failed_login_attempts` and `locked_until` on `users`, `TIMESTAMP WITH TIME ZONE` UTC, compared against an injectable `Clock`. `Std:451` requires lockout to persist across restarts and `Std:574` lists "Lockout state in-memory only" as a named failure source, so 02's ruling is independently mandated.
- **Both rate-limit counters are in-memory**, not restart-durable, not multi-instance-safe. Honest under 01's single-instance topology.
- **The throttle key is the verbatim `getRemoteAddr()` string** — no IPv6 normalization, no `X-Forwarded-For` — so it is the same string the audit log's `source.ip` carries and the two correlate.
- **The throttle-rejection event is audited with `source.ip`**, one of 18's six classes. No recipe templates it; its field set is 12's.
- **After a restart the audit log shows throttle rejections with no preceding accumulation.** Noted so 14 does not read it as a bug.

### A field-naming conflict in the recipes, resolved

The two recipes disagree with each other: `Standalone_Privileged_User_Administration_and_Password_Reset.md:64`/`:75` uses `accountNonLocked` (boolean) and `failedLoginAttempts`, while `Standalone_Session_Login_with_CSRF_Bootstrap.md:116-118` uses `getFailedAttempts()` and `setLockedUntil(...)`.

**Take the PRD's column names — `failed_login_attempts`, `locked_until` — and drop `accountNonLocked` entirely.** A nullable `locked_until` timestamp is strictly more informative than a boolean and **self-expiring**: lock state is `locked_until != null && locked_until.isAfter(now)`, so automatic lift at 20 minutes needs no write at all. Carrying both invites them to disagree, and a stale `accountNonLocked=true` beside a future `locked_until` is a silent auth bypass. 02 already ruled the column types.

**Consequence for 11:** `Priv:440-442` clears lock state on admin password reset by setting `accountNonLocked(true)` and `failedLoginAttempts(0)`. With the boolean gone that becomes "null out `locked_until`, zero the counter" — and note that `Std:131` says the opposite ("the lock is **not** automatically cleared"). That contradiction is 11's to settle, not this ticket's; flagged so it is not discovered during implementation.

Neither recipe overrides `UserDetails.isAccountNonLocked()` or handles `LockedException`, and no `locked_until`-expiry check appears anywhere in the Standalone recipes — so the expiry comparison is ours to write, with no template.

### Amends

- **01** — `PATCH ${api.base-path}/users/{userId}/unlock` joins the endpoint inventory.
- **08** — the conditional unlock row is now unconditional; no other matrix change.
- **11** — inherits reset-endpoint rate limiting (`Std:113`, `:379`) on this mechanism, owns the unknown-email keying problem, and owns the `Std:131`-versus-`Priv:441` lock-clearing contradiction.
- **12** — three rejection event classes to field, all carrying `source.ip` per 18; `Std:325` puts rate-limit breaches at `WARN` "with endpoint".
- **13** — no interaction; the limiters run before session handling.
- **15** — add Bucket4j **and** Caffeine to the POM (not one or the other); add the NAT/shared-egress risk to deployment assumptions.
- **21** — a fourth and fifth off-MVC writer site (`429` bodies from the rate-limit filter).
- **14** — tests for: 5 consecutive failures → lock; a successful login mid-sequence resets the counter to zero; lockout survives restart (`Std:451`); automatic lift at 20 minutes on an injected `Clock`; `429` + `Retry-After` on each of the three limiters; admin unlock lifts it; self-unlock is `403`; and the restart-wipes-throttle-counters behaviour above.
