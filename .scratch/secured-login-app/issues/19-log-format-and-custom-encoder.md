# 19 — Structured log format and the custom encoder

Type: grilling
Status: open
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
