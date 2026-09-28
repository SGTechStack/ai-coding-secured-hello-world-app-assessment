# 09 — Decide lockout and dual rate limiting semantics

Type: grilling
Status: resolved
History: resolved once, re-opened by ticket 21, re-resolved at "§R — Resolution of the ticket 21 reopening"
Blocked by: 01, 02, 04, 06

## Question

Exactly when does an account lock, exactly when does an IP get throttled, and how do the two
interact at every edge?

## Settled going in

**Both limiters, independently.** The PRD (Story 3) demands per-IP throttling so an attacker cannot
lock out a legitimate user from a single source. The App Standard demands per-account counters and
explicitly argues per-IP is bypassable via IP rotation — while its own sequence diagram shows a
per-IP check, so the standard contradicts itself. Both arguments are correct about different
attacks, so we implement both. Bucket4j + Caffeine behind a `LoginRateLimiter` abstraction, in
memory, single instance.

## Inherited from ticket 06 — constraints, not open questions

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
settled the response-time-normalisation question that used to sit in the map's fog, and it lands here as two
hard ordering rules plus one closed leak:

- **Lockout is checked inside or after the authentication attempt, never as a pre-auth branch keyed on
  account existence.** `DaoAuthenticationProvider` already dummy-encodes and runs a full `matches()` against
  a cached dummy hash for unknown users with `hideUserNotFoundExceptions=true`, so the timing mitigation is
  the framework's — a pre-auth lockout branch reintroduces the signal *in front of* it. No `PasswordEncoder`
  wrapper or fast path may skip `matches()`. An artificial response-time floor was considered and **rejected**
  (must sit above the slowest legitimate outcome, leaks whenever exceeded, harder to test than the control it
  replaces).
- **The per-account limiter counts against the submitted username, not a resolved account.** A miss creates a
  counter keyed by the submitted string, so an unknown username is throttled on the same schedule as a known
  one. Otherwise the per-account 429 is an existence oracle — the uniformity rule covers status, body and
  timing but **not headers**, so this leak is not closed by anything already written down.
- Wire contract is fixed: **429 `TOO_MANY_REQUESTS`** in the RFC 9457 envelope, `Retry-After` in **integer
  seconds**. Lockout emits no distinct code — a locked account returns the same 401 `AUTHENTICATION_FAILED`
  as every other auth failure, and `account locked` is a **log reason only**.

Budgets, windows, the XFF default and the two-limiter ordering remain yours.

## Inherited from ticket 07 — a constraint, not an open question

[Decide the password policy and hashing parameters](07-password-policy-and-hashing.md) made the password
pipeline expensive, which turns an ordering question you would otherwise not have noticed into a real one:

- **Per-IP rate limiting runs *before* the password pipeline, never after validation.** Setting a password now
  costs a blocklist lookup, context-term checks, a zxcvbn estimation and a BCrypt encode at cost 12.
  **Self-registration is unauthenticated**, so it is the most expensive unauthenticated endpoint in the
  application — a small-request/expensive-response amplifier reachable without credentials. Validate-then-throttle
  hands an attacker that CPU cost for free on every rejected request.
- Scope therefore includes **registration**, not just login and the reset endpoints. The standard only names
  login and reset; registration is ours to add, and it is now the cheapest endpoint to abuse.
- Note the interaction with the lockout duration decision: BCrypt cost 12 also makes a self-service password
  change a ~2 second request (five BCrypt operations). That is not an attack surface — it is authenticated and
  rare — but it is the slowest endpoint in the app and worth knowing about when setting any global timeout.

## What to decide

**Account lockout.**

- Threshold: PRD 5, standard 5. Agreed.
- Duration: PRD says 15 minutes, standard default 20. Value conflict — standard wins per the map's
  conflict rule, but confirm.
- Is the counter *consecutive* failures, or failures within a rolling window? The PRD says both
  things in different places ("N consecutive failed attempts" and "within a window"). Decide one.
- Automatic lift on expiry, plus admin unlock. The standard also says an admin-initiated password
  reset does **not** clear the lock. Confirm.
- Counter persistence: must survive restart (the standard lists in-memory lockout state as a failure
  source), so it lives on the user row — which is what the PRD's data model already implies.
- Does a correct password against a locked account extend the lockout or leave it alone? PRD Story 2
  says it is still rejected; it does not say whether the clock resets. Decide, and note that
  extending it creates a denial-of-service lever.

**The edges that leak information or cause bugs.**

- **Unknown username**: does it increment anything? There is no account to count against. Decide
  whether unknown usernames feed only the IP limiter. This interacts with the timing mitigation in
  "Decide the API error envelope".
- **Disabled account with correct password**: does the failed counter increment?
- **Non-verified account** (from the registration gating decision): same question.
- Does the lockout response differ from a wrong-password response? It must not — the standard
  requires `account locked` to be indistinguishable from invalid credentials on the wire.

**IP throttling.**

- Budget and window. The standard's per-account budget is 10/minute; the per-IP budget is ours to
  set. Consider that a shared corporate NAT puts many legitimate users behind one IP — set a budget
  that doesn't lock out an office.
- Response: 429 with `Retry-After` per the standard.
- Scope: login only, or also password-reset request and token redemption? The standard requires rate
  limiting on the reset endpoints too.
- **`X-Forwarded-For` trust.** Deployment topology is out of scope, so there is no known proxy to
  trust. Default to the direct socket address and treat XFF as untrusted, because trusting a
  spoofable header turns the limiter into a no-op. Decide how the trusted-proxy configuration is
  exposed for whoever does deploy this, and make the insecure default impossible.
- Eviction and memory bound on the Caffeine cache — an unbounded key space keyed by attacker-chosen
  IPs is itself a denial-of-service vector.

**Interaction.** Which check runs first, and does an IP-throttled request still increment the
account counter? If it does, an attacker can lock any account by deliberately tripping their own IP
limit — exactly the attack the PRD wants prevented. Get this ordering right; it is the crux.

## Done when

Every edge above has a stated behaviour, the ordering question is resolved, and the XFF default is
recorded along with how a deployer overrides it safely.

## Inherited from ticket 11 — one confirmation now load-bearing, and one CVE

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
built a recovery story on a number this ticket owns.

- **Lockout auto-expiry is now a recovery control, not just a DoS mitigation.** A fresh deployment runs with one
  admin, and an admin cannot unlock themselves. Ticket 11 declined to seed a second admin on the grounds that
  auto-expiry (standard default: 20 minutes) means a locked-out sole admin is *delayed* rather than locked out.
  If this ticket sets lockout to permanent-until-admin-unlock — which Q15 offers — then a sole admin who trips
  the threshold is unrecoverable without database access, and ticket 11's bootstrap decision has to be reopened.
  Confirm the auto-expiry explicitly rather than by omission.
- **The admin surface manufactures the states a CVE cares about.**
  [CVE-2026-22746](https://www.sentinelone.com/vulnerability-database/cve-2026-22746/) found that
  `DaoAuthenticationProvider`'s timing-attack defence was bypassable for accounts that are **disabled, expired
  or locked**, because the status-check path skipped the timing normalisation the dummy-hash mitigation provides.
  Patched above our pinned version, but it is independent evidence for ticket 06's ordering rules and it
  **extends them**: no pre-authentication branch on account **state**, not merely on account existence. Our own
  lockout check must sit inside or after authentication for the same reason the framework's now does.
- **`credentialIssuedAt` is checked on the login path**, per ticket 11's lazy 30-day forced-change expiry. It
  refuses the login with the generic `401 AUTHENTICATION_FAILED`, and it must not interact with the failed-login
  counter — a refusal for an expired forced-change credential is not a failed credential presentation and must
  not increment toward lockout, or an admin-provisioned account that sat unused for a month locks itself.
- **Ticket 11's endpoints add no new rate-limited surface**, but the unlock endpoint is worth a line in whatever
  budget table this ticket produces: it is the only endpoint that *clears* limiter-adjacent state, so decide
  whether unlock also resets the per-IP throttle or only the per-account counter.
---

## Answer

**Lock at 5 consecutive failures within a 20-minute observation window, for 20 minutes with automatic
lift; throttle per source IP in a pre-authentication filter and per submitted username inside the
authentication converter; trust no forwarded header the deployer has not explicitly named; and accept
malicious account lockout as a documented residual because the 20-minute auto-lift is the only
mitigation any account-bound counter can have.**

The two limiters are one component, `AuthRateLimiter`, invoked from two call sites on two different
axes. Splitting them by placement rather than putting both in one filter is the decision the rest of
the ticket hangs off, and it was forced by a constraint ticket 05 had already recorded.

### 1. The body can only be read once, which decides where each limiter lives

Ticket 05 chose Route C — `AbstractAuthenticationProcessingFilter` with a JSON
`AuthenticationConverter` — and noted at its item 6 that the converter consuming the body means
**downstream filters cannot re-read it**. A pre-authentication filter that needs the username therefore
has to buffer the body and hand a wrapper downstream, which means introducing bounded body buffering in
front of every login: exactly the small-request/expensive-work amplifier ticket 07 warned about, in the
one place it must not exist.

**Resolved by asking which limiter actually needs pre-authentication placement. Only the per-IP one does.**
Its reason is ticket 07's rule that throttling precedes the CPU-expensive password pipeline, and it needs
nothing from the body. The per-account limiter's jobs (§4 below) are all satisfiable later, so it moves
into the converter, immediately after the username is parsed and before `authenticationManager.authenticate`
— still ahead of BCrypt.

Four consequences, three of them improvements on the draft:

- **Ticket 06's "key on the submitted username, not a resolved account" becomes structural.** The converter
  holds the raw submitted string before any repository lookup, so the rule cannot be violated by a later
  refactor that resolves the account first.
- **The seventh envelope producer does not exist.** The per-account `429` is thrown as a
  `TooManyRequestsAuthenticationException`, propagates out of `attemptAuthentication`, is caught by
  `doFilter`'s `catch (AuthenticationException)` and written by `unsuccessfulAuthentication` — which is
  already one of ticket 06's producers. Producer count stays at **six** (ticket 06's four plus ticket 08's two).
  The per-IP `429` comes from the early filter, which *is* a new producer, so the count is six plus one
  filter, not seven independent ones: both paths still go through the single `ProblemDetailWriter`, and
  `sendError` remains prohibited.
- **The crux guarantee changes mechanism on the account axis, and must be asserted separately.** For the
  per-IP limiter the guarantee is filter ordering: a throttled request never reaches the authentication
  filter. For the per-account limiter it is *where the throw happens*: a converter throw never reaches
  `ProviderManager`, so no `AuthenticationFailureBadCredentialsEvent` fires and the lockout counter cannot
  move. Two different arguments for the same property, so two tests — not one.
- **The uniform-401 rule now has a deliberate exemption inside the handler whose job is uniformity.**
  `unsuccessfulAuthentication` branches on exception type to emit `429` instead of `401`. A later
  simplification to "always 401" would silently delete the per-account limiter's only observable behaviour
  and nothing would fail except §5:452's prescribed test. **A comment at the branch recording the exemption,
  and a test asserting 429-not-401 for `TooManyRequestsAuthenticationException`**, are both required — the
  comment because the code reads like a bug, the test because the failure mode is silent. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-001, T-RL-003, T-RL-002. Amend the table by ID, not this list.*

### 2. Lockout: the numbers, and the one window that fixes two bugs

Threshold **5** (PRD and standard agree; an Enforced Constraint, nothing to decide). Duration **20 minutes
with automatic lift** — the standard's default, taken over the PRD's 15 per the map's conflict rule, and
inside WSTG-ATHN-03's 5-to-30-minute band. Q15's "Permanent (admin unlock required)" is **rejected on the
record**: with one seeded admin and no self-unlock it turns a mistyped password into an unrecoverable
deployment.

> **Amended by the ticket 21 reopening (§R below): the duration is no longer a single value.** It escalates
> **20 / 40 / 60 minutes** as the account approaches the NIST cap, which is that reopening's replacement for
> the declined `429`-expressed progressive delay. The 60-minute rung sits **outside** WSTG's 5-to-30 band; NIST
> §3.2.2's own example ("30 seconds up to an hour") is the governing citation and WSTG's band is descriptive.
> Everything below about the *window* stands unchanged.

**Observation window = 20 minutes.** Originally stated as *window = duration = one configured value*; the
ladder above splits them, with the constraint **`duration ≥ window`**, and both bugs the single value fixed stay
fixed because a lock that has just lifted still guarantees `last_failed_at` is at least a window old.
Ticket 02 found the standard
has no window at all — "5 *consecutive* failures" resetting only on success, so five typos spread over
months lock the account, and OWASP names the observation window as one of the three required parameters.
Mechanism is a staleness reset on `last_failed_at`: on a failure, if the last failure is older than the
window, set the counter to 1 instead of incrementing.

Using one value rather than two is not tidiness. It fixes a second bug nothing on this map had recorded:
**with lazy expiry the counter stays at 5 after the lock lifts, so the first wrong password after auto-lift
re-locks immediately for another 20 minutes.** A lock that has just expired guarantees `last_failed_at` is
at least 20 minutes old, so the staleness reset catches it and the user gets five fresh attempts.

Adopting the same mechanism **ticket 22 called a defect in `MFA_Core`**, deliberately and with the same
limitation stated: a paced attacker still locks a known username, because the reset looks only at the most
recent failure. No account-bound counter can prevent that. The window's job is protecting legitimate users
from their own typos across days, not stopping attackers.

`is_account_non_locked` is **not a stored column**. It is derived: `locked_until == null || locked_until <= now`.
Two sources of truth for one fact would drift, and auto-lift is then free — no scheduler, no cron. This
contradicts the admin recipe's entity, which stores `accountNonLocked` as a `Boolean` and whose generic-update
guard reads it; ticket 11 already blocked locking through that path, so the guard becomes "reject any attempt
to set lock fields at all" rather than a comparison. Columns owed to ticket 12: `failed_login_attempts`,
`locked_until` (both in the PRD's data model) and `last_failed_at` (new, and the reason the window is
implementable). The corpus prescribes two mutually incompatible column sets across two recipes in the same
folder — `failedAttempts`/`lockedUntil` in the login recipe versus `failedLoginAttempts`/`accountNonLocked`
in the admin recipe — so the naming follows the PRD, which at least agrees with itself.

### 3. Counting is atomic under a row lock, and the listener may never change the response

Counting from an `AuthenticationFailureBadCredentialsEvent` listener is a read-modify-write outside the
authentication transaction. Ten parallel wrong-password attempts each read the same starting value and
write the same result: the account under-counts and the threshold goes soft. The per-account limiter narrows
the window to ten attempts per six seconds, which is wide enough for ten concurrent requests to fit.

**Pessimistic `SELECT … FOR UPDATE` on the user row by primary key, computed in Java — the same idiom as
ticket 11's two-admin guard.** H2's `LOCK_MODE` defaults to 3, row-level locking for writes, and unlike
ticket 11 there is no aggregate restriction to work around because this is a single row by key. The
alternative — a single atomic `UPDATE … SET failed_login_attempts = failed_login_attempts + 1` that never
reads first — was declined because it cannot cheaply report whether *this* call caused the transition, and
the transition is load-bearing: it drives the WARN, ticket 08's session kill, and the owner notification.
H2's data-change-delta syntax would return the new value and is not portable, which breaks the map's
vendor-neutral Flyway rule.

Three risks the mechanism introduces, all of which must be closed:

- **The listener can turn a 401 into a 500, which is a state oracle.** A synchronous `@Transactional`
  listener that throws — lock timeout, deadlock — propagates into the publisher. Contention on a single
  user row is precisely what an attacker manufactures, and a 500 on a contended account against 401
  everywhere else is the leak class ticket 06 exists to prevent. **Rule: fail open on counting, never fail
  open on responding.** The listener body is wrapped in catch-and-log at `ERROR`; a lost increment is
  acceptable, a distinguishable response is not.
- **H2's lock timeout is pinned explicitly.** `SET LOCK_TIMEOUT` and `SET DEFAULT_LOCK_TIMEOUT` both default
  to **1000 ms**, low enough that contention produces exceptions rather than waits. Set it on the JDBC URL
  rather than inheriting it, so the value is visible where the datasource is configured.
- **Row locking is now a codebase convention, so lock ordering is pinned: user rows before session rows,
  always.** Ticket 11's guard locks admin user rows, this locks a user row, and ticket 08's lockout trigger
  reaches session rows. Without a stated order this is a deadlock that only appears under load an attacker
  supplies.

Concurrent success and failure serialise on the row lock and both orderings are benign: failure-then-success
resets to 0, success-then-failure leaves a live session with a counter of 1. The interesting case is a success
landing alongside the fifth failure — the account ends locked *and* the winner holds a session — which
self-heals, because ticket 08 already made lockout invalidate the target's sessions. The listener is
synchronous, so the lock commits before the 401 is written, which is the ordering the standard's own sequence
diagram shows.

### 4. What the per-account limiter is actually for — three jobs, and ticket 02's defect corrected

> **Amended by the ticket 21 reopening: there is a fourth job, and it belongs to a new axis rather than this
> one — bounding the *width* of the mass permanent-lockout primitive §R.2 quantifies.** Recorded here because
> this is the section someone reads when asking what the account axis is for, and the answer is now "three
> jobs on the submitted-username axis, and a fourth on a third axis this section predates."

Ticket 02 recorded that the standard's two counters "cannot both bite": lockout at 5 makes the 10/min
per-account limiter unreachable, so §3.5:378 and Failure Path 3 are dead code on the account axis. **That is
half wrong, and the correction keeps the number unchanged.** A rate limiter counts *all* attempts; the lockout
counter counts *consecutive failures*. Four failures plus a success, twice over, is ten attempts with no
lockout — so the limiter is reachable, just not by an attacker holding no valid credential.

Its three jobs, in ascending order of how much they justify its existence:

1. **Abuse control** on mixed or successful traffic — a client stuck in a retry loop, or credential farming
   with known-good credentials.
2. **The only account-axis control on username-discovery traffic.** A non-existent username has no row and
   therefore no lockout counter at all (§6), so the submitted-string bucket is the *only* thing bounding
   per-username probing of accounts that do not exist.
3. **It bounds write contention on a single user row.** Without it, 60/min/IP across N sources against one
   username is a lock convoy on one row — which makes §1's placement load-bearing for §3, because the
   limiter has to stay ahead of the listener, and it now does.

Job 3 is brute-force-adjacent rather than mere abuse control and is the strongest argument for keeping the
prescribed 10/min. Recorded so the budget is not "optimised away" later as dead code. *Consolidated into the register (ticket 33): R-STD-018. Amend the table by ID, not this list.*

### 5. Budget table

One component, `AuthRateLimiter`, two call sites, three axes. The ticket's settled text called it
`LoginRateLimiter`; renamed, because it now serves reset-request too and a misleading name is how "which
axis runs where" becomes tribal knowledge. All budgets greedy-refill, so each row is a burst of N followed
by a sustained rate — stated per row because "5 per minute" and "one attempt every 12 seconds" feel like
different products and someone will file the second as a bug.

| Route | Axis | Burst | Sustained | Call site |
|---|---|---|---|---|
| `POST /api/login` | source IP | 60 | 1 / s | early filter |
| `POST /api/login` | submitted username | 10 | 1 / 6 s | converter |
| `GET /api/csrf` | source IP | 30 | 1 / 2 s | early filter |
| `POST /api/register` | source IP | 5 | 1 / 12 s | early filter |
| `POST /api/password-reset/request` | source IP | 5 | 1 / 12 s | early filter |
| `POST /api/password-reset/request` | submitted identifier | 3 / hour | 1 / 20 min | handler |
| `POST /api/password-reset/confirm` | source IP | 10 | 1 / 6 s | early filter |
| `POST /api/register/activate` | source IP | 10 | 1 / 6 s | early filter |
| `PATCH /api/profile/password` | source IP | 10 | 1 / 6 s | early filter |
| TOTP challenge | source IP | 20 | 1 / 3 s | early filter |

**A third axis exists and is deliberately not a row in the table above**, because burst/sustained are token-bucket
columns and this is not a token bucket. Added by the ticket 21 reopening, §R.6:

> **Axis:** source IP → **set of distinct accounts this source has driven into tier-1 lockout**.
> **Limit:** cardinality `k ≈ 5` per rolling hour, not a rate.
> **Call site:** the `AuthenticationConverter`, beside the per-account bucket.
> **Recorded at:** the tier-1 lockout transition in §3's listener — *not* at the attempt.
> **State:** a bounded per-source set, not a `Bucket`.

Given its own form because a reader treating the table as a schema will otherwise implement it as a bucket, and
a bucket counts attempts, which is the formulation §R.6 rejects for tripping on every shared egress.

Notes that are decisions, not commentary:

- **The reset-request submitted-key row exists because per-IP bounds nothing per address.** 5/min across N
  sources is unbounded mail to one victim, and the per-submitted-key axis is the only control that bounds it.
  It lives in the reset-request handler rather than the converter, which is why `AuthRateLimiter` is one
  component invoked from three places. Cheap now while the notification transport is a stub; expensive after
  ticket 10 lands.
- **`/api/register/activate` is ticket 06's activation-token redemption**, unauthenticated and token-bearing,
  structurally identical to reset-token redemption. It would have been missed because ticket 06 created it
  after this ticket's scope was written.
- **`/csrf` at 30/min revises ticket 08's figure of 10.** 10 breaks a developer reloading the SPA, because
  each load fetches a token. Consequence recorded rather than left implicit: live anonymous session rows rise
  from roughly 150 to roughly **450 per source** against the 15-minute idle window, since the JDBC cleanup
  cron only deletes rows that have already expired. Separately, an unauthenticated `GET` with a persistent
  side effect is at odds with HTTP safe-method semantics, and that **cannot be fixed here**: §3.1:238 requires
  the token be session-bound, so ticket 08 was right that there is no structural escape. Raised to 08 and 12
  as a recorded oddity, not a fixable cause.
- **`PATCH /api/profile/password` is keyed on source IP, not principal.** It cannot be per-principal: the *Consolidated into the register (ticket 33): R-CSRF-001. Amend the table by ID, not this list.*
  early filter runs before `SecurityContextHolderFilter` (the default chain is `DisableEncodeUrlFilter` →
  `WebAsyncManagerIntegrationFilter` → `SecurityContextHolderFilter`), so there is no authenticated principal
  to key on. The residual is the exact inverse of the login row's NAT concern — **one authenticated user can
  consume an office's entire password-change budget** — and it is accepted because the route is authenticated,
  audited, rare, and capped by ticket 08's single-session rule at roughly 20 s of CPU per minute per source.
  Written down so it is not "fixed" later by someone who only sees the asymmetry. *Consolidated into the register (ticket 33): R-RL-001. Amend the table by ID, not this list.*
- **No per-session axis.** Considered and deleted rather than documented-but-unused; a documented unused axis
  is an invitation.
- **Admin endpoints are deliberately unthrottled**, including unlock. They are authenticated, factor-gated,
  rate-limited by the human operating them, and adding a limiter would create a path where an attacker who
  cannot authenticate can still deny an admin their own recovery tool.
- **Unlock clears `failed_login_attempts` and `last_failed_at` and `locked_until`, and never touches any
  bucket.** Resetting the per-IP throttle on unlock would let an attacker clear their own throttle by
  provoking an admin unlock.
- **No refund-on-success.** Refunding a per-IP token when a legitimate login succeeds lets an attacker behind
  a shared NAT ride their colleagues' successful logins.

`Retry-After` is `ceil` of Bucket4j's `getNanosToWaitForRefill()`, minimum 1, integer seconds — `delay-seconds`
per RFC 9110 §10.2.3 and the integer form `MFA_Core` L198 mandates. At greedy refill it is almost always
`1`, which is correct and worth stating: a well-behaved client never sees sustained denial. `429` carries
`Cache-Control: no-store`; RFC 6585 §4 already forbids caching a 429, so this is defence against
non-conforming intermediaries — and per ticket 05's finding, setting our own `Cache-Control` **suppresses
Spring Security's three-header set**, so the 429 writes the full `Cache-Control`/`Pragma`/`Expires` triple
rather than one header.

### 6. Every leaking edge collapses onto Spring's own exception-to-event mapping

**Count from `AuthenticationFailureBadCredentialsEvent`; reset from `InteractiveAuthenticationSuccessEvent`.**
`DefaultAuthenticationEventPublisher` maps both `BadCredentialsException` and `UsernameNotFoundException` to
the former, while `LockedException`, `DisabledException` and `CredentialsExpiredException` each get their own
event. So:

- **Unknown username** increments nothing on the account axis — no row exists — and feeds only the per-IP
  limiter and the submitted-string bucket.
- **Disabled account with the correct password** does not increment.
- **Locked account with the correct password** neither increments nor extends the lock. This closes the
  ticket's open question and removes a DoS lever, and it is not a choice: `performPreCheck` discards the
  password comparison's result for a locked account, so we *cannot* know whether the password was right.
- **`credentialIssuedAt` expiry**, implemented as `isCredentialsNonExpired()`, increments nothing — exactly
  what ticket 11 requires. The standard's §2 step 7 warning against using `isCredentialsNonExpired()` is
  about the *forced-change flag*, where a session is needed so the user can change their password; the 30-day
  *expiry* deliberately wants no session, so the warning does not apply. Recorded because the two look
  identical at a glance. Note the consequence: a correct password against an expired credential neither
  increments nor resets, so a stale counter persists. Accepted. *Consolidated into the register (ticket 33): R-LCK-001. Amend the table by ID, not this list.*

Two invariants:

- **`alwaysPerformAdditionalChecksOnUser` stays at its default `true`.** It is declared on
  `AbstractUserDetailsAuthenticationProvider` (not `DaoAuthenticationProvider`, which the advisory names it
  under by inheritance), `@since 5.7.23`, and it is what makes our pre-authentication lock check safe: when a
  pre-check throws it still runs the password comparison, swallows the result, and rethrows the original
  failure, so a locked account costs the same as an unlocked one. **`false` reads as a free optimisation and
  reintroduces the CVE.** Assert it in a test.
- **Do not read the username from `request.getParameter("username")`**, as the login recipe's failure handler
  does. Under Route C's JSON filter that returns `null` and `recordFailure` silently never runs — a defect
  that surfaces as "lockout mysteriously never triggers". The event's `getAuthentication().getName()` is the
  stable source. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-005. Amend the table by ID, not this list.*

### 7. Forwarded headers: the deployer names their proxy or the app does not start

`server.forward-headers-strategy` is unset by default in Boot 4.1.1 and resolves to `NONE` off a recognised
cloud platform, so `getRemoteAddr()` is the socket peer — the correct default, inherited for free.

**`framework` is prohibited outright.** `ForwardedHeaderFilter` performs no trusted-proxy validation of any
kind; its own javadoc says an application cannot tell whether forwarded headers came from a trusted proxy or
a malicious client. A spoofable key does not weaken the limiter, it **nullifies** it — every attacker request
presents a fresh bucket — which is why this is a hard startup failure and not a warning.

`native` installs Tomcat's `RemoteIpValve`, which peels `X-Forwarded-For` right-to-left, skipping entries
matching `internalProxies`, and which ignores forwarded headers entirely when the direct peer is not a listed
proxy. Its Boot 4.1.1 default is a CIDR list (not the historic regex) covering loopback, RFC 1918, CGNAT and
link-local. **A `@NotEmpty` check against that default passes with the deployer having decided nothing**, and
the default trusts *any* peer in those ranges — in a shared private network, every neighbour.

So we own the properties and derive Tomcat's:

- `app.security.client-ip.source` = `socket` (default) or `proxy`.
- `app.security.client-ip.trusted-proxies` — **no default**.
- A `@Validated @ConfigurationProperties` bean fails during context refresh when source is `proxy` and the
  list is absent or empty, following ticket 11's refresh-phase validation pattern.
- A `WebServerFactoryCustomizer` sets `internal-proxies` **and `forward-headers-strategy`** from `source`.
  The customizer must own both: `source=proxy` with the strategy left unset means the valve never installs,
  every request's `getRemoteAddr()` is the proxy's address, and the entire internet shares one bucket — 60
  logins per minute globally, then 429 for everyone. An outage that looks like a working config, so the two
  are derived from one input and a divergence fails fast.
- `server.tomcat.remoteip.internal-proxies` is never set directly and never documented, so Tomcat's default
  range is unreachable by configuration.

**Naming:** our `trusted-proxies` maps to Tomcat's `internalProxies`, *not* to Tomcat's `trustedProxies`,
which has different semantics — matches are recorded into `X-Forwarded-By` rather than silently stripped.
The mapping is documented at the property, because someone setting both gets the peeling wrong.

### 8. Library and cache

`com.bucket4j:bucket4j_jdk17-core:8.20.0` plus our own `Caffeine<String, Bucket>` — **not** the
`bucket4j_jdk17-caffeine` module, whose `ProxyManager` machinery buys nothing single-instance and which
depends on Caffeine 2.9.3 at `provided` scope while we run 3.3.0. The map's "Bucket4j + Caffeine" phrasing is
ambiguous and someone will reach for the module.

The recipe's `Limit.of(10, Refill.intervally(...))` compiles against no Bucket4j release: **`io.github.bucket4j.Limit`
does not exist** at any version. `Refill` *does* still exist at 8.20.0 with `greedy`, `intervally` and a
four-argument `intervallyAligned`, but the whole class and every factory are `@Deprecated`. Stated precisely
because someone will otherwise "fix" the wrong half. Current form: *Consolidated into the register (ticket 33): R-STD-011. Amend the table by ID, not this list.*

```java
Bucket.builder().addLimit(l -> l.capacity(60).refillGreedy(60, Duration.ofMinutes(1))).build();
```

Four cache rules, each closing something specific:

- **`maximumSize(10_000)` per bucket map**, with the calculation stated: ten rows across three axes at 100k
  each is a real heap number on a demo, and the security rationale for a size cap — an attacker-chosen key
  space — argues the number *down*, not up. With expiry set as below the cap is rarely binding anyway.
- **`expireAfterAccess` at twice the refill period.** Eviction or expiry silently discards consumed-token
  state and the bucket is recreated **full**, so a TTL shorter than time-to-full-refill is a bypass. This is
  the rule most Bucket4j-plus-cache implementations get wrong. `expireAfterAccess` specifically loses nothing,
  because a bucket idle for longer than its refill period would have refilled anyway.
- **The account key is ticket 11's canonical form** (NFC, trim, lowercase) — otherwise `Alice` and `alice`
  get separate buckets and the limit is bypassed by capitalisation.
- **The key is length-capped in the limiter itself**, because the limiter runs before the §2 Failure Path 5
  validation that rejects oversized input, and would otherwise accept megabyte cache keys.

Caffeine's W-TinyLFU admission is a genuine advantage over LRU here: a flood of one-shot keys is admitted on
frequency, so an attacker cannot evict their own hot bucket by flooding with distinct keys.

### 9. Three things the standard asks for that we do not deliver

> **Amended by the ticket 21 reopening. This section is now two things, not three.**
> **Item 2 is withdrawn in full** — the NIST §3.2.2 cap is **implemented**, at 100 consecutive failures, under
> the standard's own explicit lower-limit allowance. Its entire argument below rested on reading the cap as
> cumulative-over-a-window; it is consecutive, and the whole text is retained only so the misreading is
> traceable. **Do not cite it.** See §R.
> **Item 3 is qualified** — "the per-account 429 is not reachable by an attacker" remains true of the 10/min
> submitted-username axis, and is **false by design** of the third axis added in §5, which exists precisely to
> be reachable by the mass primitive. Read it as scoped to the axis it was written about. *Consolidated into the register (ticket 33): R-STD-018. Amend the table by ID, not this list.*

- **Distributed-deployment consistency (§5:453) is unimplementable** and deferred with justification. The
  limiter is in-memory and single-instance by the map's own decision, so a restart resets every bucket.
  Lockout state lives on the user row and survives restart, satisfying §5:451 and the runbook's L574 warning.
  "Assert single-instance" is not testable — a test cannot assert deployment topology — so the implementable
  form is a **startup check that fails if a clustering-related property is present**, plus a line in ticket 25's
  handover. And the escape hatch is smaller than it looks: Bucket4j ships **no H2 `ProxyManager` and no generic
  JDBC one** — only per-dialect PostgreSQL, MySQL, MariaDB, MSSQL, Oracle and DB2 modules over
  `select_for_update`/`compare_and_swap` scaffolding. So "swapping to a `ProxyManager` later" is local at the
  call site and **untestable on our baseline**.
- **NIST SP 800-63B-4 §3.2.2's cumulative cap.** The `SHALL` is at most 100 consecutive failed attempts per *Consolidated into the register (ticket 33): R-RL-006. Amend the table by ID, not this list.*
  account, enforced by **disabling** the authenticator and requiring re-binding. We have no ceiling at all.
  Deviation stands on two legs and a commitment, and deliberately **not** on the claim that we implement
  NIST's alternatives — §3.2.2 offers a bot-detection challenge, an increasing mandatory wait, and risk-based
  signals, we implement **none of the three**, and §11 below declines one of them outright. The legs:
  (1) *calibration* — the ceiling is set against low-entropy authenticators, where 100 guesses at a six-digit
  OTP is meaningful; against a 15-character password behind a breach-corpus blocklist and a zxcvbn score-3
  gate, an uncapped **4 failures per 20 minutes = 12/hour = ~288/day = ~105,000/year** against one known
  username is negligible; (2) *failure mode* — disabling at a cap, with one seeded admin and no recovery flow,
  is worse than the attack. Said plainly: §3.2.2's spirit is "there must be a ceiling" and our position is
  "there is no ceiling". The **commitment** that replaces it is detection, owned by ticket 21: alert when a
  single account exceeds **50 failed logins in 24 hours** (four times the paced-attack rate, so it fires on a
  real campaign and not on a forgetful user). The honest caveat, because ticket 18 will find it otherwise:
  **ticket 01 already recorded lm-16, no monitoring, as an unacknowledged IM8 FAIL**, so this deviation's
  compensating control is itself a known gap on this map — and if ticket 21 does not land, the deviation has
  no compensating control at all. Reopening trigger: any weakening of the password floor or the strength gate,
  or the arrival of a recovery flow, brings the cap back.
- **The per-account `429` is not reachable by an attacker** (§4). Recorded rather than fixed. *Consolidated into the register (ticket 33): R-STD-018. Amend the table by ID, not this list.*

### 10. Malicious account lockout: satisfied with a documented residual, not a partial

PRD Story 3 promises an attacker "cannot lock out a legitimate user merely by failing that user's password
from one source". **Unachievable as written.** Sustaining a targeted lock costs 5 failures per 20 minutes —
15 requests/hour against a 3600/hour budget, **240× headroom**. No per-IP budget above 5/min can deliver the
promise, and 5/min would lock out an office.

Resolved as reading (a): the limiters are independent in *state*, which is what the criterion's own second
clause literally says. Reading (b) — that per-IP throttling prevents lockout — is recorded as an explicit
deviation from the PRD's wording. The 20-minute auto-lift **is** the mass-lockout mitigation, which is exactly
the rationale §2 Failure Path 2 gives, and OWASP's own Blocking Brute Force Attacks page says account-bound
locking is inherently a DoS and enumeration vector. *Consolidated into the register (ticket 33): R-LCK-002. Amend the table by ID, not this list.*

**ASVS 5.0 6.1.1 (L1) is satisfied with a documented residual — a pass-with-note, not a fail.** 6.1.1 is a
documentation requirement: define how rate limiting, anti-automation and adaptive response are used against
credential stuffing and brute force, and make clear how they are configured and prevent malicious account
lockout. Nothing in ASVS requires malicious lockout to be *impossible*. Documenting the residual, the
auto-lift, the audited unlock and the detection plan **is** the artefact 6.1.1 asks for, and 6.3.1 (L1) is met
because the implementation matches that documentation. The difference between a pass-with-note row and a fail
row in ticket 18's report is free, and conceding more than ASVS asks would have been the expensive mistake.

**The load-bearing sentence of this ticket, previously unwritten: the 20-minute auto-lift is now unremovable
by three independent arguments.** Ticket 11's bootstrap depends on it (a sole admin cannot unlock themselves).
§9's NIST deviation depends on it (a lock that lifts is why we need no cumulative ceiling). And WSTG-ATHN-03
depends on it: our compensating control is WSTG's **tier 3**, manual administrator unlock, and the same
remediation section attaches a precondition — the administrator should also have a recovery method in case
their own account gets locked — and warns that admin-only unlock can itself become the DoS. So our tier-3
control is **backed by tier 1 as the admin's own recovery path**. Remove the auto-lift and three separate
decisions on this map break at once.

Self-service unlock (WSTG tier 2) is **not** taken, and the reason is not cost. It would *lower* assurance
from tier 3 to tier 2 while raising availability; it needs a single-use token, its own rate limit, and a
notification transport that is still unspecified; and it grows ticket 10. It is therefore added to the map's
**Not yet specified**, attached to the owner-notification patch that already carries ticket 06's activation
token, rather than silently omitted.

### 11. Progressive backoff and bot detection: declined, on the reasons that actually hold

**Progressive/exponential per-account delay — declined.** The draft argument (weaker than a 20-minute lock)
was wrong: backoff and lockout are *alternatives*, not additions, and the point of backoff is to replace the
lock and remove the DoS lever, so comparing their strength head-to-head misses the argument. **The real reason
is thread-pool exhaustion**: a sleep-based delay on blocking Tomcat holds a request thread, so 200 concurrent
slow-failing logins starve the connector — a self-inflicted DoS, and a worse one than the attack. The
`429`-expressed form does not have that problem, which is why only that form was ever on the table; saying so
is what makes the decline legible rather than arbitrary. Ticket 06's rejection of an artificial response-time
floor covers the same ground from the other direction.

**Bot detection / CAPTCHA — declined.** NIST lists it first among anti-lockout techniques and ASVS 6.1.1 names
anti-automation explicitly, so leaving it unmentioned would be the omission that most directly invites S3's
question. Declined for scope: no third-party service, no network egress, demo application. WSTG-ATHN-03
supports the shape of the decline — it says a CAPTCHA should not replace a lockout mechanism, and lists five
ways a CAPTCHA is bypassed when implemented incorrectly.

**IETF `RateLimit` / `RateLimit-Policy` headers — declined.** Still an Internet-Draft
(draft-ietf-httpapi-ratelimit-headers-11, 23 May 2026, expires 24 November 2026), never published as an RFC,
and on the login route the fields would leak limiter policy regardless. One line so ticket 18 does not ask.

**Threshold 5 is conservative and we know it.** WSTG-ATHN-03 gives two typical bands — 3 to 5 in its Summary
and 5 to 10 in Factors item 4 — so 5 is the single value both agree on; NIST permits up to 100. The cost of
5 is paid in DoS exposure (§10) and user friction. Nothing to decide, since the PRD and the standard agree and
§4:400 makes lockout an Enforced Constraint, but ticket 18 will ask why the DoS lever is so cheap and the
answer should already be written down. Microsoft's baselines were considered as a comparison point and
**dropped**: the SCT baseline is 10, KB5020282 frames it 10/10/10, and Entra smart lockout defaults to 10
attempts / 60 seconds — none of which support the "no lockout at all" framing, so the citation would have been
wrong.

### 12. Observability: a 429 that can be investigated without an address in the log stream

§3.4 L325 requires WARN on rate-limit breaches "with endpoint"; the PII rules ban raw usernames, and ticket 03
left unresolved whether `source.ip` may be logged at all. Without something correlatable, the runbook's
"monitor 429 hits" (§6 L536) is unactionable.

> **Load-bearing dependency found by the ticket 21 reopening (§R.7): this section assumes the listener can see
> the client IP, and by default it cannot.** The listener has no `HttpServletRequest`, so the only in-band
> carrier is `Authentication.getDetails()` — and under ticket 05's Route C converter that is `null`, because
> `authenticationDetailsSource` is read only by subclasses that build their own token. **The converter must set
> `WebAuthenticationDetails` explicitly, with an assertion.** Unset, every row here carries one key and still
> looks plausible because a hash is present. Verified at
> [§20 of the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md).
> Note also **one resolution path, two representations**: the §5 cardinality axis keys on the resolved *raw*
> address while this field is the domain-prefixed HMAC of it. Neither HMAC the cache key nor log the raw one. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-005. Amend the table by ID, not this list.*

**Log a keyed HMAC of the client IP as `source.ip.hash`**, alongside `url.path`, `trace.id` and `session.hash`,
reusing ticket 11's HMAC facility — the same trade already accepted for email, so no new key and no new
argument. Two riders: **the HMAC input is domain-prefixed** (`ip:` / `email:`) so the two streams cannot be
cross-correlated and can be reasoned about independently at rotation, which is cheap now and awkward later;
and ticket 11's "pseudonymisation, not anonymisation" limit is **restated where an IP is the subject**, because
an IPv4 space is 2³² and therefore brute-forceable if the key leaks. The per-account axis logs no key at all:
it is a raw submitted username, which §3.4 L331 names outright.

### 13. Cross-authenticator rules, and the event that does not discriminate by itself

Three rules stated here:

- A failed TOTP code never increments the password lockout counter, and a failed password never increments the
  factor counter. They are different authenticators under different policies — 5 / 20-minute auto-lift versus
  `MFA_Core`'s 10-in-an-hour lock until administrative review.
- Neither counter is reset by the other's success. This closes the hole where the factor step clears the
  password failure count.
- **`InteractiveAuthenticationSuccessEvent` does not separate the two authenticators by itself.** If the TOTP
  step is a second `AbstractAuthenticationProcessingFilter` it publishes the *same event type*, so a successful
  factor step would reset the password counter — precisely the hole the rule above claims to close. **The
  listener discriminates on the `Authentication` type or the source filter, not on the event type.** One line
  of code; without it the rule reads as if it works and does not.

Why the interactive event rather than `AuthenticationSuccessEvent`: `ProviderManager` publishes the latter the
moment credentials verify, before `sessionStrategy.onAuthentication` runs, so a login rejected at the session
stage would still reset the counter. The exposure today is narrow — `exceptionIfMaximumExceeded` defaults to
`false` and ticket 08 chose new-login-wins, so the concurrent-session path expires the old session rather than
throwing, and no live path fires the provider event and then fails. The interactive event is used anyway
because it costs nothing and flipping that flag later would silently open the gap.

**Owned by ticket 23, named rather than left implicit:** NIST §3.2.2's rule that where more than one
authenticator is implicated in excessive attempts **both** are disabled, and the constraint that a reset
`SHALL NOT` raise an authenticator above the AAL of the session performing it — live, not theoretical, now
that admin MFA is in scope.

### Deviations owed as ADRs

1. Lockout duration 20 minutes, not the PRD's 15 (standard wins on control behaviour). **Amended by §R: the *Consolidated into the ADR routing (ticket 34): ADR-011. Amend by ID, not this list.*
   duration escalates 20 / 40 / 60, so the deviation from the PRD's 15 is larger than first recorded, and the
   60-minute rung also departs from WSTG-ATHN-03's 5-to-30 band on NIST's authority.**
2. Observation window of 20 minutes added where the standard has none; same mechanism ticket 22 called a
   defect in `MFA_Core`, with the paced-attack limitation stated. **Amended by §R: window and duration are now *Consolidated into the ADR routing (ticket 34): ADR-012. Amend by ID, not this list.*
   two values with `duration ≥ window`, not one.**
3. ~~NIST §3.2.2's cumulative cap not implemented — no ceiling, justified on calibration and failure mode, with
   detection as the compensating control and lm-16 named as that control's own gap.~~ **Reversed by §R. The ADR *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*
   is now: §3.2.2's cap *is* implemented, at 100 consecutive failures, under the standard's own
   "agencies MAY impose lower limits" — with 100 chosen as the throughput-minimising value rather than merely
   the permitted maximum. The detection commitment is retained, not retired, because reset-on-success makes the
   cap structurally blind to a compromised-but-active account.**
4. PRD Story 3's lockout-prevention criterion not met; 240× headroom quantified; the state-independence
   reading adopted. *Consolidated into the ADR routing (ticket 34): REJ-013. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-002. Amend the table by ID, not this list.*
5. ASVS 6.1.1 satisfied with a documented residual; malicious account lockout accepted. *Consolidated into the ADR routing (ticket 34): REJ-014. Amend by ID, not this list.*
6. Progressive backoff declined on thread-pool exhaustion; bot detection declined on scope; `RateLimit`
   headers declined on draft status. **Amended by §R: the decline now covers the *sleep-based* form only. The *Consolidated into the ADR routing (ticket 34): ADR-014 / register (bot detection, `RateLimit` headers). Amend by ID, not this list.*
   escalating form is taken, expressed as escalating lockout duration rather than as `Retry-After`, because a
   delay derived from a per-account counter would put account state in a header and §1's placement exists to
   keep the per-account axis indistinguishable. Bot detection and `RateLimit` headers stay declined as
   recorded.**
7. `forward-headers-strategy: framework` prohibited; trusted proxies must be explicitly named or the context
   fails to refresh. *Consolidated into the ADR routing (ticket 34): REJ-015. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-RL-007. Amend the table by ID, not this list.*
8. Admin password reset does **not** clear the lock — following standard L131 over the recipe's
   `ResetPasswordCommand` (L440-442, mandated at L731), because the recipe's version is an undocumented unlock
   path that bypasses ticket 11's audited endpoint and its mandatory reason enum. Friction goes in ticket 25's
   handover or support will read a lingering lock as a failed reset. *Consolidated into the ADR routing (ticket 34): REJ-016. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-010. Amend the table by ID, not this list.*
9. `is_account_non_locked` derived rather than stored, deviating from the admin recipe's entity. *Consolidated into the ADR routing (ticket 34): REJ-017. Amend by ID, not this list.*
10. §5:453 distributed-limiter test deferred; single-instance asserted by a startup check, not a test. *Consolidated into the ADR routing (ticket 34): REJ-018. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-RL-006. Amend the table by ID, not this list.*

### Glossary terms owed

- **Lockout** — temporary, account-bound, auto-lifting, DB-persisted, **with an escalating duration
  (20 / 40 / 60 minutes) as the account approaches the cap**. Amended by §R; it previously read as flat.
- **Throttle** — per source or per submitted key, in-memory, `429`, leaves no state on the account.
- **Disable** — no auto-lift, exited only by **rebinding**. Amended by §R: this was previously defined as
  "administrative or policy-driven" with the note that NIST §3.2.2's disable "is this third thing, which is why
  §9 reads as a deviation rather than a tuning argument". **That clause is withdrawn** — the disable is now
  implemented, so the term names a control this design has rather than one it declined, and it is
  *policy-driven and automatic*, never administrative: no admin action sets or clears it.
- **Authenticator disable** *(distinct from the above, and the reason the above needed narrowing)* — the NIST
  §3.2.2 state on **one authenticator** of an account, as opposed to ticket 11's admin **account** disable
  (`enabled = false`). §R.4 keeps them as separate columns precisely so an admin re-enable cannot clear a cap.
- **Observation window** — the period within which failures must fall to count as consecutive.
- **Client IP** — the resolved address the limiter keys on, which equals the socket peer unless a named
  trusted proxy is configured.

### Handoffs

- **05:** the per-account limiter lives in the Route C `AuthenticationConverter`; the failure handler gains a
  429 branch with a recorded uniformity exemption; add both to Route C's existing compile-check flag. The
  CVE-2026-22746 line needs the declaring class corrected — `AbstractUserDetailsAuthenticationProvider`.
- **08:** `/csrf` is 30/min, not 10 — live anonymous rows roughly 450 per source, not 150. Lockout invalidates
  the target's sessions, as you specified. The safe-method oddity on `GET /csrf` is recorded as unfixable.
- **11:** ⚠️ **this line is quoted by ticket 11's ADR 13 and is amended by §R.5 — read that first.** Auto-expiry
  survives but is no longer a flat 20 minutes (it escalates to 60), and the cap adds a **third** fresh-install
  path that auto-expiry cannot close at all. Original text follows, retained because ticket 11 rests on its
  wording: lockout auto-expiry at 20 minutes **confirmed** — your bootstrap decision holds, and §10 adds a third
  independent reason it cannot be removed. Your CVE line is stale: affected ≤ 7.0.4 and the 5.7/5.8/6.3/6.4/6.5
  lines, fixed in 7.0.5, and we pin 7.1.1, so we are patched rather than "patched above our pinned version";
  keep the assertion test, because the risk was never the version, it is someone flipping the flag. Also the
  generic-update lock guard becomes "reject any attempt to set lock fields" now that `accountNonLocked` is not
  a column.
- **12:** `failed_login_attempts`, `locked_until`, `last_failed_at`; no `account_non_locked` column; user rows
  are locked before session rows; H2 `LOCK_TIMEOUT` pinned on the JDBC URL.
- **13:** lockout-locked and lockout-lifted transitions, rate-limit breach with `source.ip.hash` and
  `url.path`, and the domain-prefixed HMAC input.
- **21:** the detection commitment is now load-bearing for §9's NIST deviation — a single account exceeding 50
  failed logins in 24 hours — and lm-16 means it is currently a gap standing in for a `SHALL`.
- **16:** six tests that exist because the failure mode is silent — **plus eight more from §R.9, handed over as a
  delta against this list rather than a replacement for it** — 429-not-401 for the per-account limiter;
  N+1 over the per-account budget leaves `failed_login_attempts` unchanged; `alwaysPerformAdditionalChecksOnUser`
  is `true`; concurrent failures reach exactly the threshold; the first failure after auto-lift does not
  re-lock; `forward-headers-strategy` is never `framework`.
- **23:** the three cross-authenticator rules, the listener discrimination requirement, NIST's
  both-authenticators rule, and the AAL reset constraint.
- **25:** the trusted-proxy configuration and its startup failure; the single-instance limitation; admin reset
  leaving the lock in place; and the sole-admin recovery path resting on auto-lift. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-005, T-RL-002, T-RL-003, T-LCK-008, T-LCK-001, T-CFG-001. Amend the table by ID, not this list.*

### Done when

Every edge has a stated behaviour (§6), the ordering question is resolved structurally on both axes (§1),
and the XFF default is recorded with a safe override mechanism (§7). Met.

## Amendment from ticket 10 (credential flows)

- **§10's malicious-lockout residual is closed, and self-service unlock leaves the map's fog patch.** Ticket 10
  resolved that a successful reset redemption clears the password lockout. That *is* WSTG-ATHN-03's tier 2,
  reached through a channel we already have: gated on mailbox control, bounded by the 3/hour per-identifier
  reset-request budget, and needing no new token and no new limiter — which were the three costs that made it fog
  rather than a ticket. The three independent arguments for the 20-minute auto-lift are **unaffected**, because
  all three concern an administrator's own recovery path and an admin without their authenticator cannot use an
  email-gated route either. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*
- **Scoped precisely, because the loose version would break MFA:** redemption clears `failed_login_attempts`,
  `last_failed_at` and `locked_until` only, and **never touches any TOTP counter or enrolment state**. ASVS
  6.4.3 (L2) requires that the forgotten-password process not bypass an enabled multi-factor mechanism, and
  ticket 22 found a separate TOTP failure window with no unlock path anywhere in the corpus. Combined with the
  log leak, a shared counter would have let a log reader retry TOTP against an admin freely.
- **The open question at §6 is answered: the self-service change endpoint does not feed the lockout counter.**
  The current password is verified with `passwordEncoder.matches()` directly rather than through the
  `AuthenticationManager`, so no `AuthenticationFailureBadCredentialsEvent` is published. Routing it through the
  manager would let anyone holding a hijacked session lock the victim out of their own account — converting a
  session compromise into a denial of service — while the guessing risk it would mitigate is already bounded by
  the 10/min per-IP budget and by the attacker needing a session first. Failures log at `WARN`.
- **§5's per-IP key for redemption is forced, not preferred.** §3.5:379 and §5:452 mandate *per-account* limiting
  on an endpoint where the account is only knowable after the token lookup the limit exists to protect. Recorded
  as the Standard's tenth defect on this map. *Consolidated into the register (ticket 33): R-STD-019. Amend the table by ID, not this list.*
- Budget rows unchanged. `POST /api/register` now carries no BCrypt, so the 5/min row no longer sits in front of
  the most expensive unauthenticated endpoint in the application.

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**Your auto-lift cannot cross to the factor axis, and your own calibration argument is why.**

**1. Three rate-limit rows, one of which names your pathless row.** All per source IP in the early filter:

| Route | Axis | Burst | Sustained |
|---|---|---|---|
| `POST /api/mfa/totp/verification` | source IP | 20 | 1 / 3 s |
| `POST /api/mfa/totp/enrolment/confirmation` | source IP | 20 | 1 / 3 s |
| `POST /api/mfa/totp/enrolment` | source IP | 10 | 1 / 6 s |

The first is your "TOTP challenge" row with a path. Admin endpoints including `DELETE .../totp` stay unthrottled per
your decision.

**2. The factor axis gets a second tier, because your calibration argument forbids copying the first.** Your record
says *"100 guesses is a six-digit-OTP number; ours is ~12 guesses/hour against a 15-char zxcvbn-3 credential"* — you
distinguished the credential spaces and then declined NIST §3.2.2's cumulative cap on that basis. Applying your
auto-lift to a 10⁶ space inverts the argument: 10 failures per 20-minute cycle is **720/day**, each guess matches a
±1 window with probability ≈3/10⁶, so 1 − exp(−262 800 × 3×10⁻⁶) = **≈55% over a year**. So tier 1 mirrors your
password axis exactly (10 consecutive / 20-minute window / 20-minute auto-lift / staleness reset / derived
`locked_until`), and **tier 2 adds a monotonic 100-failure cumulative cap** that disables the factor until admin
reset — which, because reset deletes the secret and forces re-enrolment, *is* NIST's required rebinding. Residual
**0.03%**.

Three notes that must travel with those figures. The 55% is **conditional on prior password compromise**, because
the verification endpoint sits behind an authenticated session and the username is taken from the `SecurityContext`
rather than the body — that provider-side precondition, not `shouldPerformMfa`, is what makes every guess cost a
password. NIST's cap is verbatim "**consecutive**", so tier 2's deliberate non-reset on success is **stricter than
the SHALL**, which §3.2.2's "agencies MAY impose lower limits" permits. And **your per-IP throttle never engages on
the guess rate**: at tier 1's pace a single source sits at 0.5/min against its 20/min budget, so tier 1 is the sole
rate ceiling and the cap is necessary rather than redundant. The throttle's remaining job there is unauthenticated
noise the precondition rejects.

**3. Your three cross-authenticator rules stand, with one addition.** Counters stay independent and neither resets
the other — but **if both are simultaneously over threshold, both lock**, which satisfies NIST §3.2.2's
multi-authenticator SHALL ("both authenticators SHALL be disabled") with one predicate and creates no single-axis
lever. Your declining of that SHALL is superseded. Tier 1 **does** reset on its own success (NIST's SHOULD), subject
to the AAL ceiling; tier 2 does not.

**4. Your listener discrimination debt is discharged in code, and it was real.** Both factor filters publish
`InteractiveAuthenticationSuccessEvent` from `successfulAuthentication`, so the listener discriminates on the
`Authentication` type or the source filter, not the event type. Also note `unsuccessfulAuthentication` calls
`clearContext()` **before** the failure handler, so the handler cannot read who failed — the username is captured
during `attemptAuthentication` for counter attribution. Because the repository is written only on success, clearing
the holder does not end the session, so a failed TOTP attempt leaves the password authentication intact in the store.

**5. Lock ordering extended and your fail-open rule deliberately inverted on this path.** Ordering becomes **user
rows → TOTP rows → session rows**, H2 `LOCK_TIMEOUT` pinned at 1 s as you have it. But the TOTP path **does not fail
open**: your password axis loses a count on a lock timeout, whereas here the lock is held across
load → decrypt → compare → write and is what makes replay rejection atomic (ASVS 6.5.1 (L2), RFC 6238 §5.2 MUST
NOT). A lock timeout on this path is a failed authentication, not a pass.

**6. Escalating backoff declined again, on your grounds.** NIST offers it as a **MAY** framed as anti-lockout rather
than as strengthening the throttle, and your thread-pool-exhaustion argument applies unchanged. Tier 2 now does the
work it would have done. Your ASVS **6.1.1 (L1)** documentation obligation gains one paragraph — and note 6.1.1's
text also names **anti-automation**, not only rate limiting and adaptive response.

**7. Your unlock endpoint grows.** `POST /api/admin/users/{uuid}/unlock` now clears the TOTP **tier-1** lock as well
as your three password columns — one endpoint, both axes, rather than a second endpoint nobody remembers. It does
not clear tier 2, which is reserved for rebinding. It still touches no bucket.

---

## Amendment from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) amends three things here. None reopens
this ticket: under the rule ticket 13 recorded — *does the amendment weaken a compensating control
standing in for a declined `SHALL`?* — all three either strengthen this ticket's compensating control
or correct a citation.

**1. `source.ip.hash` is prohibited as a field name; use `source.ip_hash`.** ECS types `source.ip` as
`ip` (core) and `Log_Schema.md` types it `string`. Either way a sub-field forces `source.ip` to become
an object where every other producer on the shared platform emits a scalar — a mapping conflict, i.e.
dropped documents, which is the cross-service breakage the closed enum exists to prevent. The sibling
spelling `source.ip_hash` cannot collide. §12's field list is otherwise unchanged.

**Rotation is now constrained.** §12 (via ticket 24) recorded rotation as "freely — correlation need
not span a rotation". Qualified: rotation **breaks source correlation across the boundary**, so the
interval is pinned at **≥ the investigation window**, or the previous key is retained for verification.
A key rotating faster than the window destroys the only thing the field exists for, and the failure is
invisible because every row still carries a hash.

**2. The failed-login row now carries `user.id` when the account resolves.** §12's "the per-account
axis logs no key at all" is correct for the **429** row and is preserved; it is amended only for the
**failed-login** row. Authority is the Standalone standard §3.4 Privacy clause, which mandates
`user.id` and carves out only entry points where the UUID is unresolved. The decisive argument is that
ticket 06 §3 already routes the internal failure reason — wrong password, unknown user, locked,
disabled, grace-expired — to the audit log at WARN, so the enumeration protection this ticket was
preserving had already been spent, and withholding the UUID was cost without benefit.

**Three rows, one change**, and the boundary is principled rather than chosen:

- **Failed login** — changes; `user.id` present when resolved, absent when not.
- **Lockout transition** — unchanged; already carried `user.id`, and it is the row §3.4's Log Levels
  parenthetical grammatically attaches to. The UUID is resolved with certainty because this ticket's
  listener holds the row lock by primary key at the transition.
- **Per-account 429** — unchanged and **keyless by construction, not by policy**. This ticket placed the
  limiter in the `AuthenticationConverter`, throwing before `authenticationManager.authenticate`, so no
  repository lookup has happened and no UUID exists at that call site. It is therefore covered by the
  *same* Privacy carve-out — one principled reason, not two competing policies.

> **Invariant added here because this is where it will be violated: do not add a repository lookup to
> the converter to make the two rows consistent.** It would put an existence check in front of the
> framework's timing mitigation (ticket 06), reintroduce the CVE-2026-22746 shape, and destroy the
> guarantee this ticket bought by placement. The asymmetry is the control.

**3. §9's detection commitment is computable as written, and its NIST framing is corrected.** The
50-failures-in-24-hours alert was **not computable** from the stream §12 prescribed, which mattered
because it is the sole compensating control for the §3.2.2 deviation and lm-16 is already an
unacknowledged IM8 FAIL. Amendment 2 makes it computable **verbatim** — an earlier proposal to restate
it as "≥10 lockout transitions in 24 hours" was dropped precisely because it would have missed the
attacker §9 already admits it cannot stop: the paced attacker who fails four times, waits out the
staleness reset, and never locks.

Corrected against the primary source (`pages.nist.gov/800-63-4/sp800-63b/authenticators/`, Rate
Limiting (Throttling)), for ticket 17 to carry:

- The `SHALL` limits **consecutive** failures on one account to no more than 100 **by disabling that
  authenticator**, and disabled authenticators **SHALL rebind**.
- Reset-on-success is a **SHOULD** and is the only sanctioned reset. There is **no time-based reset**,
  so the 20-minute staleness window means the consecutive counter can never approach 100 and the
  `SHALL` cannot be met by that counter at all. This is sharper than "we implement no cumulative
  ceiling".
- **The deviation is the remedy, not the ceiling.** NIST states 100 is an upper bound agencies MAY
  lower; locking at 5 *is* a lower consecutive limit. What we omit is disabling and requiring rebinding.
- NIST's stated rationale for choosing 100 is balancing guess-likelihood against "the potential need
  for account recovery when the limit is exceeded" — **this ticket's auto-lift argument in NIST's own
  words**, which strengthens the deferral. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*
- **Progressive delays are additions, not alternatives.** NIST lists bot-detection challenges, an
  increasing wait after failures (30 seconds up to an hour) and risk-based signals as techniques to
  reduce *the chance an attacker locks out the legitimate claimant*. §9's parenthetical that backoff
  and lockout "are alternatives, not additions" is wrong, and progressive delay would have partly *Consolidated into the register (ticket 33): R-LCK-006. Amend the table by ID, not this list.*
  addressed the malicious-lockout residual. Recorded as an unadopted NIST addition rather than
  reopening the decision. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*

**4. A new detection signal this ticket could not previously produce.** §4 argued the submitted-string
bucket was the only account-axis control on discovery traffic against accounts that do not exist. That
traffic is now also **visible**: with `user.id` present on resolved failures and absent on unresolved
ones, a high ratio of identity-absent failures from one `source.ip_hash` is a username-enumeration
signature. Handed to ticket 21 alongside the 50-in-24h alert.

**5. Rows this ticket implied but never named**, now in the catalogue: the **lockout-cleared** row
(§3.3 requires "unlocked" transitions and the lazy auto-lift had no producer — one row with reason
`AUTO_LIFT | ADMIN_UNLOCK | RESET_REDEMPTION`, which is also the only place the map records that three
mechanisms clear the same state), and the **per-account 429** and **per-IP 429** as two distinct rows
with distinct reason values.

---

## Reopened by ticket 21 (observability signals)

> **Discharged — see §R at the foot of this file.** This section is the reopening brief, retained because §R
> discharges it item by item and four of its nine items turned out to be forced rather than open. It is not a
> live list of work.

**This ticket declined NIST SP 800-63B-4 §3.2.2 on a misreading of §3.2.2.** Verified against the final
July 2025 publication — see
[the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md) §8,
§12, §13, §14. The cap is on **consecutive** failed attempts per (account, authenticator), not cumulative
over a window. §4 of this file's own amendment text calls it "§3.2.2's cumulative cap" and then describes
"consecutive failed attempts" in the same sentence;
[ticket 23](23-totp-enrolment-stepup-and-reset-flows.md) line 520 already recorded the correct reading.

Why this is a reopening rather than an amendment: ticket 13's rule covers amendments that *weaken a
compensating control standing in for a declined `SHALL`*. Here the declining ticket's premise about the
`SHALL` is wrong, which is a stronger trigger than the rule anticipates. The rule should gain this case.

**Two of the three arguments for declining do not survive the correction.**

- **Calibration** (~12 guesses/hour, ~105,000/year against a 15-character zxcvbn-3 credential) answers a
  question the corrected reading does not ask. You do not need a strength argument to decline a control that
  costs one integer and a flag — §4.4 describes the obligation as exactly "state information on recent failed
  authentication attempts". And §10's own observation that "100 guesses is a six-digit-OTP number" *is*
  NIST's stated rationale, verbatim: the limit "was chosen to balance the likelihood of a correct guess
  (e.g., 100 attempts against a six-digit decimal OTP authenticator output) versus the potential need for
  account recovery". What that licenses is **setting the limit lower** under §3.2.2's explicit
  "The limit of 100 attempts is an upper bound, and agencies MAY impose lower limits" — not declining the cap.
- **"Permanent disable is an unauthenticated DoS"** overstates the requirement. Disabled authenticators
  SHALL rebind per §4.1, and for a password with no other authenticator the route is §4.2 account recovery —
  which [ticket 10](10-credential-flows.md) already built, and which already clears the password lockout on
  redemption. So the rebinding channel NIST requires exists on this map today. What remains is friction, and
  friction is precisely the account-recovery burden NIST weighed and accepted when it chose 100.
- **Failure mode** survives in narrowed form and is now a threshold-calibration argument rather than a
  reason to decline.

### What the reopened ticket owes

1. **A disable threshold ≤ 100 consecutive failures**, chosen rather than inherited, under the explicit MAY.
   §11's WSTG bands and the calibration arithmetic finally do useful work here — licensing a number below
   NIST's, which is the move that was available all along.
2. **Whether attempts during an active tier-1 lockout increment the cap counter.** This is the whole
   calibration and it is one decision: if they do not, the counter advances at 5 per 20-minute window and 100
   takes ~6.5 hours of sustained attack; if they do, the cap is reachable in minutes and becomes a cheap
   targeted DoS. NIST's MAY on lower limits is meaningless until this is settled, because the limit and the
   increment rule together determine time-to-disable.
3. **Its own integer, and this is forced rather than chosen.** The cap cannot share the existing lockout
   counter, because that counter resets on window expiry (§6's "one window value fixes two bugs") and a cap
   built on it could never reach 100 — unreachable dead code, the **third** occurrence on this map of the
   exact defect ticket 02 found in the standard. NIST independently sanctions no time-based reset: reset on
   success is a SHOULD and is the only sanctioned reset. One new column on the user row, taken under the same
   pessimistic lock §7 already holds, so no new contention and no change to the lock ordering.
4. **The disable must not be `enabled = false`.** That collides with
   [ticket 11](11-admin-module-role-model-and-bootstrap.md)'s admin enable/disable: an admin re-enable would
   clear a NIST cap, and the user list could not distinguish "disabled by admin" from "disabled by cap".
   Ticket 23's tier 2 is the precedent — its own state, cleared only by rebinding.
5. **The timing edge, which runs the opposite way to the obvious worry.** §10 already pins
   `alwaysPerformAdditionalChecksOnUser = true`, and verification confirms that with it true
   `performPreCheck` still runs `PasswordEncoder.matches` against the real stored hash before rethrowing the
   pre-check exception — so a capped account pays one BCrypt-12 verify, same as a wrong password, and
   ticket 06's Rule 2 already forbids a fast path. The uncovered edge is `UserCache`: with a non-null cache
   the pre-check-failure path re-runs `retrieveUser` **and** `performPreCheck`, paying **two** verifies, so a
   capped account would answer measurably *slower*. Owes a negative assertion that no `UserCache` bean
   exists, which nothing on this map has stated.
6. **The two thresholds are one mechanism.** Ticket 21 needs an alert threshold below the disable threshold,
   both emitted once per transition off this same integer per row 3's idiom. Ticket 09's "50 failed logins"
   survives as the alert threshold; the 24-hour window it was specified against does not, and is not needed.
   Under the consecutive reading with reset-on-success the alert is **better** calibrated, not merely cheaper:
   a forgetful user's successes reset it, while the paced attacker who never succeeds is unaffected.
7. **§3.2.2's multi-authenticator sentence, as an ADR rather than a citation.** "If more than one
   authenticator is involved with an excessive number of authentication attempts … both authenticators SHALL
   be disabled." The trigger is an episode involving more than one authenticator, not independent exhaustion,
   and the text is **silent** on staged flows. The narrow reading — stage-2 failures involved a password that
   *succeeded*, so the password authenticator did not fail — is the stronger one on the text's own words
   ("failed authentication attempts using a specific authenticator"), but it must be recorded as an
   interpretation. Under the wide reading, ticket 23's tier 2 firing would force disabling the password too
   and escalate straight to §4.2 recovery.
8. **§6's per-account failed-to-successful ratio is not subsumed by the cap.** Reset-on-success means the cap
   is structurally blind to a compromised-but-active account, so the ratio covers a shape the cap cannot.
   Note it is a *different* ratio from ticket 13's commitment 2, which is per `source.ip_hash` and is an
   enumeration signature; only the per-account one is affected by reset-on-success.
9. **The 429-expressed progressive delay is declined with no reason stated anywhere.** §11 correctly retired
   the "alternatives, not additions" framing and confined the thread-pool-exhaustion argument to the
   **sleep-based** form, noting that "the `429`-expressed form does not have that problem, which is why only
   that form was ever on the table" — and then never gave a reason to decline that form. Ticket 13 corrected
   the parenthetical again and ticket 23 §6 declined it a third time "on your grounds", but the map's summary
   line compresses the whole decline to "thread-pool exhaustion", which argues against a form nobody
   proposed. NIST names it verbatim as a MAY — a wait "that increases as the subscriber account approaches
   its maximum allowance for consecutive failed attempts (e.g., 30 seconds up to an hour)" — framed as an
   *additional* technique for reducing attacker-induced lockout of the legitimate claimant, not as an
   alternative to the cap. A `Retry-After` response costs no thread time. Either decline it on a stated
   reason or take it; both are defensible, silence is not.

### One thing that is not owed

**The TOTP axis is already compliant, so this is one reopening rather than two.** NIST §3.1.4.2 puts a
six-digit OTP verifier on the `SHALL` branch of the same requirement ("SHOULD implement or, if the
authenticator output is less than 64 bits in length, SHALL implement … as described in Sec. 3.2.2"), and
ticket 23's tier 2 implements it: 100 cumulative failures, disable, cleared only by rebinding, deliberately
stricter than the consecutive reading it cites correctly. The same clause family produced a compliant
implementation on the admin factor and a decline on the password, from the same misreading — and the axis
that got it right is the one this ticket never touched. Copy it.

### Two further inputs from ticket 21, beyond the nine items above

**1. The per-IP budget is now load-bearing for disk sizing.** Ticket 13's rows 5 and 6 carried no
per-transition annotation, so as catalogued each rejected request wrote an audit row — meaning this ticket's
limiter bounded the *work* a request causes but not the *logging* a rejection causes, and audit volume was set
by the attacker's ingress rate. Amended to emit once per bucket-exhaustion transition, plus a per-window
distinct-source cap with the truncation recorded (row 46), because transition-keying alone does not bound the
per-IP axis: **`maximumSize(10_000)` caps memory, not transitions** — eviction only makes a source spend
another budget's worth of requests to breach again, which is already counted. Transitions per window are
`min(distinct sources, requests_in_window / per_IP_budget)` with distinct sources attacker-chosen.

The consequence for this ticket: **the 60/min per-IP budget is now an input to
`management.health.diskspace.threshold`**, because `daily_audit_volume` is computed from it. Changing the
budget changes the disk threshold. Filed under the map's mechanism/constants seam rule as its third instance,
not as a one-off note.

**2. `.recordStats()` is required on the bucket caches, and this is not optional instrumentation.** Ticket 21
wants gauges on both Caffeine bucket maps as the saturation signal for this limiter. Cache auto-instrumentation
does **not** reach them — it is `@ConditionalOnBean(CacheManager.class)`, iterates
`CacheManager.getCacheNames()`, and `CaffeineCacheMeterBinderProvider` is typed to Spring's `CaffeineCache`
wrapper, so §on's hand-built `Caffeine<String, Bucket>` receives nothing. And without `.recordStats()` at build
time, `CaffeineCacheMetrics` registers **only `cache.size`** and every other statistic reads zero;
`monitor(...)` returns the cache unwrapped and cannot retrofit it. So `recordStats()` goes on the builders here,
with explicit `CaffeineCacheMetrics.monitor(registry, cache, name)` registration, and the test asserts
`cache.gets` is non-zero after exercising the limiter rather than trusting the bind-time WARN — a WARN in a log
nobody reads is not a control. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-001. Amend the table by ID, not this list.*

Also confirmed for §10's benefit: `alwaysPerformAdditionalChecksOnUser = true` does what this ticket claimed.
On Spring Security 7.1.x, `performPreCheck` runs `PasswordEncoder.matches` against the real stored hash even
when the pre-authentication checks fail, swallows that exception and rethrows the pre-check one — so a locked
or disabled account pays one BCrypt-12 verify, the same as a wrong password. The uncovered edge runs the other
way and is item 5 above: a non-null `UserCache` makes the pre-check-failure path re-run `retrieveUser` **and**
`performPreCheck`, paying **two** verifies, so the account would answer measurably *slower*.

---

## §R — Resolution of the ticket 21 reopening

**The NIST §3.2.2 cap is implemented at 100 consecutive failures with its own counter, its own state and its own
pre-authentication refusal; the declined `429` progressive delay returns as an escalating lockout duration; and
the correction exposed a single-source mass permanent-lockout primitive that the cap creates and that a third
limiter axis now bounds.** ADR 3 flips from a declined `SHALL` to an implemented one, which retires the only
deviation on this map whose compensating control was itself a known gap.

Read with [§8, §12, §13, §14, §18, §19 and §20 of the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md).
Five of this resolution's load-bearing facts are there and are not restated here.

### R.1 The counting rule, and why it diverges from ticket 23 rather than copying it

**Consecutive, reset on password-authenticator success.** The reopening said "copy ticket 23"; copying would have
been wrong. Ticket 23 chose *cumulative, never reset* deliberately, because a legitimate admin's morning login
would otherwise zero the counter and its 55%-per-year arithmetic returns. That reasoning is correct for a
freshly-bound, admin-only, cheaply-rebindable secret in a 10⁶ space. **It is wrong for a credential held by every
account for the life of that account**, where cumulative counting fires as an availability failure with no
attacker present. So the two axes differ on purpose, and the asymmetry is recorded because a reviewer comparing
them will otherwise read an inconsistency.

NIST supports the reset more strongly than the reopening claimed, and in a way that settles the mechanism rather
than just permitting it. §3.2.2 carries **two** SHOULDs — disregard previous failed attempts "for the
authenticators used in the successful authentication", and reset the retry count "of the authenticators that were
used" — so **scoping the reset to the authenticator involved is NIST's own requirement, not our inference from
the AAL ceiling.** The AAL clause then permits it here: the maximum AAL of the authenticator being reset SHALL not
exceed the AAL of the session resetting it, and a password is the lowest-AAL authenticator, so a stage-1
password-only success is a sufficient basis. The same clause independently forbids resetting the *TOTP* counter
from an AAL1 session, which is what ticket 23's "subject to the AAL ceiling" was reaching for.

**No new mechanism is needed, and a drafted one was withdrawn.** An earlier draft of this resolution held that
`InteractiveAuthenticationSuccessEvent` fires only after stage 2, so an admin with a locked factor could never
reset their password counter. **That is false**: `AbstractAuthenticationProcessingFilter.successfulAuthentication`
publishes `new InteractiveAuthenticationSuccessEvent(authResult, this.getClass())` on *every* successful filter
authentication, which is why §13 described the hole as "a successful factor step would reset the password
counter" — a hole that only exists because both stages publish. So the cap counter simply rides §13's existing
discrimination on `Authentication` type or source filter. Recorded because the false premise would have added a
control to fix a problem that does not exist.

### R.2 The reachability finding that outranks the threshold, and the arithmetic identity behind it

The reopening narrowed §9's "failure mode" argument to threshold calibration. **It survives at full strength,
because what bites is reachability, not the number.**

**The recovery channel §R.3 depends on does not exist outside `dev`.** Ticket 13 §13 confines the stubbed
`EmailService`'s link to a separate non-audit logger emitting only under the `dev` profile, enforced three ways —
a prohibited-configuration entry in ticket 24's refresh-phase validator, an `ApplicationReadyEvent` check reading
the effective level through `LoggingSystem.getLoggerConfiguration(...)` to catch `LOGGING_LEVEL_…=DEBUG`, and
`/actuator/loggers` absent or read-only. So outside `dev` a reset request produces **no deliverable artefact at
all**: not emailed, not logged, not returned. (An earlier draft of this resolution claimed the logged link *was*
the recovery channel, having confused it with ticket 10's prohibition on logging the **admin-issuance** token. *Consolidated into the register (ticket 33): R-CRED-021. Amend the table by ID, not this list.*
Corrected by reading ticket 13.)

**And the attack is parallel.** Advancing one account costs 5 failures per lock cycle and **100 requests total**.
Against the 60-burst / 1-per-second per-IP budget — 3600 requests/hour — the throughput identity is:

> **permanent disables per hour = per-IP budget ÷ requests-per-disable = 3600 ÷ 100 = 36**

invariant under the escalation ladder, because the ladder changes *when* the 100 requests land and not how many
there are: 240 tracks × 6.7 h and 540 tracks × 15 h are both 36/hour. Neither existing limiter engages on any
track — each is 240× inside the per-IP budget and **40× inside** the per-account bucket (600/hour against
15/hour). Discovery is supplied by the application: ticket 10 deliberately made username conflicts **specific**
(`USERNAME_UNAVAILABLE`), so 240 usernames are *confirmed*, not guessed, in ~48 minutes at the 5/min registration
budget. Ticket 10's decision was argued correctly and is not revisited; it is named because it is the discovery
step.

So §10's 240× headroom finding is this same arithmetic. It was not load-bearing when the outcome was 20 minutes.
**It is the headline now: roughly 100 unauthenticated requests permanently disable an administrative surface, and
one host can sustain 36 such disables per hour.**

**This retroactively settles the threshold better than the MAY does.** Requests-per-disable *is* the threshold, so
halving it to 50 would **double** the primitive's throughput to 72/hour. **100 is the throughput-minimising choice
inside the `SHALL`**, not merely the compliant one — and it maximises the defender's lead time between the alert
at 50 and the irreversible state. Item 1 discharged.

### R.3 What clears it, and the entrypoint that makes the cap enforceable outside `dev`

**Cleared only by rebinding: reset-token redemption, or an operator-invoked rebinding runner.** Admin token
*issuance* does not clear it, which keeps ADR 8 intact; ticket 11's `unlock` endpoint does not clear it either.

The principle is **credential destruction plus rebinding**, not "only self-service clears a cap" — ticket 23's
tier 2 is cleared by an *admin* action (`DELETE .../totp`) that destroys the secret and forces re-enrolment. An
earlier draft mis-stated the precedent as barring administrative clearing, which would have foreclosed the
break-glass path the standard itself contemplates.

**A gate on "is recovery working?" was proposed and rejected.** §3.2.2's recovery-burden sentence is the rationale
for choosing 100, not a condition on the requirement; the `SHALL` is unconditional and §4.2 recovery is the
route, not a precondition. Worse, the condition would be **false in exactly this deployment**, so gating
enforcement on it means the cap never engages — ADR 3 again with better wording, and ticket 18 would be right to
grade it so.

**So the recovery channel is built instead of the control being weakened.** A command-line runner
(`--rebind=<username>`) invalidates the hash, mints a single-use credential token through ticket 10's existing
machinery, and prints it once. ASVS **6.4.6 (L3)** is the shape — the operator initiates and cannot choose the
password — and **6.4.1 (L1)** is the binding one, because it forecloses the "operator types a temporary password"
variant someone will propose as simpler. Four constraints, each a silent failure otherwise:

1. **The token prints to `System.out`, never through the logging system.** Routed through a logger it lands in
   whatever appender the profile configures — a fourth door into precisely what ticket 13 spent three controls
   closing. Assertion owed on ticket 13's absolute negative list *and* as a ticket 24 prohibited-configuration
   entry.
2. **Explicit scope** (`password` | `totp` | `both`). An admin can hold both states, and an implicit default
   produces a half-recovery that looks complete.
3. **A batch form**, because §R.2's event is 240 accounts and a one-at-a-time procedure fails exactly when
   invoked.
4. **An audit row with its own reason and a ticket 21 alert.** It is the only privileged path with no HTTP
   authentication in front of it, so both readings — genuine recovery, or someone with shell access — are worth
   waking up for.

Deploy-level access to the running system is a **strictly higher bar than admin HTTP access**, so ticket 25 owes
this as an *ownership* statement and not only a procedure: if the sole admin holds no shell and the platform team
is unreachable, the recovery exists on paper. *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*

### R.4 State and placement: pre-authentication, or the refusal becomes a password oracle

**Two new columns on the user row, under the pessimistic lock §3 already holds** —
`consecutive_failures_since_success` and `password_disabled_at`. Named at length on purpose:
`failed_login_attempts` already exists and is *windowed*, and two similarly-named counters on one row is how the
wrong one gets read. Its own integer is forced, not chosen: the windowed counter resets on staleness, so a cap
built on it could never reach 100 — the third occurrence on this map of ticket 02's unreachable-dead-code defect.
Items 2 and 3 discharged, item 2 structurally: attempts during an active lockout raise `LockedException`, a
different event, so they cannot advance the cap, and §6 already records that we cannot even *know* whether such
an attempt carried the right password.

**Not `enabled = false`** (item 4): it would collide with ticket 11's admin enable/disable, letting a re-enable
clear a NIST cap and making "disabled by admin" indistinguishable from "disabled by cap" in the user list.

**The refusal is a custom `UserDetailsChecker` composed into `preAuthenticationChecks`, throwing our own
`AuthenticationException` subclass** — and the slot choice is the decision, not an implementation detail.
`DefaultPostAuthenticationChecks`, which holds `isCredentialsNonExpired`, runs **only** when
`additionalAuthenticationChecks` matched, so **a post-authentication refusal fires if and only if the submitted
password was correct.** Hosting the cap there would emit `authenticator-disabled` exclusively on correct guesses:
a password-confirmation oracle in the audit stream, on the one account an attacker has already spent 100 guesses
on, and ticket 06's wire-uniformity rule does not reach it because the channel is the log.

Three consequences of the slot:

- **Timing is inherited, not built.** With `alwaysPerformAdditionalChecksOnUser = true`, `performPreCheck` still
  runs `PasswordEncoder.matches` against the real stored hash before rethrowing, so a capped account pays one
  BCrypt-12 verify exactly like a wrong password. A check moved earlier — into the filter or converter — would be
  *fast* and therefore detectable, which ASVS **6.3.8 (L3)** names explicitly ("different response times"). Owes
  a **timing** test, not only the existing flag assertion. Item 5 discharged, together with the uncovered edge
  running the other way: a non-null `UserCache` makes the pre-check-failure path re-run `retrieveUser` **and**
  `performPreCheck`, paying **two** verifies, so a negative assertion that no `UserCache` bean exists is owed.
  `private UserCache userCache = new NullUserCache()` is the field initialiser, so the assertion guards a bean
  someone adds later.
- **A custom exception publishes no event**, because `DefaultAuthenticationEventPublisher` resolves by exact class
  name and falls back to `defaultAuthenticationFailureEventConstructor`, null unless set. That is the behaviour we
  want — a refusal must increment nothing — but it is **one configuration away from breaking**, so a negative
  assertion that no default failure event is configured is owed. Subclassing a mapped exception would not inherit
  its mapping either.
- **The audit row is emitted at the checker**, not from the listener, since no event reaches one. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-003, T-LCK-009. Amend the table by ID, not this list.*

**A live defect in ticket 11 follows from the same fact and is the reopen trigger for it:** the 30-day
`credentialIssuedAt` expiry is implemented on `isCredentialsNonExpired()`, so it already has this oracle. Ticket
13's "the enumeration protection was already spent" argument does not cover it — and does not merely omit it.
**Ticket 13's own list of already-routed reasons contains `grace-expired`**, so it was swept into an *enumeration*
argument when it leaks something strictly worse: password correctness rather than account state. *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*

### R.5 The escalating delay, taken — as duration, not as `Retry-After`

§11 declined progressive backoff on thread-pool exhaustion, confined that argument to the **sleep-based** form,
conceded the `429` form "does not have that problem", and then gave no reason to decline the `429` form. Item 9
said silence was not an option. **It is taken, and the decline is reversed with its cause.**

An earlier draft declined it again on subsumption by the auto-lift, claiming the flat 20-minute lock *is* NIST's
technique. **That was wrong**: the technique is defined by a wait that *increases* as the account approaches its
maximum. The honest framing is arithmetic — amortised pacing between the lock threshold and the cap is 20 minutes
per 5 attempts, 240 s/attempt, inside NIST's 30 s-to-1 h band — and the decline does not survive the residual
becoming permanent and, outside `dev`, unrecoverable. Of NIST's three additional techniques, bot detection is out
of scope and risk-based signals need a baseline this application can never learn, so **technique (2) is the only
one implementable at all**.

**Expressed as escalating lockout duration, not as `Retry-After`.** An escalation derived from a per-account
counter would put account state in a response header, and §1's whole achievement was making the per-account axis
indistinguishable for an unknown username — ticket 06's uniformity rule covers status, body and timing but
**not headers**. A third party probing a victim's username would read a long delay and learn both that the
account exists and that it is under attack: ASVS 6.3.8 territory, undoing what placement bought. Escalating
`locked_until` leaks nothing, because the response during and after a lock is the same uniform 401 either way and
only the owner can observe the difference — which is the friction the technique is made of. It reuses a derived
column, holds no thread, and adds no state.

**Ladder: 20 minutes for the first five cycles, 40 for the next five, 60 thereafter.** 5×20 + 5×40 + 10×60 = **900
minutes** to the cap; the alert at 50 fires at 300 minutes, leaving **~10 hours** of lead time instead of 3.3.
Read off `consecutive_failures_since_success`, since the windowed counter resets. Property keys and their binding
test are owed here per the map's mechanism/constants seam rule. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-010. Amend the table by ID, not this list.*

Three costs recorded rather than found later. The 60-minute rung sits **outside** WSTG-ATHN-03's 5-to-30 band, so
NIST's hour is the citation and WSTG's band is descriptive. §10's malicious-lockout residual becomes "20 minutes
initially, up to an hour under sustained attack", and the three arguments resting on auto-lift must be re-read
against an hour — **ticket 11's survives, because an hour is still delayed rather than locked out.** And the
ladder is **latency-increasing and throughput-neutral** per §R.2: the in-flight set grows, which makes the
signature *louder*, not quieter. An earlier draft priced widening as a cost; there is no such cost and it must not
appear in the ADR.

**Item 6 discharged**: the two thresholds are one mechanism off this integer — alert at **50**, disable at **100**,
each emitted once per transition. The 24-hour window is dropped and not replaced. Dropping it means the alert
carries **no rate information**, so 50 failures in 5 hours and 50 across two years emit identical rows; ticket 13
already emits a timestamped per-attempt failed-login row, so **ticket 21 derives the rate from the stream** —
stated explicitly so nobody adds a `consecutive_failures_started_at` column and makes it a fourth counter.

**And ticket 11's bootstrap does not reopen, but ADR 13 narrows.** The cap adds a **third** fresh-install path
(seed → login → forced change → enrol → factor granted; before that completes the sole admin has no TOTP, no
second admin and no reset channel), and auto-expiry cannot close it. R.3's entrypoint closes it instead, so
"one admin, not two" survives and its credential-hygiene argument never has to lose to an availability one.
ADR 13's wording is amended: **auto-expiry closes the lockout path; the operator rebinding entrypoint closes the
cap path.** The two-admin invariant is separately amended — it counts *enrolment*, not authenticability, and per
ticket 11's own "the guard is decrement-safe, not phantom-safe" it cannot see the authentication path at all, so
it is **monitorable only**, as ticket 21's zero-authenticable-admins signal. One `authenticable` predicate spans
five terms across two tables (enabled, activated, not password-disabled, TOTP row present, not tier-2 disabled);
the guard reads three today and the signal needs five, so it is defined **once** with two readers or they drift
silently. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*

### R.6 Bounding the width: a third axis, and the three formulations that do not work

Everything above bounds *time*. Nothing bounded **width**, and at 36 disables/hour that is the finding, so a third
axis is added — the only lever on throughput other than the per-IP budget, which is now pinned for three other
reasons.

**Axis: distinct accounts this source has driven into tier-1 lockout, within a rolling hour, `k ≈ 5`.** Three
rejected formulations, recorded because each is the obvious one:

- **Distinct accounts *attempted*** — a hundred employees behind one NAT are a hundred distinct accounts on an
  ordinary morning, so it trips on every shared egress before it meets an attacker. Keying on *lockouts* inverts
  that: legitimate NAT traffic produces very few, the primitive produces exactly one per track per cycle, which
  is what lets `k` sit at 5/hour with almost no false-positive exposure and is the only version that survives
  §5's NAT residual.
- **Row 46's mechanism with the key reversed** — row 46 counts distinct sources as a truncated *metric*; this
  needs per-source **membership** state to answer "how many distinct accounts", which Bucket4j does not provide.
  It is a bounded per-source set (a small Caffeine cache per source, own `maximumSize`, `expireAfterWrite` at the
  window), so §8's eviction discipline applies twice over and **eviction here is a bypass, not a memory saving**.
  One new data structure, priced as one — pricing it as a table row is how it gets half-built.
- **A row in §5's budget table** — burst and sustained are token-bucket columns and this is a cardinality limit.
  Given its own line form in §5 for that reason.

**Placement is free: the converter**, which holds both the source and the submitted username, sits beside the
existing per-account bucket, reuses the 429 branch, and still precedes BCrypt. **Recording is not free**: the
lockout *transition* is only knowable in §3's listener, which has no `HttpServletRequest` — see R.7.

**Honest ceiling, stated in these terms or the 6.1.1 argument fails.** It is keyed on attacker-chosen source, so
**IP rotation restores full throughput** — the App Standard's original objection to per-IP limiting, already a
recorded property of this axis. At `k`=5/hour it takes 36/hour to 5/hour per source: a **7× reduction and a much
louder signature, not a fix.** It also false-positives on a shared NAT carrying credential-stuffing-shaped *Consolidated into the register (ticket 33): R-RL-002. Amend the table by ID, not this list.*
legitimate traffic, which is the residual the login row already accepts. *Consolidated into the register (ticket 33): R-RL-003. Amend the table by ID, not this list.*

### R.7 The dependency both new readers share, and it fails in opposite directions

**The converter must set `WebAuthenticationDetails`, with an assertion.** Under ticket 05's Route C the login
filter's `attemptAuthentication` is the base class's concrete three-statement body, which sets no details;
`authenticationDetailsSource` is read only by subclasses that build their own token, such as
`UsernamePasswordAuthenticationFilter`, which we do not use. So `getDetails()` is `null` by default, and it is the
only in-band carrier of the client IP into an event listener. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-005. Amend the table by ID, not this list.*

Two readers, failing opposite ways, which is why this is a control and not instrumentation:

- **§12 fails open, quietly** — every audit row carries one key, and it still looks right because a hash is
  present.
- **R.6's limiter fails closed, loudly** — every lockout in the system attributes to one key, the cardinality
  threshold trips at once, and the limiter `429`s everyone. **A fail-closed inversion of a control added to bound a
  fail-open one.**

The split is safe *only* because `WebAuthenticationDetails` reads `getRemoteAddr()`, the same call the early
filter resolves through, so both sides pass through `RemoteIpValve` under §7's `source=proxy` and the keys cannot
diverge. One resolution path, **two representations**: R.6 keys on the resolved raw address, §12 logs the
domain-prefixed HMAC. Neither HMAC the cache key nor log the raw one.

### R.8 Session termination, and the transaction that is not available

**ASVS 7.4.2 (L1)** requires terminating all active sessions when an account is disabled — binding at our declared
level, on the substance argument that the password is the only stage-1 authenticator, so disabling it disables
authentication. (7.4.3 (L2) is the weaker citation and was considered: it requires *offering the user the option*
to terminate *other* sessions after a factor change, which is not an automated kill of the subject's own.)

**Atomicity is unavailable.** Spring Session 4.1.x's `JdbcHttpSessionConfiguration` builds its
`TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`, and `JdbcIndexedSessionRepository` routes `deleteById`
through it, so every session write suspends the caller's transaction and commits independently. A row lock cannot
be released early to work around it — locks release at commit or rollback, and savepoints do not drop them — so
the only choice is *before commit, inside the lock* or *after commit, outside it*. An earlier draft of this
resolution specified "lock, write, release, then kill sessions", which has no implementation.

**After commit, plus an idempotent startup reconciliation sweep.** Inline would give the fail-safe ordering, but
it demands a second pooled connection **while a row lock is held**, so under §R.2's in-flight set the pool joins
the lock graph and threads block on the pool's timeout, which H2's pinned 1-second `LOCK_TIMEOUT` does not
bound — **thread-pool exhaustion, the exact mode §11 used to decline sleep-based backoff, through a door nobody
was watching**, and §3's fail-open-on-counting would swallow it so the visible symptom is lost cap increments
under load. After-commit's unsafe half is a crash between commit and dispatch, repaired by a sweep: for every
user with `password_disabled_at` set, delete their sessions. Indexed, idempotent, and ticket 08's JDBC cleanup job
already exists to host it.

This keeps tickets 13 and 10's after-commit dispatch rule instead of inverting it, and dissolves the lock-graph
problem instead of documenting a pool size nobody owns. **The rule generalises and is simpler than the mechanism
behind it: user rows then TOTP rows inside the transaction, session rows only after commit, never inside the
lock** — followable without knowing what `REQUIRES_NEW` is.

**Ownership moves.** Ticket 08 owns the trigger list, so it owns the dispatch rule and the sweep for **all five**
triggers — its four plus this one — rather than this ticket deciding for the fifth while four inherit by accident.

### R.9 The remaining two items, and what ticket 16 is handed

**Item 7 — §3.2.2's multi-authenticator sentence, as an ADR and not a citation.** **Narrow reading**: stage-1
failures never involve the TOTP, and stage-2 failures involved a password that *succeeded*, so that authenticator
did not fail. It is the stronger reading on the text's own words ("failed authentication attempts using a
specific authenticator"), and NIST's parenthetical describes one ceremony consuming two authenticators rather
than two independent counters exhausting. Ticket 23's conjunction rule already covers the genuine simultaneous
case. The best argument for the wide reading is recorded because it is real: every tier-2 increment cost a valid
password, so 100 factor failures are 100 confirmations that the password is known.

**Middle path taken, hosted on ticket 11's forced-change flag**: a tier-2 factor disable is treated as a
credential-compromise signal that forces password rebinding at next login, and kills sessions. The flag is the
right host precisely because it is evaluated **after** a successful authentication, which §18's rule identifies as
the only thing the post-success slot is safe for — a reason already implied by success. Hosting it on ticket 11's
30-day expiry would rebuild R.4's oracle; an earlier draft named that mechanism. Two things travel with it: the
consequence is **deferred, not proportionate** — a tier-2-disabled admin cannot complete stage 2, so the forced
change fires only after another admin re-enrols them, and the ADR must not describe it as session-preserving
relief for someone already locked out — and the wide reading's arithmetic goes in the ADR, because combined with
ticket 23's cumulative tier 2 it guarantees a legitimate admin eventually destroys both authenticators with no
attacker present.

**Item 8 — §6's per-account failed-to-successful ratio is not subsumed.** Reset-on-success makes the cap
structurally blind to a compromised-but-active account, so the ratio covers a shape the cap cannot. It is a
*different* ratio from ticket 13's commitment 2, which is per `source.ip_hash` and is an enumeration signature;
only the per-account one is affected by reset-on-success. **The detection commitment is therefore retained, not
retired**, even though ADR 3 no longer needs a compensating control.

**Ticket 16 gets a delta of eight against §16's existing six, handed over as a delta so half of them do not go
unwritten:** 429-not-401 on the third axis; the cardinality key derived from lockout transitions rather than
attempts; `WebAuthenticationDetails` set by the converter; no default authentication-failure event configured on
the publisher; a capped account's response time matching a wrong password; the reset and redemption paths never
routing through `AuthenticationManager`; the reconciliation sweep being idempotent; and the ladder constants bound
to their properties.

**One invariant worth more than the tests:** *assert that the reset-request and redemption paths never route
through `AuthenticationManager`.* They do not today — the checker lives in the provider and §10 verified the
self-service change endpoint calls `passwordEncoder.matches()` directly — but if redemption is ever routed
through the manager, the disabled password authenticator blocks the only operation that can clear it. **A recovery
path that is self-blocking fails exactly when it is needed, and silently.** *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-006, T-RL-007, T-RL-005, T-LCK-009, T-AUTH-003, T-CRED-009, T-SES-022, T-LCK-010. Amend the table by ID, not this list.*

### R.10 Compliance, ADRs, and amendments

**ASVS 6.1.1 (L1) is re-argued rather than inherited from §10, and the verdict is conditional.** §10 called it
pass-with-note when the worst residual was 20 minutes of auto-lifting lockout; the residual is now permanent and,
outside `dev`, unrecoverable. 6.1.1 is a documentation requirement, so an honest document describing a bad
residual still passes most of it — **what fails is its final clause**, that the documentation make clear how the
controls "prevent malicious account lockout". Ticket 18's row must cite those words rather than the requirement
entire, or the finding reads as an opinion. **Pass-with-note survives only because R.3's entrypoint and R.5's
ladder both land**; without them it is an honest F. Note also that R.5 is **adaptive response**, not
anti-automation — 6.1.1 names three classes, bot detection stays declined on scope and risk-based signals need a
baseline, so this takes us from one of three to **two of three**. *Consolidated into the ADR routing (ticket 34): REJ-014 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-003. Amend the table by ID, not this list.*

**A reserved-name denylist** goes into the refresh-phase validator beside the bootstrap strength validation, in
**one set with two readers** — that validator and ticket 10's registration username validator — because reserving
only at bootstrap leaves `administrator` available to the next self-registrant. Justified as an **availability
control against R.2's targeting**, explicitly **not** as anti-enumeration: `USERNAME_UNAVAILABLE` keeps any name
confirmable, so a denylist removes only the zero-cost guess. Ticket 11's 6.3.2 (L1) rows are untouched and its
line 693 wording is the one to cite — 6.3.2 "names default accounts with default credentials, and yours has an
operator-supplied username and no default credential" — so this control cannot be justified on 6.3.2. An earlier
draft tried to.

**ADRs.** Amended: **1** (duration escalates), **2** (window and duration split, `duration ≥ window`), **6** *Consolidated into the ADR routing (ticket 34): ADR-011 (attached amendment) / ADR-012 (attached amendment). Amend by ID, not this list.*
(decline narrowed to the sleep-based form). **Reversed: 3** — cap implemented under the explicit lower-limit *Consolidated into the ADR routing (ticket 34): ADR-014 (attached amendment). Amend by ID, not this list.*
allowance, 100 chosen as throughput-minimising. **New, nine:** the consecutive/cumulative asymmetry against *Consolidated into the ADR routing (ticket 34): ADR-013. Amend by ID, not this list.*
ticket 23 with NIST's "authenticators that were used" as authority; the pre-authentication slot choice with the *Consolidated into the ADR routing (ticket 34): ADR-013. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-004. Amend the table by ID, not this list.*
post-auth oracle as its reason; own-state columns rather than `enabled = false`; the operator rebinding entrypoint *Consolidated into the ADR routing (ticket 34): REJ-019 / REJ-020. Amend by ID, not this list.*
with its four constraints and the rejected enforcement gate; the escalating ladder with its three costs; the *Consolidated into the ADR routing (ticket 34): ADR-072 / ADR-011. Amend by ID, not this list.*
per-source cardinality axis with its three rejected formulations and its IP-rotation ceiling; after-commit *Consolidated into the ADR routing (ticket 34): ADR-015. Amend by ID, not this list.*
dispatch plus reconciliation sweep, and why atomicity is unavailable; the narrow multi-authenticator reading plus *Consolidated into the ADR routing (ticket 34): ADR-039. Amend by ID, not this list.*
the middle path on ticket 11's forced-change flag; and the reserved-name denylist on availability grounds. *Consolidated into the ADR routing (ticket 34): ADR-016 / REJ-021. Amend by ID, not this list.*

**Glossary:** *Lockout* and *Disable* amended in place above, *Authenticator disable* added; plus **cap counter**,
**rebinding**, **cardinality axis**, **authenticable**, **reconciliation sweep**.

**Register entries:** the 6.1.1 conditional verdict; IP rotation defeating the cardinality axis; the shared-NAT *Consolidated into the register (ticket 33): R-LCK-003, R-RL-002. Amend the table by ID, not this list.*
false positive; recovery requiring deploy-level access; and the mass primitive's 36/hour residual after the 7× *Consolidated into the register (ticket 33): R-RL-003, R-RUN-003. Amend the table by ID, not this list.*
reduction. *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.*

**Amendments owed to nine tickets.** **08:** owns after-commit dispatch and the reconciliation sweep for all five
triggers, and the never-inside-the-lock rule. **10:** its specific username conflict is R.2's discovery step
(recorded, not revisited), and redemption now clears the cap as well as the lockout. **11:** ADR 13 narrowed, the
30-day expiry's oracle as a reopen trigger, the two-admin invariant as monitorable-only, the `authenticable` *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*
predicate, a third forced-change case, and `credentialIssuedAt` not stamped on it — following the same call
ticket 11 already made for admin create and reset. **12:** two new columns, `password_disabled_at` indexed for the
sweep, and the predicate spanning two tables. **13:** the cap-crossing and disable rows, the cardinality-axis
breach row, the `WebAuthenticationDetails` assertion on its negative list, and the `grace-expired` correction.
**16:** the eight-test delta. **21:** row 45's alert at 50 off the same integer; the cross-account signature as a
**windowed count over row-45 emissions** — no distinct-key tracking, no truncation, nothing to size, so it needs
no new state and stays out of the fog patch; the zero-authenticable-admins signal; and the operator-entrypoint
alert. **23:** session termination on the automatic tier-2 trip (its guarantee attaches to the admin `DELETE`
path only), a distinct audit reason for the automatic trip versus operator action, and the trip's own
commit-then-dispatch ordering. **24:** the prohibited-configuration entry for the entrypoint's output channel.
**25:** the entrypoint's ownership statement, the batch procedure, and the non-`dev` unrecoverability of the
password axis alongside the TOTP axis's existing break-glass.

### How this round actually worked, recorded because it is the reusable part

**Three positions were reversed, and the two that mattered most were overturned by reading this repository rather
than by a better argument.** The sole-admin recovery story rested on a channel ticket 13 had already confined to
`dev`, and "release the lock, then kill sessions" rested on a lock behaviour that does not exist. Both were found
by checking files, not by debate. The third — declining the escalating delay — fell to a fact about NIST's text.
Every one of them was a claim that *favoured the conclusion being argued*, which is the pattern the map's
verification rule already names. **Five external facts in this round were checked against primary sources and two
came back against the drafted position** (`attemptAuthentication` is concrete, not abstract, on our line; and
Spring Session's `REQUIRES_NEW`), which is why §§18–20 exist.

### Done when

The cap has a threshold, an increment rule, its own state, a refusal slot, a clearing path and a recovery channel
that exists in every profile; the escalating delay is either taken or declined with a stated reason; the mass
primitive is bounded or its residual is priced; and every one of the reopening's nine items is discharged or
withdrawn with cause. **Met.**

---

## Amendment from ticket 25 — a third throttled counter, and §R.3's runner acquires a compliance target

**1. A third counter joins the lock-ordering and calibration picture.** Ticket 25 resolved that break-glass access
restoration is NIST **§4.2 account recovery**, satisfied at AAL2 by §4.2.2.2 option 2 — an issued recovery code plus
the admin's password. §4.2.1.2 then imports throttling explicitly: "The verification of issued recovery codes SHALL
be subject to the throttling requirements in Sec. 3.2.2." So §3.2.2's cap now applies to **three** counters, and
they must be named together because this ticket owns the cap's calibration argument:

| Counter | Owner | Cleared by |
|---|---|---|
| password axis (`failed_login_attempts`) | this ticket | auto-lift at 20/40/60, or reset redemption |
| factor tiers 1 and 2 | ticket 23 | admin unlock (tier 1) / rebinding only (tier 2) |
| **issued recovery-code verification** | **ticket 25's route** | single-use redemption |

A **fourth** arrives only if ticket 19 reverses its recovery-codes deferral, since §4.2.1.1 imports the same
throttling onto saved-code verification. Recorded on ticket 19 as part of that branch.

**2. §R.3's runner now has a compliance target rather than being a permanent residual.** §R.3 built the
`--rebind=<username>` runner because outside `dev` there is no deliverable artefact at all, and ticket 25 accepted
that as the interim. What changed: **the runner already mints the right thing**, through ticket 10's machinery —
"what takes it out of class 2 is printing it to `System.out` instead of delivering it." Once a mail transport
exists, delivering that token to a confirmed recovery address makes it an **issued recovery code**, and the
access-restoration half becomes a conditional pass rather than an unenumerated gap.

Worth knowing why the console print cannot be argued as compliant in the meantime: §4.2.1.2 requires a recovery
address to be "established only after the subscriber provides the correct confirmation code", and stdout
demonstrates control of the host rather than of the account holder's channel. So the shell path satisfies **none**
of §4.2.2.1's three options and fails §4.2.2.2 on arity as well. It remains an accepted failure with **one named
cause** — no transport outside `dev` — which is the same cause as three notification SHALLs. *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*

**3. One calibration note that does not change a number.** §3.2.2's cap is "no more than 100" consecutive failed
attempts *per authenticator*, described as "an upper bound, and agencies MAY impose lower limits". That is the same
clause this ticket implemented at 100 on the password axis, and it now reads across all three counters — so the
per-counter independence this ticket established is what keeps the recovery counter from interacting with the
password cap.

---

## Amendment from ticket 28 — §R.3's runner could not execute, and its standards citation is inverted

**1. The runner as specified could never run.** H2 embedded file mode is single-writer, so a second JVM cannot open
the database while the application holds it ([verification asset §1](../research/rebinding-runner-channel-and-process-model-verification.md)).
[Ticket 28](28-out-of-band-privileged-channels.md) §2 moves it to an **offline run of the same jar** with the
application stopped. Recovery is therefore a planned outage. Every §R verdict that rested on the entrypoint
existing (ASVS 6.1.1 pass-with-note, ADR 13's cap-path closure, the "recovery channel that exists in every *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*
profile") now rests on a mechanism that can actually execute. Per ticket 28 §11, 6.1.1 is re-asserted as
**pass-with-note pending first rehearsal**, and it reverts to F if the rehearsal fails. *Consolidated into the register (ticket 33): R-LCK-003, R-RUN-002. Amend the table by ID, not this list.*

**2. §R.3's ASVS citation is inverted.** It records 6.4.1 (L1) as "the binding one, because it forecloses the
'operator types a temporary password' variant". ASVS 5.0 6.4.1 governs *system-generated* initial secrets and
says nothing about who chooses. **6.4.6 (L3)** is the requirement that forbids operator choice (asset §5). The
error made the token design look compelled at the declared L1 target. Ticket 28 takes the operator-supplied
variant and records a **scoped withdrawal** of the volunteered 6.4.6 pass on the runner path only. *Consolidated into the register (ticket 33): R-RUN-007. Amend the table by ID, not this list.*

**3. Constraints superseded.**
- **Constraint 1** ("token prints to `System.out`, never through the logging system") is superseded: no secret is
  emitted at all. *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*
- **Constraint 3** (the batch form) now has a specification: dry-run by default, a state-bound confirm digest,
  the 240 cap checked against the whole input before any write, one transaction, one outcome row. The batch form
  mints nothing.
- **Constraint 4** (the audit row) is now dry-run, intent and outcome rows carrying `labels.operator_claimed_id`,
  `process.real_user.name`, a mandatory reason, and pre- and post-operation enrolled-admin counts.
- **Constraint 2** (explicit scope) stands.

The ADR covering "the operator rebinding entrypoint with its four constraints" is **amended**, not replaced. *Consolidated into the ADR routing (ticket 34): ADR-072 (attached amendment). Amend by ID, not this list.*

**4. "Cleared only by rebinding" is unchanged.** The runner still performs credential destruction plus rebinding.
Only who supplies the new password changes.

---

## Pointer from ticket 29 (anonymous session-row growth)

- **The `/api/csrf` figure of ~450 per source is corrected to 480 unexpired / 510 present.** The budget itself is
  unchanged.
- **IPv6 source keying is graduated as [ticket 31](31-ipv6-source-keying.md).** Every per-source key on your table
  and in §R.6 uses the full address, so a single /64 makes rotation free. That ticket reopens your keys. Nothing here
  is changed in the meantime.

## Amendment from ticket 31 (IPv6 source keying)

Amended, not reopened: nothing here weakens a compensating control for a declined `SHALL` (the `13:143` test). The
cap stays implemented; what changes is how its bound is priced and enforced. Detail in
[ticket 31](31-ipv6-source-keying.md) §§2–4.

1. **Every "source IP" key in §5, §R.6 and §12 is now the source key** (31 §1): an IPv4 /32, or an IPv6 address masked
   to `app.security.client-ip.ipv6-prefix-length` (default 64). It is derived from parsed bytes by one
   `SourceKeyResolver`. `09:1295`'s "R.6 keys on the resolved raw address" is replaced: R.6 keys on the source key,
   read from `SourceKeyAuthenticationDetails`.
2. **§R.5 fencepost (`09:1217`).** 19 locks precede the 100th failure, not 20. The figures are **840 min to the cap
   (≈14 h), the alert at 260 min (≈4.3 h), and ≈9.7 h of warning**, replacing 900 / 300 / "~10 hours". In §R.2, "240
   tracks × 6.7 h and 540 tracks × 15 h" becomes **228 × 6.3 h and 504 × 14 h**. The 36/hour identity and the
   flat-duration 3.3 h are unchanged. *Consolidated into the ADR routing (ticket 34): ADR-011 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.*
3. **§R.6 honest ceiling (`09:1274-1278`) was a units error.** "36/hour to 5/hour" compared disables with distinct
   accounts locked. Per bucket the axis allows about 5 concurrent chains, so ≈0.36 disables an hour, a **≈100×
   reduction, not 7×**. The ceiling assumed one bucket per attacker. It binds only below **P ÷ k ≈ 20 source keys**,
   already cheap under IPv4 and free under IPv6 for anyone holding more than one /64. Above that, the ladder is the
   bound. The NAT false-positive sentence stands. *Consolidated into the register (ticket 33): R-RL-002, R-RL-003. Amend the table by ID, not this list.*
4. **The register entry at `09:1406`** ("the mass primitive's 36/hour residual after the 7× reduction") becomes:
   *36/hour from one unlimited source; ≈0.36/hour per limited bucket; never faster than ≈14 h per account, whatever
   the source count.* *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.*
5. **R.6's refusal is specified as reading (B), with the expiry pinned.** A full set refuses **non-member** usernames
   only. An entry's expiry runs from its **first** insertion: a re-lock checks membership and never writes, because
   Caffeine's `expireAfterWrite` resets on every put and would let five re-locked members hold an egress at 429 for a
   chain's full 14 h. Trade: both readings cost a shared NAT the same; (B) lets the attacker work its own five chains;
   the pin bounds the refusal to about an hour after the fifth first-lock. *Consolidated into the ADR routing (ticket 34): ADR-015 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-RL-003. Amend the table by ID, not this list.*
6. **The ladder gets a startup floor** (31 §3). It is derived from the lock threshold, cap, alert threshold and rungs
   (`locks_to_cap = floor((C−1)/t)`, `locks_to_alert = floor((A−1)/t)`), and fails the refresh if time-to-cap is under
   840 min or the warning is under 580. **The property keys you still owe at `09:1219-1220` are now a named owed input
   to that check.**
7. **"Recovery requires deploy-level access" stands for this build.** §R.3's reset-token route clears a disable only
   once a mail transport exists (`09:1082`). *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*

---

## Amendment from ticket 30 (sole-admin bootstrap premise)

1. **09:535–541, "the 20-minute auto-lift is now unremovable by three independent arguments".** The auto-lift is
   still unremovable, but two of the three arguments changed. It is a **20/40/60 ladder** (§R.5), not a flat 20
   minutes. Argument two ("a lock that lifts is why we need no cumulative ceiling") is **superseded**: §R built that
   ceiling as the NIST cap. Arguments one (ticket 11's bootstrap, now route 1 of ADR 13) and three (WSTG-ATHN-03's
   tier-1 precondition) stand. 09:117–122 is question framing under "Inherited from ticket 11" and is left as written.
2. **09:1235–1239, the replaced premise at its origin.** "R.3's entrypoint closes it, so 'one admin, not two'
   survives" is superseded by [ticket 30](30-sole-admin-bootstrap-premise.md) §4. Seeding one survives **conditional
   on a second enrolled admin invited before go-live**, and ADR 13 is three routes keyed on the `authenticable`
   predicate (09:1242–1245, defined at 11:816–818): auto-expiry; in-app reset when another authenticable admin exists;
   the runner, as a planned outage pending first rehearsal, when none does. A second seeded admin does not close the *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.*
   targeted-cap path, since capping is ≈14 h per account whatever the source count (item 4 of the ticket 31 amendment). *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*

---

## Amendment from ticket 16 (test plan)

This is an in-place amendment under 13:143's rule, because it weakens no compensating control. It discharges the seam-rule debt this ticket recorded at 09:1219–1220 and the one 31:186 names. It also fixes one value that was only ever written as "≈".

**1. k = 5, exactly.**
- Every derivation already uses 5: 09:1259, 09:1541, 31:87, 31:139 ("P ÷ k = 20") and 31:154.
- 31:190 and 31:448 already treat any change to k as a reopening trigger. *Consolidated into the register (ticket 33): R-RL-022. Amend the table by ID, not this list.*
- So 09:324 and 09:1253's "`k ≈ 5`" read `k = 5`.
- One terminology note, correcting this ticket only. Because §R.6's expiry pin runs from the first insertion, "per rolling hour" behaves as **a fixed hour from each member's first lock**. It is not a sliding window. *Consolidated into the ADR routing (ticket 34): ADR-015 (attached amendment). Amend by ID, not this list.*
- Ticket 31's arithmetic is unaffected. Its 5-concurrent-chains figure already reasons under the pin: an expired member re-enters on its next lock (31:151–159). The 840/580 floor does not take k as an input (31:190).

**2. Property keys.** They follow this ticket's only existing convention, `app.security.client-ip.*`. Durations use Boot `Duration` syntax. Budgets are greedy-refill, so `burst` is capacity and `refill-period` is the time per token.

| Constant | Key | Value |
|---|---|---|
| Lock threshold | `app.security.lockout.threshold` | 5 |
| Observation window | `app.security.lockout.observation-window` | 20m |
| Ladder rungs | `app.security.lockout.ladder.rungs` | 20m, 40m, 60m |
| Cycles per rung before escalating | `app.security.lockout.ladder.cycles-per-rung` | 5 |
| NIST alert threshold | `app.security.lockout.nist.alert-threshold` | 50 |
| NIST cap | `app.security.lockout.nist.cap` | 100 |
| Cardinality axis size | `app.security.rate-limit.lockout-cardinality.k` | 5 |
| Cardinality axis window | `app.security.rate-limit.lockout-cardinality.window` | 1h |
| Budget-table rows (09:309–319, plus the 09:754–756 rows) | `app.security.rate-limit.<route>.<axis>.burst` and `.refill-period` | as tabled |
| Request body cap (new, ticket 16 Q12(b)) | `app.security.request.max-body-bytes` | 16384 |

Route ids for the budget-table rows:
- `login`
- `csrf`
- `register`
- `password-reset-request`
- `password-reset-confirm`
- `register-activate`
- `profile-password`
- `mfa-totp-verification`
- `mfa-totp-enrolment-confirmation`
- `mfa-totp-enrolment`

Axis ids are `source`, `username` and `identifier`. For example, `app.security.rate-limit.login.source.burst=60` and `app.security.rate-limit.password-reset-request.identifier.refill-period=20m`.

**3. The body cap is a new control.** Ticket 16 Q12(b) settles it for Standard §5:494, where the batch half is N/A under ticket 11.
- It is enforced in the early filter, **before the Route C converter reads the body**.
- Over-limit requests get 400 `VALIDATION_FAILED`.
- The 16 KiB value is taken with headroom over the largest legitimate body. That is registration, at well under 1 KiB: a username of at most 32 characters, an email of at most 254 bytes, and no password, because the password is set at redemption.
- The value is a first-principles choice with no standards citation.
- **It counts bytes actually read, never `Content-Length`.** The filter wraps the request input stream and refuses once the running count passes the cap. A chunked request carries no `Content-Length`, and a declared length can understate the body. Neither can be trusted.
- **Filter order: after the per-IP budget filter, before the Route C converter.** An oversized request on a budgeted route still spends its per-IP token. If the cap ran first, oversized bodies would become a request class no budget meters, which is the gap ticket 26 exists to close.
- **Owed to ticket 32:** mint T-IDs for:
  - the filter-order assertion: in the assembled chain, the per-IP budget filter precedes the body-cap filter, which precedes the Route C converter;
  - a chunked over-cap body with no `Content-Length` gets 400 `VALIDATION_FAILED`;
  - an over-cap body with an understated `Content-Length` gets 400 `VALIDATION_FAILED`;
  - an over-cap request still decrements the per-IP bucket. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-012, T-RL-013, T-RL-014, T-RL-015. Amend the table by ID, not this list.*

**4. Binding tests.** Ticket 16 owns one binding test per key above, asserting the effective value off configuration, never a hard-coded number. That follows 16 §R test 8's shape rule. IDs are minted by ticket 32. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-012, T-LCK-013, T-LCK-010, T-LCK-014, T-LCK-015, T-LCK-016, T-RL-008, T-RL-009, T-RL-010, T-RL-011. Amend the table by ID, not this list.*

**5. 16:135–138 restated** in ticket 16. The "per-account 429 unreachable by pure failure traffic" line is narrowed to the **submitted-username axis**, because the third axis's 429 is reachable by design (09:482–485). *Consolidated into the register (ticket 33): R-STD-018. Amend the table by ID, not this list.*
