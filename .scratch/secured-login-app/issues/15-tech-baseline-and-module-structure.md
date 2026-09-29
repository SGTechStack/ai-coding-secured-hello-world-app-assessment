# 15 — Tech baseline and module structure

Type: grilling
Status: open
Blocked by: 01, 02
Map: [Secured Login App](../map.md)

## Question

What are the pinned technology versions, and how is the code organised?

Two halves, both of which the delivery pipeline needs before it can generate anything.

**Baseline.** Java version, Spring Boot version, build tool (Maven versus Gradle), and the Spring modules in play (Web, Security, Data JPA, Session, Validation, Actuator). On the frontend: React version, build tooling (Vite versus CRA versus Next), TypeScript or not, router, and HTTP client. Check whether `App-Standards/` pins any of these — if the standards or their recipes assume particular versions, those pins are binding, and finding that out is part of this ticket rather than a guess.

**Structure.** The standard's §4 Architectural Design and its Separation of Concerns section (`Standalone_User_Access_Control_Application_Standard.md:393,411`) prescribes how authentication, persistence and web layers are divided. Produce the concrete package layout for this application, and the frontend's directory layout. Decide whether it is one repository with two top-level directories, and where configuration lives.

**Also settle here**, since both depend on the layout:

- Configuration and secrets handling: what is externalised, how `app.admin.password` (Story 12) is supplied without landing in version control, and the documented HTTPS deployment assumption the PRD requires (`prd/assessment-prd.md:120`). This graduates the map's config/secrets fog.
- Whether architecture tests enforce the layering — `arch-tests-plan` exists in this repo's skills and its rows need real package names, so this ticket is what unblocks that fog patch.

Blocked on 01 (API surface shapes the web layer) and 02 (persistence choice shapes the data layer and the dependency set).

**Amended by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md).** The logging standard's `[Enforced Constraint]` list has direct consequences for the dependency set, the version pins and where configuration lives — all of this ticket's territory.

**Dependencies that are mandatory, not optional.**

- **Micrometer Tracing must be on the classpath.** `Structured_Logging_Application_Standard.md:321` is an `[Enforced Constraint]`; 05 established the standalone-service exemption in `Structured_Logging_Application_Standard_Questions.md:404-406` is guide text that cannot override it. Add `spring-boot-starter-actuator` plus **one** tracing starter — OpenTelemetry or Zipkin (`Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:12`). **No exporter, endpoint or collector configuration** is needed, since nothing is exported (`Trace:14`, and `Trace:10` lists trace-platform access as "Optional"). Pick which starter and record why.
- **Actuator therefore enters the dependency set** — which means its endpoint exposure becomes a security-configuration question for [09 — HTTP security, CSRF, CORS, headers](09-http-security-csrf-cors-headers.md) (`/actuator/**` must not be open), and this ticket's Spring-modules list, which already names Actuator provisionally, is now settled rather than a guess.
- **Spring Boot version.** `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:8` states Spring Boot 4.0+; `Recipes/Custom_Structured_Log_Encoder.md:29` states the same for the custom encoder. So the pin this ticket sets is **constrained by the standards, not free** — exactly the "check whether `App-Standards/` pins any of these" item already in its brief, now with a concrete answer for the logging half. Whether the custom encoder is built at all is [19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md)'s call, and `Custom_Structured_Log_Encoder.md:24` warns it extends an **internal** Spring Boot class that "must be retested after every Spring Boot version upgrade" — a maintenance liability worth recording against the pin.

**Configuration layout consequences.**

- A **`logback-spring.xml` is likely mandatory**, because `Structured_Logging_Application_Standard.md:327`'s `[Enforced Constraint]` — audit logs routed to a dedicated appender and destination — cannot be expressed in `application.yaml` alone. [19](19-log-format-and-custom-encoder.md) confirms; this ticket places the file and decides how it is kept in step with profile-specific config.
- **The structured format must be selected by environment variable, never hardcoded.** `Structured_Logging_Application_Standard.md:320` is an `[Enforced Constraint]` naming `CONSOLE_LOG_STRUCTURED_FORMAT` / `FILE_LOG_STRUCTURED_FORMAT`. 05 found the recipe itself violates this (`Custom_Structured_Log_Encoder.md:389` hardcodes `<format>ecs</format>`), so it cannot be copied as written. This adds two entries to this ticket's externalised-configuration list.
- **Timezone is pinned to Singapore Time UTC+8** (`Structured_Logging_Application_Standard.md:167`), which is a `TZ` environment variable or JVM argument — deployment-side configuration this ticket should name explicitly, since it is invisible in code and silently wrong if omitted.
- `service.name`, `service.version` and `service.environment` are **auto-populated from `application.yaml`** and must not be set at log time (`Structured_Logging_Application_Standard.md:125`). `service.version` in particular has to come from somewhere real — the build — so decide how the build stamps it (`spring.application.*` / `logging.structured.ecs.service.*`, per `Log_Schema.md`'s Service note).
- **Local rolling file output.** `Structured_Logging_Application_Standard.md:275` calls for durable local buffering to a rolling JSON file for a forwarding agent rather than shipping logs over the network from the application; `:304` notes Spring Boot provides it via `logging.file.name` and `logging.logback.rollingpolicy.*`. Nearly free, but a deliberate call given no forwarding agent is in scope — coordinate with [19](19-log-format-and-custom-encoder.md), which raises the same line.

**One baseline constraint to record, because it is cheap now and expensive later: keep the stubbed `EmailService` synchronous.** `Structured_Logging_Application_Standard.md:324` is an `[Enforced Constraint]` requiring a `TaskDecorator` that copies and restores the full MDC map around every `@Async` method. 05 confirmed that constraint — and the async-propagation sections of two long recipes — is **vacuous only while nothing in the application is `@Async`**. A logging-only stub gains nothing from being asynchronous and would activate all of it. Note also a genuine contradiction inside the binding set should this ever be revisited: `Structured_Logging_Application_Standard.md:314` says Micrometer Context Propagation does **not** carry application-defined MDC fields across async boundaries, while `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:531` and `Recipes/Enriching_Logs_With_MDC.md:461` say it does.

**Related: nothing here needs `@Scheduled`.** 05 confirmed `Recipes/Logging_Batch_And_Scheduled_Jobs.md` (1,254 lines) is vacuous, but flagged how cheaply the trigger trips: `@Scheduled` requires no dependency at all, so an expired-reset-token sweep or a session-cleanup task would activate the entire batch-logging recipe. If this ticket's structure creates any such task, it is a scope change to raise, not an implementation detail.

**Architecture-test consequence** (this ticket unblocks the `arch-tests-plan` fog patch): the §4 Separation of Concerns rows should also cover the logging boundary — that audit logging goes through one owner rather than scattered `LoggerFactory` calls (the motivation for `Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md`), and that `System.out.println` and `e.printStackTrace()` appear nowhere, which `Structured_Logging_Application_Standard.md:330` makes an `[Enforced Constraint]` and which a static rule enforces better than review.
