# 15 — Tech baseline and module structure

Type: grilling
Status: resolved
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

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md). Both blockers (01 and 02) are now resolved, so this ticket is unblocked.**

**Dependencies 02 adds to the set this ticket pins:** `spring-boot-starter-data-jpa`, `spring-session-jdbc`, `liquibase-core`, `com.h2database:h2`, `org.postgresql:postgresql`, an in-memory rate-limit cache (Caffeine or Bucket4j — [07](07-lockout-and-ip-throttling.md) picks), and Testcontainers with its PostgreSQL module for the portability run [14](14-test-and-validation-plan.md) now owns. These join the Actuator and Micrometer Tracing additions 05 already made mandatory. The Spring Boot 4.0+ floor 05 established still governs every pin.

**The datasource seam, so this ticket and 02 do not duplicate each other.** The standard requires datasource connection, pool and credentials to be externalised (`Standalone_User_Access_Control_Application_Standard.md:632`). 02 fixed the **property shape**: `spring.datasource.*` with two profile stanzas (`dev` file-based H2 at `jdbc:h2:file:./data/app` with `MODE=PostgreSQL`, `prod` PostgreSQL), HikariCP pool sizing, `spring.jpa.hibernate.ddl-auto: validate`, `spring.session.jdbc.initialize-schema: never`, `spring.session.jdbc.cleanup-cron`. **This ticket owns where the values come from** — the secrets mechanism, which now covers the PostgreSQL password alongside `app.admin.password`.

**A structural artifact this ticket must place:** `db/changelog/db.changelog-master.yaml` plus one versioned file per logical change (`001-users.yaml`, `002-roles.yaml`, …), each downstream ticket owning its own file. 02 locked that convention because `/do-work` generates these and 03, 04, 10 and 16 all add schema concurrently. This ticket decides where the directory sits in the module layout and whether the architecture-test rows say anything about it.

**Relevant to the `@Scheduled` warning already recorded here:** 02 found that Spring Session JDBC ships a framework-internal expired-session sweep, so the gate this ticket warned about is already tripped — but by framework code, not ours. 05's vacuity ruling is narrowed to *vacuous for jobs we author*, and the warning in this ticket stands unchanged for anything this structure adds.

**Also newly in scope for the layout:** the `./data/` directory the `dev` H2 file lives in must be gitignored, and the `prod` profile stanza exists without any hosting infrastructure behind it — a documented target, in the same spirit as the HTTPS deployment note this ticket already owns.

**Amended by [18 — Client IP in logs](18-client-ip-in-logs.md).** Two additions to what this ticket's layout and documented-assumptions work must place, and one secret it no longer has to handle.

- **A new structural directory: `docs/adr/`.** 18 is the first decision on this map that a reviewer outside the effort must be able to find without reading a wayfinder ticket, so it lands as an ADR rather than staying in `.scratch/`. Neither `docs/adr/` nor `CONTEXT.md` exists in this repo yet, so this ticket decides where the directory sits and whether any architecture-test row speaks to it. 18's Answer specifies the ADR's required content — title, context, decision, alternatives rejected, consequences — so whoever writes the file does not re-derive it; 18 deliberately left the file unwritten because it waits on this ticket's layout.
- **A new documented deployment assumption, beside the HTTPS gap: the `X-Forwarded-For` expiry.** 18 rejected `X-Forwarded-For` entirely, making `getRemoteAddr()` the sole source of client IP for both the audit trail and the IP throttle. That is correct **only** while topology stays single-instance with no proxy (01). Introducing any reverse proxy or load balancer makes `getRemoteAddr()` return the proxy's address, silently collapsing the throttle to one bucket and the audit trail to one address — a degradation with no error and no log line to announce it. It belongs in the same list as the HTTPS note and the `prod`-stanza-without-infrastructure note this ticket already carries, because it is the same shape: a correctness claim that holds only under a stated deployment assumption.
- **One secret this ticket does *not* need to handle.** 18 rejected HMAC'ing `source.ip` partly because `Std:278`'s "system-wide salt or HMAC" would have landed a long-lived, effectively non-rotatable key on this ticket's secrets mechanism. There is no log-hashing key; the secrets scope stays `app.admin.password` plus the PostgreSQL password.

## Answer

Resolved by grilling, two rounds, all sixteen questions accepted as recommended.

### A correction to this ticket's own premise

**§4 Separation of Concerns does not prescribe a package layout.** Its five bullets (`Standalone_User_Access_Control_Application_Standard.md:411-417`) govern *endpoint* boundaries — HTTP method plus path as the authorization contract, admin full-record access versus a dedicated self-read endpoint, role provisioning configuration-driven while user records are database-driven, generic update separate from password reset, scheduler runs serialized per job name. None of it names a package, module or layer. What the standard constrains there was already settled by [01](01-context-topology-and-api-surface.md) and [03](03-role-model-reconciliation.md); the code layout below is a free choice made on other grounds. The recipes' sample code uses `com.example.security.{filter,controller,command}`, which is illustrative, not normative.

### Baseline — the pins are the standards', not ours

**Java 21, Spring Boot 4.0.x, Spring Security 7.0.x**, exact patch versions in the POM rather than ranges.

The version floor is binding and unambiguous. All four Standalone recipes and all four Shared recipes state **Spring Boot 4.x with Spring Security 7.x** (`Standalone_Session_Login_with_CSRF_Bootstrap.md:18`, `Standalone_Privileged_User_Administration_and_Password_Reset.md:11`, `Standalone_Self-Service_Password_and_History_Management.md:15`, `Common_Security_Headers_and_SPA_CSRF_Configuration.md:16`, `Common_Role-Based_Access_Control_Configuration.md:15`, `Common_Secure_Self-Read_User_Endpoint.md:17`, `Common_Automatic_Database_Role_Synchronization_and_Deleted_Role_Backups.md:15`), three of them adding **Java 21/25**. The logging recipes agree (`Structured_Logging_Trace_Correlation_And_Context_Propagation.md:8`, `Custom_Structured_Log_Encoder.md:29`).

**The one apparent contradiction is not one.** `Standalone_User_Access_Control_Application_Standard.md:299` mandates the "SLF4J 2.0+ / Spring Boot 3.4+ fluent API". That is a **floor**, not a ceiling — Boot 4 satisfies it. There is nothing to reconcile.

Java 21 over 25: both are LTS and both are named by the standards, so 21 is chosen as the lower named floor, maximising tooling compatibility (ArchUnit, Testcontainers, Liquibase) at no conformance cost.

**Maintenance liability recorded against the pin:** if [19](19-log-format-and-custom-encoder.md) decides to build the custom encoder, it extends an **internal** Spring Boot class that `Custom_Structured_Log_Encoder.md:24` says "must be retested after every Spring Boot version upgrade". The pin is not the whole cost of that decision.

**Build tool: Maven**, with `mvnw`/`mvnw.cmd` committed so the build needs no local install. No standard pins Maven or Gradle; Maven is the Initializr default and what a reviewer will expect to run.

**Lombok: yes**, confined to entities and DTOs, with Java `record` preferred for anything immutable (API request/response bodies, `@ConfigurationProperties`). `Common_Role-Based_Access_Control_Configuration.md:17` names a "Standard JPA/Lombok stack" as a prerequisite and the recipe code we are meant to follow uses it; declining would mean hand-translating every snippet for no gain.

### The frontend is unconstrained by Appfw

Every binding recipe was grepped for `axios|fetch(|react-router|vite|package.json|tsx` — **zero hits**. The only Appfw frontend standard lives under `Appfw-Mfa-Standards/MFA_Frontend/`, which the map rules out of scope along with MFA. React choices therefore answer to the PRD and to this ticket alone.

**Vite + React 19 + TypeScript + `react-router` v7 + `axios`.**

Axios over `fetch` is the one non-taste call. `Standalone_User_Access_Control_Application_Standard.md:437` requires the SPA to catch a `401`/`403` from logout in a **global interceptor** and redirect to login without showing an error — a first-class axios feature, and code we would otherwise write by hand to satisfy a standard clause. A single axios instance with `withCredentials: true` also centralises the PRD's cross-origin credentials requirement in one place rather than per call site.

### Repository shape

**One repository, two sibling top-level directories.** The PRD runs the apps on separate origins (`localhost:3000` / `localhost:8080`) with CORS between them (`prd/assessment-prd.md:9-11`). A Boot-at-root layout with the SPA under `src/main/frontend/` implies a single deployable artifact that contradicts that topology; a Maven multi-module reactor buys nothing with CI/CD out of scope.

```
/
├── backend/          Spring Boot, Maven, mvnw
├── frontend/         Vite + React + TypeScript
├── prd/              (existing)
├── App-Standards/    (existing — 06's territory)
└── .scratch/         (existing — this map)
```

### Backend package layout

Base package **`com.assessment.auth`**, organised **feature-first** rather than layer-first. The reason is operational, not aesthetic: `/do-work` will generate these from separate issues concurrently, and feature-first keeps each ticket's work inside one directory ([04](04-password-policy-and-history.md) owns `password`, [07](07-lockout-and-ip-throttling.md) owns `ratelimit`, [11](11-password-reset-flow.md) owns `reset`), which minimises the merge surface between them.

```
com.assessment.auth
├── config/          SecurityConfig, CorsConfig, role hierarchy, Clock bean, @ConfigurationProperties records
├── user/            User entity, repository, self-read + admin user endpoints (03, 10)
├── auth/            login filter, logout, CSRF endpoint, session policy (01, 13)
├── password/        policy, history, reset tokens, stubbed EmailService (04, 11)
├── ratelimit/       account lockout + IP throttle (07)
├── audit/           the single typed audit-logging owner (12, 18, 19)
└── common/          global exception handler, error contract, MDC filter
```

Two of these packages are load-bearing rather than taste:

- **`audit/` as a single owner** is what makes the architecture-test rule below expressible at all, and is the motivation `Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md` gives. Note the module itself remains *recommended, not mandatory* per [05](05-logging-standards-applicability.md); what this ticket fixes is that if audit logging exists, it has exactly one home.
- **`common/`** holds the global exception handler that `Structured_Logging_Application_Standard.md:326` makes an `[Enforced Constraint]`, and the MDC filter with its `finally`-block clearing from `:325`.

### Dependency set — now closed

| Dependency | Source |
| --- | --- |
| `spring-boot-starter-web`, `-security`, `-validation` | PRD |
| `spring-boot-starter-data-jpa` | [02](02-persistence-and-session-backend.md) |
| `spring-session-jdbc` | [02](02-persistence-and-session-backend.md) |
| `liquibase-core` | [02](02-persistence-and-session-backend.md) |
| `com.h2database:h2`, `org.postgresql:postgresql` | [02](02-persistence-and-session-backend.md) |
| `spring-boot-starter-actuator` | `Structured_Logging_Application_Standard.md:321` (via tracing) |
| `micrometer-tracing-bridge-otel` | this ticket — see below |
| `org.projectlombok:lombok` | `Common_Role-Based_Access_Control_Configuration.md:17` |
| Testcontainers + PostgreSQL module | [02](02-persistence-and-session-backend.md), for [14](14-test-and-validation-plan.md)'s portability run |
| ArchUnit (JUnit 5) | this ticket — see architecture tests |
| `dependency-check-maven` (plugin, profile-gated) | `Standalone_User_Access_Control_Application_Standard.md:643` |
| in-memory rate-limit cache | **slot reserved; [07](07-lockout-and-ip-throttling.md) picks** |

**Tracing starter: `micrometer-tracing-bridge-otel`, not Zipkin.** `Structured_Logging_Application_Standard.md:321` is an `[Enforced Constraint]` putting Micrometer Tracing on the classpath, and `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md:12` says to add Actuator plus **one** tracing starter. OpenTelemetry wins on a concrete compliance detail: the recipe's own note at `:20` states the OTel starter defaults to **W3C Trace Context**, which the standard names as the format to propagate (`:277`), while Brave/Zipkin defaults to B3 and would need `management.tracing.propagation.type: w3c` set explicitly. One fewer property, and the compliant default rather than a compliant override.

**No exporter, endpoint or collector is configured**, per 05's finding (`Trace:14`; `Trace:10` lists trace-platform access as "Optional"). Actuator is on the classpath purely as tracing's carrier — which makes `/actuator/**` exposure a security question for [09](09-http-security-csrf-cors-headers.md), not an observability feature this app offers.

**The rate-limit cache slot stays genuinely empty.** [07](07-lockout-and-ip-throttling.md) picks, knowing that Caffeine is a cache with TTL eviction while Bucket4j is a purpose-built token-bucket rate limiter. If 07 forms no preference, default to Caffeine.

### Configuration layout

```
backend/src/main/resources/
├── application.yaml          profile-independent: app.*, role definitions + url-guards (03),
│                             session timeouts (13), logging.structured.*, management.*
├── application-dev.yaml      file-H2 datasource, relaxed logging, seed switches
├── application-prod.yaml     PostgreSQL datasource, Secure cookies, prod log levels
├── logback-spring.xml        appenders only
└── db/changelog/             db.changelog-master.yaml + 001-users.yaml, 002-roles.yaml, …
backend/src/test/resources/
└── application-test.yaml     in-memory H2, MODE=PostgreSQL
```

**Role definitions sit in the profile-independent file deliberately.** `Standalone_User_Access_Control_Application_Standard.md:414` makes role provisioning configuration-driven, and a role set that varied by profile would make [03](03-role-model-reconciliation.md)'s guards untestable — the `test` profile would be asserting against a different authorization matrix than `prod` runs.

**Liquibase changelogs are classpath-relative** (`db/changelog/db.changelog-master.yaml`), so a single `spring.liquibase.change-log` value serves every profile and the Testcontainers run alike. 02's convention — master including one versioned file per logical change, each downstream ticket owning its own file — is unchanged; this ticket only places the directory.

### Secrets

**Environment variables only, with no fallback default in any committed file.**

- `app.admin.password: ${APP_ADMIN_PASSWORD}` — no `:default`, so a missing variable **fails context startup** rather than silently seeding a guessable admin. This is the security property, not a style preference: a default here is a published credential.
- `spring.datasource.password: ${DB_PASSWORD}` — likewise, satisfying the externalised-credentials item at `Standalone_User_Access_Control_Application_Standard.md:632`. 02 owns the property *shape*; this ticket owns where the values come from.
- Committed: a **`.env.example`** listing names and dummy values, documentation only and never read by the app. `.env` is gitignored.
- **The `dev` profile is the one exception**: file-based H2 needs no password, so `dev` boots with only `APP_ADMIN_PASSWORD` set.

### Logging configuration — `logback-spring.xml` is mandatory, and minimal

The file is unavoidable: `Structured_Logging_Application_Standard.md:327` requires audit logs routed to a **dedicated appender and destination**, which `application.yaml` cannot express. It is kept as small as that constraint allows.

- **Exactly two appenders**: `CONSOLE` for application logs, and a rolling-file `AUDIT` to a separate path.
- **Every encoder reads `${CONSOLE_LOG_STRUCTURED_FORMAT}` / `${FILE_LOG_STRUCTURED_FORMAT}`**, never a literal. `:320` is an `[Enforced Constraint]` on exactly this, and 05 caught the recipe itself violating it (`Custom_Structured_Log_Encoder.md:389` hardcodes an `ecs` format element) — so that snippet **cannot be copied as written**. Defaults are supplied through `logging.structured.format.*` in `application.yaml`; Spring's relaxed binding makes the property and the environment variable the same knob, so one default serves both and a deployment can still switch format without editing XML.
- **Levels, log groups and the rolling policy stay in `application.yaml`**, where they are readable and profile-overridable.
- **The audit logger is bound by logger name, not by marker.** This is what makes the architecture-test rule below enforceable — a name is a static fact, a marker is a runtime argument.
- This does **not** decide the encoder. Whether a custom one is built at all remains [19](19-log-format-and-custom-encoder.md)'s call, and the file must accept either outcome.

### `service.*` fields, stamped from the build

`Structured_Logging_Application_Standard.md:125` forbids setting these at log time; `Log_Schema.md:50,57` says they come from `application.yaml`.

- **`service.version` comes from the build, not a hand-typed string.** Bind the `spring-boot-maven-plugin`'s **`build-info` goal** so Maven writes `META-INF/build-info.properties` at package time; Boot populates `spring.application.version` from it automatically (`Log_Schema.md:50` accepts exactly that property). The POM version becomes the single source of truth and there is nothing to keep in sync — which matters because a stale `service.version` is invisible until someone tries to correlate an incident to a release.
- `service.name` via `spring.application.name`.
- `service.environment` via `logging.structured.ecs.service.environment`, set **per profile** to a value from `Log_Schema.md:121`'s allowed set (`dev`, `test`, `staging`, `prod`).

### Timezone and local file output — deployment-side, named explicitly

**`TZ` is pinned to `Asia/Singapore`** per `Structured_Logging_Application_Standard.md:167`. Set as `-Duser.timezone=Asia/Singapore` in the Maven run configuration **and** documented as `TZ=Asia/Singapore` in the deployment note. Relying on the host clock is precisely the silent-failure mode the clause guards against. 02 already confined the blast radius: all timestamps are stored `TIMESTAMP WITH TIME ZONE` in UTC, so this pin affects **log rendering only**.

**The rolling local JSON file is taken**, despite no forwarding agent being in scope. `:275` calls for durable local buffering rather than shipping logs over the network from the application, and `:304` makes it pure configuration (`logging.file.name`, `logging.logback.rollingpolicy.*`). It is also the audit appender's destination regardless. With no forwarding agent, "durable local disk" is simply where the 90-day retention evidence (`:291`) physically lives; the absence of the agent is the integrator's gap, documented rather than hidden.

### Gitignore

`backend/data/` (02's file-H2 at `jdbc:h2:file:./data/app`), `backend/logs/`, `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.env`.

**One trap recorded:** `./data` resolves against the **working directory**, so the path differs between `mvn spring-boot:run` from `backend/` and a run from the repo root — two different databases, and Story 12's no-duplicate-admin branch would appear to fail. Pin the `dev` datasource URL to an absolute-from-project-root path rather than leaving it relative.

### Architecture tests

**ArchUnit on the backend; no dependency-cruiser on the frontend.** Four rows, each guarding an `[Enforced Constraint]` or a decision above, and each chosen because review does it badly:

1. **No `System.out.println`, no `e.printStackTrace()`** anywhere — `Structured_Logging_Application_Standard.md:330`, an `[Enforced Constraint]`.
2. **Only `com.assessment.auth.audit` may log to the audit logger** — makes the single-owner routing structural rather than a convention that erodes.
3. **No `@Async` and no `@Scheduled` anywhere under `com.assessment.auth`** — the tripwire this ticket's brief demanded. 05 showed `@Scheduled` requires no dependency at all, so an expired-token sweep would silently activate the 1,254-line batch-logging recipe; `@Async` would activate the `TaskDecorator` constraint at `:324`. A failing test converts "a scope change to raise" into something that cannot be added by accident. **Framework-internal scheduling is unaffected** — 02's Spring Session JDBC cleanup sweep is not our code and no ArchUnit rule over our packages can see it.
4. **No manually constructed HTTP clients** (`new RestTemplate()` and friends) — `:322`, an `[Enforced Constraint]` about trace propagation.

Dependency-cruiser is declined: the SPA has no layering worth enforcing at this size, and an unenforced config file is worse than none.

**This resolution graduates the map's architecture-test fog patch**: `arch-tests-plan` now has real package names to write rows against.

### Dependency vulnerability scanning — plugin wired, schedule declined

`Standalone_User_Access_Control_Application_Standard.md:643` lists the scan tool, its schedule and a CVE severity threshold under **Required Runtime Configuration**, so it is a configuration item the standard expects answered rather than left blank. Add **`dependency-check-maven`** to the POM in a **non-default Maven profile** (so `mvn verify` stays fast) with `failBuildOnCVSS=7`. The **schedule is documented as the integrator's**, since CI/CD is a PRD exclusion — the standard asks for a scan capability, and a scan that can only be triggered by hand is an honest answer to it where a fabricated CI schedule would not be.

### The HTTPS deployment note

`prd/assessment-prd.md:120` requires the transport gap to be documented, and the standard's Required Runtime Configuration expects `Secure=true` cookies and HSTS. The note lives in a **`backend/README.md`** deployment section and states: any real deployment sits behind HTTPS (required for `Secure` cookies and HSTS); local development runs over HTTP as an accepted, documented gap; the `prod` profile exists as a **declared target with no hosting infrastructure behind it**, in the same spirit. It collects the deployment-side items that are invisible in code — `TZ`, the environment variables from the secrets section, and the absent log-forwarding agent.

### Tickets amended by this resolution

- **[07](07-lockout-and-ip-throttling.md)** — the rate-limit cache slot is reserved in the POM; 07 picks Caffeine or Bucket4j, defaulting to Caffeine if indifferent.
- **[09](09-http-security-csrf-cors-headers.md)** — Actuator is now definitely on the classpath as tracing's carrier, so `/actuator/**` exposure is 09's to close; nothing about it is an offered feature.
- **[12](12-audit-and-logging-contract.md)** — the audit logger is bound **by logger name**, not by marker, and `com.assessment.auth.audit` is its single owner.
- **[14](14-test-and-validation-plan.md)** — inherits the four ArchUnit rows, the profile-gated `dependency-check-maven` run, and the `application-test.yaml` placement.
- **[16](16-admin-bootstrap-and-seeding.md)** — `app.admin.password` resolves from `${APP_ADMIN_PASSWORD}` with **no default**, so startup fails when it is absent; and the `dev` H2 path must be absolute-from-project-root or Story 12's idempotence branch is untestable.
- **[19](19-log-format-and-custom-encoder.md)** — the `logback-spring.xml` is placed and mandatory, must select format by environment variable, and must accept either encoder outcome; the rolling file is taken.
- **[17](17-handoff-to-delivery-pipeline.md)** — the baseline, layout and configuration inventory the pipeline needs are now fixed.

**Amended by [19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md).** 19 settled the encoder this ticket deliberately left open, confirmed one of its "likely" rulings, and **corrected one**.

- **Correction: the appender inventory is three, not two.** This ticket's "exactly two appenders" (`CONSOLE` plus a rolling-file `AUDIT`) rests on the reasoning that the rolling local JSON file "is also the audit appender's destination regardless". 19 found that conflates two distinct clauses. `Structured_Logging_Application_Standard.md:275` wants a durable local rolling file so a forwarding agent can ship **all** logs without losing them to an outage; `:327` requires audit logs on a **separate** appender and destination so retention and access controls apply independently. Under two appenders, ordinary application logs have **no durable local file at all** — `CONSOLE` is not durable, and with hosting out of scope nothing captures stdout — while the `AUDIT` file cannot absorb them without breaching `:327`. The inventory becomes **`CONSOLE` + rolling `APPLICATION` + rolling `AUDIT`**, the latter two on separate paths. This ticket's decision to take the rolling file stands; what changes is that it is not the same file as the audit destination.
- **Confirmed: `logback-spring.xml` is mandatory**, settling this ticket's "likely". `:327`'s dedicated audit appender cannot be expressed in `application.yaml`, and the requirement is now doubly forced — `Custom_Structured_Log_Encoder.md:32` requires the XML to register the custom encoder at all.
- **Confirmed: the encoder is built**, so the maintenance liability this ticket recorded against the Boot 4.0.x pin is live rather than contingent. It is also worse than recorded: 19 found the encoder is the only mechanism available under this route to discharge `:328`'s masking `[Enforced Constraint]`, so `Custom_Structured_Log_Encoder.md:24`'s retest-after-upgrade is a compliance gate. 14 carries it as a named test.
- **The encoder is roughly half the recipe's size.** 19 dropped `ErrorCategoryResolver` (`Custom_Structured_Log_Encoder.md:128-243`, ~115 lines) because `:334` invokes it only when a throwable is present, and the three security events that matter here have none. What remains is one transformation (`error_code` / `error_category` / `error_follow_up_action` into the `error` object, creating it when absent) plus the masking hook.
- **This ticket's env-var mechanism holds, with one addition.** Supplying defaults through `logging.structured.format.*` in `application.yaml` and relying on Spring's relaxed binding is correct — `Trace:940,949` confirms Spring Boot exposes those properties as the `CONSOLE_LOG_STRUCTURED_FORMAT` / `FILE_LOG_STRUCTURED_FORMAT` system variables during initialisation. 19 adds the inline `${...:-ecs}` fallback shown at `Trace:912` as defence, because a custom `logback-spring.xml` disables Boot's logging auto-configuration (`Trace:897`) and an unresolved variable yields *no* format rather than a default one. Also worth recording: `Trace:912` is the correctly-written form of the very snippet this ticket flagged `Custom_Structured_Log_Encoder.md:389` for violating — so that trap is a defect in one recipe, not a standards conflict.
- **No `service.*` encoder workarounds are needed.** `Custom_Structured_Log_Encoder.md:65-70` lists `service.system`, `service.subsystem` and `service.connection.*` as fields that would also require the underscore workaround. None applies here: this ticket put all `service.*` values in `application.yaml`, which is where `Custom_Structured_Log_Encoder.md:85` requires them.

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** One new ArchUnit row, and one dependency-shaped consequence of 08 making the URL matrix the sole authorization mechanism.

- **A fifth ArchUnit row: every request-mapped method under `com.assessment.auth` must have its method+path present in `app.security.authorization-matrix`.** 08 deleted all five recipe `@PreAuthorize` annotations (they name authorities that do not exist under 03's role model) and ruled against `@EnableMethodSecurity`, so a controller's guard now lives entirely in configuration. The new failure mode is an endpoint with **no** row: it fails closed, which is safe, but silently — and tests only assert the rows that exist. Only a static check catches the endpoint that has no row at all. This sits alongside the `@Scheduled`/`@Async` ban rather than replacing anything.
- **`@EnableMethodSecurity` must not appear**, and that is worth stating as a rule rather than an absence: adding it later would silently reactivate any annotation someone copies back in from `Priv`, `SelfSvc` or `SelfRead`, restoring a privilege vocabulary 03 deleted.
- **Two configuration properties land in 15's layout**, both profile-independent: `app.security.authorization-matrix` (a flat ordered sequence of `{method, path, access}` rows — 08 rejected `RBAC:38-43`'s authority-keyed map because it cannot express order) and `app.security.forced-change-allowlist` (04's four paths, deliberately *not* derived from the matrix's public rows). Plus `server.error.whitelabel.enabled=false`, which pairs with 08's `permitAll` row on `/error`.
- **No new dependency.** 08 needs nothing beyond what 15 already pinned; `HttpStatusEntryPoint`, `PathPattern` and the `AccessDeniedHandler` contract are all in `spring-security-web` / `spring-web`. 08's pattern-matching evidence was produced against `spring-web-7.0.8`, consistent with this ticket's Spring Framework 7 pin.
