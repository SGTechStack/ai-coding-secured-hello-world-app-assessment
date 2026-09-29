# 05c — Tracing, MDC and batch-job logging: what binds a single-service app

Part of research ticket [05 — Which logging standards actually bind a two-process app](../issues/05-logging-standards-applicability.md).

Scope of this note: `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md`,
`Recipes/Understanding_OpenTelemetry_And_Micrometer_Tracing.md`, `Recipes/Enriching_Logs_With_MDC.md`,
`Recipes/Logging_Batch_And_Scheduled_Jobs.md`. Citations to the parent standard
(`Structured_Logging_Application_Standard.md`), `Log_Schema.md` and
`Structured_Logging_Application_Standard_Questions.md` are included where they are the
binding authority for a recipe's behaviour; the full read of those three is another
sub-agent's scope.

Short names used below: **Trace** = `Structured_Logging_Trace_Correlation_And_Context_Propagation.md`;
**Understanding** = `Understanding_OpenTelemetry_And_Micrometer_Tracing.md`; **MDC** =
`Enriching_Logs_With_MDC.md`; **Batch** = `Logging_Batch_And_Scheduled_Jobs.md`; **Standard**
= `Structured_Logging_Application_Standard.md`; **Questions** =
`Structured_Logging_Application_Standard_Questions.md`.

## 0. A note on modal language in these four files

The four recipes are written as *guides*, not as normative clause lists. They contain almost
no `MUST`/`SHALL`. Their strongest words are "must" (lowercase, ~5 uses), "required",
"recommended", "Use X when…", "skip this section if…", and the explicit section-level
applicability gates ("This section is only relevant if…"). **The normative force for
anything in these recipes comes from the parent standard**, which does use `MUST` and
labels decisions `[Enforced Constraint]` vs `[Design Choice]` (Standard.md:299-320).

Practical consequence for this effort: a requirement that exists *only* in a recipe and is
phrased "recommended" / "the recommended approach" is RECOMMENDED. A requirement that the
parent standard states as a required field or an `[Enforced Constraint]` is MANDATORY, and
the recipe merely shows how.

---

## 1. The key finding: trace correlation with one service and no downstream hop

### (a) Is a trace/correlation id mandatory on every log line here?

**MANDATORY — yes, an id must be on every log line; but the standard offers a choice of
which id, and its wording is conditional.**

- `trace.id` and `span.id` are listed under the heading **"Required fields (always present
  in every log event)"** — `Standard.md:123`, item at `Standard.md:126`:
  > "`trace.id`, `span.id` — auto-populated by Micrometer Tracing **when a span is active**;
  > do not set manually"

  Note the two qualifiers in one line: the field is in the *always present* list, yet its
  population is conditioned on "when a span is active", and the application is forbidden
  from setting it by hand. This is the single most important sentence for this ticket.

- `correlation.id` is **NOT** in the required list. It is a *contextual* field:
  `Standard.md:132` — "`correlation.id` — end-to-end business process correlation **when** a
  single `trace.id` does not span the full workflow (e.g., a process that triggers multiple
  independent traces across time or systems)". `Log_Schema.md:96` describes it the same way.

- Independently of the field list, the standard's happy-path flow requires *an* identifier
  per operation: `Standard.md:39` — "This is achieved by assigning a unique identifier, the
  correlation ID, at the start of the operation and attaching it to **every log entry** so
  that the complete sequence of events can be traced across all stages and services."
  `Standard.md:49-53` restates it as a per-request obligation (propagate if present,
  generate if not, log request metadata, "clear the correlation context afterward").

- The recipe's own verification bar: `Trace.md:1074` — "The `trace.id` and `span.id` fields
  are present in **every** log entry for the flow" and `Trace.md:1075` — "The `trace.id`
  remains identical across the entire flow."

**Does the requirement only bite across process boundaries? NO — the standard is explicit
that it does not.** `Standard.md:39` scopes it to "an operation's full lifecycle" within one
service, and `Trace.md:1005` gives the intra-service rationale: "a field missing from some
log entries produces incomplete aggregations… Fields that belong to a whole operation
should be set in MDC so they appear on every related entry automatically." Cross-service
propagation is an *additional* surface (`Standard.md:169`: "Propagate and log trace context
(W3C Trace Context) **when available**"), not the reason the id exists.

**SILENT on:** whether `trace.id` may be absent when no tracer is installed. The
"when a span is active" qualifier at `Standard.md:126` combined with `Standard.md:164`
("If required fields are absent, the application MUST still emit the event and MUST NOT
suppress or drop it silently") is the only handling of the absent case.

**My reading (INFERENCE, flagged):** the required-field list at `Standard.md:123-129` is
satisfied by `trace.id`+`span.id` when a tracer is installed, or by an
application-generated per-request id when one is not — because `Standard.md:39` and
`:49-53` make the *identifier on every line* the actual obligation and are agnostic about
which mechanism supplies it. `correlation.id` is genuinely not required for this app on the
standard's own terms: there is no multi-trace business process here (no jobs, no async
workflow spanning requests), which is the exact trigger `Standard.md:132` and
`Trace.md:758-762` name. So: **one id per line is mandatory; `correlation.id` specifically
is not.** This is a reading, not a quote — see human decision Q1.

### (b) Must the id be generated/propagated by the SPA, or may the backend mint it?

**The backend MAY mint it. The SPA is NOT required to send a header. This is explicit, in
three places.**

- `Standard.md:50-51`:
  > "**Propagate Correlation ID:** **If** an incoming request includes a correlation ID from
  > an upstream service, propagate it… **Generate Correlation ID:** **If no correlation ID
  > is present in the request, generate a new unique identifier** to start the trace for
  > this operation"
- `MDC.md:21` — the filter "reads the `X-Correlation-ID` header from the incoming request,
  **or generates a new UUID if none is present**". The code at `MDC.md:46-47` is exactly
  `Optional.ofNullable(request.getHeader("X-Correlation-ID")).orElse(UUID.randomUUID().toString())`.
- `Questions.md:442` — "Extract correlation ID from upstream headers (e.g.,
  `X-Correlation-ID`, `X-Request-ID`). **Generate a UUID if not present.**"

`Questions.md:444` adds a RECOMMENDED, not a mandate: "> **Recommendation:** Always
generate a correlation ID if upstream doesn't provide one to ensure complete traceability."

`MDC.md:16` states when the inbound-header path applies at all:
> "Sections 3.1 and 3.2 apply when external callers (such as a partner system, API gateway,
> or **frontend**) supply their own `X-Correlation-ID` that must be accepted and propagated,
> or when the application does not use Micrometer Tracing."

So a React SPA setting `X-Correlation-ID` is one of the contemplated triggers — but it is a
*choice this effort makes*, not an obligation the standard imposes. Nothing in the four
files requires the SPA to originate an id.

**Security note that bites if the SPA does send one:** `Understanding.md:93` —
> "**Security**: Spring Boot accepts incoming `traceparent` headers by default. For public
> endpoints, most production systems strip trace headers at API gateways/load balancers to
> prevent untrusted trace injection."

Accepting a browser-supplied correlation id means accepting attacker-controlled data into
every log line. The standard's log-injection rules (`Standard.md:380`, `Questions.md:385-387`)
then apply to that value. See human decision Q2.

### (c) Is Micrometer Tracing / OpenTelemetry instrumentation mandatory?

**Not as an `[Enforced Constraint]`. Every mention of it is `[Design Choice]`, a Note, or
"recommended". But the mandatory-field list names it as the mechanism, which creates a
genuine tension.** Quoting the modal language:

Against mandatory:
- `Standard.md:302` — "**[Design Choice]** Correlation IDs in logs **when** Micrometer
  Tracing is on the classpath. The default correlation uses `traceId` and `spanId`…"
  (Contrast `Standard.md:319-320`, which are labelled **[Enforced Constraint]** — and those
  are about structured/ECS format, not tracing. Nothing about tracing carries that label.)
- `Standard.md:308` (a Note, not a constraint) — "Micrometer Tracing **is** the Spring
  Boot-native tracing abstraction… **When OTel export is required** (e.g., to an OTel
  Collector or a vendor backend), add `micrometer-tracing-bridge-otel`… **Use the OTel SDK
  directly only when** the platform mandates OTel as the telemetry standard across polyglot
  services, or when OTel-specific features are needed…"
- `MDC.md:16` — "**If Micrometer Tracing is already in use** and `traceId` is sufficient as
  the correlation identifier, skip sections 3.1 and 3.2… Sections 3.1 and 3.2 apply when…
  **or when the application does not use Micrometer Tracing**." The standard set therefore
  explicitly contemplates an app with no Micrometer Tracing.
- `MDC.md:540` — an entire subsection headed "**Without Micrometer Tracing**", giving the
  manual `TaskDecorator` fallback (`MDC.md:545-555`).
- `Questions.md:393-406` — Q19 Step 1 asks the question as a *choice*, and states:
  > "You do **NOT** need distributed tracing if: — Your application is a standalone service
  > with **no downstream dependencies** — All operations are self-contained within one
  > service" (`Questions.md:404-406`)

  That is a verbatim description of this app.
- `Understanding.md:255` — "Section 6 covers **custom instrumentation**… **Most
  applications don't need this**." `Understanding.md:267` — Micrometer Tracing API is the
  "**recommended default**". `Understanding.md:520` — "**Adding Actuator + tracing starter
  is sufficient** for most applications."
- `Trace.md:348` — "**If the auto-configured request-level spans are sufficient for your
  needs, you can skip this section**" (§5 method observability). `Trace.md:373` — `@Observed`
  is "the **recommended** approach".

For mandatory (the tension):
- `Standard.md:126` puts `trace.id`/`span.id` in the *always present* list and names
  Micrometer Tracing as the populator, adding "**do not set manually**". If you take the
  field list literally and obey "do not set manually", you must install a tracer.
- `Understanding.md:515` — "**No default tracer** — you **must** add either
  `spring-boot-starter-opentelemetry` or `spring-boot-starter-zipkin`". (This is a factual
  statement about Spring Boot, not a policy mandate, but it is the lowercase "must" in
  these files.)
- `Trace.md:14` — "Spring Boot uses Micrometer Tracing as its tracing facade, which
  **requires** a backend implementation to create and export spans. **Add** Actuator and
  **choose one** tracing starter (OpenTelemetry or Zipkin) to enable trace propagation."
- `Standard.md:146` (Note) — "…fields such as `service.name`… `trace.id`, and `span.id`
  **require explicit configuration** (e.g., ECS JSON layout and tracing)."

**Is a plain servlet-filter + MDC correlation id sufficient to conform?** The standard
never says so in those words — **SILENT** on the direct question.

**My reading (INFERENCE):** it is *defensible* but it leaves `trace.id`/`span.id` empty on
every line, which reads badly against `Standard.md:123`. The cheap resolution is to do
both: `spring-boot-starter-actuator` + `spring-boot-starter-opentelemetry` with **no
exporter endpoint** costs two dependencies and zero infrastructure (see (d)), and yields
`trace.id`/`span.id` on every line automatically for free at the HTTP entry point
(`Understanding.md:124-138`, `Understanding.md:203`). That satisfies `Standard.md:126`
literally and without manual field-setting. The servlet filter is then still needed for the
request start/end events (`Standard.md:49-53`) and, if adopted, `X-Correlation-ID`
acceptance. I recommend this belt-and-braces route; it is not forced by the text. See Q1.

### (d) Is an OTLP collector / exporter endpoint required?

**NO — explicitly not. The standard pre-empts this exact question.**

- `Trace.md:14` — "**If you are not exporting traces to an external platform, you can omit
  the endpoint configuration.**"
- `Trace.md:98` — "Sampling controls only which traces are **exported**… **All requests
  still generate trace IDs that appear in logs via MDC, regardless of the sampling rate.**"
- `Standard.md:308` — "**When OTel export is required** (e.g., to an OTel Collector or a
  vendor backend), add `micrometer-tracing-bridge-otel` and the relevant OTel exporter" —
  conditional, and the condition is unmet here.
- `Trace.md:10` (Prerequisites) — "**Optional:** Access to a trace platform such as
  Dynatrace for span visualization".

So installing a tracing starter creates **no** hosting dependency: no collector, no port
4317/4318, nothing to provision. The tension the ticket anticipated **does not arise for
the tracing endpoint**.

**However — there IS a real hosting tension, in a different place, and it should be flagged
because it is the same shape of problem:** the standard assumes a centralised log platform
and a forwarding agent, which is hosting infrastructure the PRD excludes
(`map.md:59` puts "Containerization, CI/CD, hosting infra" out of scope):

- `Standard.md:287` (Note) — "Log retention TTL is enforced by the **centralised log
  management platform** (e.g., Splunk, ELK index lifecycle policy), **not by the
  application**. **Integrators must configure** the applicable retention policy on the log
  management platform."
- `Standard.md:304` — "**[Design Choice]** Local file output and rolling rotation via
  `logging.file.name` and `logging.logback.rollingpolicy.*`, providing the **durable local
  disk buffer required for log forwarding agents**"
- `Standard.md:391` — "**Log delivery**: Logging failures must not crash the application and
  should self-recover. If the centralised log collector, log storage, or network becomes
  unavailable, logs are buffered to local disk for a forwarding agent to transmit…"

These are satisfiable *from the application side* (enable `logging.file.name` + rolling
policy; that is in-repo config, not infra) and the rest lands on a deployment note. But
"who configures retention" has no owner in this effort. See human decision Q6.

`TZ=Asia/Singapore` / `-Duser.timezone=Asia/Singapore` (`Trace.md:307-321`,
`Standard.md:167`: "Standardise on Singapore Time Zone (UTC+8)") is likewise a
**deployment-time** setting, not application code. Mandatory per `Standard.md:167`;
unownable without a hosting story. Same bucket as the accepted local-HTTPS gap
(`map.md:60`). See Q6.

### Sampling rate

`Trace.md:84-96` gives environment-tiered values ("Local and test: `1.0`… Production:
`0.05` to `0.2`"), phrased as guidance ("Adjust based on environment and traffic volume").
RECOMMENDED. Irrelevant to log content given `Trace.md:98`, and moot with no exporter.
Setting `management.tracing.sampling.probability: 1.0` locally is free and harmless.

---

## 2. MDC: mandatory keys, lifecycle, thread-safety

### 2.1 Which keys are mandatory

There is **no mandatory MDC key list**. The standard constrains MDC by *shape and size*,
and mandates *fields on events* (which MDC is then the recommended way to supply).

MANDATORY constraints on MDC contents:
- `Standard.md:129` — "**Limit MDC to a small set of fixed, known-safe fields** such as
  `trace.id`, `span.id`, and job or request IDs. **Avoid** storing domain objects,
  collections, or sensitive data in MDC"
- `Questions.md:457-462`, under the heading "**Required constraints (§3.1)**":
  - "**Limit to 5-10 application-defined fields maximum** (excludes trace.id and span.id)"
  - "**Store only scalar, fixed values** (no collections, no domain objects)"
  - "**Store `user.id` UUID** for user identity in MDC — **never** store raw usernames,
    emails, or hashed PII"
  - "**Never store session IDs** in MDC (even hashed)"
  - "**Clear all MDC fields** in `finally` blocks"
- `MDC.md:621` (Key Takeaways) — "**Store user identity as UUID**: Set `user.id` to the UUID
  from the authenticated principal. **Never** write raw usernames, emails, or credentials to
  MDC"
- `Log_Schema.md:104` — "Always use `user.id` (UUID) for logging user context… **Never** log
  raw usernames or emails (PII) in cleartext. **For pre-authentication entry points where
  the UUID is not yet resolved, omit user identity entirely** and rely on `session.hash`
  and `trace.id` for correlation."

RECOMMENDED starting set: `Questions.md:464` — "> **Recommendation:** Start minimal. Trace
ID and correlation ID handle most needs. Add custom fields only for specific gaps (tenant
ID for multi-tenant apps, job ID for batch processing, user ID for user-level correlation)."

Concretely for this app, the keys the recipes would have us set, and their status:

| Key | Status | Cite |
|---|---|---|
| `trace.id`, `span.id` | MANDATORY as fields, auto-populated, **do not set manually** | `Standard.md:126` |
| `correlation.id` | CONTEXTUAL (not required here — no multi-trace process) | `Standard.md:132`, `Log_Schema.md:96` |
| `user.id` (UUID) | CONTEXTUAL — "authenticated user context"; MANDATORY on the authn/authz events that name it | `Standard.md:134`, `Standard.md:56`, `MDC.md:294` |
| `session.hash` | named as the pre-auth correlation fallback | `Log_Schema.md:104` |
| `batch.*`, `trigger.*` | VACUOUS here (no jobs) | `Standard.md:137` |
| `interface.*`, `file.*` | VACUOUS here (no outbound calls, no files) | `Standard.md:138` |

Note the pre-auth rule at `Log_Schema.md:104` interacts with `MDC.md:332`: "`user.id` is
**not set** for anonymous requests" — consistent, and it means a failed-login log line
carries no `user.id`. That is deliberate.

### 2.2 How MDC must be set and cleared

**Set at the request boundary.** `MDC.md:21` — "An `OncePerRequestFilter` sets
`correlation.id` in MDC **once at the entry point** so every log entry for that request
inherits it automatically." `MDC.md:620` (Takeaway) — "**Set MDC at request boundaries**".
This is the recipe's recommended shape; the *obligation* behind it is `Standard.md:39`
("attaching it to every log entry") and `Standard.md:1005`-equivalent at `Trace.md:1005`.

**Cleanup is MANDATORY and the strongest "always" in the MDC recipe.** `MDC.md:213`:
> "**Always pair every `MDC.put(...)` with a corresponding removal** to prevent stale values
> from leaking into the next request on a pooled thread."

`Standard.md:53` — "…and **clear the correlation context afterward**."
`Questions.md:462` — "**Clear all MDC fields** in `finally` blocks."

**Which cleanup idiom — three cases, and the traps** (`MDC.md:212-279`):
1. `MDC.putCloseable` + try-with-resources: one key, no catch-block logging (`MDC.md:215-231`).
   **Do not use it when** (`MDC.md:233-235`):
   - "A `catch` block needs to log with that key. Try-with-resources closes the resource
     **before** `catch` runs, so the key is already gone by the time the catch block executes."
   - "The key may already be set on the current thread. `putCloseable` **removes** the key on
     close rather than **restoring the previous value**" (cites SLF4J Issue #404).
   Both traps are live in this app: failed-login and lockout paths log from catch/handler
   blocks. So prefer idiom 2 for anything an exception handler must log with.
2. `MDC.put` + explicit `MDC.remove(...)` in `finally`: multiple keys, or catch-block
   logging (`MDC.md:239-259`). "Set all keys before the `try` block, then remove **only
   those keys** in `finally`. This keeps other MDC fields intact. `traceId` and `spanId` set
   by Micrometer Tracing are not affected" (`MDC.md:241`).
3. `MDC.clear()` — **only** at a thread-owning entry point (`MDC.md:262-264`): "It removes
   **everything** on the thread, **including `traceId` and `spanId`** set by Micrometer
   Tracing. Calling it in shared or nested code will **silently strip** those fields from
   all subsequent log entries on that thread." The recipe's only example of a legitimate
   `MDC.clear()` is a batch job (`MDC.md:266-278`) — i.e. **no legitimate use in this app**.
   Treat `MDC.clear()` as banned here. (INFERENCE from the stated constraint; the standard
   does not use the word "banned".)

**Filter ordering — the highest-value operational finding in the MDC recipe for THIS app.**
`MDC.md:75`:
> "Spring Boot registers `@Component` filters automatically. By default, they run **after**
> Spring Security's filter chain (order Integer.MAX_VALUE vs. Spring Security's order -100).
> This is acceptable for most use cases, as application logs will have the correlation ID."

`MDC.md:79` (Note):
> "**If you need correlation IDs in Spring Security's authentication and authorization logs
> (e.g., to trace failed login attempts or 403 errors), add
> `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)`** to the filter class to ensure it
> runs before Spring Security"

This app's whole audit surface *is* failed login attempts, lockout and 403s. The default
ordering would leave exactly the events the PRD cares about uncorrelated. The Note phrases
it conditionally ("If you need…"), so strictly it is RECOMMENDED — but the condition is met
here, so for this effort it is effectively binding. See Q3.

**Two filters, not one** (`MDC.md:284`): "By default, `MdcRequestFilter` runs outside the
Spring Security filter chain and has no access to the authenticated user. **A separate
filter placed inside the Spring Security chain, after authentication completes, is
required** to read the authenticated principal and set `user.id` in MDC."
- Placement: after `AnonymousAuthenticationFilter` (`MDC.md:292-294`), i.e.
  `http.addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class)`
  (`MDC.md:355`).
- Registration warning (`MDC.md:336`): "**Register the filter in the Spring Security
  configuration rather than as a `@Component` or via `FilterRegistrationBean`.** Registering
  it outside the security chain means it runs before authentication completes and the user
  context will not be available."
- `instanceof AnonymousAuthenticationToken` guard is required (`MDC.md:317`, `:332`):
  "`AnonymousAuthenticationToken.isAuthenticated()` returns `true`, so checking
  `isAuthenticated()` alone is not enough".

**Do NOT use Logback's `MDCInsertingServletFilter`** — `MDC.md:98`:
> "It is **not recommended under this standard** because it sets fields that **must not be
> logged**, including client IP (`req.remoteHost`, `req.xForwardedFor`) and query strings
> (`req.queryString`). Its position in the filter chain is also not guaranteed…"

**What the request filter must log** (`MDC.md:21`, and the code at `MDC.md:52-69`): request
start with `event.kind`, `event.category: ["network"]`, `event.type: ["start"]`,
`http.request.method`, `url.path`; request end with `event.type: ["end"]`, `event.outcome`
(`status < 400 ? success : failure`), `http.response.status_code`, `event.duration_ms`. The
recipe says this is "as required by the app standard" — and it is: `Standard.md:49-53`.
MANDATORY.

**The masking warning that collides with a PRD story** — `MDC.md:94`:
> "**Warning:** **Avoid logging the client IP address**, query parameters, authentication
> headers, or request and response bodies, as these may contain personal data or sensitive
> information."

The PRD has an IP-throttling story. Throttling decisions are exactly the events an operator
would want the IP on, and the standard says avoid it. See Q4 — this needs a human ruling and
is likely the most consequential conflict in this sub-scope.

**One-off fields must not go in MDC** — `MDC.md:565-567`: "MDC persists until explicitly
removed. Using it for a field that appears on only one log entry requires cleanup that
serves no purpose and leaks if the `remove` is missed… For fields needed on only one entry,
**add them directly with `.addKeyValue(...)`**." RECOMMENDED (phrased as AVOID/Instead).

**Helper methods for repeated field groups** (`MDC.md:362-452`): RECOMMENDED, and
`MDC.md:452` limits it — "Use this pattern for repeated field groups. **If a field is used in
only one place, set and remove it inline instead.**" The two worked examples are
`IntegrationLogContext` and `BatchLogContext` — both vacuous here. Likely no helper is
warranted in this app beyond the two filters.

**Dotted-key trap (applies to every log call, not just MDC)** — `MDC.md:598` and
`Trace.md:852`: "Fields like `error.code` and `error.category` **cannot** be set with a
plain `.addKeyValue("error.code", ...)` call when the ECS formatter is active. Spring
interprets dotted keys as paths into nested objects and **throws a runtime exception**
because the `error` object is already written by the formatter. **A custom encoder is
required.**" Hence the `error_code` / `error_category` / `error_follow_up_action` underscore
spellings throughout. This is a hard implementation constraint for the error paths and
points at `Recipes/Custom_Structured_Log_Encoder.md` (not in my read scope — flag to whoever
owns it that the encoder is **load-bearing**, not optional, if any error field is logged).

### 2.3 Thread-safety and async caveats

- `MDC.md:457` — "MDC is **thread-local**, so when a task is submitted to an async thread,
  the worker thread starts with an **empty** MDC map and loses all context from the
  originating thread." Same point at `Trace.md:513`.
- `Standard.md:314` (Note) — "Micrometer Context Propagation (when on the classpath)
  propagates trace and span IDs across `@Async` thread boundaries, but **does not propagate
  application-defined MDC fields** beyond trace context. **A `TaskDecorator` is required** to
  carry those fields across async boundaries." (`Questions.md:280` repeats this.)
  - Note this contradicts `Trace.md:531` ("MDC | Yes | Spring Boot registers the accessor
    automatically") and `MDC.md:461` ("the decorator propagates **both** trace context and
    custom MDC fields"). Internal inconsistency in the standard set. It does not bite here
    (no async), but if `@Async` is ever introduced, trust `Standard.md:314` and verify
    empirically. Recorded so it is not re-litigated.
- Fix when Micrometer Tracing is present: `ContextPropagatingTaskDecorator`
  (`MDC.md:461`, `Trace.md:525`). "It does **not** apply to a raw `ExecutorService` or a
  `CompletableFuture` using its default executor. Those require manual propagation"
  (`Trace.md:525`).
- Fix without Micrometer Tracing: manual `TaskDecorator` copying
  `MDC.getCopyOfContextMap()` / `MDC.setContextMap()` with `MDC.clear()` in `finally`
  (`MDC.md:545-555`).
- **Applicability gate:** `MDC.md:455` — "This section is only relevant if the application
  uses async task execution, for example with `@Async` methods or a
  `ThreadPoolTaskExecutor`. **If the application does not use async tasks, skip this
  section.**" `Trace.md:511` says the same.

**Status for this app: §5 of the MDC recipe and §6 of the Trace recipe are VACUOUS** — no
`@Async`, no custom executor, no outbound HTTP. **Conditional on one thing:** if the stubbed
`EmailService` is ever made `@Async` (a natural instinct for "send" semantics even when it
only logs), the whole `ContextPropagatingTaskDecorator` requirement activates. See Q5.

Also vacuous by explicit gate: `MDC.md:101` §3.2 (forwarding correlation id downstream —
"only relevant if the application makes outbound HTTP calls"); `Trace.md:626-688` (outbound
client builders); `Trace.md:690-754` (baggage — needs a service boundary to cross,
`Trace.md:691`, and `Trace.md:696`: "If you only care about local logging, use `MDC.put()`
directly to avoid this configuration overhead"); `Trace.md:756-800` (`correlation.id` for
multi-request business processes — `Trace.md:758`: "Use `correlation.id` when the process
involves multiple unrelated requests").

### 2.4 Is MDC mandatory independently of tracing?

**The standard never says "MDC is mandatory" — SILENT on the mechanism.** What it mandates
are *outcomes* that MDC is the named means to:
- `Standard.md:39` — the id must be "attach[ed] to **every** log entry".
- `Standard.md:129` — constrains what MDC may contain, presupposing it is used.
- `Standard.md:301` — "**[Design Choice]** Structured formats include MDC key-value pairs".
- `Trace.md:1005` — "Fields that belong to a whole operation **should** be set in MDC so
  they appear on every related entry automatically." ("should")
- `Trace.md:167` / `Trace.md:1121` — MDC values are included automatically in ECS output;
  "**Put operation-scoped fields in MDC**".

**My reading (INFERENCE):** MDC is mandatory *in effect* and independently of tracing,
because (i) `user.id` must appear on the authn/authz events (`Standard.md:56`,
`Standard.md:134`) and the only sanctioned way to get it there across a request is the
in-security-chain filter writing MDC (`MDC.md:284`), and (ii) even with a tracer installed,
`trace.id`/`span.id` *reach the log line via MDC* (`Standard.md:308`: Micrometer "writes
`traceId` and `spanId` to MDC automatically"). There is no MDC-free way to conform. So:
**yes — MDC is required whether or not a tracer is installed, and installing a tracer does
not remove the need for an application-owned MDC filter** (it only removes the need to
*generate* an id).

---

## 3. Batch and scheduled jobs: confirming vacuity, not skipping it

### 3.1 The binding trigger, stated precisely

`Logging_Batch_And_Scheduled_Jobs.md` binds when **either** of two things exists in the
codebase:

1. **A Spring Batch job.** Gate: `Batch.md:10` (Prerequisites) — "**Spring Batch on the
   classpath (required for batch job lifecycle logging)**"; `Batch.md:59` — "**Note:**
   Sections 4-7 apply to Spring Batch applications. For plain `@Scheduled` methods, skip to
   Section 8."
2. **A plain `@Scheduled` method.** Gate: `Batch.md:501` — §8 "Log `@Scheduled` job
   execution — For plain `@Scheduled` methods (without Spring Batch)…"

Neither exists in this effort's scope. `map.md:62` rules out
`Standalone_Scheduled_Account_Hygiene_Jobs.md` ("a whole subsystem serving no PRD story")
and `map.md:27` already records the recipe as "binding but **vacuous**… It binds if one ever
appears."

**CONFIRMED VACUOUS.** No requirement in this recipe has a surface in the planned app. This
is a positive finding from reading all 1,254 lines, not a skip.

The second-order trigger to watch: Spring Batch is **not** on the classpath, but `@Scheduled`
needs no dependency at all — only `@EnableScheduling` and an annotation. The cheapest way
for this app to accidentally acquire a 1,254-line recipe obligation is someone adding a
`@Scheduled` cleanup of expired password-reset tokens or expired sessions. Both are natural
instincts in a login app. **That is the trigger to name in the ticket**: any `@Scheduled`
method, however small, activates §3, §8 and §11 of this recipe in full.

### 3.2 What it WOULD require, if a `@Scheduled` method appeared

The minimum (§8, `Batch.md:501-575`; verification checklist `Batch.md:1155-1161`):
- **Startup**: a schedule-registration log on `ApplicationReadyEvent` carrying
  `batch.job.name`, `trigger.cron.expression`, `trigger.cron.timezone`, `event.category:
  ["configuration"]`, `event.type: ["info"]` (`Batch.md:43-54`; checklist `Batch.md:1140`).
  Mandated in the parent standard too: `Standard.md:80` — "**Log schedules at startup:** Log
  each job's name, cron expression, human-readable schedule description, and timezone at
  application startup so the intended schedule is visible without inspecting configuration".
- **Job-start**: generate a UUID `batch.job.run.id`; put `batch.job.name`,
  `batch.job.run.id` and `trace.id` in MDC; log `event.kind`, `event.category: ["batch"]`,
  `event.type: ["job-start"]`, `event.severity`, `event.start`, `trigger.type:
  ["scheduled"]`, `trigger.by: [{"name":"spring-scheduler","type":"system"}]`
  (`Batch.md:516-538`, checklist `Batch.md:1156-1159`).
- **Job-end**: `event.type: ["job-end"]`, `event.outcome`, `event.end`, `event.duration_ms`;
  on failure `event.severity: high`, `error_code`, `error_category`,
  `error_follow_up_action: true` and `.setCause(e)` (`Batch.md:544-567`).
- **MDC cleanup in `finally`**, removing `batch.job.name`, `batch.job.run.id`, `trace.id`
  (`Batch.md:568-572`). Mandated in the standard: `Standard.md:85` — "**Clean up MDC after
  each execution:** Remove all job and step MDC fields in a `finally` block. **Job context
  must not leak** into the next scheduled execution." Checklist `Batch.md:1161`, `:1208-1211`.
- **Parameter masking**: `Batch.md:1220` — "**Mask job parameters**: Log
  `batch.job.run.parameter` with masked values (`***`)". `Standard.md:81` — "Log input
  parameter names but **not their values** if the values may contain personal data or
  secrets".

Spring Batch would additionally pull in: job/step listener pairs
(`Batch.md:96-226`, `:242-363`), `batch.job.id`/`batch.job.status`/`trigger.id`, step item
counts (read/write/skip/rollback — `Standard.md:83`, `Batch.md:1169-1172`), and the whole
file-integrity/ACK apparatus (`Batch.md:746-1026`) if a step touched files. None of it is
reachable here.

`Batch.md:17-21` also confirms the *optionality* of the advanced fields even inside a batch
app: "`span.id` — Use when integrating with OpenTelemetry or Micrometer Tracing…
`correlation.id` — Use when tracking business processes that span multiple batch jobs…
**These fields are optional and outside the scope of this guide.**"

### 3.3 Requirements in the batch recipe that LEAK into non-batch code paths

Three, and only the third matters here:

1. **§10 Log external service calls** (`Batch.md:1027-1130`) is generic outbound-HTTP
   logging — `event.category: ["interface"]`, `interface.direction: outbound`,
   `interface.type`, `interface.system`, `url.full`, `http.request.method`,
   `http.request.body.bytes`, `http.response.status_code`, `http.response.body.bytes`,
   `event.duration_ms`, and on failure `error_category: "network"` (`Batch.md:1050-1115`,
   checklist `Batch.md:1193-1201`). It is *shelved under batch* but the same obligation
   binds independently via `Standard.md:87-91` (§6 External Integrations) and
   `Standard.md:138` ("Interface fields… **required for** outbound API calls and file-based
   integrations").
   **Status here: VACUOUS.** The service makes no outbound HTTP call; the `EmailService` is
   stubbed and only logs (`map.md:58`). **INFERENCE:** a stub that writes a log line and
   returns is not an "outbound call to an external service", so `interface.*` does not bind
   to it. If the stub is ever pointed at real SMTP, `Standard.md:91` activates ("For
   non-HTTP integrations (e.g., SFTP, message broker, database), log the outcome and
   duration… **Do not include the host, URL, database name, or credentials** in the log
   entry"). Worth one line in the audit contract (ticket 12) so the stub's log line is
   deliberately *not* dressed up as an interface event.
2. **§3 startup schedule logging** (`Batch.md:25-56`) fires on `ApplicationReadyEvent`,
   which every app has — but its content is per-registered-job, so with zero jobs it emits
   nothing. Does not leak. (The app's *own* startup event is a separate obligation:
   `Standard.md:41-46`, covered by `Logging_Application_Lifecycle_Events.md`, another
   sub-agent's scope.)
3. **A contradiction that would leak if copied blindly:** `Batch.md:526` writes
   `MDC.put("trace.id", runId);  // Use run ID as trace ID for correlation`, and
   `Batch.md:1218` makes it a Key Takeaway ("**Set trace.id for correlation**: Use
   `batch.job.run.id` as `trace.id`"). `Log_Schema.md:94` and `:166` agree ("For batch jobs,
   this should be the `batch.job.run.id`"). **But `Standard.md:126` says of `trace.id`:
   "auto-populated by Micrometer Tracing when a span is active; **do not set manually**."**
   These cannot both be followed when a tracer is installed — and `Understanding.md:140-150`
   notes `@Scheduled` methods are *already* auto-instrumented with a generated trace id, so
   the manual put would fight the tracer.
   **Status: LATENT, not active** (no jobs). But it is a trap for anyone who later adds a
   `@Scheduled` method *and* has installed a tracing starter per §1(c). Record it: the batch
   recipe's manual `trace.id` pattern is written for a **no-tracer** application. See Q7.

Also worth recording as vacuous-by-name so it is not re-derived: `MDC.md:409-450`
(`BatchLogContext` helper with `batch.job.name` / `batch.job.id` /
`trigger.cron.expression`) and `Trace.md:802-865` (§8 batch step-summary `record` array
pattern). Both are the batch recipe's shadow in the other two files. `Trace.md:808` points
at the batch recipe for the full treatment.

---

## 4. Consolidated requirement list for this sub-scope

### MANDATORY (traceable to the parent standard's required-field list, a `MUST`, or an `[Enforced Constraint]`)

| # | Requirement | Cite |
|---|---|---|
| M1 | Every log event carries an operation identifier attached at the start of the operation | `Standard.md:39`, `Standard.md:123` |
| M2 | `trace.id`, `span.id` in every event when a span is active; **never set manually** | `Standard.md:126` |
| M3 | On request start: propagate an inbound correlation id if present, else **generate** one | `Standard.md:50-51` |
| M4 | On request start: log HTTP method and path. **Not** client IP, query params, auth headers, or body | `Standard.md:52`, `MDC.md:94` |
| M5 | On request end (success **and** failure): status code, duration, outcome; then clear the correlation context | `Standard.md:53` |
| M6 | Pair every `MDC.put` with a removal; clear all MDC fields in `finally` | `MDC.md:213`, `Questions.md:462` |
| M7 | MDC limited to a small set of fixed, known-safe scalar fields (5-10 app-defined max); no domain objects, collections, or sensitive data | `Standard.md:129`, `Questions.md:457-459` |
| M8 | `user.id` in MDC is the **UUID**; never raw username/email/credential; omit entirely pre-authentication | `MDC.md:294`, `Questions.md:460`, `Log_Schema.md:104` |
| M9 | **Never** put session ids in MDC, even hashed | `Questions.md:461` |
| M10 | `user.id` filter registered **inside** the Spring Security chain, after `AnonymousAuthenticationFilter`, with an `AnonymousAuthenticationToken` type guard | `MDC.md:284`, `:292-294`, `:317`, `:336`, `:355` |
| M11 | If required fields are absent, still emit the event; never suppress or drop silently | `Standard.md:164` |
| M12 | Structured/ECS output enabled (`[Enforced Constraint]`) — the carrier for all MDC fields | `Standard.md:319`, `Trace.md:156-167` |
| M13 | Timestamps ISO-8601/RFC-3339 with timezone, standardised on UTC+8 | `Standard.md:141`, `:166-167`, `Trace.md:307-321` |
| M14 | Error fields spelled with underscores (`error_code`, `error_category`, `error_follow_up_action`) + custom encoder to remap; dotted keys throw at runtime under ECS | `MDC.md:598`, `Trace.md:852` |

### RECOMMENDED (the standard's own "recommended" / "should" / "Recommendation:" / "AVOID…Instead")

| # | Recommendation | Cite |
|---|---|---|
| R1 | Micrometer Tracing as the tracing abstraction; OTel starter for new projects; W3C Trace Context | `Standard.md:308`, `Understanding.md:71-73`, `Trace.md:20`, `Trace.md:1095` |
| R2 | Micrometer Tracing API over OTel API for custom spans ("recommended default") | `Understanding.md:267`, `:424` |
| R3 | `@Observed` for method-level spans ("the recommended approach") — explicitly skippable | `Trace.md:373`, `Trace.md:348`, `Understanding.md:255` |
| R4 | Always generate a correlation id when upstream supplies none | `Questions.md:444` |
| R5 | `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)` on the MDC filter **if** correlation ids are needed on Spring Security's own authn/authz logs — condition IS met here | `MDC.md:79` |
| R6 | Use `.addKeyValue(...)` not MDC for single-entry fields | `MDC.md:565-567` |
| R7 | Helper classes for repeated MDC field groups; inline for single-use fields | `MDC.md:362-365`, `:452` |
| R8 | Sampling 1.0 local / 0.05-0.2 production | `Trace.md:84-96` |
| R9 | Outcome in structured fields, never only in the message text | `Trace.md:1039-1064` |
| R10 | Set `spring.application.name` once so logs and traces agree on `service.name` | `Trace.md:103`, `:123`, `:1099` |
| R11 | `logging.file.name` + rolling policy as the local buffer for a forwarding agent | `Standard.md:304`, `Trace.md:169-195` |

### VACUOUS for this app (confirmed, with the gate that makes it so, and the trigger that would activate it)

| Area | Gate | Activation trigger |
|---|---|---|
| Outbound trace propagation; client builders | `Trace.md:511`, `:627-631` | Any outbound HTTP call from the service |
| `X-Correlation-ID` forwarding downstream (`MDC` §3.2) | `MDC.md:101` | Any outbound HTTP call |
| Baggage (`remote-fields`, `correlation.fields`) | `Trace.md:691`, `:696` | A second service to carry business context to |
| `correlation.id` for multi-request processes | `Standard.md:132`, `Trace.md:758` | A business process spanning multiple traces/time |
| Async MDC propagation, `ContextPropagatingTaskDecorator` | `MDC.md:455`, `Trace.md:511` | Any `@Async` method or custom executor — **incl. making `EmailService` async** |
| OTLP/Zipkin exporter endpoint, collector | `Trace.md:14`, `:10` | A decision to export spans to a platform |
| Whole batch/scheduled recipe (1,254 lines) | `Batch.md:10`, `:59`, `:501` | **Any `@Scheduled` method**, or Spring Batch on the classpath |
| `batch.*` / `trigger.*` MDC and helper | `Standard.md:137`, `MDC.md:409` | Same |
| Batch step-summary `record` array | `Trace.md:804-808` | Same |
| `interface.*` / `file.*` fields | `Standard.md:138`, `Batch.md:1027` | Real SMTP, file processing, or any outbound call |
| `@Observed` / Observation API / custom `ObservationHandler` | `Trace.md:348`, `Understanding.md:255-259` | A need for timer metrics or internal-method spans |
| HTTP exchanges actuator endpoint | `Trace.md:868` | Actuator on the classpath (note: **§1(c)'s tracer needs Actuator** — this then becomes relevant and `Trace.md:878` requires restricting access) |
| Custom `logback-spring.xml` (§10 of Trace) | `Trace.md:882` | Env-specific appender routing or custom masking in Logback |
| `MDC.clear()` | `MDC.md:262-264` | Only legitimate at a thread-owning entry point, i.e. a job — none here |

---

## 5. Questions needing a HUMAN decision

**Q1 — Install a tracing starter, or run with no tracer and an application-minted
correlation id?**
The standard lists `trace.id`/`span.id` as required-in-every-event and forbids setting them
manually (`Standard.md:126`), yet `Questions.md:404-406` says a standalone service with no
downstream dependencies does **NOT** need distributed tracing, and `MDC.md:16`/`:540`
provide a first-class no-tracer path. Options: **(A)**
`spring-boot-starter-actuator` + `spring-boot-starter-opentelemetry`, **no exporter
endpoint** — two dependencies, zero infra (`Trace.md:14`), and `trace.id`/`span.id` appear
automatically on every line at the HTTP entry point (`Understanding.md:124-138`, `:203`);
`Standard.md:126` satisfied literally. **(B)** No tracer; a servlet filter mints
`correlation.id` per request; `trace.id`/`span.id` are permanently absent, relying on
`Standard.md:164`. My recommendation is **(A)**, plus the filter anyway (M3-M5 need it
regardless). Decide explicitly — this determines the `pom.xml`, whether Actuator is exposed,
and whether an audit reviewer sees empty `trace.id` on every line.

**Q2 — Does the React SPA send `X-Correlation-ID`, and if so is a browser-supplied value
trusted into the logs?**
Not required either way (`Standard.md:50-51` accepts backend generation). If yes:
`Understanding.md:93` warns that trace/correlation headers from public clients are normally
stripped at a gateway to prevent untrusted trace injection — and there is no gateway here
(`map.md:59`). The value then lands in every log line and must be validated/sanitised per
`Standard.md:380`. My recommendation: **backend mints, SPA sends nothing**; revisit only if
the SPA needs to display a support reference. This also feeds the SPA route/screen
inventory (`map.md:45`).

**Q3 — Does the MDC filter run before Spring Security (`@Order(DEFAULT_FILTER_ORDER - 1)`)?**
`MDC.md:75` says the default (after Spring Security) is "acceptable for most use cases";
`MDC.md:79` says you need the earlier order to get correlation ids onto **failed login
attempts and 403s**. Those events are this app's entire audit surface (PRD login failure,
lockout, throttling). This looks like a yes, but it changes filter-chain ordering next to a
custom `UsernamePasswordAuthenticationFilter` subclass (`map.md:39`) and interacts with
ticket 09 (HTTP security). Needs a ruling, not a default.

**Q4 — Client IP on throttling / lockout log events: log it or not?**
Direct conflict. `MDC.md:94` and `Standard.md:52` say **avoid logging the client IP**
(potential personal data). The PRD has an IP-throttling story whose events are close to
meaningless without the IP, and `Standard.md:236` offers an escape — "If any sensitive value
must be logged for correlation, **mask or hash it**". Decide: (i) omit IP entirely,
(ii) log a salted hash of the IP on throttle events only, or (iii) log the IP in cleartext
on security events only, with a documented justification. This is the sharpest
standard-vs-PRD conflict in this sub-scope and should be its own ticket feeding 12 (audit
and logging contract) and the masking recipe.

**Q5 — Is the stubbed `EmailService` synchronous?**
If it is ever `@Async`, `MDC.md:455` and `Trace.md:511` activate in full
(`ContextPropagatingTaskDecorator` or a manual decorator, plus the
`Standard.md:314`-vs-`Trace.md:531` contradiction over whether app-defined MDC fields
actually propagate). Keeping it synchronous keeps an entire section vacuous, for free.
Recommend: **mandate synchronous**, and record it as a constraint so nobody "improves" it.

**Q6 — Who owns the deployment-side logging obligations the PRD's hosting exclusion
leaves unowned?**
Three items are real, mandatory-ish, and not application code: `TZ=Asia/Singapore` /
`-Duser.timezone` (`Standard.md:167`, `Trace.md:307-321`); log retention TTL, which
"**Integrators must configure** … on the log management platform" (`Standard.md:287`); and
local-disk buffering for a forwarding agent (`Standard.md:304`, `:391`). The first is
satisfiable with a JVM arg in the run instructions; the second has no owner at all; the
third is `logging.file.name` + rolling policy, i.e. in-repo config. Decide whether these go
into the same documented-deployment-assumption bucket as local HTTPS (`map.md:60`) — and if
so, that the *configuration* half (file output, rolling policy, TZ) is still implemented,
not deferred. Feeds `map.md:48` (configuration and secrets).

**Q7 — Record the `trace.id` trap before it can bite.**
If Q1 lands on (A) **and** a `@Scheduled` method is ever added, the batch recipe's
`MDC.put("trace.id", runId)` (`Batch.md:526`, `:1218`, backed by `Log_Schema.md:94`) directly
contradicts `Standard.md:126` ("do not set manually") and fights the tracer, which already
auto-instruments `@Scheduled` (`Understanding.md:140-150`). Not a live decision — a
decision to *write down* now, in the audit/logging contract, so a future scheduled job does
not silently break trace correlation.

**Q8 — Is `Custom_Structured_Log_Encoder.md` in the binding set, and who reads it?**
Not in my scope, but two files in my scope make it load-bearing rather than optional:
`MDC.md:598` and `Trace.md:852` both state that `error.code`/`error.category` **throw a
runtime exception** under the ECS formatter unless a custom encoder remaps the underscore
spellings. Any error path that logs `error_code` — i.e. every failure event in this app —
depends on it. `map.md:27` binds all of `Appfw-Logging-Standards/`, so it is in; confirm
someone has actually read it and that the encoder is on the build plan.
