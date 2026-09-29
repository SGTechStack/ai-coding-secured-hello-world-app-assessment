# 05 — Which logging standards actually bind a two-process app

Type: research
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

All 7,169 lines of [`Appfw-Logging-Standards/`](../../../App-Standards/Appfw-Logging-Standards/) are binding on this effort. Produce the concrete, de-duplicated list of requirements that actually apply to a two-process application (React SPA + one Spring Boot service, no batch jobs, no distributed hops).

Read and report on, at minimum:

- `Structured_Logging_Application_Standard.md` (437) and `Log_Schema.md` (352) — the mandatory key/field set, levels, and privacy policy.
- `Recipes/Sensitive_Data_Masking_For_Logs.md` (260) — non-negotiable given the PRD's "plaintext password is never logged" criterion.
- `Recipes/Logging_AuthN_And_AuthZ_Events.md` (516) — maps almost directly onto the PRD's audit list (login success/failure, lockout, reset requested/completed, role change, enable/disable/delete, actor + target).
- `Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md` (343) — whether a typed audit module is required or merely recommended.
- `Recipes/Enriching_Logs_With_MDC.md` (669) — whether the AuthN/AuthZ recipe depends on MDC enrichment, or it stands alone.
- `Recipes/Understanding_OpenTelemetry_And_Micrometer_Tracing.md` (577) and `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md` (1,163) — **the key finding to establish**: what trace correlation means when there is one service and no downstream hop. Is a trace/correlation id still mandatory on every log line and propagated from the SPA, or does the requirement have no surface here?
- `Recipes/Logging_Application_Lifecycle_Events.md` (337) — startup/shutdown event requirements.
- `Recipes/Logging_Batch_And_Scheduled_Jobs.md` (1,254) — confirm this is vacuous (no scheduled jobs remain in scope) rather than silently skipped.
- `Structured_Logging_Application_Standard_Questions.md` (482) — surface any question here that needs a human decision; those become their own tickets.

Deliver a Markdown findings file in the repo, cite standard + line for every requirement, and separate **mandatory** from **recommended**. Distinguish what the standard states from what you infer.

This ticket gates the audit and logging contract (12).

## Answer

Resolved by three parallel research subagents; full detail in the findings files, which are the authority for line-by-line cites:

- [`research/05a-core-logging-standard.md`](../research/05a-core-logging-standard.md) — core standard, `Log_Schema`, questions file, custom encoder, exception logging.
- [`research/05b-audit-and-masking.md`](../research/05b-audit-and-masking.md) — AuthN/AuthZ events, masking, typed audit module, lifecycle events.
- [`research/05c-tracing-mdc-batch.md`](../research/05c-tracing-mdc-batch.md) — trace correlation, OpenTelemetry/Micrometer, MDC, batch vacuity.

Cites below were **re-verified against the source files** when writing this answer, because the three reports disagreed on two load-bearing points. Both disagreements are resolved here and one report was wrong — see [Corrections](#corrections-to-the-research-reports).

Short names below: `Std` = `Structured_Logging_Application_Standard.md`, `LS` = `Log_Schema.md`, `Q` = `Structured_Logging_Application_Standard_Questions.md`, and recipes by first word (`AuthN`, `Mask`, `Typed`, `MDC`, `Trace`, `Lifecycle`, `Batch`, `CSLE`).

### The framing that governs everything below

**The recipes are guides; `Std` is the clause list.** All ten recipes together contain almost no normative modals. Normative force lives in `Std`, which uses `MUST` and tags §4 decisions `[Enforced Constraint]` or `[Design Choice]`. Two consequences, both of which the map must not lose:

1. **A requirement with no recipe template is still mandatory.** `Std:239` — "Audit logs must record the following:" — followed by fourteen items at `:240-253`. Five of the PRD's seven audit events have no recipe template, and are mandatory anyway. Absence of a template is an implementation gap, not an exemption.
2. **A recipe's enthusiasm is not a mandate.** The typed audit module is the clearest case; see below.

**`LS` binds despite its title.** `LS:1` says "for Batch and Interface Applications", but `Std:121` makes it the authority for all log fields in `Std`'s scope: "Log fields, their canonical definitions, allowed values, and ECS auto-population behaviour are specified in Log Schema." Its Core/User/Service/Event/Error sections bind; its batch, interface, file, report, MCC, MCNS and MPDS sections are vacuous here.

### Mandatory — settled by the standard, no decision needed

**Required on every single log line** (`Std:123-129`): `@timestamp`, `log.level`, `message`, `service.name`, `service.version`, `service.environment`, `trace.id`, `span.id`, `thread.name`, `logger.name`. Every one is annotated **"do not set manually"** / "do not set at log time" — so the build cost of the required field set is *configuration*, not code.

**Emission rules** (`Std:163-170`): NDJSON to stdout, one event per line; UTF-8; RFC-3339 timestamps with timezone; **Singapore Time UTC+8**; and `Std:164` — if required fields are absent the application "MUST still emit the event and MUST NOT suppress or drop it silently". `log.level` MUST be one of `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR` (`Std:141`); `FATAL` does not exist in Logback and silently maps to `ERROR` (`Std` note following `:141`).

**The §4 `[Enforced Constraint]` list** (`Std:319-330`) is the real build checklist:

| `Std` line | Constraint | Status here |
|---|---|---|
| `:319` | Enable the agreed structured format via `logging.structured.format.*` | **Binds** — which format is a decision: [19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md) |
| `:320` | With a custom `logback-spring.xml`, select the encoder via the `CONSOLE_LOG_STRUCTURED_FORMAT` / `FILE_LOG_STRUCTURED_FORMAT` env vars, never hardcoded | **Binds** — and `CSLE:389` violates it by hardcoding `<format>ecs</format>`; see [19](19-log-format-and-custom-encoder.md) |
| `:321` | **Add Micrometer Tracing to the classpath** so `traceId`/`spanId` reach MDC automatically | **Binds** — this is the answer to this ticket's key question |
| `:322` | Use auto-configured HTTP client builders for outbound calls | **Vacuous** — no outbound HTTP; the `EmailService` stub only logs |
| `:323` | **Register an MDC filter/interceptor** for app-defined fields (a correlation id from e.g. `X-Correlation-ID`, and the `user.id` UUID after authentication); **clear all MDC fields in a `finally` block** | **Binds** — placement is a chain decision, pushed to [09 — HTTP security, CSRF, CORS, headers](09-http-security-csrf-cors-headers.md) |
| `:324` | `TaskDecorator` copying MDC around `@Async` | **Vacuous iff** the `EmailService` stub stays synchronous — see [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md) |
| `:325` | Global exception handler logging unhandled exceptions **once** at the boundary; business exceptions logged where handled, not delegated | **Binds** |
| `:326` | Masking at the logging boundary — a custom Logback converter, a `PatternLayout` rule, **or** a structured-encoder extension | **Binds**; three permitted mechanisms, so `Mask`'s decorator is not the only route |
| `:327` | **Separate audit logs from application logs** onto a dedicated appender and destination | **Binds** — the strongest single driver of [12 — Audit and logging contract](12-audit-and-logging-contract.md)'s shape |
| `:330` | No `System.out.println` / `e.printStackTrace()` | **Binds** |

**The audit event catalogue** is `Std:240-253`, fourteen classes of event, of which these have surface: authentication success/failure **with the authentication method** (`:240`), credential changes including password changes and resets (`:242`), authorisation failures and sensitive-data access (`:243`), privileged/admin operations including account creation, deletion, lockout and **role or permission changes** (`:244`), modifications to sensitive entities including user profiles (`:245`), security-control bypass attempts (`:248`), **repeated input validation failures** (`:249` — the natural home for the PRD's IP throttling), session management failures (`:250`), critical configuration changes (`:251`), **application startup and shutdown** (`:252`), and unexpected errors and security control failures (`:253`). `Std:257` requires each audit event to answer who / what action / **what resource was affected** / when / outcome, with a generic reason on failure.

**Per-event shape from the recipe** (`AuthN`): success at `INFO`, failure at `WARN`, **lockout at `ERROR`** (`AuthN:37`); `user.id` is a UUID, never a username or email (`AuthN:17`, `Std:276`, `LS:104`); on failure and lockout `user.id` is **omitted entirely** because the account may not exist, and a hashed username is explicitly forbidden as a fallback since it would confirm account existence to anyone reading logs (`AuthN:31,37`); never distinguish account-not-found from bad-credential (`AuthN:37`). A single Spring Security `ApplicationEvent` listener centralises this rather than scattering log statements (`AuthN:35`).

**Lifecycle events are mandatory** — via `Std:252`, not via `Lifecycle`, whose language is non-normative. Startup carries `host.name`/`host.ip` (`Std:132`); shutdown hooks off `ContextClosedEvent` rather than `@PreDestroy` (`Lifecycle:287`).

**Retention is mostly *not* this application's problem.** `Std:287` note: "Log retention TTL is enforced by the centralised log management platform ... **Integrators must configure** the applicable retention policy." What *is* the application's: rotation for local file logging (`Std:260`) and durable local buffering to a rolling JSON file for a forwarding agent, rather than shipping over the network from the app (`Std:275`). This defuses most of the feared collision with the PRD's hosting exclusion — but not `Std:259` (tamper-proof audit trails) or `Std:262` (alert when audit writing fails), which remain unowned; [12](12-audit-and-logging-contract.md) draws that line.

**A typed audit module is RECOMMENDED, not mandatory.** Verified by grepping all 343 lines of `Typed` for normative modals: the only hits are descriptive prose (`:5`) and a benefits summary (`:327`), and §7 is titled "Key decisions". However `Std:327`'s mandatory dedicated audit appender needs a stable logger name to route on, and `Typed:100`'s `getLogger("audit")` is exactly that hook — so the recommended module is the cheap way to satisfy a mandatory constraint. [12](12-audit-and-logging-contract.md) decides.

**Masking: prevention is primary, masking is explicitly secondary.** `Mask:150` calls it "a second line of defense, not a substitute", and `Mask:146` says to skip the decorator section entirely if the app does not use that encoder. The PRD's "plaintext password is never logged" criterion is therefore satisfied **by prevention**, not by masking: `Mask:24` bans it outright, no `AuthN` template accepts a credential argument, `Mask:38-58` bans whole-object logging, and `Mask:87` disables DEBUG/TRACE in production. Masking could not cover it anyway — `Mask:189` excludes the `message` field from the decorator's reach.

### The key finding this ticket existed for: trace correlation with one service

**A trace id is mandatory on every log line, and the requirement is NOT confined to service hops.** `Std:123` heads its list "Required fields (always present in every log event)" and `:126` puts `trace.id`/`span.id` in it. Independently, `Std:39` requires "assigning a unique identifier, the correlation ID, at the start of the operation and attaching it to **every log entry**" — scoped to an operation's lifecycle, not to a network boundary. The recipe's own verification bar is "`trace.id` and `span.id` ... present in **every** log entry" (`Trace:1074`).

**Micrometer Tracing is mandatory — `Std:321` is an `[Enforced Constraint]`**: "Add Micrometer Tracing to the classpath so that `traceId` and `spanId` are written to MDC automatically whenever a span is active." So this is *not* a human decision, and the apparent escape hatch does not open: `Q:404-406` ("You do NOT need distributed tracing if: your application is a standalone service with no downstream dependencies") is a *guide* passage in the questions file and cannot override an enforced constraint in §4. `MDC:16,540`'s "Without Micrometer Tracing" path is likewise a fallback for apps outside this standard's enforced set. Add `spring-boot-starter-actuator` plus one tracing starter (`Trace:12`).

**No OTLP collector, endpoint or trace platform is required.** `Trace:14`: "If you are not exporting traces to an external platform, you can omit the endpoint configuration." `Trace:10` lists trace-platform access as "Optional", and `Trace:98` confirms "All requests still generate trace IDs that appear in logs via MDC, regardless of the sampling rate." **The anticipated tension with the PRD's hosting exclusion does not arise.** Cost of conformance: one dependency, zero application code.

**The SPA is not required to send anything.** `Std:50-51` is conditional — "**If** an incoming request includes ... **If no correlation ID is present, generate** a new unique identifier" — and `MDC:46-47` implements exactly that with `orElse(UUID.randomUUID())`. A frontend-supplied `X-Correlation-ID` is a contemplated trigger (`Std:323`, `MDC:16`), not an obligation. Recommendation for [12](12-audit-and-logging-contract.md): **backend mints, SPA sends nothing** — an SPA-supplied header is untrusted input with no gateway to strip it, and `Std:276` then obliges CRLF sanitisation of it to prevent log forging (CWE-117).

**`correlation.id` is not required here.** `Std:132` makes it contextual, triggered only "when a single `trace.id` does not span the full workflow". One workflow qualifies — password reset spans two HTTP requests minutes apart — so [11 — Password reset flow](11-password-reset-flow.md) inherits that question.

**MDC constraints.** No mandatory key list; the standard constrains *shape*: a small set of fixed, known-safe scalar fields, no domain objects or collections (`Std:129`), `user.id` as UUID, cleared in `finally` (`Std:323`). Two live traps recorded for the build: `putCloseable` closes *before* a `catch` block runs and *removes* rather than restores a pre-existing key (`MDC:233-235`) — both bite precisely on the failed-login and lockout paths; and `MDC.clear()` strips Micrometer's `traceId`/`spanId`, with no legitimate use in this app. Logback's built-in `MDCInsertingServletFilter` is **"not recommended under this standard"** because it sets client IP and query strings (`MDC:98`).

### Confirmed vacuous, not skipped

- **`Batch` (1,254 lines) is vacuous.** Its gates are Spring Batch on the classpath (`Batch:10`) and `@Scheduled` (`Batch:59,501`); neither holds, since scheduled hygiene jobs are out of scope. The map's existing call at `map.md:27` is **confirmed correct**. Two things to carry forward: the trigger is cheap to trip — `@Scheduled` needs no dependency at all, so an expired-token sweep or session cleanup would activate all 1,254 lines — and `Batch:526` does `MDC.put("trace.id", runId)` in direct violation of `Std:126`'s "do not set manually", so it must not be copied as a pattern.
- **MFA audit** — `AuthN:271` says MFA factor changes "must be audited" and `Std:241` lists them. A **mandatory requirement rendered vacuous by scope**, not a waived one. If MFA ever returns, so does this.
- `Std:322` outbound HTTP tracing, `Std:324` `@Async` MDC propagation (conditional on the `EmailService` decision), `Batch` §10 external-service-call logging, `data-export` events, `url.query` masking, and `LS`'s batch/interface/file/report/MCC/MCNS/MPDS field groups.

### The schema does not fit the application — three hard gaps

These are the sharpest findings, all verified directly in `LS`. They land on [12 — Audit and logging contract](12-audit-and-logging-contract.md).

1. **No target/resource field exists.** `Std:257` requires every audit event to record "what resource was affected", but `LS`'s User section (`:100-106`) offers only `user.id`, `user.name` and `session.hash`. Worse, the binding documents bind `user.id` **oppositely**: `AuthN`, `Typed` and `MDC` use it for the **actor** — `MDC:320` writes it onto every line from inside the security chain — while `Standalone_Privileged_User_Administration_and_Password_Reset.md:185,213,244,453` uses it for the **target**. The PRD's "actor + target" requirement has nowhere to go without a non-schema field.
2. **`event.action` is a closed enum with no value for two of the PRD's operations.** `LS:136` enumerates ~45 actions; there is no `user-status-change`, no `user-role-change`, no value for IP throttling and none for unlock. Both admin mutations collapse to `user-administration`. **This does not invalidate [01 — Context, topology and API surface](01-context-topology-and-api-surface.md)'s `Q9` decision, but it does invalidate one stated rationale for it** — 01's claim that sub-resource verbs make `event.action` one-to-one with operations is false. The decision stands on its other grounds.
   **`event.category` is worse**: its enum is `configuration`, `network`, `database`, `batch`, `interface`, `process` (`LS:135`) — there is **no authentication or security category at all** in a security-logging standard. The nearest fit is `process`.
   The escape valves that *do* exist: **`event.reason`** ("a short, machine-readable string explaining the reason for the `event.outcome`") and **`event.severity`** (`low`/`medium`/`high`/`critical`, routable independently of `log.level`) — both in `LS`'s Event table.
3. **A failed login against an unknown username has no permitted subject field.** `LS:104` directs pre-authentication entry points to "omit user identity entirely and rely on `session.hash` and `trace.id`". But `Q:94` forbids logging session identifiers **"in any form, including hashed"**, and `AuthN:31` independently forbids a hashed username. So `session.hash` is defined by `LS:106` and prohibited by `Q:94`. **`trace.id` alone is the only field left standing** — a further reason `Std:321`'s Micrometer requirement has real surface here rather than being a formality.

Plus a fourth, softer contradiction: **`user.name`** is permitted by `LS:105` "only ... in authentication/authorization contexts where explicitly permitted by policy" — precisely this application's contexts — while `Std:226`, `Std:276` and `AuthN:17` prohibit raw usernames absolutely. A policy escape hatch with no policy behind it; [12](12-audit-and-logging-contract.md) must either invoke it deliberately or shut it.

### Corrections to the research reports

Recorded so the findings files are read with the right caveats.

- **`05c` states that nothing about tracing carries `[Enforced Constraint]`, and that the only two enforced constraints in §4 concern the structured format. This is wrong.** `Std:321` is an `[Enforced Constraint]` mandating Micrometer Tracing on the classpath, and §4 carries roughly a dozen enforced constraints (`:319-330`), not two. `05a` read this correctly. The consequence is material: the trace-id question is **settled by the standard** rather than being a human decision, and `05c`'s Q1 ("install the tracer, or run tracer-less?") is closed, not ticketed.
- **`05b` states that no reason-code field exists in the schema.** It does: `event.reason`, with `event.severity` alongside it (`LS` Event table). `05b`'s consequent hunt for a free-string precedent in the privileged-admin recipe is unnecessary.
- `05b`'s and `05a`'s independent findings on the `event.action` enum gap, the missing target field, and the typed module being recommended-only were each verified and hold.

### Newly created tickets

Only two of the ~30 candidate questions across the three reports are genuinely separable decisions; the rest are either settled above by the standard or belong to tickets that already own them.

- **[18 — Client IP in logs](18-client-ip-in-logs.md)** — a three-way contradiction with a PRD story riding on it.
- **[19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md)** — decides whether ~200 lines of fragile code extending an internal Spring Boot class get built.

### Amendments to existing tickets

- **[12 — Audit and logging contract](12-audit-and-logging-contract.md)** — the mandatory event list, the three schema gaps, the `user.name` hatch, the typed-module ruling, correlation-id ownership, and `Std:259`/`:262`. Now also blocked by 18 and 19.
- **[09 — HTTP security, CSRF, CORS, headers](09-http-security-csrf-cors-headers.md)** — MDC filter placement in the security chain; two filters are needed, not one.
- **[11 — Password reset flow](11-password-reset-flow.md)** — reset-request auditability under enumeration resistance, and the one workflow a single `trace.id` cannot span.
- **[14 — Test and validation plan](14-test-and-validation-plan.md)** — assertable log requirements, plus a latent defect in the recipe's auth-method resolution that 01's custom filter will trip.
- **[15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md)** — the dependency and configuration consequences of the enforced constraints above.

---

**Narrowed by [02 — Persistence and session backend](02-persistence-and-session-backend.md).** This ticket ruled `Recipes/Logging_Batch_And_Scheduled_Jobs.md` **confirmed vacuous** on the grounds that neither of its gates — Spring Batch on the classpath, and `@Scheduled` — holds. The second gate **does** hold: 02 chose Spring Session JDBC, which ships a framework-internal expired-session cleanup sweep, exposed by Boot as `spring.session.jdbc.cleanup-cron` (default: every minute). 02 verified this from the property's existence and Spring Session's documented behaviour, **not** by inspecting the jar — there is no `pom.xml` yet — so it is to be confirmed at build time.

The ruling is narrowed, not reversed: **vacuous for jobs we author.** The recipe's subject is logging emitted *from* batch and scheduled jobs, and we neither write this sweep nor log from it. 02 kept the task rather than disabling it via `cleanup-cron: "-"`, because disabling it would leave the `SPRING_SESSION` table unbounded — and under 02's file-based `dev` H2, that table now actually persists across restarts.
