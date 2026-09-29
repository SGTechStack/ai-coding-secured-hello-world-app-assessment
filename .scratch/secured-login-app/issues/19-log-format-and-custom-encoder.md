# 19 — Structured log format and the custom encoder

Type: grilling
Status: resolved
Assignee: taniakoh
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

Which structured log format does this application emit, and does that oblige building the custom encoder?

Surfaced by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md). The format choice is not cosmetic: it decides whether roughly 200 lines of fragile code extending an **internal** Spring Boot class get built, and it is the one logging decision with a direct line into [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).

**What binds.** `Structured_Logging_Application_Standard.md:319` is an `[Enforced Constraint]`: enable "the agreed structured logging format ... (e.g., `ecs`)" via `logging.structured.format.console` / `.file`. `:308` names ECS, GELF and Logstash as the available formats, and `:293` requires "the agreed structured format (e.g., ECS JSON) so that automated masking, redaction, and pipeline parsing apply consistently". So *a* structured format is mandatory; **which** one is left to the project — and there is no log platform in scope to agree it with, since hosting is out of scope. That absence is what makes this a decision rather than a lookup.

**Why ECS is not free.** `Log_Schema.md` is written in ECS field names and `Structured_Logging_Application_Standard.md:188` expects nested `error.code` / `error.category` / `error.follow_up_action`. Under Spring Boot's native ECS support those nested error fields are **not** emitted without the custom encoder of `Recipes/Custom_Structured_Log_Encoder.md`, which `Recipes/Logging_Exceptions_With_Enhanced_Details.md:10` lists as a hard prerequisite. Two research reports independently flag that under ECS without the encoder, `error.code` **throws at runtime** (`Recipes/Enriching_Logs_With_MDC.md:598`, `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:852`) — so every error path in the application depends on this choice. Confirm that claim directly when resolving; it was not re-verified in 05.

**The cost of the encoder.** `Custom_Structured_Log_Encoder.md:24` warns it extends an **internal** Spring Boot class and "must be retested after every Spring Boot version upgrade"; `:29` requires Spring Boot 4.0+ (which [15](15-tech-baseline-and-module-structure.md) must then pin). `Custom_Structured_Log_Encoder.md:11` and `:511` state that **Logstash and GELF are immune** — they need no custom encoder.

**Two traps to resolve, not just note.**

- `Custom_Structured_Log_Encoder.md:389` hardcodes `<format>ecs</format>` in the Logback XML, which **directly violates** `Structured_Logging_Application_Standard.md:320`, an `[Enforced Constraint]` requiring the encoder be selected via the `CONSOLE_LOG_STRUCTURED_FORMAT` / `FILE_LOG_STRUCTURED_FORMAT` environment variables so the format can be switched at deployment without editing the file. The recipe cannot be copied as written.
- `Custom_Structured_Log_Encoder.md:408` means an `error_code` set on a **non-exception** `ERROR` event is **silently dropped**, colliding with `Structured_Logging_Application_Standard.md:186`. Several of this application's audit events are error-level without an exception — lockout is logged at `ERROR` (`Recipes/Logging_AuthN_And_AuthZ_Events.md:37`) and carries no throwable. Decide how those events carry a code.

Settle:

- The format: ECS, Logstash or GELF, with the reasoning recorded — including the fact that no downstream platform exists to constrain it, so the choice is being made on the standard's own field vocabulary rather than on a consumer.
- Whether the custom encoder is built. If ECS, it is effectively forced; if not ECS, state what is given up, since `Log_Schema.md`'s vocabulary is ECS-shaped and the whole binding set is written against it.
- Native Spring Boot structured logging (`logging.structured.format.*`) versus a third-party `logback-ecs-encoder` in a custom `logback-spring.xml` — `Structured_Logging_Application_Standard.md:319` explicitly distinguishes the two configuration routes and they are not interchangeable.
- Whether a `logback-spring.xml` exists at all. It is required by `Structured_Logging_Application_Standard.md:327`'s mandatory **separate audit appender**, which cannot be expressed in `application.yaml` alone — so this likely forces the XML regardless of format, and [12 — Audit and logging contract](12-audit-and-logging-contract.md) depends on the answer.
- The `ERROR`-without-exception code path, per the second trap above.
- Whether the local rolling JSON file of `Structured_Logging_Application_Standard.md:275` (durable buffering for a forwarding agent) is configured, given no forwarding agent is in scope. `:304` notes Spring Boot provides it via `logging.file.name` and `logging.logback.rollingpolicy.*`, so it is nearly free — but it is still a deliberate call, not a default.

Blocks [12 — Audit and logging contract](12-audit-and-logging-contract.md) and feeds [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md) (Spring Boot version pin, dependency set, config file layout).

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** 15 placed the configuration files and settled everything about `logback-spring.xml` **except** the encoder, which stays this ticket's call. What is fixed: the file **is mandatory** (only it can express the dedicated audit appender `Structured_Logging_Application_Standard.md:327` requires), it declares **exactly two appenders** (`CONSOLE`, plus a rolling-file `AUDIT` to a separate path), levels/groups/rolling policy stay in `application.yaml`, and **every encoder must read `${CONSOLE_LOG_STRUCTURED_FORMAT}`/`${FILE_LOG_STRUCTURED_FORMAT}` rather than a literal** — `:320` is an `[Enforced Constraint]` and `Custom_Structured_Log_Encoder.md:389` violates it, so that snippet cannot be copied as written whichever way this ticket rules. 15 also **took the rolling local JSON file** (`:275`, `:304`), independently of the encoder question this ticket raises it under. And it recorded the maintenance liability against the Boot 4.0.x pin: the custom encoder extends an internal Spring Boot class that `Custom_Structured_Log_Encoder.md:24` says must be retested after every Boot upgrade.

---

## Resolution

**Format: ECS**, via **Spring Boot's native structured logging** (`logging.structured.format.console`/`.file`), with a **custom `StructuredLogEncoder` subclass that is built** — but deliberately deviating from `Recipes/Custom_Structured_Log_Encoder.md` on the one behaviour that matters, and shrunk to roughly half the recipe's footprint.

### The ticket's framing was wrong on three counts, and two of the corrections changed the answer

**1. The runtime-crash claim is true but misframed, and it does not force the encoder.** Only *dotted* keys throw (`Recipes/Enriching_Logs_With_MDC.md:596`, `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:852`, `Recipes/Logging_Exceptions_With_Enhanced_Details.md:27`, `Recipes/Custom_Structured_Log_Encoder.md:9`) — verified directly, as the ticket asked. But `Structured_Logging_Application_Standard.md:188` *mandates the underscore form*, and `:390` names `error_code`/`error_category` as the fields incident investigation filters on. Underscore keys write safely at root **with no encoder at all**. So no error path in this application crashes without the encoder; the encoder's contribution is nesting, not correctness. The ticket's "every error path in the application depends on this choice" is false as stated — which removes the coercion and made this a genuine decision rather than a forced move.

**2. Trap 2 is far worse than the ticket states, and it is what the ticket should have led with.** The ticket names lockout. In fact **all three** of the AuthN recipe's security-event templates carry the full error triplet and **none** attaches a throwable:

| Event | Level | Fields | `.setCause(e)` |
|---|---|---|---|
| Account locked (`Recipes/Logging_AuthN_And_AuthZ_Events.md:76-87`) | `ERROR` | `error_code: 423`, `error_category`, `error_follow_up_action` | no |
| Authentication failed (`:89-101`) | `WARN` | `error_code: 401` + both | no |
| Authorisation denied (`:438-449`) | `WARN` | `error_code: 403` + both | no |

`Custom_Structured_Log_Encoder.md:408` and `:512` are explicit: no throwable → no `error` object → the workaround keys are **stripped from root and silently dropped**. The encoder *as the recipe specifies it* would therefore delete **nine fields across the three events at the centre of this application's audit trail** — including the lockout code that is the entire machine-readable signal of an active brute-force attack. This is not a corner case to note; it is the majority of this app's audit failures, and it is why the recipe is followed only in part.

**3. That behaviour is non-compliant with the standard the recipe serves.** `Structured_Logging_Application_Standard.md:167`: *"If required fields are absent, the application MUST still emit the event and MUST NOT suppress or drop it silently."* The recipe's strip-without-inject is exactly a silent drop of a required field. The deviation below is therefore **compelled, not chosen** — the recipe and the standard genuinely conflict, and the standard is the clause list (05's governing finding).

**Trap 1 dissolves: it is already solved inside the binding set.** The ticket treats `Custom_Structured_Log_Encoder.md:389`'s hardcoded `<format>ecs</format>` as an unresolved conflict with `:320`. But `Structured_Logging_Trace_Correlation_And_Context_Propagation.md:912` gives the correct form the encoder recipe botches: `<format>${FILE_LOG_STRUCTURED_FORMAT:-ecs}</format>`, and `:949` documents the variable mapping. `:389` is a defect in one recipe, not a standards conflict. The `:-ecs` fallback is load-bearing, not cosmetic — a custom `logback-spring.xml` disables Spring Boot's logging auto-configuration (`Trace:897`), so an unset variable yields *no* format rather than a default one.

### Why ECS, when nothing downstream constrains it

No log platform is in scope, so the choice rests on the standard's own field vocabulary — and that vocabulary *is* ECS. The alternatives are formally permitted and practically unsupported:

- **GELF appears exactly once in all 7,169 lines** of the binding set: `Structured_Logging_Application_Standard.md:300`, a `[Design Choice]` enumerating what Spring Boot supports. Zero guidance, zero examples.
- **Native `logstash` appears only at `:300` and `Trace:167`.** Every other "Logstash" hit in the set is the *third-party* `logstash-logback-encoder` in `Recipes/Sensitive_Data_Masking_For_Logs.md` — a different artifact from Spring Boot's `logstash` format, and a distinction easy to misread as support.
- **`Structured_Logging_Application_Standard_Questions.md` mentions the format question nowhere** — zero hits for ecs/gelf/logstash across all 760 lines. The map's note that the question set is a pre-written decision frontier holds, but it simply does not reach this decision.

`Custom_Structured_Log_Encoder.md:11` and `:511` are right that Logstash and GELF are immune to the sealed-object problem. That immunity is not worth its price: it is bought by forfeiting **every ★ auto-populated field** in `Log_Schema.md:40-52` — `log.level`, `log.logger`, `process.thread.name`, the whole `service` object, and `error.type`/`error.message`/`error.stack_trace` from `.setCause(e)`. Spring's native `logstash` format emits `level`, `logger_name` and `thread_name` instead: *different field names from the schema's*, and not renameable. `logging.structured.ecs.service.*` is ECS-only (`Log_Schema.md:49-52`, `Trace:293`). **Avoiding a problem confined to three error fields would break eight named ones.**

Recorded scope nuance, so it is not re-litigated: `Log_Schema.md:1` titles itself *"Logging Schema for **Batch and Interface** Applications"*, yet the standard cites it as the universal field authority. Read as universal, consistent with 05.

### Native route, not the third-party encoder

`Structured_Logging_Application_Standard.md:319` sanctions two routes and says they are not interchangeable. The third-party `logback-ecs-encoder` route is real, and it would dissolve the encoder question entirely (it does not pre-seal objects, so dotted keys work). It is rejected on two grounds:

1. **It appears to collide with an `[Enforced Constraint]`.** `:320` requires the encoder be selected via `CONSOLE_LOG_STRUCTURED_FORMAT`/`FILE_LOG_STRUCTURED_FORMAT`. Those are Spring Boot's own variables, wired to `logging.structured.format.*` (`Trace:949`) — precisely the mechanism a third-party encoder bypasses.
2. **It trades a known, bounded defect for an unevidenced one.** `:319` is the *only* mention of `logback-ecs-encoder` in the entire standards set. Its Boot 4.0.x compatibility, its `.setCause(e)` behaviour, and whether `:320` can be honoured under it are all unverified — and unverifiable from the repo.

Every recipe, verification checklist and configuration property in the binding set assumes the native route. **The native route is chosen with its cost stated plainly:** it is the route that *creates* the sealed-object problem. We take it because the binding set is written against it.

### The encoder is built — and its justification is masking, not nesting

The ticket frames the encoder as ~200 lines serving one cosmetic nesting concern. That undersells it, because of a coupling the ticket does not mention.

`Structured_Logging_Application_Standard.md:328` is an `[Enforced Constraint]`: apply masking at the logging boundary via one of three named mechanisms — a custom Logback converter, a `PatternLayout` masking rule, or **a structured log encoder extension**. Under ECS with the native route, `PatternLayout` is irrelevant to JSON output, which leaves the converter or the encoder. `Sensitive_Data_Masking_For_Logs.md:146` does offer an escape — *"If the application does not use this encoder, skip this section and rely on prevention at the source"* — but that hatch is scoped to **that recipe's specific `logstash-logback-encoder` technique**, not to the standard's clause. The clause stands.

So one boundary mechanism is required, and the only fitting one is an encoder extension — **the same artifact the error-nesting question is about**. The encoder is therefore not optional overhead bought for field cosmetics; it is the single artifact discharging **both** `:328` (masking mechanism) and `:186`/`:188` (nested error fields). That is the reasoning that decides this ticket.

**One encoder, two responsibilities:** move `error_*` root keys into the `error` object, and mask sensitive fields at the boundary. The masked-field list is **not this ticket's** — it belongs to [12 — Audit and logging contract](12-audit-and-logging-contract.md); this ticket fixes only where masking lives and that the hook exists. `Sensitive_Data_Masking_For_Logs.md:230` constrains 12: masking reaches JSON fields only, **never `message` text**, so sanitisation at the log site remains mandatory alongside it.

### The deviation from `Encoder:408`, stated unhedged

**When no `error` object exists in the formatter's output, the encoder CREATES it and nests the values, rather than stripping them.** Lockout emits:

```json
{ "error": { "code": 423, "category": "cert/auth", "follow_up_action": true } }
```

with no `type`/`message`/`stack_trace`. This is schema-valid: `Log_Schema.md:301-306` marks only those three fields ★ throwable-derived and leaves `code`, `category` and `follow_up_action` independently settable. `:301` also permits any integer `100–599`, so `423` is in range.

Two rejected alternatives, recorded so the choice is legible:

- **Strip only when injectable, leaving underscore keys at root otherwise.** No data loss, and defensible — `Std:390` itself names the underscore keys. Rejected because one logical field would carry two shapes depending on whether a throwable happened to be present, and every query over the audit trail would have to know which.
- **Attach a synthetic throwable so the `error` object exists.** Disqualified outright: it fabricates a stack trace pointing at the logging code rather than at a fault, putting fiction into an audit record to satisfy a formatter.

The deviation is from a **recipe**, not from the standard — and it is the standard (`:167`) that compels it. Under 05's governing finding, that is the correct direction of travel.

### `ErrorCategoryResolver` is out

`Custom_Structured_Log_Encoder.md:128-243` (~115 lines) maps exception classes to `error.category`. `:334` invokes it **only when a throwable is present**. Our three highest-value events have no throwable and set `error_category` explicitly, so **the resolver would never fire for any of them**. It would serve only the global exception handler's unexpected-exception path, where `Structured_Logging_Application_Standard.md:178` already makes `application` the near-universal answer.

Dropped. Every log site sets `error_category` explicitly. This roughly halves the encoder — the thing that made this ticket expensive — and removes a mapping table that silently mis-categorises any exception type it was not taught. **The encoder reduces to one transformation plus masking.** Its injection targets are the three from `Custom_Structured_Log_Encoder.md:118-120`: `error_code`, `error_category`, `error_follow_up_action`. The `service.*` workarounds that `:65-70` also lists are **not needed** — 15 put service fields in `application.yaml`, where `:85` requires them.

### Appender wiring, and a correction to 15

**Both appenders carry the custom encoder.** `Structured_Logging_Application_Standard.md:326` bans mixing plain text with JSON in one stream, and audit records need the same masking and nesting as application logs. `CONSOLE` reads `${CONSOLE_LOG_STRUCTURED_FORMAT:-ecs}`, the file appenders read `${FILE_LOG_STRUCTURED_FORMAT:-ecs}` (`Trace:912`, `:949`). The `:-ecs` fallback is mandatory, per `Trace:897`.

**`logback-spring.xml` is confirmed mandatory**, settling the "likely" in `15:35`: `:327` requires a dedicated audit appender, which `application.yaml` cannot express. Note this is now doubly forced — the custom encoder must be registered in XML regardless (`Custom_Structured_Log_Encoder.md:32`).

**Correction to [15](15-tech-baseline-and-module-structure.md): the inventory needs three appenders, not two.** `15:184` declares exactly two (`CONSOLE`, plus a rolling-file `AUDIT`), and `15:202` reasons that the rolling local JSON file "is also the audit appender's destination regardless". That conflates two distinct clauses:

- `:275` wants a durable local rolling JSON file so a forwarding agent can ship **all** logs without losing them to network outages.
- `:327` requires audit logs routed to a **separate** appender and destination, so retention and access controls apply independently.

Under the two-appender inventory, ordinary application logs have **no durable local file at all** — `CONSOLE` is not durable, and with hosting out of scope nothing captures stdout. The audit file cannot absorb them without violating `:327`. So: **`CONSOLE` + rolling `APPLICATION` file + rolling `AUDIT` file**, the latter two on separate paths. 15's ruling that the rolling file is taken stands; what changes is that it cannot be the same file as the audit destination.

### Downstream effects

- **[12 — Audit and logging contract](12-audit-and-logging-contract.md)** — unblocked. Owns the masked-field list (hosted in this encoder), must require `message`-text sanitisation separately per `Masking:230`, and inherits: every audit log site sets `error_category` explicitly; the error triplet uses underscore keys at the log site and appears dotted-and-nested in output on **all** events including throwable-less ones.
- **[14 — Test and validation plan](14-test-and-validation-plan.md)** — gains two **named** cases, not comments. First, the encoder is now load-bearing for an enforced constraint (`:328`), so `Custom_Structured_Log_Encoder.md:24`'s "retest after every Spring Boot upgrade" is an audit-compliance retest. Second, a test asserting the three throwable-less events emit nested `error.code` — that is the `Encoder:408` deviation, the one place our encoder intentionally departs from its recipe, and the regression that would silently gut the audit trail.
- **[15](15-tech-baseline-and-module-structure.md)** — amended: third appender; `logback-spring.xml` confirmed mandatory rather than likely; encoder confirmed built, so the maintenance liability 15 recorded against the Boot 4.0.x pin is real and now also a compliance liability.
- **Error-contract fog** — one of its two constraints is discharged: the `error_code`-dropped-on-non-exception-`ERROR` collision (`Custom_Structured_Log_Encoder.md:408` vs `Structured_Logging_Application_Standard.md:186`) is resolved here by the create-the-object deviation, so the error contract no longer has to work around it. The `ERROR`-vs-`WARN` contradiction for Bean Validation (`:97` vs `Questions.md:298`) is untouched and remains that patch's problem.

### No ADR

Deliberately, against 18's precedent. 18 earned an ADR because it recorded four genuine deviations from the *standard* and an unused compliance hatch. This ticket deviates from a **recipe** in the direction the standard commands, and the standard's own clause list is the justification. Recording it as an architectural decision would misrepresent a defect-workaround as a policy choice. The ticket body is the record; `Std:167` is the authority.
