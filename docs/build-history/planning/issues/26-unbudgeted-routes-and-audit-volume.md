# 26 — Decide the rate-limit default for unbudgeted routes, and bound pre-routing audit volume

Type: grilling
Status: resolved
Blocked by: 09, 13, 21
Graduated: 29 (anonymous session-row growth and the unwatched H2 file)

## Question

What does the per-IP limiter do for a route that is not in its budget table — and what bounds the audit
volume produced by controls that run **before** routing, on paths no endpoint-keyed registry covers?

Graduated from [ticket 15](15-threat-model.md) as **TM-01** (High), with **TM-05**'s budget half and
**TM-10** folded in because all three size the same disk.

## Why this is a decision and not a correction

Three resolved tickets each own one third of the mechanism and none owns the composition:

- **Ticket 09** owns the limiter. Its budget table is a **route allowlist** — ten rows, twelve after ticket
  23 — and it states admin endpoints are *deliberately* unthrottled, with a good reason (an attacker who
  cannot authenticate must not be able to deny an admin their own recovery tool). What it never states is
  what happens to a route that appears in neither list.
- **Ticket 13** owns the audit catalogue, and it found this exact amplification shape once already: rows 5
  and 6 were "a log-amplification path to disk exhaustion" because volume was set by the attacker's request
  rate, and it closed that with **row 46**, a per-window distinct-source truncation cap.
- **Ticket 21** owns the arithmetic that depends on both: disk sizing `90 × daily`, and
  `management.health.diskspace.threshold` as `daily × lead_days` with `lead_days = 2`.

## The finding

Spring Security's ordering — verified, not assumed, at
[verification asset §1](../research/threat-model-external-fact-verification.md) — puts exploit protection
**before** authentication and **before** authorization. `CsrfFilter` is therefore evaluated on every
non-safe request to **every path**, including paths that match no controller, and before any authorization
decision has been reached.

So an unauthenticated client sending `POST` with no CSRF token to an arbitrary path produces:

- **audit row 13** (`access-control` / `["denied"]` / WARN / medium, reason `CSRF_MISSING | CSRF_INVALID`) —
  the row ticket 13 added precisely because the control guarding every state-changing endpoint was silent;
- carrying a `url.path` that is **raw client bytes**, because handler mapping has not happened — ticket 13's
  own statement is that the injection surface "survives only on rows emitted before handler mapping";
- on a route with **no bucket**, because the table is an allowlist.

The same shape reaches rows 12 (403 `INSUFFICIENT_ROLE`) and 14 (412 factor) through `/api/admin/**`, which
is deliberately unthrottled. All three rows sit in ticket 21's **rate-above** alert class — they are not
transition-keyed, and **row 46's cap bounds only `RATE_LIMITED_SOURCE` rows**.

Two consequences:

1. **Audit volume is attacker-set**, which is the amplification ticket 13 closed for rows 5 and 6, arriving
   through a door row 46 does not cover. Its target is a file that deliberately has **no `total-size-cap`**,
   and disk-full is §3.4's own audit-failure condition.
2. **`daily` is not a property of the workload.** Ticket 21's disk sizing and health threshold are both
   computed from it, so both are computed from a number an unauthenticated attacker controls. Ticket 21
   deferred threshold *calibration* to the deployer honestly; it did not know the input was unbounded.

## What to decide

- **The limiter's default for an unlisted route.** Default-deny with a catch-all budget, or default-allow
  with the table as an exhaustive contract? Default-deny is the obvious answer and it is not free: it puts a
  bucket in front of `/api/admin/**`, which ticket 09 deliberately left unthrottled, and its reason must be
  answered rather than overridden — is a catch-all budget sized for a human operator still a path by which
  an attacker denies an admin their recovery tool?
- **Whether the bound belongs on the limiter or on the emitter, or both.** Ticket 13 chose the emitter for
  rows 5 and 6 (row 46's truncation), on the argument that transition-keying alone does not bound anything.
  The same argument applies here and reaches a different answer, because these rows have no transition to
  key on. Decide whether rows 12, 13 and 14 get their own truncation row, join row 46's, or are bounded
  upstream by the budget.
- **Row 46's missing constants.** `N` has no value, no property key and no named binding test anywhere —
  `21:574-585` invokes the map's mechanism/constants seam rule and then does not discharge it. Per that
  rule this ticket **names the property key and the binding test**, because the aggregate ceiling
  `N × windows/day` is the arithmetic that has to carry audit volume once the flood above is bounded too.
- **`daily` as a named owed input.** Ticket 14 already handed back `GET /api/profile` as "audited on every
  call, throttled on none — an unbounded input to ticket 21's `daily`". This ticket owns closing that, and
  the honest version may be that `daily` is expressible only as a formula over the budget table, which is
  the shape the map's remaining fog patch is already circling.
- **The endpoint-coverage matrix.** Three orthogonal registries exist — the authorization matrix (11), the
  budget table (09), the audit catalogue (13) — with no cross-check. Two endpoints have already fallen
  between them: `GET /api/profile` (ticket 14's finding) and `GET /api/hello` (TM-05). Decide whether the
  remedy is a generated coverage matrix asserted in `verify`, on ticket 25's precedent, or a convention.

## Inherited from ticket 15 — the concrete instance, and one thing that is already safe

**`GET /api/hello` is PRD Story 5's only endpoint and no ticket owns it.** It appears three times in the
whole tree and never as a decision: `03:239` and `13:254-257` both say do not log it, and `11:192` cites it
only to fix the unversioned base path. It has no matrix row and no budget row.

It **fails closed** — `anyRequest().denyAll()` is last — so this is a completeness defect and not a hole,
and the matrix row is a one-line amendment to ticket 11 that ticket 15 has already made. The budget row is
yours, and the reason it belongs here rather than as a second amendment is that it is the same question:
whether a route nobody listed is unbudgeted by default.

## Done when

The limiter's behaviour for an unlisted route is decided and stated as a property rather than a table; the
pre-routing rejection rows have a stated volume bound; row 46's `N` has a value, a property key and a
binding test; `daily` is either a number, a formula, or a registered owed input with a named owner; and the
coverage question is answered either way.

## Answer

### 0. The sentence the rest of this hangs on

**Per-source rate and distinct-source count are orthogonal dimensions, and audit volume is their product.**
Ticket 13's row 46 caps *distinct sources*; transition-keying caps *rows per source*. Rows 5 and 6 are bounded
because they have both. Rows 11, 12, 13, 14 and 35 have neither, so row 46's mechanism applied to them alone
would bound nothing — `N` distinct sources times unbounded rows each is still unbounded. That is why this
ticket is not a one-line extension of row 46.

Second, and the reason the limiter cannot be the whole answer: **a limiter bounds requests per source, and the
threat is rows on disk.** Ticket 13 found the mirror image of this for rows 5 and 6 — "transition-keying buys a
factor of the per-IP budget and nothing more". Inverted: a budget buys a factor and nothing more, because
distinct sources remain attacker-chosen. So the bound is at the emitter, and the limiter is here for a
different cost entirely.

### 1. Three premises of this ticket's own brief were wrong

**The ticket names rows 12, 13 and 14. The unauthenticated pair is 11 and 13.** Rows 12 (`INSUFFICIENT_ROLE`)
and 14 (`FACTOR_MISSING | FACTOR_EXPIRED`) both carry a resolved actor: an anonymous request to a matrix path
fails `hasRole`, `ExceptionTranslationFilter` sees `isAnonymous`, and it becomes 401, not 403 — which is the
composition ticket 23 fixed by hand-ordering role-first. So 12 and 14 cost an attacker a credential. What costs
nothing is **row 11** (`session-end` / `UNKNOWN_OR_EXPIRED`, INFO, low), reachable on any *safe* method with a
fabricated cookie, and row 13 on any unsafe one. Row 11 appears in no finding on this map and is in no alert
class.

**The flood is not only disk. It is the database, and it is method-agnostic.** Verified from source at the
pinned tags:

- `SessionManagementFilter.doFilter` evaluates `securityContextRepository.containsContext(request)` **first** —
  the requested-session-id check is nested inside its `else` arm — and
  `HttpSessionSecurityContextRepository.containsContext` opens with `request.getSession(false)`.
  `DelegatingSecurityContextRepository.containsContext` polls the HttpSession delegate *before* the cheap
  request-attribute one. The filter is in our chain because ticket 08 configures `invalidSessionStrategy`
  (`08:161-166`), one of the properties that populate `propertiesThatRequireImplicitAuthentication`.
- Under Spring Session that `getSession(false)` is `JdbcIndexedSessionRepository.findById`, a `LEFT JOIN` across
  `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES`, with **no id-format validation of any kind** — no length
  check, no UUID parse. `JdbcHttpSessionConfiguration` sets `PROPAGATION_REQUIRES_NEW`, so it is one pooled
  connection and one real transaction per call.
- On unsafe methods there is a second route: ticket 08's CSRF repository is session-bound (forced by §3.1:238),
  and `CsrfFilter` calls `deferredCsrfToken.get()` before the token comparison. On **safe** methods `CsrfFilter`
  returns before dereferencing — the default matcher ignores `GET, HEAD, TRACE, OPTIONS` — so `CsrfFilter` is
  *not* the load-bearing path. `SessionManagementFilter` is.
- The only free rejection is `DefaultCookieSerializer.readCookieValues`, which `continue`s past a value that
  fails Base64 decode. An attacker satisfies that trivially.

So **every request carrying a Base64-decodable `SESSION` cookie costs one SELECT and one audit row, on any
path, any method, unauthenticated** — including `/actuator/health`, which ticket 21 handed here as an
unbudgeted route. An earlier draft of this answer claimed actuator was "exempt without needing an exemption"
because `permitAll` never dereferences the `Supplier<Authentication>` (true —
`SingleResultAuthorizationManager.authorize` ignores it) and `CsrfFilter` returns early on GET (also true).
Both are true and both are the wrong components. Corrected here because the wrong version is the plausible one.

**Ticket 13's numberless raw-URI cap is a 15× term.** `StrictHttpFirewall` performs four checks and imposes
**no URI length limit**. The only ceiling is Tomcat's `server.max-http-request-header-size`, `DataSize.ofKilobytes(8)`,
whose javadoc confirms Tomcat applies it to "the combined size of the request line and all of the header names
and values". Uncapped, an attacker sets bytes-per-row to ~7 KB against a ~500 B baseline. The cap is therefore
an input to the disk arithmetic, not a hygiene nicety.

### 2. The limiter's default for an unlisted route: the table stays an allowlist, and a second budget is added

Stated as a property, which is what the Done-when asked for:

> **Two mechanisms over two costs.** Ticket 09's budget table is an **allowlist for raw request rate** and stays
> one: a route absent from it is not throttled on request rate by this application, and raw request rate on
> unlisted routes is bounded at the infrastructure layer as a declared deployer obligation. Separately, **every
> request is subject to a per-source budget on session-store misses**, which bounds the database cost the table
> never saw. A listed route carries both; an unlisted route carries the second only. No route is unmetered, and
> what varies is which cost is metered where. *Consolidated into the register (ticket 33): R-OPS-006. Amend the table by ID, not this list.*

Three options were on the table and the middle one lost.

**Default-deny with a catch-all request budget was rejected**, and not on cost. It puts a bucket in front of
`/api/admin/**`, which `09:358-360` deliberately left unthrottled on a reason that has to be answered rather
than overridden — an attacker who cannot authenticate must not be able to deny an admin their own recovery
tool. A limiter sitting before `SecurityContextHolderFilter` cannot tell an admin from an attacker, so any
catch-all it applies is exactly what ticket 09 refused. And sized generously enough not to inconvenience a
human, it cuts a 10,000/min flood by perhaps 16× — a factor, which §0 says is all a budget can ever buy.

**Rejecting unmatched paths before `CsrfFilter` was considered and declined.** It would delete row 13's
unmatched-path amplification at source along with the entire raw-URI injection surface, and it is buildable
without rot, since `RequestMappingHandlerMapping.getHandlerMethods()` returns
`Map<RequestMappingInfo, HandlerMethod>` and can be enumerated at startup rather than hand-written. Declined on
three grounds: it converts a uniform 403 on every unmatched path into a route oracle, deliberately weakening
ticket 11's `anyRequest().denyAll()`; the "mapped set" is not just `@RequestMapping` — it must union actuator,
`/error` and resource handling, and that union could **not** be verified as excluded from `getHandlerMethods()`,
so it is the hand-maintained list the approach exists to avoid; and it buys nothing the miss budget does not,
because a fabricated cookie on a *mapped* path costs the same SELECT. Spring Security offers nothing built-in
here: all CSRF scoping is matcher-based and mapping-blind.

**The miss budget, in full.**

- **Axis:** source IP → session-store lookups that did not resolve.
- **Capacity:** 300 per 15-minute window, `app.security.rate-limit.session-miss.capacity` /
  `.window`.
- **Call site:** ticket 09's existing early filter, before `SecurityContextHolderFilter`. Not a new filter and
  not a new envelope producer — `AuthRateLimiter` gains a third call site, which is the idiom ticket 09 already
  uses ("one component invoked from three places").
- **Check on the way in, record on the way out:** a `try/finally` around `chain.doFilter`, recording when
  `getRequestedSessionId() != null && !request.isRequestedSessionIdValid()`. Both are public API; neither costs
  a second lookup, because `requestedSessionCached` is already set by `containsContext`. **That caching
  dependency is pinned by a test**, because if it ever fails the observation doubles the cost it is measuring.
- **On refusal:** `429` in ticket 09's envelope with `Retry-After` per `09:367-374`, **before** the lookup, and
  **it must not create a session**. So a refused request emits row 5 and **no row 11** — which is the bounded
  outcome, stated as a property rather than left to be noticed. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-021. Amend the table by ID, not this list.*

**"An admin consumes zero tokens, ever" was drafted and is false.** Ticket 08 deliberately omits cookie
`Max-Age` (`08:143-148`), so the cookie is a browser-session cookie that outlives the 15-minute idle window;
and Spring Session does **not** clear a non-resolving cookie — `isInvalidateClientSession()` requires
`requestedSessionInvalidated`, set only by `HttpSessionWrapper.invalidate()`, so a miss emits no `Set-Cookie`.
Ticket 08 keeps `Clear-Site-Data` "logout-only and … never attached to 401s" (`08:389-390`), and the cookie is
`HttpOnly`, so ticket 14's unconditional local clear cannot reach it. What *does* heal it is a path worth
naming: `commitSession` rewrites the cookie whenever anything creates a session, and the next call after the
401 is ticket 14's lazy `GET /api/csrf`, which mints one. So the honest property is:

> **An admin with a live session pays nothing. An admin returning to an expired one pays about two, self-healed
> by the CSRF bootstrap.**

Sizing follows a named cause rather than a fudge factor: the fan-out is not in-page — ticket 14's bootstrap is
lazy, "`GET /api/csrf` on first need, never on load" (`14:207`), and `GET /api/profile` is the only on-load call
(`14:187-190`) — it is **browser session restore and multiple tabs**, each tab's on-load self-read presenting
the same stale cookie before any of them has reached the bootstrap. So capacity is
`concurrent_returning_tabs_per_source × 2`: at fifty staff behind one NAT egress with three tabs each, 300.
That is 500× below a 10,000/min flood and an order of magnitude above the worst legitimate morning.

**Honest limit, and it is the standard's own:** the budget is keyed on attacker-chosen source, so IP rotation
restores throughput, exactly as it does for ticket 09's third axis. ASVS **15.3.4 (L2)** requires the real
client IP be used for security decisions such as rate limiting and states in the same breath that the address
may be unreliable through dynamic IPs, VPNs or corporate firewalls. That is a caveat the standard writes down,
not a residual this ticket is apologising for — which is why the emitter-side bound in §4 is not optional. *Consolidated into the register (ticket 33): R-RL-008. Amend the table by ID, not this list.*

### 3. The session-id cap, and the fail-open the fix creates

`HttpSessionIdResolver.resolveSessionIds` returns `List<String>`, `CookieHttpSessionIdResolver` returns every
cookie matching the session name, and `getRequestedSession()` iterates the list breaking only on a hit — so a
**total miss iterates to exhaustion**. A `SESSION=` pair plus a Base64'd UUID is 58 bytes, so the 8192-byte
combined request-line-and-headers budget admits 141 as an arithmetic ceiling and about 130 after a ~650-byte
reservation the attacker partly controls. Outside dev, `__Host-SESSION` makes it 65 bytes and **126**. At
`PROPAGATION_REQUIRES_NEW` that is up to ~130 pool checkouts and ~130 transactions on one request.

That breaks the budget's **accounting**, which is the decision: one token is charged for up to 130 lookups, so
a budget sized for 130 starves legitimate users and a budget sized for 1 under-charges by two orders of
magnitude. A budget cannot be made correct while the cost per request is variable and attacker-set. So the cost
is fixed upstream rather than priced:

**Honour at most one resolved session id.** A delegating `HttpSessionIdResolver` truncates
`resolveSessionIds` to its first element. `CookieHttpSessionIdResolver` is `final`, so this is delegation, not
subclassing, and all three interface methods — `resolveSessionIds`, `setSessionId`, `expireSession` — forward.
When more than one is present, emit **row 11 under a new reason `DUPLICATE_SESSION_COOKIE`**: cheap,
diagnosable, and it doubles as cookie-tossing detection. It also removes a reporting ambiguity we would
otherwise inherit, since `getRequestedSession()` assigns `requestedSessionId` from the first element *before*
any validity check, so `getRequestedSessionId()` today reports the first cookie even when a later one resolved.

**The cap ships two beans, and that is the load-bearing part.** Boot's
`SessionAutoConfiguration.DefaultCookieSerializerCondition` creates the `DefaultCookieSerializer` bean only
when no `HttpSessionIdResolver` bean exists, or when the registered one is a `CookieHttpSessionIdResolver`.
Registering a delegating resolver satisfies neither branch, so the serializer bean vanishes and **every
`server.servlet.session.cookie.*` property silently stops being applied** — name, `Secure`, `Path`, `SameSite`,
`Max-Age`. That would unwind `__Host-SESSION`, `SameSite=Strict` and the deliberately-omitted `Max-Age` in one
move, with nothing failing and every config value still reading correct: ticket 08's entire cookie contract and
ticket 20's topology, undone by a fix aimed at a database. So the resolver bean is registered **together with an
explicit `CookieSerializer` bean**, and the pairing carries a comment naming the condition class. Two smaller
inherited costs: `SpringHttpSessionConfiguration.afterPropertiesSet` injects the serializer onto the *default*
resolver only, so ours must be wired explicitly; and delegating forfeits the
`instanceof CookieHttpSessionIdResolver` guard that throws "Cannot create a session after the response has been
committed", converting that from an exception into a silent failure.

**Residual, stated rather than closed here.** RFC 6265 §4.2.2 says servers "SHOULD NOT rely upon the
serialization order" and names the two-cookies-same-name case explicitly, so "first" is attacker-influenced
wherever anything can set a cookie on a parent or sibling domain. The closure is the `__Host-` prefix, whose
user-agent criteria (`draft-ietf-httpbis-rfc6265bis` §5.7) require the secure-only flag, the host-only flag and
`Path=/` — and the host-only flag is reachable only through the empty-`Domain` branch, so a subdomain cannot
get a `__Host-`-named cookie delivered to the parent host. Three limits on that closure: it does **not** stop
the same host setting a second `__Host-` cookie, it **ignores ports**, which matters under ticket 20's two-port
localhost topology, and in `dev` the name is unprefixed `SESSION` so the residual is live. **This half is
ticket 08's, not this ticket's** — ticket 08 names only the `Secure` constraint (`08:78-82`) and says nothing
about duplicate cookies, cookie tossing or ordering. The id cap is ours because the budget's arithmetic depends *Consolidated into the register (ticket 33): R-SES-007. Amend the table by ID, not this list.*
on it. One consistency note for ticket 17: ticket 09 declined `RateLimit` headers for resting on a live
Internet-Draft, and `__Host-`'s normative text is one; ticket 08 adopted it without recording that. *Consolidated into the register (ticket 33): R-SES-006. Amend the table by ID, not this list.*

### 4. The emitter-side bound: two tiers, keyed on two key spaces

Ticket 13's reason for needing row 46 at all was that the key space is attacker-chosen. Applied consistently,
that sorts the unbounded rows into two tiers.

**Tier 1 — unbounded key space. Rows 5, 11, 13.** Keyed on source; no account required. Emit **once per
(source, row, reason, window)**, and apply the distinct-source cap. Reason variants: row 5 gains
`RATE_LIMITED_SOURCE_MISSES` beside `RATE_LIMITED_SOURCE`, row 11 gains `DUPLICATE_SESSION_COOKIE` beside
`UNKNOWN_OR_EXPIRED`, row 13 keeps `CSRF_MISSING | CSRF_INVALID`. So `C₁ = 6`.

**Tier 2 — bounded key space. Rows 12, 14, 35.** Keyed on `user.id`. Emit **once per (user, row, reason,
window)**. Reason variants: 12 has one, 14 has two, 35 has none. So `C₂ = 4`.

**Tier 3 — already bounded, unchanged.** Rows 2, 17, 20, 22, 23 and 39 sit behind budgeted routes and per-account
counters. Row 6 stays exactly as ticket 13 left it: transition-keyed, keyless by construction, bounded by the
user population.

**Tier 2 gets a distinct-user cap too, for a reason better than the one first drafted.** A draft argued the
population is attacker-growable because `POST /api/register` is unauthenticated at 5/min, which is 7,200 a day.
The arithmetic is right and the conclusion is wrong: ticket 10 decided **registration stores no credential**
(`10:184-192`), the password is set at redemption, reset cannot serve as a second activation channel
(`10:376-383`), and ticket 13 confines the stub's link to a dev-only non-audit logger enforced three ways
(`13:623-639`), so outside `dev` "a reset request produces no deliverable artefact at all" (`10:734-738`). An
attacker looping registrations produces records that can never authenticate, and tier 2 requires a resolved
actor. **So population is bounded by a control, and this ticket can name it — but the control is the absence of
a mail transport, which the map's own fog patch plans to remove.** The cap is therefore taken as insurance and
carries an explicit reopening trigger. Recorded separately, because it is an inference the repo does not state: *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
the "no deliverable artefact outside `dev`" sentence is written about **reset**, not activation; the activation
case follows from the shared stub (`10:522`) and the shared confinement, and is written down here rather than
left to be re-derived.

**Licence for keying is corpus-side, not ASVS-side, and that distinction matters.**
`Structured_Logging_Application_Standard.md:221` blesses "a counter-based gate (e.g., log every Nth
iteration)", and `:214` is the direct analogue — "A per-request evaluation log generates one entry per request
and will flood the log under load", with the instruction to log at startup or on change rather than on every
evaluation. ASVS is **silent**: no requirement in 5.0.0 prohibits or constrains aggregation, sampling,
deduplication or truncation of security log records, confirmed by scanning all 28 files under `5.0/en` at the
`v5.0.0_release` tag. Silence is not endorsement, and the answer says so rather than claiming a permission.

Two ASVS requirements that *are* engaged, and their honest disposition:

- **16.2.1 (L2)** — each entry must carry enough when/where/who/what to investigate a timeline. This bites
  **row 46 only, not the keyed rows.** A tier-1 row keyed on (source, row, reason, window) has exactly one
  source: who and where are intact and only the individual timestamps within the window are lost, which §5's
  count and first-seen fields answer. The row with no single actor is the truncation row, which already existed
  and was already accepted in ticket 13. *Consolidated into the register (ticket 33): R-AUD-028. Amend the table by ID, not this list.*
- **16.3.1 (L2)** — all authentication operations logged, successful and unsuccessful. **Not engaged at all,
  and that belongs on the record rather than being conceded.** Nothing being keyed records an authentication
  operation: rows 1 and 2 stay per-event, and the keyed set is a session-end observation (11), two authorization
  denials (12, 14), a control-bypass attempt (13), a read (35) and a throttle breach (5). A throttle breach is
  not an authentication attempt, so routing the miss-budget refusal through row 5 does not change this. Row 39,
  the one `user-authentication` row with client-set volume, is tier 3 and stays per-event.

### 5. The count field, and row 46's two exact numbers

**Every keyed row carries the count of occurrences it replaces, plus the window's first-seen timestamp.** One
integer and one timestamp, and they do four things: aggregation becomes lossy only in individual timestamps and
never in magnitude, which is the 16.2.1 answer; the detection signal survives in the audit record; row 35 gains
a reason to exist after keying; and no membership set beyond the cap is needed. *Consolidated into the register (ticket 33): R-AUD-028. Amend the table by ID, not this list.*

An earlier draft proposed a 10,000-entry counting set to keep `source.distinct_count` exact, on ticket 09's
`maximumSize(10_000)` precedent. **Dropped.** It buys an exact number below 10,000 and an over-count above it,
for 500× the memory and a documented inexactness, because a bounded set cannot dedupe the sources it is not
tracking. The count field makes it unnecessary. Row 46 therefore carries **two exact numbers instead of one
approximate one**:

- `source.distinct_count` — sources actually tracked. Exact, and capped at `N` by construction.
- `events.untracked_count` — emission events from sources beyond the cap. Exact, because it needs a counter and
  not membership.

The true distinct count is then bracketed honestly at **at least `N`, at most `N` + untracked**, which is all
the campaign signal ticket 13 claimed for the aggregate, and memory is `N` entries. This closes a defect
neither ticket 13 nor ticket 21 had seen: as written, `source.distinct_count` was not computable under any
bounded structure, and neither ticket specified the structure, the computation or a `maximumSize`. *Consolidated into the register (ticket 33): R-AUD-029. Amend the table by ID, not this list.*

**One truncation row, widened, not three new ones.** Row 46 carries the truncated row's identity as a field.
Two truncation rows sharing one `N` is how the mechanism/constants seam rule acquires its sixth instance.

**Why per-source membership state must not be evicted within a window** is a sentence already on this map, and
its home is `09:1264` in §R.6, not ticket 21 — which restates it at `21:682-684`. The asymmetry is worth
preserving because it cuts both ways: for Bucket4j **bucket caches** eviction is *not* a bypass of audit volume,
since "an evicted source must spend another budget's worth of requests to breach again, which is already
counted" (`13:717-722`); for a bounded **membership set** it is a bypass of the control itself. Tier 1's
per-window structure is the latter, so it holds at most `N` entries and the N+1th source is never admitted —
which makes eviction inside a window structurally impossible rather than merely discouraged, and makes the
evidence bound and the memory bound the same number.

**Raw `url.path` on rows emitted before handler mapping is capped at 256 characters with a trailing truncation
marker.** The cap is load-bearing arithmetic, per §1. The marker is so the evidence is not silently altered;
the citation is the corpus's own functional test at `:255` ("enough context to answer") and ASVS 16.2.1, not
16.4.1, which is about encoding rather than truncation marking. Ticket 13's `[\r\n|]` strip at the single
emitter is unchanged.

### 6. Row 35 keeps the row, loses nothing, and stops being unbounded

Ticket 14 handed back `GET /api/profile` as "audited on every call, throttled on none — an unbounded input to
ticket 21's `daily`". **Row 35 is kept and keyed on (user, window), with the count and first-seen fields and no
reason dimension** — it has one reason, so keying on it is a no-op.

Deletion was analysed and declined. No standard mandates the row: the Application Standard's §3.3 list of nine
auditable events omits read, its CRUD bullet enumerates create/unlock/update/delete and pointedly not read,
its §3.4 INFO list omits it, and logging `:60`, `Logging_AuthN_And_AuthZ_Events.md:337` and Q4 at `:80` all
restrict authorisation-success logging to sensitive, privilege-crossing or audit-required operations. ASVS
**16.3.2 (L2)** requires failed authorization only, with all decisions at **L3**, which this project does not
claim. But two prescribed recipes emit it, and
`Appfw-Logging-Standards/Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md:177-192` puts `profileRead`
on the **audit** logger — so a reviewer can call it an audit event and invoke `:218`, "Do not suppress log
entries for security or audit events". Deletion is the one option with no textual cover.

Keying has cover and costs less. What the recipe-deviation note can honestly say: **the event still exists, the
magnitude still exists, and only per-navigation timestamps are gone.** It also fixes the term that made the
workload input unestimable — a deployer can estimate meaningful actions per user per day; nobody can estimate
SPA guard re-renders.

### 7. `daily` is a formula with one named deployer input

```
daily_bytes = bytes_per_row × ( tier1_ceiling + tier2_ceiling + workload_rows )

tier1_ceiling  = N_src × C₁ × windows_per_day   +  C₁ × windows_per_day        (keyed rows + truncation rows)
tier2_ceiling  = min(P, N_usr) × C₂ × windows_per_day  +  C₂ × windows_per_day
workload_rows  = deployer input: audited business actions per day (rows 1–4, 7–10, 16–34, 36–44)
```

With `windows_per_day = 96`, `N_src = 20`, `C₁ = 6`, `N_usr = 500`, `C₂ = 4`, and `bytes_per_row` measured:

| Term | At `P` = 100 | Owner |
| --- | --- | --- |
| `tier1_ceiling` | 11,520 + 576 = **12,096** rows/day | wholly this application |
| `tier2_ceiling` | 38,400 + 384 = **38,784** rows/day | ours × the deployer's `P` |
| `workload_rows` | ~2,000 rows/day | the deployer |
| `daily` at 512 B/row | **≈ 27 MB/day** | |
| `90 × daily` | **≈ 2.4 GB** | |
| `threshold = daily × 2` | **≈ 54 MB** | |

Which vindicates ticket 21's claim that overriding the framework's 10 MB default mattered — the correct value is
five times it — and the `new File(".")` path override with it.

Two properties of this that are the point rather than the detail. **The ceiling is a ceiling, not a forecast**:
tier 1 requires an attack and rows 12 and 14 require a misbehaving authenticated user, so steady state is far
below — routine tier 2 is row 35 alone at `P × 96`, about 9,600/day at `P` = 100. And **the window is the
volume lever**: doubling it halves both ceiling terms. If the population grows to where the second term
dominates, the deployer lengthens the window rather than re-deriving anything, which is ASVS **15.1.3 (L2)**'s
documentation obligation and **2.3.2 (L2)**'s implementation of it in one sentence.

**`bytes_per_row` is measured, not argued.** A test serialises the widest row at the capped URI length and pins
an upper bound; the arithmetic cites the measured figure. 512 B is the planning value and the test owns the
real one. Nothing on the map supplied a bytes-per-row constant before, which is why `daily` was symbolic in
ticket 21 §8. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-032. Amend the table by ID, not this list.*

**Ticket 14's handback closes as a formula term, not as an owed input**, and the fog patch gets its answer: yes,
`daily` is expressible as a formula over quantities this map already owns plus one deployer input, so the
patch's open question about rate-above thresholds is answered by precedent rather than by argument.

### 8. Endpoint coverage: generated, with its scope stated

Three registries exist — ticket 11's authorization matrix, ticket 09's budget table, ticket 13's audit
catalogue — and two endpoints have already fallen between them. Ticket 15 established the shape of the remedy:
three of its eight build-phase assertions are "**enumerated rather than written**", because "a hand-written list
omits the route added next year, which is the failure TM-04 *is*".

**A `verify`-phase test enumerates `RequestMappingHandlerMapping.getHandlerMethods()` and asserts every mapped
handler has a disposition in all three registries** — a matrix row or membership of the public whitelist, a
budget row or explicit coverage by the stated default, and an audit-catalogue disposition that is either a row
or a recorded deliberate omission. The matrix is a typed `@Validated @ConfigurationProperties` record, so it is
machine-readable; `RequestMappingInfo.getPatternValues()` and `getMethodsCondition().getMethods()` supply the
other side.

**Scope stated rather than overclaimed:** `getHandlerMethods()` and both accessors are verified against the
7.0.9 source, but that the mapping *excludes* actuator, functional and servlet-registered routes could **not**
be verified from a primary source. The assertion therefore covers `@RequestMapping` handlers and says so. Note
also that `RequestMappingInfo` carries params, headers, consumes, produces and version conditions, so a
path-plus-method pair is not the full mapping identity. *Consolidated into the register (ticket 33): R-BLD-016. Amend the table by ID, not this list.*

**This closes the endpoint gap, not the band gap.** `GET /api/hello` (TM-05's budget half) and
`GET /api/profile` are endpoints that fell between registries; TM-01 is a control evaluated at a *band*, on
paths no endpoint-keyed registry covers, and it is closed by §2 and §4. Two remedies at two layers, which is
ticket 15's own transferable finding — "a control placed correctly at one layer, against a threat that does not
pass through that layer."

**`GET /api/hello` needs no budget row.** Under §2's property it is covered by the miss budget like every other
route, and its matrix row was already added by ticket 15's amendment at `11:861-863`. That amendment is prose;
ticket 11's endpoint-set table at `11:208-218` was never edited, so the row exists in one place and not the
other — flagged to ticket 11, and the coverage test above is what makes it stop mattering.

### 9. Standards position, with levels, and the honest label

Every hook below is **L2**, and this map's declared target is **ASVS 5.0 Level 1**. So none of this is "the
standard requires it": it is all **adopted because cheap**, and 2.4.1 being L2 means anti-automation is not an *Consolidated into the register (ticket 33): R-STD-057. Amend the table by ID, not this list.*
L1 obligation at all. Verified verbatim at the `v5.0.0_release` tag.

| Requirement | Level | What it carries here |
| --- | --- | --- |
| **2.4.1** | L2 | Anti-automation against "quota exhaustion, rate-limit breaches, denial-of-service, or overuse of costly resources" — the citation §2 exists to satisfy. Phrase appears verbatim and contiguously. |
| **2.1.3** | L2 | Business logic limits documented "both per-user and globally" — §4's two tiers and §7's formula. Documentation only. |
| **2.3.2** | L2 | The implementation pair to 2.1.3. |
| **15.1.3** | L2 | Document resource-demanding functionality and how availability loss is prevented. Note "limiting parallel processes per user and per application" sits under "Potential defenses **may** include", so it is illustrative, not mandated. §7's published formula is how this is satisfied. |
| **15.2.2** | L2 | Implement the above — and it binds us to **our own documented** strategy, which is what makes §7's formula self-binding rather than decorative. |
| **15.3.4** | L2 | The real client IP must be used for security decisions including rate limiting, with the standard's own caveat that the address may be unreliable. §2's IP-rotation limit is the standard's caveat, not our apology. Three causes named, dynamic IPs first. |
| **16.1.1** | L2 | The log inventory must record what is logged and the log formats — where §4's keyed forms are documented. |
| **16.3.3** | L2 | The application logs the events its documentation defines. This is the route by which a keyed row is the defined event rather than a suppressed one — but see the caveat below. |
| **16.2.1** | L2 | Each entry carries enough to investigate a timeline. Answered for keyed rows by §5's count and first-seen; the exposure is row 46, which ticket 13 already accepted. |
| **16.3.2** | L2 | Failed authorization at L2, **all** decisions only at L3 — the licence for §6. |
| **16.3.1** | L2 | Not engaged. Nothing keyed is an authentication operation. |

**Caveat recorded rather than buried:** 16.3.3 delegates *which* events to our documentation. Reading that as
licence over *granularity* is our inference, not the standard's words, and the answer says so. The operative
licence for keying is the corpus's `:221` and `:214`, quoted in §4.

**V16's level structure, as a note and not a correction.** An earlier draft filed "V16 contains no L1
requirement" as a correction to this map's citations. It is neither a correction nor new: the fact is already
recorded at `13:611`, `13:843`, `17:259`, `21:146`, `25:318` and `map.md:654`, and no ticket has ever tagged
16.3.2, 16.4.1 or 16.2.3 as L1 — every level tag in the tree is L2. The draft was retracted under this map's own
verification rule, which is the rule working. The only residue worth a line: the tally is 17 rows, 16 at L2 and
one at L3 (16.5.4), and **16.3.2's L3 escalation lives inside an L2 row's text**, so tooling reading only the
level column will miss it.

### 10. Constants, property keys and binding tests

Per the mechanism/constants seam rule, every constant here is named with its property key and its binding test.
`N_src` discharges the debt `21:574-585` invoked and did not pay.

| Constant | Value | Property key | Binding test |
| --- | --- | --- | --- |
| Keying window, both tiers | 15 min | `app.audit.keying.window` | emitter constant equals bound property; one property, not two — see below |
| Tier-1 distinct-source cap `N_src` | 20 | `app.audit.truncation.distinct-sources` | `N+1` distinct sources in one window yield exactly `N` keyed rows, one row 46, and nothing further |
| Tier-2 distinct-user cap `N_usr` | 500 | `app.audit.truncation.distinct-users` | as above, on the user axis |
| Raw URI cap | 256 chars | `app.audit.url-path.max-length` | a 7 KB path yields a 256-char field with the truncation marker |
| Miss-budget capacity | 300 / window | `app.security.rate-limit.session-miss.capacity` | capacity+1 misses from one source yields 429, and the 429 creates no session row |
| Miss-budget window | 15 min | `app.security.rate-limit.session-miss.window` | — |
| `lead_days` | 2 | unchanged, ticket 21 §8 | unchanged |
| `bytes_per_row` | measured | not a property | widest row at capped URI length is under the pinned bound *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-033, T-AUD-034, T-AUD-035, T-AUD-024, T-RL-016, T-AUD-032. Amend the table by ID, not this list.* |

**One window property, shared by both tiers, and per-row windows are refused.** The count field removes most of
what a shorter window bought, and a longer one saves volume in the term the deployer already owns and can see in
the formula. The map is at five instances of the seam rule; row 35 is not worth a sixth.

### 11. Tests

Two of these exist because a citation cannot prove the claim — the claim is about our assembled chain, not
about the library.

1. A `GET /actuator/health` bearing a fabricated Base64-decodable `SESSION` cookie produces **exactly one**
   `SPRING_SESSION` query and **exactly one** row 11. Pins §1's method-agnostic finding and the presence of
   `SessionManagementFilter` in our chain.
2. A request bearing 50 `SESSION` cookies produces **exactly one** session query. Pins §3's cap.
3. The `CookieSerializer` bean is present and `server.servlet.session.cookie.*` still applies — asserted on
   resolved cookie attributes (name, `Secure`, `SameSite`, absence of `Max-Age`) with the resolver bean
   registered. This is the §3 fail-open, and it fails silently without the test.
4. `N+1` distinct sources in one window: exactly `N` keyed rows, one row 46 carrying both counts, nothing
   further.
5. `source.distinct_count` and `events.untracked_count` are both exact against a scripted source set.
6. A flood of 1,000 identical CSRF-less POSTs from one source yields **one** row 13 per (reason, window) with
   `event.count` equal to 1,000.
7. The keyed-row observation costs no second session lookup — pins the `requestedSessionCached` dependency in §2.
8. A refused request emits row 5 and **no** row 11, and creates no session row.
9. The widest audit row at the capped URI length is under the pinned byte bound.
10. Coverage: every `@RequestMapping` handler has a disposition in all three registries.
11. A 7 KB request URI on an unmatched path yields a 256-char `url.path` with the truncation marker, and the
    `[\r\n|]` strip still applies.
12. Two `SESSION` cookies yield row 11 with reason `DUPLICATE_SESSION_COOKIE`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-029, T-SES-030, T-SES-031, T-AUD-036, T-AUD-037, T-RL-021, T-RL-023, T-ARCH-005, T-AUD-038, T-AUD-034, T-AUD-024, T-AUD-032. Amend the table by ID, not this list.*

### Handover items (ticket 25)

- **Raw request rate on unlisted routes is bounded at the infrastructure layer, not by this application.**
  Discharges ASVS **15.1.3 (L2)** (document it) and **15.2.2 (L2)** (implement the documented strategy); ticket
  20 left no production environment and the map rules hosting infrastructure out of scope, so the application
  can enforce **no part** of it. Provable by a documented edge or ingress rate-limit policy naming a request
  ceiling per source, plus evidence it is applied to paths outside ticket 09's budget table. *Consolidated into the register (ticket 33): R-OPS-006. Amend the table by ID, not this list.*
- **Recompute `management.health.diskspace.threshold` from §7's formula when the mount moves, the mount is
  resized, the user population changes materially, or `app.audit.keying.window` changes.** Discharges ASVS
  **15.1.3 (L2)**; the application can enforce the property's presence but not its correctness. Provable by the
  recorded computation alongside the configured value, with the four inputs shown. *Consolidated into the register (ticket 33): R-OBS-012. Amend the table by ID, not this list.*
- **Alerting and rate computation for row 46 and the two count fields.** Row 46 is per-event in ticket 21's
  taxonomy and must not be aggregated away; `event.count` and `events.untracked_count` are the magnitude
  signals that replace per-occurrence rows. Discharges **16.3.3 (L2)** in its second limb (bypass attempts) and
  ASVS **2.4.1 (L2)**'s detection half. The application emits; it cannot alert. Provable by rules in whatever
  consumes the stream. *Consolidated into the register (ticket 33): R-OBS-013. Amend the table by ID, not this list.*
- **The log inventory must record the keyed and truncated forms, not just the event list.** Discharges ASVS
  **16.1.1 (L2)** and is the precondition for **16.3.3 (L2)** being satisfied by a keyed row rather than failed
  by a missing one. Application-enforceable only to the extent the catalogue is code. Provable by the inventory
  naming, per keyed row, the key tuple, the window, the cap and the count fields. *Consolidated into the register (ticket 33): R-AUD-027. Amend the table by ID, not this list.*

### ADRs owed

1. The budget table stays an allowlist for request rate; unlisted routes are metered on session-store misses
   instead — including why default-deny was refused rather than adopted, on ticket 09's admin-availability
   reason. *Consolidated into the ADR routing (ticket 34): ADR-017. Amend by ID, not this list.*
2. Rejecting unmatched paths before `CsrfFilter` — considered and declined on the route oracle, the
   unverifiable mapped-set union, and redundancy against the miss budget. *Consolidated into the ADR routing (ticket 34): ADR-018. Amend by ID, not this list.*
3. The two-tier emitter bound, and why a limiter cannot substitute for it. *Consolidated into the ADR routing (ticket 34): ADR-019. Amend by ID, not this list.*
4. The count-and-first-seen field pair, and the retraction of the 10,000-entry counting set. *Consolidated into the ADR routing (ticket 34): REJ-078. Amend by ID, not this list.*
5. Row 46 widened to two exact counts rather than one approximate one, carrying the truncated row's identity. *Consolidated into the ADR routing (ticket 34): REJ-079. Amend by ID, not this list.*
6. `N_src = 20`, `N_usr = 500` and the 15-minute window as one judgement with published arithmetic, on ticket
   21's `lead_days` precedent, with the recomputation trigger. *Consolidated into the ADR routing (ticket 34): REJ-080. Amend by ID, not this list.*
7. The 256-character raw-URI cap with a truncation marker, cited to `:255` and 16.2.1 rather than 16.4.1. *Consolidated into the ADR routing (ticket 34): REJ-081. Amend by ID, not this list.*
8. Row 35 keyed rather than deleted — the recipe-deviation note, and what remains true after keying. *Consolidated into the ADR routing (ticket 34): REJ-082. Amend by ID, not this list.*
9. The session-id cap, and the paired `CookieSerializer` bean as the condition of it being safe. *Consolidated into the ADR routing (ticket 34): REJ-083. Amend by ID, not this list.*
10. `daily` as a formula with one named deployer input, and `bytes_per_row` as a measured constant. *Consolidated into the ADR routing (ticket 34): REJ-084. Amend by ID, not this list.*
11. The generated three-registry coverage assertion, with its verified scope and its unverified exclusions. *Consolidated into the ADR routing (ticket 34): REJ-085. Amend by ID, not this list.*
12. Every standards hook in §9 recorded as adopted-because-cheap at L2 against a declared L1 target. *Consolidated into the ADR routing (ticket 34): REJ-086. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-STD-057. Amend the table by ID, not this list.*

### Glossary terms

**Keyed row** · **Truncation row** · **Tier 1 / Tier 2 (by key space, not by severity)** · **Session-store
miss** · **Miss budget** · **Keying window** · **Untracked source**

### Deferral register entries

1. Raw request rate on unlisted routes — deferred to the infrastructure layer, deployer-owned, ASVS 15.1.3 /
   15.2.2 (L2) satisfied by documentation only. *Consolidated into the register (ticket 33): R-OPS-006. Amend the table by ID, not this list.*
2. Per-occurrence timestamps on keyed rows — lost by design, magnitude preserved; ASVS 16.2.1 (L2) satisfied for
   keyed rows, exposure confined to row 46 and inherited from ticket 13. *Consolidated into the register (ticket 33): R-AUD-028. Amend the table by ID, not this list.*
3. Per-source identity beyond `N_src` — lost, bracketed at `[N, N + untracked]`. *Consolidated into the register (ticket 33): R-AUD-029. Amend the table by ID, not this list.*
4. IP rotation defeats the miss budget — ASVS 15.3.4 (L2)'s own caveat, compensated by the emitter bound. *Consolidated into the register (ticket 33): R-RL-008. Amend the table by ID, not this list.*
5. The `__Host-` ordering closure does not apply in `dev`, does not stop same-host duplicates, and ignores
   ports — ticket 08's residual, recorded here because the id cap's soundness depends on it. *Consolidated into the register (ticket 33): R-SES-007. Amend the table by ID, not this list.*
6. The `getHandlerMethods()` coverage assertion does not reach actuator, functional or servlet-registered
   routes — scope stated, not claimed. *Consolidated into the register (ticket 33): R-BLD-016. Amend the table by ID, not this list.*

### Reopening triggers

1. **A real mail transport arrives.** Tier 2's key space is bounded today because a self-registered account
   cannot reach an authenticatable state — a consequence of the stub, not of a control designed for it. When
   activation becomes deliverable, `min(P, N_usr)` must be re-argued against an attacker-growable `P`. Same
   trigger the fog patch on owner notification already owns. *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
2. **A non-root `server.servlet.context-path` is configured.** `DefaultCookieSerializer.getCookiePath` defaults
   to the context path and falls back to `/` only when it is empty, so a `__Host-` cookie would be rejected by
   the browser and present as total session loss rather than a config error. Ticket 21 already requires that
   property unset as a CVE predicate; this is a second, independent reason. *Consolidated into the register (ticket 33): R-SES-008. Amend the table by ID, not this list.*
3. **`bytes_per_row` measures materially above the pinned bound**, or a new keyed row class is added — §7's
   arithmetic and the diskspace threshold both move. *Consolidated into the register (ticket 33): R-AUD-030. Amend the table by ID, not this list.*
4. **Any new audit row is emitted from the security filter chain.** It joins tier 1 or tier 2 by key space, and
   `C₁` or `C₂` changes, which changes `daily`. *Consolidated into the register (ticket 33): R-AUD-031. Amend the table by ID, not this list.*

### Amendments owed to other tickets

**Nine tickets: 08, 09, 11, 12, 13, 14, 16, 17, 21.**

- **08:** the invalid-session 401 leaves the stale cookie in place, so the miss is not self-healing except via
  the CSRF bootstrap; duplicate session cookies, cookie ordering and the `__Host-` no-`Domain` constraint are
  absent from the ticket and are the closure the id cap depends on; the `__Host-` prefix rests on an
  Internet-Draft that ticket 09 declined `RateLimit` headers over. *Consolidated into the register (ticket 33): R-SES-006. Amend the table by ID, not this list.*
- **09:** the budget table is now explicitly an allowlist for request rate, stated as a property; `AuthRateLimiter`
  gains a third call site and a third axis; row 5 gains the `RATE_LIMITED_SOURCE_MISSES` reason; the
  admin-unthrottled decision survives untouched and the reason it survives is that the new axis is keyed on
  misses, which admin traffic does not produce.
- **11:** `GET /api/hello` exists only in the amendment prose at `11:861-863` and in no table row; the coverage
  assertion is enumerated from the matrix, so the matrix is now load-bearing for a build-phase check.
- **12:** the keyed-row structures are bounded at `N_src` and `N_usr` entries, not 10,000; no schema consequence.
- **13:** rows 11, 12, 13, 14 and 35 become keyed; rows 5, 11 gain a reason each; row 46 is widened to two exact
  counts plus the truncated row's identity; `source.distinct_count` as originally written was not computable
  under any bounded structure; the raw-URI cap has a number and a marker; row 6 is unchanged.
- **14:** the interceptor must now handle a **429** where it previously expected only 401 on a stale cookie; the
  audited-but-unbudgeted handback closes as a formula term; the tab-restore fan-out is the named cause of the
  miss-budget capacity and is the first place the map records it.
- **16:** twelve tests, two of which exist because a source citation cannot prove a claim about our assembled
  chain.
- **17:** twelve ADRs, six register entries, four reopening triggers, and the `__Host-`/Internet-Draft
  consistency note. *Consolidated into the register (ticket 33): R-SES-006. Amend the table by ID, not this list.*
- **21:** `daily` is a formula and the threshold is computable; row 46's `N` has a value, a property key and a
  binding test; rows 11 and 35 are **client-rate-driven and unmonitored** and go back for a monitoring story —
  narrowly, because 27 of 46 rows sit in no alert class and the discriminator explains why, so this is not an
  omission in the taxonomy; the "eviction is a bypass" sentence belongs to `09:1264` §R.6, not to ticket 21.

### Graduated: one new ticket

**`GET /api/csrf` mints a session row per call and nothing bounds the total.** At 30/min against the 15-minute
idle window a single source holds ~450 live anonymous rows (`08:483-489`, `09:342-348`, carried to `12:128-131`
and `12:800-802`), the JDBC cleanup cron deletes only already-expired rows so the limiter and not the cron is
the sizing control, and the per-source ceiling is attacker-multipliable by source count — which no ticket
multiplies out. Ticket 21 **declined** the only instrument that would see it (`21:401-402`), and **nothing
anywhere watches the H2 file**: the sole disk indicator is pointed at the log directory. That is TM-01's twin on
a second resource, and §2's miss budget bounds SELECTs on forged cookies while bounding no INSERTs at all.

It graduates as its own ticket rather than a handover item, because a handover item cannot reopen a resolved
decision and this must: ticket 08 deliberately declined a shorter pre-authentication timeout (`08:118-121`), and
"do not mint a session for anonymous callers" is unavailable under §3.1:238 (`08:490-494`). So the remedy is a
choice among live decisions — shorter anonymous expiry, a cap on live anonymous rows, or accept-and-instrument —
spanning tickets 08, 09, 12 and 21. It inherits §7's shape explicitly so the precedent transfers instead of
being re-argued, and ticket 23's `NullRequestCache` contributor (`08:566-570`) belongs in its arithmetic as a
second term, already fixed but still part of the sum.

Status: resolved.

---

## Amendment from ticket 29 (anonymous session-row growth)

- **Your §1 finding at `26:142` has a write side you did not price.** On unsafe methods with no session,
  `deferredCsrfToken.get()` does more than look up: `RepositoryDeferredCsrfToken.init()` also generates and saves a
  token, which creates a session row before the 403, on any path
  ([asset](../research/anonymous-session-growth-and-h2-file-verification.md) §F.1). Ticket 29 closes it with a CSRF
  repository wrapper, so only `/api/csrf` can create an anonymous session.
- **Your miss budget meters ticket 29's delete-by-touch path.** Lookups of expired rows return null (`26:199-202`),
  so they count. Sources are free under IPv6 (ticket 31), so ticket 29's bound rests on its dwell, not on this
  budget.
- **§7's disk sizing gains an H2 term.** The volume must be at least
  `90 × daily + F_base + (k + 1) × N_max × b + floor + A`, about **6.96 GB** at the planning values, not 2.4 GB.
  The H2 term alone is about 4.25 GB, 1.8× your audit figure. *Consolidated into the ADR routing (ticket 34): REJ-084 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-OBS-012. Amend the table by ID, not this list.*

## Amendment from ticket 31 (IPv6 source keying)

- The miss budget, tier-1 keyed rows (per (source, row, reason, window)), and row 46's `N_src` all key on the **source
  key**: IPv4 /32, IPv6 /64 by default ([ticket 31](31-ipv6-source-keying.md) §2). Distinct sources are distinct
  `source.ip_hash` values, which now hash the key. *Consolidated into the ADR routing (ticket 34): ADR-017 / ADR-019 (attached amendment) / REJ-080. Amend by ID, not this list.*
- **Register entry 4** is re-unitised to "IP rotation, **per source key**, defeats the miss budget". The emitter bound
  is still the compensating control, and no constant changes. *Consolidated into the register (ticket 33): R-RL-008. Amend the table by ID, not this list.*
- Your 50-staff × 3-tab sizing (`26:224`) carries over to an office on one IPv6 /64 unchanged.
- **Your edge per-source limit handover item (`26:546`) is tightened by ticket 31:** the edge must aggregate IPv6 at a
  prefix the deployer chooses. Envoy's default is /128. *Consolidated into the register (ticket 33): R-OPS-006. Amend the table by ID, not this list.*
