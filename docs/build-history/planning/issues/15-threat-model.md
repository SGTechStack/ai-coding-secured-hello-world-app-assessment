# 15 — Run the threat model against the design

Type: task
Status: resolved
Blocked by: 08, 09, 10, 11, 12, 20

## Question

What does a STRIDE threat model over the decided design surface that the decisions missed?

Run after the design decisions land but **before** the spec is finalised, so findings can still
change the design cheaply. That ordering is the entire point — a threat model after the build is an
audit, not a design tool.

## How

Use the `owasp-threat-modeling` skill. Build the data flow diagram from the decided design, not from
the PRD: SPA origin, API origin, session store, H2, the stubbed email transport, and the trust
boundaries between them.

Apply STRIDE across at least these flows:

- Anonymous → login (credential stuffing, brute force, enumeration, timing side channels, the
  interaction of the two rate limiters)
- Anonymous → registration (enumeration via the generic response, verification token guessing,
  mass account creation, notification-based harassment of existing owners)
- Anonymous → password reset request and redemption (token guessing, token leakage via logs or
  referrer, the reset-to-takeover chain, race between two concurrent redemptions)
- Authenticated user → protected endpoints (session fixation, session theft via XSS, CSRF given the
  session-bound synchronizer token, privilege escalation)
- Authenticated user → self-service password change (current-password brute force through the change
  endpoint, which is an authenticated bypass of the login rate limiter — check it is also limited)
- Admin → user management (self-action guard bypass, IDOR on target user IDs, mass-disable as
  denial of service, tombstone abuse, role escalation)
- Scheduled and background paths, if any remain once hygiene jobs are out of scope
- Log and audit flow (log injection via username or email fields, PII leakage, secret leakage)

## Report

For each threat: the affected flow, STRIDE category, likelihood and impact, whether the current
design mitigates it, and the residual risk. Separate findings into:

1. **Design changes required** — these graduate into new tickets or reopen existing ones. Say which.
2. **Build-phase controls** — requirements to carry into the spec.
3. **Accepted risks** — with written justification, feeding "Produce the deferral register and ADR
   set". The known ones going in are: local HTTP, stubbed email, no MFA, no durable audit store,
   single-instance in-memory rate limiting, and enumeration via any path that survived. *Consolidated into the register (ticket 33): R-AUD-013, R-AUTH-001, R-CRED-020, R-OPS-003, R-RL-006. Amend the table by ID, not this list.*

## Done when

The threat model artifact exists and is linked here, and every "design change required" finding has
become a ticket rather than a note.

---

## Inherited from ticket 09 (§R, the ticket 21 reopening) — a quantified primitive, already in scope

**You inherit one attack fully worked out, with arithmetic, and it should be a named threat rather than rediscovered
in STRIDE.**

**Single-source mass permanent administrative lockout.** Implementing NIST SP 800-63B-4 §3.2.2's cap creates a state
that does not auto-lift, and the throughput identity is:

> permanent disables per hour = per-IP budget ÷ requests-per-disable = **3600 ÷ 100 = 36**

invariant under the escalating lockout ladder, because the ladder changes *when* the 100 requests land and not how
many there are. One host, at exactly its permitted rate, drives hundreds of accounts concurrently; **no limiter
engages on any track**, each sitting 240× inside the per-IP budget and 40× inside the per-account bucket. The
application supplies the discovery step itself: ticket 10's deliberate `USERNAME_UNAVAILABLE` makes usernames
*confirmable*, so ~240 are enumerated in ~48 minutes at the registration budget. Unauthenticated throughout.

**What bounds it, and what does not.** A third limiter axis — distinct accounts a source has driven into tier-1
lockout, cardinality `k ≈ 5` per hour — takes 36/hour to 5/hour per source: a **7× reduction and a louder signature,
not a fix**, and **IP rotation restores full throughput**, which is the App Standard's own objection to per-IP
limiting. The escalating ladder buys **lead time** (~10 hours between the alert at 50 and permanence, versus 3.3) and
is throughput-neutral. Recovery is an operator-invoked rebinding runner requiring **deploy-level access**, a strictly *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.*
higher bar than admin HTTP access.

**Three conditions that make the impact severe rather than annoying, all of them decided elsewhere and all correct on
their own terms:** ticket 13 confines the stubbed reset link to `dev`, so outside `dev` there is **no self-service
recovery artefact at all**; ticket 11 seeds **one** admin; and ticket 23's tier-2 factor disable bricks the same
population through a second, independent path. So a fresh non-`dev` deployment has a window — seed → login → forced
change → enrol → factor granted — in which the sole admin has no TOTP, no second admin and no reset channel.

**Two threats worth modelling that are *not* this one:**

- **Audit-stream password oracle.** A refusal evaluated in `postAuthenticationChecks` fires only when the submitted
  password was **correct**, so its audit reason confirms a guess. Ticket 11's 30-day `credentialIssuedAt` expiry has
  this today (reopen trigger filed). The threat actor is a **log reader**, which ticket 10 established is a real *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*
  actor in this build, and ticket 06's wire-uniformity rule does not reach it. *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*
- **Inverted rate limiter.** The new cardinality axis records on a lockout transition inside an event listener that
  has no `HttpServletRequest`. If the Route C converter does not set `WebAuthenticationDetails`, every lockout
  attributes to one key and the limiter `429`s **everyone** — a fail-closed self-denial created by a control added to
  bound a fail-open one. Worth modelling because the failure is a configuration absence, not an attack.

**Where to be sceptical of ticket 09.** Its own record says three positions in this round were reversed, two of them
by reading resolved tickets rather than by argument, and every reversed claim favoured the conclusion being argued.
Treat its residual pricing as the thing to stress-test first.

---

## Answer

**Fourteen threats over three diagrams, of which one is inherited and thirteen are new; two graded High; three
graduate into tickets and five amend resolved ones — and the two High findings are both cases where a control is
correctly enforced at a layer the threat does not pass through.**

Artefacts: [`threat-model/secured-hello-world.json`](../threat-model/secured-hello-world.json) (Threat Dragon
v2.2.0 schema — 3 diagrams, 6 trust boundaries, 14 threats) and
[`threat-model/report.md`](../threat-model/report.md), which carries the DFD description, the STRIDE pass per
flow and the full finding table. External facts:
[verification asset](../research/threat-model-external-fact-verification.md).

### 0. What this ticket was actually able to add

The design under test is exhaustively worked, and the honest headline is that **the eight flows this ticket named
produced almost nothing between them**. Login, registration, reset, self-service change and the admin surface are
not merely mitigated but argued, priced and tested; §2 of the report records each as clean and says why, because a
flow that came back clean is a result and deleting it would misrepresent the coverage.

Everything new came from three places, and naming them is the reusable part:

1. **Composition** — two individually-correct decisions that combine badly. Each ticket saw its neighbours; none
   saw the whole. Six of fourteen findings, including both Highs.
2. **The DFD itself** — drawing the flows surfaced that several controls run at a **band** rather than at an
   endpoint, and therefore apply to paths that no endpoint-keyed registry covers. That is the whole of TM-01.
3. **What arrived after the blockers closed.** This ticket was blocked by 08, 09, 10, 11, 12 and 20; the two
   privileged channels in TM-12 and TM-13 were designed after all six resolved, and ticket 25 records plainly
   that neither had been threat-modelled.

### 1. The two High findings, and the shape they share

**TM-01 — audit volume on pre-routing rows is attacker-set, which invalidates published arithmetic.**
Spring Security's ordering is verified, not assumed: exploit protection runs **before** authentication and
authorization. So `CsrfFilter` is evaluated on **every path**, including paths matching no controller, before any
authorization decision — and a CSRF-less `POST` anywhere emits ticket 13's **row 13** carrying a `url.path` that
is raw client bytes, on a route with no bucket, because **ticket 09's budget table is a route allowlist and no
ticket states its default**. Rows 12 and 14 arrive the same way through `/api/admin/**`, which is *deliberately*
unthrottled for a good reason. All three are in ticket 21's rate-above class, not transition-keyed, and
**row 46's cap bounds only `RATE_LIMITED_SOURCE` rows**.

This is the amplification ticket 13 found for rows 5 and 6 and closed with row 46, arriving through a door row 46
does not cover — and its target is the file ticket 13 deliberately gave no `total-size-cap`. The consequence that
makes it High rather than Medium is arithmetical: ticket 21 sizes the disk at `90 × daily` and the health
threshold at `daily × lead_days`, so **both numbers are computed from a quantity an unauthenticated attacker
controls**. Ticket 21 deferred *calibration* to the deployer honestly; it did not know the input was unbounded.

**TM-12 — the rebinding runner's `System.out` channel is the audit ingestion channel.**
Ticket 24 routes the rebinding token to `System.out` rather than through a logger, reasoning that a logger would
put it "in whatever appender the profile configures". Sound as far as it goes. But ticket 13 emits audit NDJSON to
**stdout in every profile** — calling stdout the Enforced Constraint, the platform ingestion path and the only
route to ASVS 16.4.3, and explicitly reversing a draft that would have dropped the console copy outside `dev` —
and ticket 25's sequence step 6 has the deployer install a forwarder over it. **They are the same file
descriptor.** A collector tails a descriptor; it does not distinguish bytes Logback wrote from bytes
`System.out.println` wrote, and on a container runtime that collection is unconditional and is the point of the
runtime.

So the control protects against *the application* writing the token to an appender and does nothing against *the
platform* collecting the identical bytes — for the credential of last resort, on a channel that exists in every
profile **by design**, for an account that by construction has no other route back in. Two consequences land on
ticket 25: its §4 grades this item `enforced` against four, and its step-7 rehearsal acceptance check ("confirm
the output warning holds") is **currently unpassable**.

**The shape both share, stated once because it is the transferable finding:** a control placed correctly at one
layer, against a threat that does not pass through that layer. TM-01's limiter is keyed on routes and the threat
is keyed on filters; TM-12's prohibition is scoped to the logging subsystem and the threat is scoped to a file
descriptor. Neither is a mistake anybody made carelessly — both are what you get when a mechanism is specified by
the ticket that owns it and the composition has no owner.

### 2. The other twelve, in one line each

Full argument per finding is in the model; the report's §3 table carries severity, mitigation status and residual.

- **TM-02** (Med, Spoofing) — `trace.id` is caller-supplied. W3C Trace Context ~~§4.3~~ §3.2 adopts a valid inbound
  `traceparent`; §7.2 names forged `trace-id` collisions as an attack on a public API. Ticket 13 made `trace.id`
  the **join key** of the authentication correlation chain. → ticket 27. *(Amended by
  [ticket 27](27-inbound-trace-context.md): §4 is non-normative, so the normative basis is §3.2/§3.4.
  **Mitigated:** every inbound trace is restarted at the boundary, as W3C §3.4 allows, and this is asserted by ticket
  16 row 4.)*
- **TM-03** (Med, DoS) — the tier-2 TOTP trip writes `users.force_password_change` while holding the
  `totp_user_details` lock, **inverting the order three tickets pinned**, against `AdminActionGuard`'s opposite
  acquisition. Asymmetric failure: the guard logs row 34 `LOCK_TIMEOUT`; ticket 23 deliberately does not fail
  open, so the same contention rejects a legitimate admin's **correct** code. → amend 23.
- **TM-04** (Med, EoP) — the factor gate is single-layered while the role gate is double-layered, *because*
  ticket 23 correctly abandoned `@EnableMultiFactorAuthentication`. A mis-written matcher removes the factor with
  nothing behind it. → amend 11 and 23; ticket 16 owes an assertion **enumerated from the matrix**.
- **TM-05** (Low, EoP) — `GET /api/hello`, PRD Story 5's only endpoint, has no matrix row, no budget row and no
  owner. **Fails closed** on `denyAll()`, so a completeness defect rather than a hole. → amend 11; budget half
  to 26.
- **TM-06** (Med, Tampering) — session attributes are JDK-deserialised on every request and no ticket states
  whether a filter is configured. Ticket 24 already concedes file *read* means live session hijack; *write* is a
  gadget path. The framework default is **unverified**, which is why this is a build check, not a reopening. *Consolidated into the register (ticket 33): R-SES-003. Amend the table by ID, not this list.*
- **TM-07** (Med, DoS) — every limiter is per-key; there is no global bulkhead, so aggregate BCrypt CPU and
  thread occupancy are unbounded across sources. Also narrows ticket 10's claim: the unauthenticated-BCrypt lever
  disappeared from **registration only**, not from the two redemption endpoints. *Consolidated into the register (ticket 33): R-CRED-016, R-RL-005. Amend the table by ID, not this list.*
- **TM-08** (Med, EoP) — **any admin can take over any other admin's account**, composing reset-token issuance
  (no `actor ≠ subject`, deliberately) with factor reset (bounded only by the two-admin guard). Inherent to the
  flat role the PRD asks for; nobody had stated it as a whole. → accepted risk. *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*
- **TM-09** (Low, Repudiation) — the tombstone records no role, and the audit row that says the deleted account
  was privileged expires on a TTL the application does not own, while the tombstone is indefinite. IM8 **ac-7**.
  → accepted risk, because the fix cuts against ticket 11's own PDPA argument. *Consolidated into the register (ticket 33): R-ADM-010. Amend the table by ID, not this list.*
- **TM-10** (Low, DoS) — row 46's `N`, the only audit-volume ceiling on the map, has **no value, no property key
  and no named binding test**; ticket 21 invoked the mechanism/constants seam rule and did not discharge it.
  → ticket 26, because the two numbers size the same disk.
- **TM-11** (High, Information Disclosure, **Mitigated**) — the log-reader actor. Inherited from tickets 10, 13
  and 19, carried explicitly because the actor must appear on the DFD or the diagram misrepresents the system.
- **TM-13** (Med, EoP, contingent) — activating ticket 25's §4.2.2.2 recovery-code route while the `dev` stub
  logs what it would send **inverts ticket 10 §12's containment** from partial to total for administrators, and
  §4.2.1.2's recovery-address confirmation travels the same channel. Not live: mail transport is the named
  prerequisite. → amend 25's existing trigger to carry the consequence. *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*
- **TM-14** (Med, EoP) — **three** non-mutation channels now change the two-enrolled-admins count: ticket 12's
  cascade, ticket 11 §R.3's NIST cap, and the two out-of-band channels. → amend 11 and 21; the
  zero-authenticable-admins signal must observe all three. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-002, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028. Amend the table by ID, not this list.*

### 3. Report, in the three parts this ticket asked for

**Design changes required** — three graduate, five amend. Which and why, per the map's rule (*amend unless the
amendment weakens a compensating control standing in for a declined `SHALL`, in which case reopen*):

| Finding | Disposition |
|---|---|
| TM-01, TM-05 (budget), TM-10 | **New [ticket 26](26-unbudgeted-routes-and-audit-volume.md)** — spans three resolved tickets, remedy is an open decision |
| TM-02 | **New [ticket 27](27-inbound-trace-context.md)** — no resolved ticket owns inbound trace context |
| TM-12 (+3 smaller on the same channel) | **New [ticket 28](28-out-of-band-privileged-channels.md)** — channel choice between three mechanisms |
| TM-03 | **Amend 23** — forced, one right answer |
| TM-04 | **Amend 11 and 23** + a ticket 16 assertion |
| TM-05 (matrix) | **Amend 11** — one row |
| TM-14 | **Amend 11 and 21** |
| TM-13 | **Amend 25** — explicitly *not* a reopening; the weakening is contingent on a trigger that has not fired, and the call is recorded so a later reader can disagree with it rather than guess *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.* |

**Build-phase controls** — eight assertions, handed to [ticket 16](16-test-plan.md) with its three new blockers.
Two shapes recur: three are **enumerated rather than written** (a hand-written list omits the route added next
year, which is the failure TM-04 *is*), and three are **negative assertions about a configuration someone adds
later** — the map's recurring form. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-002, T-MFA-007, T-RL-016, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028, T-RUN-003, T-CRED-023, T-SES-025, T-OBS-005. Amend the table by ID, not this list.*

**Accepted risks** — six rows, declared below in ticket 25's schema rather than as prose, because its document
and ticket 17's register are two renderings of one extracted table and a prose residual is a row nobody can
generate. The reconciliation of the six risks this ticket named *going in* is in
[ticket 17](17-deferral-register-and-adrs.md): five stand, and **"no MFA" is withdrawn** — ticket 19 put TOTP in
scope, so carrying it would be the register claiming a gap the map closed. *Consolidated into the register (ticket 33): R-AUD-013, R-AUTH-001, R-CRED-020, R-OPS-003, R-RL-006. Amend the table by ID, not this list.*

### 4. What I could not verify

Stated so ticket 18 does not read confidence into it. Whether Spring Session's default conversion service
installs **no** deserialisation filter — the hook exists, the default is undocumented, hence TM-06 is a check
rather than a reopening. Boot 4.1's default trace-context propagator and whether extraction can be disabled *Consolidated into the register (ticket 33): R-SES-003. Amend the table by ID, not this list.*
without losing outbound propagation — the standards-level claim is verified, the framework-level one is
**ticket 27's own research obligation and it must not assume the answer**. And the Threat Dragon model is
JSON-valid and schema-shaped but **has not been opened in Threat Dragon**; no instance is available here, so cell
geometry is plausible rather than laid out.

One thing I checked because the ticket told me to and expected to overturn: **ticket 09's residual pricing holds.**
`3600 ÷ 100 = 36` is right, the escalating ladder is genuinely throughput-neutral because it changes when the 100
requests land rather than how many, and the 7×-reduction-defeated-by-IP-rotation figure is honest rather than
flattering. Carried unchanged.

### Handover items (ticket 25)

Seven items. Each carries the obligation, the requirement ID **with its level**, what the application can enforce,
and the acceptance check — encoded in ticket 25 §3's schema.

- **Assume stdout is collected: prove the rebinding token did not reach the collector.** Ticket 25's step-7
  rehearsal currently checks that the output warning holds, which cannot be true on a collected stream; the check
  becomes "run the rebinding runner on the deployed topology and confirm the token is absent from the collector's
  index", which is strictly stronger evidence than confirming a warning was printed. Blocks on
  [ticket 28](28-out-of-band-privileged-channels.md) choosing the channel.
  *ASVS 6.4.1 (L1)* on the token, *16.4.3 (L2)* on the stream, *IM8 as-8*.
  Application enforcement: **partial and currently mis-graded** — the negative-list assertion and ticket 24's
  prohibited-configuration entry constrain the application and cannot constrain the platform.
  `responsibility: shared` · `status: procedural` · `priority: blocking` ·
  acceptance check: *the rehearsal, with collector-absence as the assertion*.

- **Recompute the audit disk sizing and the `diskspace` threshold against a bounded `daily`, and alert on the
  truncation row.** Ticket 21 gave the deployer the recomputation duty on mount resize; this adds that the input
  itself was unbounded until [ticket 26](26-unbudgeted-routes-and-audit-volume.md) bounds it, so the first
  computation must happen *after* 26 lands, not before. The truncation row (ticket 13 row 46) is the signal that
  the ceiling was reached and is per-event, not rate-above — it must not be aggregated away.
  *ASVS 16.4.1 (L2)*, *16.2.3 (L2)*; *IM8 lm-16* (Level 2, so a WARN here is a **High**); the org standard's
  §3.4 audit-failure condition.
  Application enforcement: **yes, in part** — it emits the truncation row and raises the disk-full ERROR; it
  cannot size a mount or hold an alert rule.
  `responsibility: deployer` · `status: enforced-elsewhere-cited` · `priority: required` ·
  acceptance check: *the sizing arithmetic is recorded with its inputs, and a rule on the truncation row exists in
  whatever consumes the stream*. *Consolidated into the register (ticket 33): R-OBS-013. Amend the table by ID, not this list.*

- **Pin aggregate request capacity, and keep the two saturation meters.** Every limiter on this map is per-key —
  per source IP, per submitted username, per submitted identifier — with no global bulkhead, so aggregate BCrypt
  CPU and thread occupancy scale with source count, which ticket 09 already concedes IP rotation makes free.
  `server.tomcat.threads.max` is pinned by the application; the deployer owns the capacity it is pinned to, and
  owns noticing saturation.
  *ASVS 6.1.1 (L1)*'s anti-automation limb; *NIST SP 800-63B-4 §3.2.2* as the calibration reference.
  Application enforcement: **yes for the pin and the meters, no for the sizing.** Worth naming: ticket 21's Tomcat
  and Hikari meters are the **only evidence for ticket 09's thread-exhaustion argument**, which is what declined
  sleep-based backoff — so losing them retroactively unsupports a decision.
  `responsibility: shared` · `status: asserted-by-test` · `priority: required` ·
  acceptance check: *the pinned value is recorded against the deployed instance size, and both meters are
  non-zero after a password path is exercised*. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-005. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-RL-005. Amend the table by ID, not this list.*

- **On any move off the dev H2 file, the session store's JDK deserialisation path is inherited and needs a
  filter.** Ticket 24 scoped H2-file risk to a developer workstation and established that file *read* means live
  session hijack; `SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES` is JDK-serialised by ticket 05's deliberate choice,
  so file *write* is a gadget path rather than a hijack. The framework default is unverified.
  *ASVS 13.2.2 (L2)* (accepted **F** on H2), *16.4.2 (L2)* by analogy on integrity.
  Application enforcement: **yes once decided** — an `ObjectInputFilter` allowlist over the four attribute types
  actually stored is a first-implementation check plus one bean.
  `responsibility: shared` · `status: unmitigated` · `priority: required` ·
  acceptance check: *the effective filter is recorded, and a non-allowlisted class fails to deserialise*. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-025. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-SES-003. Amend the table by ID, not this list.*

- **Admin-on-admin takeover has no preventive control; its only detector is one audit row.** Composing
  `POST /api/admin/users/{uuid}/password-reset` (no `actor ≠ subject`, deliberately) with
  `DELETE /api/admin/users/{uuid}/totp` lets any admin take over any other, bounded only by the two-enrolled-admins
  guard. Inherent to the flat role the PRD specifies. The detector is ticket 13's **row 18 with reason
  `ADMIN_RESET`**, which ticket 10 calls the sole detector of admin abuse — and it sits in a 90-day file the
  application owns and rewrites.
  *IM8 ac-7*; *ASVS 16.4.2 (L2)* **F** and *16.4.3 (L2)* **F** on the detector's own durability; *6.4.6 (L3)* is
  satisfied and does **not** reach this.
  Application enforcement: **no preventive part.** Detection only, and the detection's retention is not ours.
  `responsibility: deployer` · `status: unmitigated` · `priority: required` ·
  acceptance check: *an alert rule on row 18 with reason `ADMIN_RESET` exists, and its retention exceeds the
  in-application 90 days*. *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*

- **If role-at-deletion matters to the assessor, platform retention is the only thing that can preserve it.**
  `deleted_users` records no role and is retained indefinitely; the audit row that records the deleted account was
  an ADMIN expires on a policy ticket 03 established the application does not own. So privileged deprovisioning
  loses its evidence first, and the asymmetry is the reverse of the intuitive one.
  *IM8 ac-7*; *ASVS 16.1.1 (L2)* on the inventory's retention column.
  Application enforcement: **no** — and deliberately so: adding the column widens indefinite retention of
  personal-adjacent data, which cuts against the PDPA argument that made the tombstone email an HMAC.
  `responsibility: deployer` · `status: procedural` · `priority: recommended` ·
  acceptance check: *the platform retention policy value is recorded beside this row, so the horizon is a number
  rather than an assumption*. *Consolidated into the register (ticket 33): R-ADM-010. Amend the table by ID, not this list.*

- **RETIRED by [ticket 27](27-inbound-trace-context.md). Do not extract.** The application now restarts every
  inbound trace, so this obligation has disappeared, as the item itself anticipated. It is **replaced** by ticket *Consolidated into the register (ticket 33): R-OBS-015. Amend the table by ID, not this list.*
  27's first handover item ("know that the application restarts every inbound trace"), which cites IM8 **lm-4**,
  not lm-16: lm-16 is Key Signals Monitoring (ticket 01 line 193). The original text follows, struck, for the
  record.
  ~~**Any upstream proxy introduced later must strip or regenerate `traceparent`.**~~ The application currently adopts
  a caller-supplied `trace.id`, which ticket 13 uses as the authentication correlation join key. If
  [ticket 27](27-inbound-trace-context.md) decides to restart the trace at the boundary this obligation
  disappears; until it decides, a terminator or gateway in front is the only other place the header can be
  neutralised — and note this composes with ticket 09's existing handover line, because the same deployer decision
  that introduces a proxy also flips `app.security.client-ip.source` to `proxy`.
  *W3C Trace Context §7* (defensive parsing) and *§7.2* (denial of monitoring); *IM8 lm-16*.
  Application enforcement: **undecided, pending ticket 27** — validation and length-capping are ours either way.
  `responsibility: deployer` · `status: unmitigated` · `priority: recommended` ·
  acceptance check: *none possible — attests a named role is filled and its holder reachable*, until a proxy
  exists, at which point it becomes *an inbound `traceparent` does not survive the hop*.

### Counts

Fourteen threats (13 new, 1 inherited-and-carried); 2 High, 8 Medium, 3 Low, 1 Mitigated. **Three new tickets**
(26, 27, 28), **six amendments** to resolved tickets (11 ×3, 21 ×2, 23 ×2, 25 ×2 — six distinct items across four
tickets), **eight build-phase assertions** to ticket 16 plus three new blockers on it, **four new ADRs and two
amended** to ticket 17 plus three new blockers on it, **six accepted risks** reconciled with one withdrawn, and
**seven handover items**. Two artefacts and one verification asset. **No new fog** — every consequence landed as a
ticket, an amendment or a handover row.

---

## Amendment from ticket 28 — TM-12 closed, and its premise was one layer too shallow

TM-12 is **closed** by [ticket 28](28-out-of-band-privileged-channels.md). The finding was right that `System.out`
and the audit stream share a descriptor. But the deeper fact was that the runner could never execute beside the
application, because H2 file mode is single-writer. The resolution runs it offline and **emits no secret at all**:
the password goes in on stdin. So the collected-stdout premise no longer bears on confidentiality.

TM-12's three smaller findings are all answered:
- **Accountability:** mandatory `--operator`, recorded as `labels.operator_claimed_id`, with an OS-reported
  corroborating user.
- **Guard bypass:** stated as TM-14's third exception and made observable through admin counts plus a zero-transition
  alert.
- **Batch safety:** plan/apply with a state-bound digest, a pre-write cap, and a single transaction.

The argument-gating assertion went to ticket 16. Your record of 6.4.6 (L3) as satisfied **stands for the in-app
admin paths**; ticket 28 withdraws it on the runner path only. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-003. Amend the table by ID, not this list.*

---

## Amendment from ticket 27 — TM-02 mitigated

**TM-02 is mitigated** by [ticket 27](27-inbound-trace-context.md). Every inbound trace is restarted at the
boundary: a header-stripping wrapper at `HIGHEST_PRECEDENCE` removes `traceparent`, `tracestate`, `b3`, `X-B3-*`
and `baggage` before the observation filter reads them. W3C §3.4 names this "Restart trace" and describes it as
removing a denial-of-service attack surface. The mitigation is asserted by ticket 16 row 4, which is restated and
now includes a pinned-value collision case. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028. Amend the table by ID, not this list.*

**Two corrections to this ticket:**

1. **The W3C §4.3 citation is corrected in place.** §4 is non-normative, so the normative basis is §3.2/§3.4.
2. **The proxy handover item is retired in place.** Its replacement lives in ticket 27, where ticket 25's
   extraction will find it. Its control ID was also wrong: it cited lm-16 where lm-4 is the right control.

**Counts:** the handover-item total above ("seven") now includes one retired item. Ticket 27 adds two items: the
replacement, and a new Tomcat access-log item.

**Also recorded:** the one browser path, a hostile page pinning the header, was never open. The Fetch spec plus
ticket 05's `allowedHeaders` block it. `SameSite=Strict` is not the control there.

## Amendment from ticket 31 (IPv6 source keying)

`15:73-76` inherits two errors from ticket 09, both corrected there (09's ticket 31 amendment, items 2–3):

- "takes 36/hour to 5/hour per source: a 7× reduction" is a units error. The axis allows ≈0.36 disables an hour per
  bucket (≈100×), and the ladder binds from ≈20 source keys (`P ÷ k`).
- "~10 hours between the alert at 50 and permanence" is ≈9.7 h. 19 locks precede the cap, not 20.

The same figures appear in `threat-model/report.md:92` and the JSON model's IP-rotation notes (`json:118`). Update
them when the model is next regenerated. Per-source threats now key on the source key (IPv4 /32, IPv6 /64 by
default); see [ticket 31](31-ipv6-source-keying.md) §8 for the re-priced residuals, including one AWS VPC as the
cheapest supply of 256 keys.

---

## Amendment from ticket 30 — TM-08's bound is removed at exactly two admins

[Ticket 30](30-sole-admin-bootstrap-premise.md) exempts `DELETE /api/admin/users/{uuid}/totp` from the two-admin
count (actor ≠ subject stays), because at exactly two admins the guard refused the lost-phone reset ticket 19 relied
on. TM-08's text at 15:196–197 and 15:309–312 ("bounded only by the two-enrolled-admins guard") changes:

- **At exactly two enrolled admins, A can now take B over completely** — password reset plus factor reset, and A
  holds B's reset token, so A can be the one who re-enrols as B. Before, A got the password but not the factor. At
  three or more nothing changes; it was already open.
- **Detectors:** ticket 13 row 18 `ADMIN_RESET` and row 33 `totp-remove`. If and only if the deployer enables OTLP
  export, the authenticable-admins gauge drops below 2 during the takeover step. It also fires on every legitimate
  factor reset at two, and not at all at three or more (3 → 2), so it is a notification, not a discriminator.
- **TM-14:** the guard enforces the count on **three** mutating paths (disable, demote, delete), not four.

Severity unchanged (Med); status unchanged (accepted risk). *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*
