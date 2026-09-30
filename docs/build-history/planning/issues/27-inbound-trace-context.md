# 27 — Decide inbound trace-context handling: `trace.id` is currently the caller's to choose

Type: grilling
Status: resolved
Blocked by: 03, 13, 21

## Question

Where does `trace.id` come from on an inbound request, and what happens to the audit correlation chain
once the answer is "the caller"?

Graduated from [ticket 15](15-threat-model.md) as **TM-02**.

## The finding

Three tickets built on `trace.id` and none asked where it originates.

- **Ticket 03** adopted `spring-boot-starter-opentelemetry` *for* `trace.id` (`03:275`, `03:307`).
- **Ticket 06** routes the internal login-failure reason to the audit log "keyed by `session.hash` and
  `trace.id` where the UUID is not yet resolved" — which ticket 13 then called the decisive argument for
  putting `user.id` on resolved failure rows.
- **Ticket 08 and ticket 13** made it a **join key**. The authentication correlation chain works because
  rotation happens inside the login request, so a `session-start` row carrying the pre-rotation hash and the
  login-success row carrying the post-rotation hash "**share one `trace.id`**". Ticket 13 records the
  alternative explicitly: placed wrongly, "the join silently evaporates — no test fails, the chain just
  stops working".
- **Ticket 21** keeps the audit listener synchronous specifically so MDC — `trace.id`, `session.hash`,
  `user.id` — survives.

The W3C Trace Context Recommendation, checked at
[verification asset §3](../research/threat-model-external-fact-verification.md):

- **§4.3** — a received `traceparent` is *used*. A conforming implementation parses the version, validates
  `trace-id` and `parent-id`, and generates fresh identifiers **only if** parsing or validation fails. §4.2
  confirms the converse. So a well-formed inbound `trace-id` becomes the trace identity.
- **§7.2** — for a publicly reachable API that continues any incoming trace without question, the
  specification names contriving **`trace-id` collisions that render the monitoring data useless** as an
  attack, alongside loading the application with tracing work. It places the obligation on implementers to
  have checks against denial *of monitoring* by malicious callers, and offers as an example protection
  **treating authenticated and unauthenticated requests differently**.
- **§7** — anyone relying on these headers is told to apply defensive parsing to them, length and content
  included.

So: an unauthenticated caller pinning `traceparent` to a constant collapses the correlation space that ticket
13's chain depends on, and pinning it to a value a victim's requests also carry poisons an investigation. The
join key is not a server-side fact.

## Why this is its own ticket

No resolved ticket owns inbound trace context. Ticket 03 chose the starter for an outbound-shaped reason and
ticket 13 built a correlation guarantee on top of the result. The remedy is a real choice with three
candidates, one of which is "accept it", and each changes what other tickets may claim.

## What to decide

- **Restart, gate, or accept.**
  - *Restart at the boundary* — always generate a fresh `trace-id`, never continue an inbound trace. Cheapest
    to reason about, and it costs nothing we use, because nothing upstream of this application participates
    in a trace: there is no gateway, no proxy (`framework` forwarding is prohibited), and the SPA does not
    emit `traceparent`. State that plainly if it is true, because it makes this a free decision rather than a
    trade.
  - *Gate continuation on authentication*, which is the specification's own suggestion. More faithful to
    distributed tracing and more machinery, and it does not help the flow that matters most — the audit rows
    on the login path are written for requests that are **not yet** authenticated.
  - *Accept*, and stop describing `trace.id` as a join key. This is the option that must be priced, not
    dismissed: if it is taken, ticket 13's pre/post-rotation join needs a different key or ticket 08's
    `session-start` row needs a field, which is the cost ticket 08 avoided by using `trace.id`.
- **Validation independent of the above.** §7's defensive-parsing obligation binds whichever option is taken:
  length cap and content validation on `traceparent` and `tracestate`. Ticket 13 already caps and strips the
  raw-URI field at the single emitter for the same reason; this is the same control on a second input.
- **Whether `trace.id` may appear on an audit row at all if it is caller-chosen.** Ticket 13's negative-list
  discipline is strict about what reaches the audit stream. A caller-controlled string on every row is not a
  secret leak, but it is attacker-supplied content in an evidence stream, and the map has a rule about that.
- **The research obligation, stated because it must not be assumed.** Boot 4.1's default propagator and
  whether extraction can be disabled without losing outbound propagation are **not verified**. Searching
  returned only secondary sources, which the verification asset does not accept. "Extraction is on by
  default" is very likely and is exactly the shape of claim the map's verification rule exists to catch —
  five of eleven such facts were wrong in ticket 09's reopening, every one favouring the argument being
  made. Check it against Boot and Micrometer reference documentation before deciding.

## Done when

The inbound-continuation decision is made and the cost to ticket 13's join key is either nil or paid;
`traceparent` validation is specified; Boot 4.1's actual default is verified against a primary source and
recorded in `research/`; and if continuation is accepted, the correlation chain names what it rests on
instead.

## Answer

**Restart every inbound trace at the application boundary. No inbound trace header, valid or not, ever sets
`trace.id`. `trace.id` is therefore a server-generated random value per request again, and ticket 13's
correlation chain costs nothing.** Baggage is switched off. `correlation.id` is dropped.

Every framework fact below is verified at source in the
[verification asset](../research/inbound-trace-context-propagation-verification.md) and cited by section (§n),
not restated. One fact there is **UNVERIFIED** (an empty `consume` list, §3.2), and nothing below rests on it.

### 1. Corrections to this ticket and to the tickets it inherits from

- **Ticket 15 cites W3C §4.3 as normative. It is not.** W3C §4 ("Processing Model") is marked non-normative.
  The conclusion stands on §3.2 and §3.4, together with OTel's own extraction code (§5). Ticket 15 is amended.
- **Boot accepts three inbound formats, not one.** The default `consume` is `[W3C, B3, B3_MULTI]`, while
  `produce` is `[W3C]`, and there is no `NONE` value (§1). A control that only looked at `traceparent` would miss
  `b3` and `X-B3-*`.
- **The trace identity is fixed before Spring Security runs.** `ServerHttpObservationFilter` is registered at
  `HIGHEST_PRECEDENCE + 1` and the Security chain at `-100` (§2). So the "gate on authentication" option cannot be
  built in the Security chain.
- **The ticket said the caller can suppress `trace.id`. It cannot.** A caller sending `-00` suppresses span export,
  but `traceId` still reaches MDC (§6). The real lever is the other direction: `-01` bypasses the 0.1 ratio and
  forces export. Restart removes that lever too.
- **Ticket 13 line 153 implies that `trace.id` is §3.3's "correlation ID" but never says so.** This ticket says it
  explicitly (§3).

### 2. Decision: restart, gate, or accept

**Restart**, which W3C §3.4 names as an allowed mutation ("Restart trace"). It is intended for services acting as a
front gate, removes a denial-of-service attack surface, and comes with the instruction that `tracestate` SHOULD be
cleaned up (§5).

**It costs nothing we use, because nothing takes part in a trace:**

- **Nothing upstream.** Ticket 20 dropped the reverse proxy, and ticket 09 prohibits `framework` forwarding.
- **Nothing in the browser.** Ticket 14 has no browser tracing.
- **Nothing downstream.** Mail is a stub, HIBP was declined (ticket 07), and ticket 21 disabled OTLP metrics.
- **Nowhere to send traces.** No trace exporter endpoint is configured (ticket 03).

**The CORS question is closed by citing controls that already exist, not by a new check.** A browser cannot send
these headers cross-origin:

- **In `cors` mode, the preflight fails twice.** Ticket 05 line 784 sets `allowedHeaders` to `Content-Type` and
  `X-CSRF-TOKEN` only. `traceparent`, `b3` and `X-B3-*` are not CORS-safelisted request-headers (Fetch Living
  Standard). A hostile origin also fails ticket 05's `allowedOrigins`.
- **In `no-cors` mode, the Fetch spec drops the headers silently.** Anything that is not a no-CORS-safelisted
  request-header is removed before the request is sent.
- **`SameSite=Strict` (ticket 20) is not the control here.** It keeps the victim's cookie off the request and does
  nothing about the header.
- **The TM-02 attacker is a direct caller anyway**, using curl or similar, where CORS does not apply. Under restart,
  none of this matters.

**Gate on authentication: rejected.** It is technically possible by replacing the receiver handler (§3.4 option
C), which runs before authentication and could inspect the request. It is still the wrong choice. It is custom
code at the observation layer, and it cannot protect the path that matters most: the audit rows on the login path
are written for requests that are not authenticated yet.

**Accept: priced and rejected.** Accepting caller trace IDs would cost four things:

- Ticket 13's row 8 and row 1 would need a new pre-rotation-hash field.
- Ticket 06's "`traceId` is per-request by definition" would become false.
- The operator's handle in the error envelope would become something an attacker can plant.
- The `-01` export lever would remain.

### 3. What `trace.id` now is, and ticket 27's third question

- **`trace.id` is server-generated on every request:** 128 random bits, never influenced by the caller. It is the
  **correlation ID** that User Access Standard §3.3 requires on every audit event ("Request path, HTTP method, and
  correlation ID"). This is stated here explicitly, not inferred from ticket 13 line 153.
- **The third question is resolved.** The ticket asked whether `trace.id` may appear on an audit row if the caller
  chooses it. Under restart the caller never chooses it, so no attacker-supplied content reaches the audit stream
  through this field. Ticket 13's negative-list discipline is untouched.
- **Ticket 13's join key is a server-side fact again.** The pre-rotation/post-rotation join between row 8 and row 1
  (tickets 08 and 13) rests on this decision. Pinning one `traceparent` across many requests (the TM-02 attack)
  now produces a different `trace.id` on every request. Both tickets are amended to name what the join rests on.
- **Ticket 06's "`traceId` is per-request by definition" is now true, and depends on this decision.** Ticket 06 is
  amended.

### 4. Mechanism: a header-stripping request wrapper

**Where it sits:**

- **A servlet filter registered at `Ordered.HIGHEST_PRECEDENCE`,** strictly before `ServerHttpObservationFilter`
  at `HIGHEST_PRECEDENCE + 1` (§2). A Spring Security filter would be too late. This is the only option whose hook
  ordering is verified.
- **Registered for at least the observation filter's dispatcher types**, `REQUEST` and `ASYNC`.

**Which headers it strips.** The strip set is a fixed list, together with `ContextPropagators` `fields()` read at
startup (§3.4 A):

- **The fixed list:** `traceparent`, `tracestate`, `b3`, `X-B3-TraceId`, `X-B3-SpanId`, `X-B3-ParentSpanId`,
  `X-B3-Sampled`, `X-B3-Flags`, `baggage`.
- **Why both.** The fixed list keeps `baggage` stripped even once baggage is off. The `fields()` half means a header
  that a Boot or OTel upgrade starts reading is stripped at runtime anyway.

**How it wraps the request:**

- **Header names are matched case-insensitively.**
- **Stripped headers are hidden from all three lookup methods:** `getHeader`, `getHeaders` and `getHeaderNames`.
  Micrometer's getter only calls `getHeader` (§2), but other code may enumerate the headers.

**What it leaves alone:**

- **`consume` stays at its default.** Narrowing it would be a second control that is only inferred (§3.2, §3.4 D).
  One tested control is easier to reason about than two.
- **`type` stays unset**, because setting it overrides both directions (§3.1).

**Guard test, pointed the direction that can fail.** Build a context with the default propagation config and
baggage on, then assert that `fields()` is a **subset** of the fixed list. If an upgrade starts reading a new
header, this test fails and someone reviews the list. The union still strips the new header at runtime, so nothing
breaks in the meantime. The opposite assertion (the union contains the fixed list) is true by construction, and
that direction is prohibited as the guard. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-015. Amend the table by ID, not this list.*

### 5. Baggage off

**`management.tracing.baggage.enabled=false`.** Nothing on the map uses baggage. It is parsed by default, and it
becomes caller-controlled MDC the moment a later ticket adds a name to `correlation.fields` (§4). Setting it to
`false` removes both the W3C baggage extractor and `Slf4JBaggageEventListener`, and `traceId` still reaches MDC
(§4, §6).

**The test is behavioural, not a check on a config value.**

- **Setup:** set `correlation.fields` (and `remote-fields`) to a probe key in the test's properties, leaving the
  application's `baggage.enabled` as it is.
- **Requests:** send the probe as `baggage: <key>=<value>` and as a header named `<key>`.
- **Assertion:** the value reaches neither MDC nor the log line.
- **What it catches:** it fails whenever someone re-enables baggage with a field set, whichever property they
  changed. A test that `correlation.fields` stays empty would check a value that has no effect while baggage is off. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-012. Amend the table by ID, not this list.*

**Where it is recorded:** as an amendment to ticket 03, which owns the tracing configuration. It is not a
deferral-register line, because it defers nothing. **Reopening trigger:** any ticket that needs a correlation
field. That ticket must decide how a caller-set value is kept out of the audit stream. *Consolidated into the register (ticket 33): R-OBS-014. Amend the table by ID, not this list.*

### 6. `correlation.id` dropped

**Ticket 03's optional Tier D `correlation.id`, read from an inbound `X-Correlation-ID`, is the same problem as
`traceparent` in a second place, and it is dropped.**

- Ticket 03 already leaned this way at line 277, which calls the field optional and says to skip it when `traceId`
  suffices.
- With restart, the server-generated `trace.id` is the correlation ID (§3), so a second ID would be a synonym with
  no reader.
- Reading it from the header would reintroduce exactly what §2 removes.

**Ticket 03 is amended in three places:**

- Tier D (line 132): the entry is struck.
- Line 277, part (a) only: struck. Part (b), the `MdcUserFilter` placement for `user.id`, stays.
- Line 278, the `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)` note for correlation IDs on Security output:
  struck.
- **Line 279, the `MDCInsertingServletFilter` ban, stays.**

`X-Correlation-ID` is **not** added to the strip set. Nothing reads it once the field is gone, and the fixed list is
scoped to trace propagation.

### 7. Validation of the header: not parsed, not logged

The ticket's "Validation independent of the above" obligation comes from W3C §7 (defensive parsing), and is
discharged by **not parsing**. What is left of it is one property: the raw header value **never reaches an
application log line**. The only length bound left on the header is the container's
`server.max-http-request-header-size`, 8KB by default (§5).

**Scope of "never logged".** The wrapper only covers the application's own logs. Tomcat's access log valve runs
before any servlet filter and can record raw request headers if its pattern includes them. It is off by default
in Boot (`server.tomcat.accesslog.enabled`). So the claim is **"application log lines, with the access log
disabled"**, not "any log line", and enabling the access log with a `%{…}i` pattern that names a trace header is a
handover item. *Consolidated into the register (ticket 33): R-AUD-032. Amend the table by ID, not this list.*

**Ticket 16 row 4 is restated** as *"no inbound trace header, valid or not, ever sets `trace.id`"*. It is asserted:

- **Once per format:** a valid `traceparent` with and without `tracestate`, single-header `b3`, and multi-header
  `X-B3-*`. Each time, the logged `trace.id` differs from the inbound trace-id.
- **For collision:** two requests carrying the same pinned `traceparent` get **different** `trace.id`s. This is the
  actual TM-02 attack, and a check on one request at a time does not prove it.
- **For sampling:** set the sampling probability to `0.0` for this case (ticket 03 line 276 sets `1.0` for local and
  test runs, which would mark every span sampled whatever the caller sends). A request carrying `-01` must produce
  a span that is **not sampled**. "Does not force export" is not the assertion, because no exporter exists, so it
  would pass trivially.
- **For logging:** the raw header value appears in no application log line, with the access log disabled.
- **Alongside it:** the guard test in §4 and the baggage test in §5. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028. Amend the table by ID, not this list.*

### Handover items (ticket 25)

- **Know that the application restarts every inbound trace, and do not re-enable continuation by configuration.**
  - **Obligation:** a deployer who puts a tracing gateway in front of the application will find their traces never
    continue into it. The fix is to reopen ticket 27, not to change configuration or remove the wrapper. Replaces
    ticket 15's "any upstream proxy must strip or regenerate `traceparent`" item, which disappears under restart,
    as ticket 15 lines 330–334 already anticipated.
  - **Discharges:** IM8 **lm-4** Audit Logging (MUST, risk 1|0; ticket 01 line 190). The restart is what keeps
    ticket 13's audit correlation chain a server-side fact. Also W3C Trace Context §3.4 (Restart trace) and §7.2.
  - **Application enforcement:** yes, tested by ticket 16 row 4.
  - **Fields:** `responsibility: deployer` · `status: asserted-by-test` · `priority: recommended`.
  - **Acceptance check:** an inbound `traceparent` never matches the logged `trace.id`.
  - **Reopening triggers:** an upstream tracing gateway introduced by the deployer; or the SPA sending *Consolidated into the register (ticket 33): R-OBS-016. Amend the table by ID, not this list.*
    `traceparent`, which cannot happen until the header is added to ticket 05's `allowedHeaders`. A note sits
    beside that line. *Consolidated into the register (ticket 33): R-OBS-015, R-OBS-017. Amend the table by ID, not this list.*
- **Keep trace headers out of the Tomcat access log.** *Consolidated into the register (ticket 33): R-OBS-017. Amend the table by ID, not this list.*
  - **Obligation:** if the access log is enabled, its pattern must not name a trace header (`%{traceparent}i`,
    `%{tracestate}i`, `%{b3}i`, `%{X-B3-*}i`, `%{baggage}i`). The valve runs before the wrapper, so it sees raw
    caller bytes.
  - **Discharges:** IM8 **lm-4** (MUST, risk 1|0), by keeping attacker-supplied content out of the log stream, and
    W3C Trace Context §7 (defensive handling).
  - **Application enforcement:** no. The access log is disabled by default, and the application cannot constrain a
    pattern the deployer sets.
  - **Fields:** `responsibility: deployer` · `status: unmitigated` · `priority: recommended`.
  - **Acceptance check:** `server.tomcat.accesslog.enabled` is false in the deployed configuration, or the
    configured pattern contains none of the listed header names. *Consolidated into the register (ticket 33): R-AUD-032. Amend the table by ID, not this list.*

### 8. ADRs, glossary, register

**One new ADR.** This is ticket 17's item 3 (lines 285–287): *"Restart inbound trace context at the application
boundary"*. It must carry:

- the decision and the three options, with gate and accept priced as in §2;
- the wrapper mechanism and its strip set;
- W3C §3.4 as the authority;
- **the reopening triggers, stated in the ADR itself**, because a future reader who wants to continue traces will
  look there first:
  - an upstream tracing gateway; *Consolidated into the register (ticket 33): R-OBS-016. Amend the table by ID, not this list.*
  - the SPA sending `traceparent`, whose tripwire is ticket 05's `allowedHeaders`; *Consolidated into the register (ticket 33): R-OBS-017. Amend the table by ID, not this list.*
  - an outbound call to a traced service. *Consolidated into the register (ticket 33): R-OBS-018. Amend the table by ID, not this list.*

  Any one of these means continuation needs a **trusted-boundary** decision, meaning which hop's trace is
  believed. It is not a configuration change. *Consolidated into the ADR routing (ticket 34): ADR-063. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-OBS-015, R-OBS-016. Amend the table by ID, not this list.*

**No other ADRs:**

- *Baggage off* is trivially reversible. It is an amendment to ticket 03. *Consolidated into the ADR routing (ticket 34): REJ-087. Amend by ID, not this list.*
- *`correlation.id` dropped* is not surprising given ticket 03 line 277. It is an amendment to ticket 03. *Consolidated into the ADR routing (ticket 34): REJ-088. Amend by ID, not this list.*

**One glossary term, owed:**

- **Trace restart:** discarding every caller-supplied trace-context header at the application boundary, so each
  request begins a new trace with a server-generated `trace.id`. It is the opposite of *continuing* a trace. It is
  not the same as "validating" a trace header, because nothing is parsed.

**Register:** no deferral lines. The one UNVERIFIED fact (§3.2, an empty `consume` list) is **not used**, so it
needs no register line.

**Amendment rule (ticket 13 §1) applied:** none of these amendments weakens a compensating control standing in for
a declined `SHALL`. Every one of them removes a caller-chosen value. So they are amendments, not reopenings.

### 9. Amendments made

- **03:**
  - Tier D `correlation.id` struck (line 132).
  - Line 277 part (a) and line 278 struck; line 279 kept.
  - Baggage off, and the §4 and §5 tests.
- **05:** a tripwire note beside `setAllowedHeaders` (line 784).
- **06:** "`traceId` is per-request by definition" (lines 163–171) now names this decision as what makes it true.
- **08 and 13:** the join-key paragraphs (08:596–602, 13:446–448) now name the restart as what the join rests on.
- **15:**
  - The TM-02 citation is corrected from §4.3 to §3.2/§3.4.
  - The proxy handover item is replaced by §Handover item 1 here.
- **16:** row 4 restated as in §7.
- **17:** item 3's ADR and the glossary term.
