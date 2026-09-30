# 03 — Extract the binding structured logging and audit schema

Type: research
Status: resolved
Blocked by: —

## Question

What exactly must an audit log line look like to satisfy `Appfw-Logging-Standards`, and what is the
complete field set, encoder configuration, and masking requirement?

The App Standard's §3.4 gives only a summary: SLF4J 2.0+ fluent API, ECS fields (`event.action`,
`event.outcome`, `user.id`), `trace.id`, a hashed session identifier, no cleartext usernames or
emails, 90-day retention. The authoritative detail lives in the logging standard and its recipes.

## What to find

Read:

- `App-Standards/Appfw-Logging-Standards/Log_Schema.md` — the canonical field list
- `App-Standards/Appfw-Logging-Standards/Structured_Logging_Application_Standard.md`
- `App-Standards/Appfw-Logging-Standards/Recipes/Logging_AuthN_And_AuthZ_Events.md` — the directly
  applicable recipe; likely prescribes our exact event shapes
- `App-Standards/Appfw-Logging-Standards/Recipes/Sensitive_Data_Masking_For_Logs.md`
- `App-Standards/Appfw-Logging-Standards/Recipes/Enriching_Logs_With_MDC.md` — correlation IDs
- `App-Standards/Appfw-Logging-Standards/Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md`
- `App-Standards/Appfw-Logging-Standards/Recipes/Custom_Structured_Log_Encoder.md`
- `App-Standards/Appfw-Logging-Standards/Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md`
- `App-Standards/Appfw-Logging-Standards/Recipes/Logging_Exceptions_With_Enhanced_Details.md`

Report:

1. The mandatory field set, with exact field names and which are required vs optional.
2. Prescribed `event.action` values — is there a closed vocabulary we must draw from, or do we coin
   our own? This directly shapes the audit event catalogue.
3. How `session.hash` is computed, and how `trace.id` is produced and propagated (Micrometer
   Tracing? OpenTelemetry? MDC filter?).
4. The encoder and appender configuration required — is a custom encoder mandated, or is a
   standard JSON encoder acceptable? Name the dependency and version.
5. The masking requirements and how they are enforced (encoder-level? call-site discipline?).
6. Whether a typed audit module is mandatory or merely recommended — it changes whether the design
   has a dedicated audit component or scatters `log.atInfo()` calls through services.
7. Anything that conflicts with the App Standard's §3.4 summary or with the PRD's plan to log the
   actor and target of admin actions. The PRD implies logging usernames; the standard bans cleartext
   usernames and requires UUIDs. Confirm whether the target of an admin action must also be a UUID.

## Done when

An authenticated log line can be written down field by field from the answer alone, the
`event.action` vocabulary question is settled, and the PRD's actor/target logging is reconciled with
the no-cleartext-PII rule.

---

## Answer

### Sources read

All under `c:\Users\zlimweil\Desktop\SGTechStack\App-Standards\`:

- `Appfw-Logging-Standards\Log_Schema.md` (canonical field list)
- `Appfw-Logging-Standards\Structured_Logging_Application_Standard.md` (§3.1 Inputs/Outputs, §3.2 Error Contract, §3.3 Logging Contract, §3.4 Audit Contract, §3.5 Security Contract, §4 Architectural Design, §5 Test & Validation)
- `Appfw-Logging-Standards\Recipes\Logging_AuthN_And_AuthZ_Events.md`
- `Appfw-Logging-Standards\Recipes\Sensitive_Data_Masking_For_Logs.md`
- `Appfw-Logging-Standards\Recipes\Enriching_Logs_With_MDC.md`
- `Appfw-Logging-Standards\Recipes\Centralising_Audit_Logging_With_A_Typed_Module.md`
- `Appfw-Logging-Standards\Recipes\Custom_Structured_Log_Encoder.md`
- `Appfw-Logging-Standards\Recipes\Structured_Logging_Trace_Correlation_And_Context_Propagation.md`
- `Appfw-Logging-Standards\Recipes\Logging_Exceptions_With_Enhanced_Details.md`
- `Appfw-Logging-Standards\index.md`, `Structured_Logging_Application_Standard_Questions.md` (Q6–Q8, spot-read)
- `Appfw-User-Standards\User_Standalone\Standalone_User_Access_Control_Application_Standard.md` §3.3 Audit Contract, §3.4 Logging Contract
- PRD: `ai-coding-secured-hello-world-app-assessment\prd\assessment-prd.md` (Stories 8–11; "Security posture → Audit logging")
- External primary source, for the actor/target question only: [ECS 8.17 User fields — Field Reuse](https://www.elastic.co/guide/en/ecs/8.17/ecs-user.html) and [ECS User fields usage and examples](https://www.elastic.co/guide/en/ecs/8.14/ecs-user-usage.html). Content was rephrased for compliance with licensing restrictions.

---

### 1. Mandatory field set — exact names, required vs optional

There are three distinct tiers. Conflating them is the main trap: `Structured_Logging_Application_Standard.md` §3.1 lists only framework-populated fields as "Required fields (always present in every log event)" and files `event.*` and `user.id` under "Contextual fields". But the recipes' Verification sections and the User Access Standard §3.4 make the event-classification fields non-optional **for audit events specifically**. Treat tier B as mandatory for us.

#### Tier A — required on every event, auto-populated, MUST NOT be set via `addKeyValue`

Source: `Log_Schema.md` "ECS Auto-Populated Fields — Do Not Set Manually" and §3.1 of the app standard.

| Field | Populated from | Notes |
|---|---|---|
| `@timestamp` | log event instant | ISO-8601/RFC-3339 with offset; standardise UTC+8 (`TZ=Asia/Singapore` or `-Duser.timezone=Asia/Singapore`) |
| `message` | the `.log("…")` / `.setMessage(…)` string | must be static human-readable text; no dynamic values |
| `log.level` | event level | `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR` only — Logback has no `FATAL` |
| `log.logger` | logger name | for us this is the literal `"audit"` logger (see item 6) |
| `ecs.version` | schema constant | |
| `process.pid` | JVM pid | present if `spring.application.pid` set |
| `process.thread.name` | current thread | §3.1 calls this `thread.name`/`logger.name`; `Log_Schema.md` names them `process.thread.name`/`log.logger`. The schema names are authoritative. *Consolidated into the register (ticket 33): R-STD-002. Amend the table by ID, not this list.* |
| `service.name` | `logging.structured.ecs.service.name` or `spring.application.name` | configure in `application.yaml` only |
| `service.version` | `logging.structured.ecs.service.version` | |
| `service.environment` | `logging.structured.ecs.service.environment` | enum `dev`, `test`, `staging`, `prod` |
| `trace.id` | Micrometer Tracing → MDC | see item 3 |
| `span.id` | Micrometer Tracing → MDC | |

Setting any of these with a dotted `addKeyValue` key throws a JSON writing error (`Custom_Structured_Log_Encoder.md` §1, §3.1).

#### Tier B — required on every audit event, set by the caller

Sources: `Logging_AuthN_And_AuthZ_Events.md` §4–§8 and its §10 Verification; `Centralising_Audit_Logging_With_A_Typed_Module.md` §4; User Access Standard §3.4.

| Field | Type | Value for us | Required? |
|---|---|---|---|
| `event.kind` | string | always `event` (we log no `state` snapshots) | required |
| `event.category` | array[string] | always `["process"]` for auth/authz/admin events | required |
| `event.type` | array[string] | per event, from the closed enum | required |
| `event.action` | string | per event, from the closed enum — see item 2 | required |
| `event.outcome` | string | `success` \| `failure` (`partial-success` unused by us) | required |
| `event.severity` | string | `low` \| `medium` \| `high` \| `critical` | required in every recipe example; §3.1 classes it contextual. Treat as required. |
| `user.id` | string (UUID) | acting principal's UUID | required **when resolved**; MUST be omitted on pre-auth failures |
| `source.ip` | string | client IP | required — "Include `source.ip` on authentication and logout events" (`Logging_AuthN_And_AuthZ_Events.md` §11). See conflict C1. |

#### Tier C — required on failure events only (`WARN`/`ERROR`)

Sources: app standard §3.2 Error Contract; `Logging_Exceptions_With_Enhanced_Details.md` §3–§4.

| Emitted key | Final JSON field | Values |
|---|---|---|
| `error_code` | `error.code` | integer 100–599, aligned to HTTP status |
| `error_category` | `error.category` | `server`, `network`, `cert/auth`, `database`, `application`, `data`, `others` — auth/authz failures are `cert/auth` |
| `error_follow_up_action` | `error.follow_up_action` | boolean; `true` = needs human action |
| `error.type` / `error.message` / `error.stack_trace` | same | auto-populated from `.setCause(e)`; never set manually |

Underscore keys are mandatory, not stylistic: the ECS formatter pre-seals the `error` object, so `addKeyValue("error.code", …)` throws. The custom encoder remaps them (item 4).

#### Tier D — optional / contextual, permitted

~~`correlation.id` (business process spanning multiple traces);~~ *(dropped by [ticket 27](27-inbound-trace-context.md) §6: the server-generated `trace.id` is the correlation ID)* `host.name`, `host.ip` (required for startup events); `event.start`, `event.end`, `event.duration_ms` (on `*-end` events); `http.request.method`, `url.path`, `http.response.status_code` (the typed-module recipe puts method + path on `accessDenied` and `profileRead`); `session.hash` (item 3); `service.id`, `service.system`, `service.subsystem`.

#### Fields our design needs that the schema does not define

Flag these in the deferral register — they are used by the recipes but absent from `Log_Schema.md`:

- `auth.method` — used in `Logging_AuthN_And_AuthZ_Events.md` §4 and mandated in prose by app standard §2.2 and §3.4 ("the authentication method"). No schema row. Values from the recipe's resolver: `password`, `jwt`, `oauth2`, `remember-me`, `unknown`. We only ever emit `password`.
- `session.max_inactive_interval` — used in the §6.1 session-created example. No schema row.
- `export.id` — used in the §8.1 example. No schema row. *Consolidated into the register (ticket 33): R-STD-003. Amend the table by ID, not this list.*
- Any target-of-admin-action field — see item 7.

#### The canonical line, field by field

Authentication success (`POST /api/login`, 200):

```json
{
  "@timestamp": "2026-01-22T14:07:51.080+08:00",
  "log.level": "INFO",
  "message": "Authentication succeeded.",
  "ecs.version": "8.11",
  "log": { "logger": "audit" },
  "process": { "pid": 88932, "thread": { "name": "http-nio-8080-exec-3" } },
  "service": { "name": "secured-hello-world", "version": "0.0.1-SNAPSHOT", "environment": "dev" },
  "trace.id": "4f5a5b5e3e4a4c5d8a7b6f78901c2d3e",
  "span.id": "a1b2c3d4e5f67890",
  "event.kind": "event",
  "event.category": ["process"],
  "event.type": ["user"],
  "event.action": "user-authentication",
  "event.outcome": "success",
  "event.severity": "low",
  "auth.method": "password",
  "user.id": "550e8400-e29b-41d4-a716-446655440000",
  "source.ip": "203.0.113.25"
}
```

Produced by:

```java
audit.atInfo()
    .setMessage("Authentication succeeded.")
    .addKeyValue("event.kind", "event")
    .addKeyValue("event.category", List.of("process"))
    .addKeyValue("event.type", List.of("user"))
    .addKeyValue("event.action", "user-authentication")
    .addKeyValue("event.outcome", "success")
    .addKeyValue("event.severity", "low")
    .addKeyValue("auth.method", "password")
    .addKeyValue("user.id", userId)      // UUID from AppUserDetails
    .addKeyValue("source.ip", clientIp)  // WebAuthenticationDetails.getRemoteAddress()
    .log();
```

Authentication failure (bad password **or** unknown account — indistinguishable by design):

```json
{
  "@timestamp": "2026-01-22T14:08:02.114+08:00",
  "log.level": "WARN",
  "message": "Authentication failed.",
  "log": { "logger": "audit" },
  "service": { "name": "secured-hello-world", "version": "0.0.1-SNAPSHOT", "environment": "dev" },
  "trace.id": "9c1d...", "span.id": "bb20...",
  "event.kind": "event",
  "event.category": ["process"],
  "event.type": ["user"],
  "event.action": "user-authentication",
  "event.outcome": "failure",
  "event.severity": "medium",
  "error": { "code": 401, "category": "cert/auth", "follow_up_action": false },
  "source.ip": "203.0.113.25",
  "session.hash": "<sha-256 hex of raw session id>"
}
```

No `user.id`. No `user.name`. No `user.hash`. No reason string distinguishing the two cases. `error.*` reaches that shape only via the custom encoder; the application emits `error_code`, `error_category`, `error_follow_up_action`.

Account lockout: identical field set, `log.level: ERROR`, `event.type: ["error"]`, `event.severity: "high"`, `error_code: 423`, `error_follow_up_action: true`, message `"Account locked."` (`Logging_AuthN_And_AuthZ_Events.md` §4).

---

### 2. `event.action` — closed vocabulary, and it already covers us

**It is a closed enum. We do not coin our own values.** `Log_Schema.md` → Event → `event.action` gives an explicit "Allowed Values / Enum" column, and every recipe's Prerequisites block repeats the instruction to take `event.action` values from `Log_Schema.md` for cross-service consistency.

The enum (full list, verbatim keys): `api-push`, `api-pull`, `file-generation`, `file-generation-retry`, `file-ack-process`, `file-retrieval`, `file-read`, `file-process`, `file-cleanup`, `application-startup`, `application-shutdown`, `user-authentication`, `user-logout`, `user-provisioning`, `user-administration`, `profile-read`, `password-reset`, `password-change-enforcement`, `session-start`, `session-end`, `totp-enrol`, `totp-remove`, `sms-otp-enrol`, `sms-otp-remove`, `hardware-token-enrol`, `hardware-token-remove`, `backup-code-enrol`, `backup-code-remove`, `data-export`, `access-control`, `REPORT_PRE_FILL`, `REPORT_FILL`, `REPORT_EXPORT`, `REPORT_BACKUP`, `ATTEMPTS_EXCEEDED`, `PIN_CREATED`, `TOTP_SETUP`, `ACCESS_DENIED`, `CRITICAL_TRANSACTION`, `OTP_DELIVERY`, `OTP_VERIFICATION`, `KMS_ENCRYPT`, `KMS_DECRYPT`, `NOTIFICATION_SEND`, `NOTIFICATION_SEND_RETRY`, `NOTIFICATION_QUARANTINE`.

The one sanctioned templated extension is MFA: `Logging_AuthN_And_AuthZ_Events.md` §7 builds `event.action` as `{factor}-enrol` / `{factor}-remove`, and each resulting value is itself already in the enum. Out of scope for us (PRD excludes MFA).

Mapping for our audit event catalogue — every PRD event lands on an existing enum value, so the catalogue can be closed without amending the schema:

| PRD event | `event.action` | `event.type` | level | `event.severity` | `user.id` |
|---|---|---|---|---|---|
| Registration (self-service account create) | `user-provisioning` | `["creation"]` | INFO | low | new user's UUID |
| Login success | `user-authentication` | `["user"]` | INFO | low | yes |
| Login failure (bad creds or unknown user) | `user-authentication` | `["user"]` | WARN | medium | **omit** |
| Login rejected — account locked | `user-authentication` | `["error"]` | ERROR | high | **omit** |
| Lockout triggered (Nth failure sets `locked_until`) | `user-authentication` | `["error"]` | ERROR | high | omit if unresolved |
| IP-level throttle breach (429) | `access-control` | `["denied"]` | WARN | medium | omit |
| Logout | `user-logout` | `["end"]` | INFO | low | yes |
| HTTP session created | `session-start` | `["start"]` | INFO | low | **no** (pre-auth) |
| Session expired / invalidated | `session-end` | `["end"]` | INFO | low | yes |
| Password reset requested (token issued) | `password-reset` | `["change"]` | INFO | low | omit — request is by email, enumeration-sensitive |
| Password reset completed (token redeemed) | `password-reset` | `["change"]` | INFO | low | yes |
| Self-service password change | `password-reset` | `["change"]` | INFO | low | yes |
| `GET /api/hello` (protected read) | — | — | — | — | do not log; §8.1 says do not log every authorisation check |
| Admin list users | `user-administration` | `["access"]` | INFO | low | actor |
| Admin enable/disable account | `user-administration` | `["change"]` | INFO | low | actor (+ target, item 7) |
| Admin role change | `user-administration` | `["change"]` | INFO | low | actor (+ target) |
| Admin delete account | `user-administration` | `["deletion"]` | INFO | low | actor (+ target) |
| Authorisation denied (403, incl. self-target guards) | `access-control` | `["denied"]` | WARN | medium | actor |
| App startup / shutdown | `application-startup` / `application-shutdown` | `["start"]` / `["end"]` | INFO | low | n/a |

Note there is **no** dedicated enum value for a role change, an account enable/disable, or an account deletion. All three collapse onto `user-administration`; the distinguishing detail has to live in `event.type` (`change` vs `deletion`) and in `message`. Two design consequences: (a) dashboards cannot separate "role granted" from "account disabled" by `event.action` alone, and (b) `event.type` carries real semantic load for us and must be set precisely. `event.type` is itself a closed enum: `access`, `admin`, `allowed`, `change`, `connection`, `creation`, `deletion`, `denied`, `end`, `error`, `group`, `indicator`, `info`, `installation`, `interface-end`, `interface-start`, `job-end`, `job-start`, `protocol`, `start`, `step-end`, `step-start`, `user`. *Consolidated into the register (ticket 33): R-STD-004. Amend the table by ID, not this list.*

`event.category` is also closed — `configuration`, `network`, `database`, `batch`, `interface`, `process`. There is **no** `iam` or `authentication` category (unlike upstream ECS), so every auth/authz/admin event uses `["process"]`, matching the recipes. Request-boundary logs use `["network"]` (`Enriching_Logs_With_MDC.md` §3.1). *Consolidated into the register (ticket 33): R-STD-005. Amend the table by ID, not this list.*

---

### 3. `session.hash` and `trace.id`

#### `session.hash`

Definition exists in exactly one place: `Log_Schema.md` → User table.

- Algorithm: a one-way **SHA-256** hash of the raw session identifier. The raw session ID must never be logged (the app standard §3.3 "What must NOT be logged" lists session IDs in raw form; the User Access Standard §3.4 "What NOT to Log" repeats it).
- Purpose: correlating **pre-authentication** entries where `user.id` is not yet resolved.
- No salt or HMAC is prescribed for `session.hash`. The salt/HMAC requirement in app standard §3.5 is scoped to *hashed PII* ("Hashed PII is permitted only if secured via a system-wide salt or HMAC"). A session ID is not PII, so a plain unsalted SHA-256 digest satisfies the letter of the standard. Recommendation anyway: use a system-wide HMAC key, because an unsalted digest of a session ID is trivially confirmable by anyone who holds the cookie. Raise as an ADR; do not treat as a standard requirement. *Consolidated into the ADR routing (ticket 34): ADR-054. Amend by ID, not this list.*
- No reference implementation exists in any recipe. Ours will be: `HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawSessionId.getBytes(UTF_8)))`, computed in the MDC filter and put into MDC under key `session.hash` so it lands on every line in the request.
- Encoding gap: the `Log_Schema.md` example value `5d41402abc4b2a76b9719d911017c592` is 32 hex characters, i.e. 128 bits — an MD5-length digest, not SHA-256's 64 hex characters. The prose ("SHA-256") is authoritative; the example is wrong. Emit the full 64-character lowercase hex digest and note the schema-example defect. *Consolidated into the register (ticket 33): R-STD-006. Amend the table by ID, not this list.*

**Where `session.hash` is and is not required.** This is a genuine fork in the sources:

- `Log_Schema.md` and User Access Standard §3.4 (both the Privacy section and the `WARN` level description) direct you to rely on `session.hash` **and** `trace.id` for correlation when the UUID is unresolved — i.e. emit it on pre-auth events.
- `Logging_AuthN_And_AuthZ_Events.md` §3 and §11, and `Centralising_Audit_Logging_With_A_Typed_Module.md` §7, say to rely on `trace.id` alone and make no mention of `session.hash` at all. The typed-module unit test even asserts `doesNotContainKey("user.hash")`.

Both are satisfiable simultaneously: emit `session.hash` (a session-scoped, non-user-derived value) **and** `trace.id`, and emit **no** user-derived identifier of any kind. The prohibition the recipes care about is on hashed *usernames* (`user.hash`), because a stable hash of a username confirms account existence. `session.hash` does not, since it is derived from the cookie the client already holds. Design decision: implement `session.hash` as the governing User Access Standard requires; never implement `user.hash`.

#### `trace.id` and `span.id`

- **Producer: Micrometer Tracing, not a hand-rolled MDC filter and not the OTel SDK directly.** App standard §4 marks this an Enforced Constraint: put Micrometer Tracing on the classpath so `traceId`/`spanId` are written to MDC automatically whenever a span is active. §3.1 lists `trace.id`/`span.id` as auto-populated — "do not set manually".
- **Backend:** Micrometer Tracing is a facade and needs an implementation. `Structured_Logging_Trace_Correlation_And_Context_Propagation.md` §3 offers exactly two sanctioned choices: `spring-boot-starter-opentelemetry` (OTLP export, W3C propagation by default) or `spring-boot-starter-zipkin` (Brave, B3 by default — set `management.tracing.propagation.type: w3c` if W3C is needed). Both write `traceId`/`spanId` to MDC identically. Use the OTel starter: W3C `traceparent` is the recommended format per §3 and per app standard §3.1 ("Propagate and log trace context (W3C Trace Context)"). Using the raw OTel SDK instead of Micrometer is permitted only under the narrow conditions in the §4 note (platform mandates OTel across polyglot services, or OTel-only features needed) — neither applies to us.
- **Sampling does not affect log correlation.** Sampling controls only which traces are *exported*. 100% of log lines carry a trace ID regardless. Default probability is `0.1`; set `1.0` for local/test.
- **What the MDC filter is still for.** Not `trace.id`. ~~Two other things: (a) an application-defined `correlation.id` from an inbound `X-Correlation-ID` header, via `OncePerRequestFilter` (`Enriching_Logs_With_MDC.md` §3.1) — optional for us, since we have no upstream caller supplying one and §3 says to skip 3.1/3.2 when `traceId` suffices; and~~ *(part (a) struck by [ticket 27](27-inbound-trace-context.md) §6: a caller-supplied correlation ID reintroduces what the trace restart removes)* One thing: (b) `user.id`, via a separate `MdcUserFilter` registered **inside** the Spring Security chain with `http.addFilterAfter(new MdcUserFilter(), AnonymousAuthenticationFilter.class)` (§3.4). Placement is load-bearing: register it as a `@Component` and it runs before authentication and sees nothing. It must check `!(auth instanceof AnonymousAuthenticationToken)` because `AnonymousAuthenticationToken.isAuthenticated()` returns `true`. Clear `user.id` in a `finally` block.
- ~~If correlation IDs must appear on Spring Security's own log output, the request filter needs `@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)`.~~ *(Struck by [ticket 27](27-inbound-trace-context.md) §6: no correlation-ID filter remains to order.)*
- Do **not** use Logback's built-in `MDCInsertingServletFilter` — `Enriching_Logs_With_MDC.md` §3.1 explicitly rejects it, because it sets client IP and query string, both on the prohibited list.
- Async propagation: `ContextPropagatingTaskDecorator` on the executor. Relevant to us only if the stubbed `EmailService` is `@Async`. Spring Security context propagates too (Spring Security 6+ registers its accessor).
- Cleanup discipline (`Enriching_Logs_With_MDC.md` §3.3): `putCloseable` for a single key with no catch-block logging; `put` + `remove` in `finally` for multiple keys or when a catch block logs; **never** `MDC.clear()` outside a thread-owning entry point — it wipes `traceId`/`spanId`.

---

### 4. Encoder and appender configuration, with dependencies

**A custom encoder is effectively mandatory for this application.** The standard never states "you must write a custom encoder" in the abstract, but the chain of requirements forces it:

1. App standard §3.2 requires `error_code`, `error_category`, `error_follow_up_action` on `ERROR` events, and the AuthN recipe emits them on `WARN` login failures and `ERROR` lockouts too — which is most of our audit surface.
2. `Log_Schema.md` requires those values to land as `error.code`, `error.category`, `error.follow_up_action`.
3. Spring Boot's `ElasticCommonSchemaStructuredLogFormatter` seals the `error` object before custom fields are processed, so dotted keys throw a JSON writing error, and underscore keys stay flat at the root.
4. `Custom_Structured_Log_Encoder.md` is the only sanctioned bridge, and `Logging_Exceptions_With_Enhanced_Details.md` §2 lists it as a **prerequisite**: "[Custom Structured Log Encoder] configured for underscore-to-dot field name mapping (required for `error_code`, `error_category`, and similar fields)".

So: Spring Boot's built-in ECS format is the mandated *base*, and a thin subclass of `StructuredLogEncoder` is required on top of it.

Carry the risk warning forward into the design: `Custom_Structured_Log_Encoder.md` §1 flags that `StructuredLogEncoder` lives in `org.springframework.boot.logging.logback` and is **not a documented public extension point** — its API may change without a migration guide, and the encoder must be retested after every Spring Boot upgrade. *Consolidated into the register (ticket 33): R-AUD-001. Amend the table by ID, not this list.*

#### Dependencies

No third-party log-encoder dependency is mandated. Nothing in the standard requires `co.elastic.logging:logback-ecs-encoder`; app standard §4 mentions it only as a conditional ("When using a third-party encoder such as `logback-ecs-encoder`, declare the encoder in a custom Logback configuration file"). Use Spring Boot native ECS instead.

| Purpose | GroupId:ArtifactId | Version | Mandatory? |
|---|---|---|---|
| Baseline platform | `org.springframework.boot:spring-boot-starter-web` | Spring Boot **4.0+** per every recipe's Prerequisites. (App standard §4 says native structured logging needs **3.4+**; User Access Standard §3.4 says **SLF4J 2.0+ / Spring Boot 3.4+**. 4.0+ is the binding floor, being the stricter and more recent.) | yes |
| JSON manipulation in the custom encoder | `com.fasterxml.jackson.core:jackson-databind` | **no explicit version** — managed by the Spring Boot BOM. Transitive via `spring-boot-starter-web`; declare explicitly only for non-web apps. | yes (transitive) |
| Tracing facade + `trace.id` | `org.springframework.boot:spring-boot-starter-actuator` | BOM-managed | yes |
| Tracing backend (pick one) | `org.springframework.boot:spring-boot-starter-opentelemetry` **or** `org.springframework.boot:spring-boot-starter-zipkin` | BOM-managed | yes — one of the two |
| `@Observed` annotation support | `org.aspectj:aspectjweaver` | BOM-managed | no — only if we use `@Observed` |
| Field-path masking | `net.logstash.logback:logstash-logback-encoder` | **no version given** in the recipe | no — see item 5 |

Every version is BOM-managed. No recipe pins a literal version string for any logging dependency. If the design needs pinned versions (per the org's exact-version preference), that is a decision for us to record, not a value to extract. *Consolidated into the register (ticket 33): R-BLD-003. Amend the table by ID, not this list.*

#### Encoder registration

`logging.structured.format.*` in `application.yaml` alone is **not sufficient** once a custom encoder is involved — a `logback-spring.xml` is required (`Custom_Structured_Log_Encoder.md` §2, §4.4), and `<format>ecs</format>` must be present inside the `<encoder>` element or the parent class will not initialise the ECS formatter.

```xml
<!-- src/main/resources/logback-spring.xml -->
<encoder class="com.example.logging.CustomStructuredLogEncoder">
  <format>ecs</format>
</encoder>
```

App standard §4 adds an Enforced Constraint: reference the `CONSOLE_LOG_STRUCTURED_FORMAT` / `FILE_LOG_STRUCTURED_FORMAT` environment variables to select the encoder rather than hardcoding the format, so output can be switched at deployment time.

`ErrorCategoryResolver` and `CustomStructuredLogEncoder` must be plain Java classes with **no** Spring annotations — logging initialises before the application context (`§4.3`).

#### Appender / destination

Three Enforced Constraints from app standard §4 and §3.5, all of which shape our design:

1. **A dedicated audit appender is mandated.** "Separate critical audit logs from standard application logs by routing them to a dedicated appender and log destination (e.g., a separate file or log stream)" so retention, access control, and alerting apply independently. This pairs with the typed audit module's named `"audit"` logger (item 6) — the logger name is what the appender binds to.
2. **Local durable buffering, not direct network shipping.** Write to a rolling local file for a forwarding agent to pick up; do not ship from the application over the network, to avoid losing audit trails during collector outages.
3. **NDJSON to stdout, one event per line, UTF-8.**

Rolling policy: `SizeAndTimeBasedRollingPolicy` is the recommended class for higher volume; Spring Boot defaults are 10 MB and 7 archives, tunable via `logging.logback.rollingpolicy.*` (`file-name-pattern`, `max-file-size`, `max-history`, `total-size-cap`, `clean-history-on-start`).

Retention: the app is **not** responsible for the 90-day TTL. App standard §3.5 note: retention is enforced by the central platform's index lifecycle policy. The User Access Standard §3.3 sets the 90-day minimum and says durability may be met by local DB storage *or* by routing to an external logging system — which, combined with the PRD's "no dedicated table required", means structured logs plus a documented platform retention policy is compliant. Record the platform-side configuration as an integrator obligation in the deferral register. *Consolidated into the register (ticket 33): R-AUD-011. Amend the table by ID, not this list.*

Also required: **alert when audit logging itself fails** (§3.4). If audit events cannot be written, raise an alert rather than silently continuing.

Never mix plain text and JSON in the same stream (Enforced Constraint, §4 Anti-patterns). Do not change log levels at runtime via `/actuator/loggers` in production — the §6 note warns it can suppress required audit events.

---

### 5. Masking requirements and where they are enforced

Enforcement is **primarily call-site discipline**, with encoder-level masking as an explicitly optional second layer.

- `Sensitive_Data_Masking_For_Logs.md` §5 makes prevention the primary control: exclude at source, because once written a value is in the central platform. The single most common defect it names is passing a domain object to `addKeyValue` — every field gets serialised. Log named safe fields only. §4 Anti-patterns in the app standard states the same as an Enforced Constraint: never pass domain objects via `toString()`.
- §6 is conditional, and this matters for us: "This section is only relevant if the application uses `logstash-logback-encoder` as its Logback encoder. If the application does not use this encoder, skip this section and rely on prevention at the source." So `MaskingJsonGeneratorDecorator` is **not mandatory**.
- **`MaskingJsonGeneratorDecorator` is incompatible with our chosen encoder.** The recipe configures it inside `net.logstash.logback.encoder.LogstashEncoder`. We are required to run a subclass of Spring Boot's `StructuredLogEncoder` with `<format>ecs</format>` (item 4). One appender cannot use both encoders. Conclusion: we do prevention at source, and we do not implement Logback masking. Record as an ADR.
- App standard §3.5 nonetheless states as an Enforced Constraint that masking be applied "at the application logging boundary — for example, using a custom Logback converter, a `PatternLayout` masking rule, or a structured log encoder extension". Our custom encoder *is* a structured log encoder extension, so the compliant resolution is to add a redaction pass inside `CustomStructuredLogEncoder.postProcess(...)` rather than adopt a second encoder. Masking by **field path**, not by value, is the prescribed approach. *Consolidated into the ADR routing (ticket 34): REJ-001. Amend by ID, not this list.*
- Masking applies to JSON fields only, never to `message`. Sensitive values must be kept out of `message` by construction — which our "static message string" rule already enforces.

The prohibition list (app standard §3.3 "What must NOT be logged", plus User Access Standard §3.4), filtered to what our app actually handles:

- Passwords, plaintext or otherwise — PRD acceptance criterion already requires this
- Raw session identifiers (hash them — item 3)
- Password reset token plaintext **and password reset token hashes** — the User Access Standard bans both. Log the reset event, never any form of the token.
- CSRF tokens
- Personal data and sensitive IDs; raw usernames and email addresses
- Authentication headers; request and response bodies
- URL query parameters
- Database connection strings, database names, schema IDs
- **Client IP addresses** — see conflict C1
- Dependency versions of libraries or frameworks
- Application source code

Log-injection control (CWE-117): strip CR/LF from untrusted input before logging. The recipe's pattern is `value.replaceAll("[\\r\\n|]", "")`. Relevant on the registration and login paths, where a username reaches the log pipeline indirectly. App standard §5 adds a test rule: sanitise injection payloads before asserting, and assert the sanitised form appears.

Exception handling nuance worth carrying into the design: infrastructure details (hostnames, ports, database names) appearing *passively* in stack traces are acceptable in access-controlled production logs, because of their diagnostic value. The real threat is credentials in stack traces, prevented by externalising secrets. So `.setCause(e)` is always required, never suppressed.

`DEBUG` and `TRACE` must be disabled in production.

---

### 6. Typed audit module — recommended, but the separate-appender rule makes it the only sane option

Strictly: **recommended, not mandatory.** `Centralising_Audit_Logging_With_A_Typed_Module.md` is a recipe, not a standard, and its §7 is headed "Key decisions" rather than constraints. No `[Enforced Constraint]` in `Structured_Logging_Application_Standard.md` names a typed audit module.

But three binding requirements converge on it, so the design should have a dedicated audit component rather than scattered `log.atInfo()` calls:

1. **The dedicated audit appender is an Enforced Constraint** (§4). An appender binds to a logger name. The typed module's `LoggerFactory.getLogger("audit")` is precisely what makes routing possible. Scattered per-class loggers (`@Slf4j` on `AdminService`, etc.) would send audit events to the application appender, failing both the §4 routing constraint and the §5 test criterion "Audit events are present in the dedicated audit log destination, not only in the standard application log". **This is the decisive argument.** Note the recipe in `Logging_AuthN_And_AuthZ_Events.md` uses `@Slf4j` class loggers and therefore does *not* by itself satisfy the separate-destination requirement — the typed module does.
2. **Centralisation is itself mandated for auth events.** §4 of the AuthN recipe: register a single event listener rather than scattering log statements; §11 repeats it.
3. The schema is wide and easy to get wrong (event.kind/category/type/action/outcome/severity + identity resolution + `trace.id` + the underscore `error_*` convention). One owner per bounded context prevents drift.

Concrete shape for our app, following the recipe:

- `AppUserDetails implements UserDetails` carrying a `UUID userId`, returned from `UserDetailsService.loadUserByUsername`. This is the mechanism that keeps usernames out of logs: `Authentication.getName()` returns a username (PII), so the UUID must ride inside the principal.
- The UUID column: `@Column(nullable = false, unique = true, columnDefinition = "UUID", insertable = false, updatable = false)` with a DB default of `gen_random_uuid()`. The `insertable = false, updatable = false` pair is what lets the database default fire. **Cross-reference ticket 12 (data model reconciliation): this implies a `uuid` column distinct from the primary key.**
- `AuditLogger` as a `@Component` with one method per event type (`loginSuccess`, `loginFailure`, `accessDenied`, `logout`, plus ours: `registrationSuccess`, `accountLocked`, `throttleBreached`, `passwordResetRequested`, `passwordResetCompleted`, `sessionStarted`, `sessionEnded`, `adminUserStatusChanged`, `adminUserRoleChanged`, `adminUserDeleted`, `adminUserListed`).
- `AuditLogger` owns **no** HTTP concerns — status codes and response bodies stay in the handler. Keeps it testable with no servlet container.
- Testing: attach a Logback `ListAppender` to the `"audit"` logger, assert on `ILoggingEvent.getKeyValuePairs()`. No integration test needed for schema compliance. Include negative assertions — `doesNotContainKey("user.id")` on failure events, `doesNotContainKey("user.name")`, `doesNotContainKey("user.hash")` everywhere. **Feeds ticket 16 (test plan).** *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-007, T-AUD-009, T-AUD-008. Amend the table by ID, not this list.*

Spring wiring that must exist for the events to fire at all:

| Bean / registration | Without it |
|---|---|
| `SpringAuthorizationEventPublisher` | `AuthorizationDeniedEvent` is never published; 403s cannot be centrally logged |
| `HttpSessionEventPublisher` | no session created/destroyed events |
| `LogoutHandler` added via `http.logout(l -> l.addLogoutHandler(...))` | no logout event |
| `MdcUserFilter` via `addFilterAfter(..., AnonymousAuthenticationFilter.class)` | `user.id` missing from non-audit lines |

Listen on `AbstractAuthenticationFailureEvent` (the parent) and branch with `instanceof AuthenticationFailureLockedEvent` inside. Registering a separate listener for the lockout subtype produces **duplicate** entries. Expect two entries on explicit logout (one `user-logout`, one `session-end`) — the recipe states this is intended.

---

### 7. Conflicts, and the actor/target reconciliation

#### C1 — `source.ip` versus the ban on logging client IPs (highest severity; blocks the audit catalogue)

Direct contradiction between two documents in the same standard.

- `Structured_Logging_Application_Standard.md` §3.3 "What must NOT be logged" lists **"Client IP addresses"**. §2.1 repeats it for request logging, §5 makes it a release test ("Request logs exclude client IP addresses…"), and `Enriching_Logs_With_MDC.md` §3.1 carries a Warning against it and rejects `MDCInsertingServletFilter` specifically because it sets client IP.
- `Logging_AuthN_And_AuthZ_Events.md` §4 states the opposite for auth events: always include `source.ip` on every event, as it is required for audit and incident investigation. §10 Verification and §11 Key Takeaways both require it. `Centralising_Audit_Logging_With_A_Typed_Module.md` §7 makes it a key decision: `source.ip` on every security event. `Log_Schema.md` defines `source.ip` as a first-class field.

Resolution: the prohibition is scoped to **general request logging**; the AuthN recipe is the specific rule for **security events**, and specific beats general. Include `source.ip` on authentication, lockout, logout, and authorisation-denied events (`WebAuthenticationDetails.getRemoteAddress()` for Spring Security events, `request.getRemoteAddr()` elsewhere); exclude it from request-boundary and business logs. Without `source.ip` the PRD's Story 3 IP-throttling event is not investigable. Record as an ADR with both citations, because a compliance reviewer reading only §3.3 will flag it. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*

#### C2 — `user.id` on lockout: required by the PRD's intent, forbidden by the recipes

Lockout is triggered by the Nth failure against a **known** account, so the UUID *is* resolvable, and the User Access Standard §3.3 requires lockout transitions to be auditable. But `Logging_AuthN_And_AuthZ_Events.md` §4 and §10 require the lockout log to carry "the same fields as authentication failure" — i.e. **no** `user.id` — because a failure log that carries an identity confirms account existence to anyone who can read logs.

The User Access Standard §3.4 `WARN` description resolves it, and it is the governing document: lockout transitions are logged "with triggering user.id UUID if resolved, or omitting user identity and relying on `session.hash` and `trace.id` for correlation if not". So: include `user.id` on the lockout-transition event when resolved; **never** include it on the plain authentication-failure event. Note this makes lockout and failure events structurally distinguishable, which is a partial enumeration signal inside the log store — acceptable because log access is itself least-privilege controlled (§3.5), but worth stating explicitly in the threat model (ticket 15).

#### C3 — Level mismatch on lockout: `ERROR` vs `WARN`

`Logging_AuthN_And_AuthZ_Events.md` §4 and §11 require lockout at **`ERROR`** ("Lockout indicates an active attack pattern"). The User Access Standard §3.4 Log Levels puts "account lockout transitions" under **`WARN`**. Use `ERROR`, per the logging standard, which owns the level contract and gives an explicit rationale; also set `event.severity: high` and `error_follow_up_action: true`. Flag the discrepancy. *Consolidated into the register (ticket 33): R-STD-009. Amend the table by ID, not this list.*

#### C4 — "Username or principal ID" in the audit contract vs the cleartext ban

User Access Standard §3.3 says all audit events must include "Timestamp, **Username or principal ID**, Outcome, Request path, HTTP method, and correlation ID (when available)". Read alone, "Username" authorises exactly what §3.4 of the same document forbids. The disjunction resolves it: satisfy it with the **principal ID**, i.e. `user.id` UUID. Never the username. Note §3.3 also requires request path and HTTP method on audit events — the typed-module recipe already models this (`http.request.method`, `url.path` on `accessDenied` / `profileRead`), so add both to all admin audit events. This is a real addition to our field set that is easy to miss by reading the logging standard alone.

#### C5 — The `user.name` carve-out

`Log_Schema.md` defines `user.name` and says it "Should only be logged in authentication/authorization contexts where explicitly permitted by policy." That reads like a licence to log usernames on exactly our events. It is not, for us: the User Access Standard §3.4 is our governing policy and states plainly that raw usernames or emails must not be logged in cleartext, and the recipes say "never" three times over. **`user.name` is prohibited in this application.** The carve-out is the mechanism by which some other application's policy could permit it; ours does not.

#### C6 — Masking encoder incompatible with the mandated ECS encoder

See item 5. `MaskingJsonGeneratorDecorator` requires `LogstashEncoder`; we require a `StructuredLogEncoder` subclass. Mutually exclusive in one appender. Resolution: prevention at source plus redaction inside our custom encoder.

#### C7 — Schema defects and gaps to record

- `session.hash` example is MD5-length while the prose says SHA-256 (item 3). *Consolidated into the register (ticket 33): R-STD-006. Amend the table by ID, not this list.*
- §3.1 names `thread.name` / `logger.name`; `Log_Schema.md` names `process.thread.name` / `log.logger`. *Consolidated into the register (ticket 33): R-STD-002. Amend the table by ID, not this list.*
- `auth.method`, `session.max_inactive_interval`, `export.id` are used by recipes but undefined in `Log_Schema.md`. *Consolidated into the register (ticket 33): R-STD-003. Amend the table by ID, not this list.*
- No `event.action` value distinguishes role change from enable/disable from delete (item 2). *Consolidated into the register (ticket 33): R-STD-004. Amend the table by ID, not this list.*
- No `event.category` value for identity/authentication; `process` is the catch-all. *Consolidated into the register (ticket 33): R-STD-005. Amend the table by ID, not this list.*
- `Log_Schema.md`'s own title scopes it to "Batch and Interface Applications", yet the User Access Standard §3.4 and every auth recipe bind interactive web applications to it. Treat the binding as authoritative and the title as stale. *Consolidated into the register (ticket 33): R-STD-007. Amend the table by ID, not this list.*

#### C8 — The PRD's actor + target of admin actions: **the target must be a UUID, under `user.target.id`**

The PRD ("Security posture → Audit logging") requires structured log lines for "role change/enable/disable/delete (**actor + target**)". Stories 9–11 describe an admin acting on another user's account. Naively the target is identified by the username shown in the admin UI. That is prohibited.

Finding, in four steps:

1. **The target must be a UUID.** The no-cleartext rule is written in terms of user identity, not in terms of *whose* identity. `Log_Schema.md` → `user.id`: always use the UUID for logging user context; never log raw usernames or emails in cleartext. App standard §3.5: "When identifying a user, prefer `user.id` UUID. Never log raw usernames or emails." §5 release test: "User identifiers appear only as `user.id` UUID; raw usernames, emails, and credentials are absent" — *identifiers*, plural, unqualified as to role in the event. User Access Standard §3.4 is equally unqualified. Nothing anywhere carves out the object of an admin action. **The target of an admin action must be logged as a UUID, and its username and email must not appear.**
2. **`user.id` cannot carry both.** It is a single string field and the recipes bind it to the acting principal (resolved from `Authentication.getPrincipal()`). Putting the target's UUID in `user.id` would silently corrupt every "what did this user do" query, and misattribute the action to the victim.
3. **`Log_Schema.md` defines no target field.** Its User table has exactly three rows: `user.id`, `user.name`, `session.hash`. No `user.target.*`, no `user.changes.*`, no actor/target distinction anywhere in the document, and no recipe logs a second party — the closest, `AdminService.exportUserData` in `Logging_AuthN_And_AuthZ_Events.md` §8.1, logs only the actor plus an `export.id`. **This is a genuine gap in the organisational schema, not something I failed to find.**
4. **The correct field name is `user.target.id`,** taken from upstream ECS, which `Log_Schema.md` declares itself to follow. Per [ECS 8.17 User fields → Field Reuse](https://www.elastic.co/guide/en/ecs/8.17/ecs-user.html), the `user` field set is designed to be nested at a defined set of locations, `user.target` among them (alongside `user.changes` and `user.effective`). [ECS User fields usage and examples](https://www.elastic.co/guide/en/ecs/8.14/ecs-user-usage.html) describes user-management events as using two event-classification fields together — one for the kind of activity, one for whether a user or a group is the target. So upstream ECS models exactly our case, and `user.target.id` is its name for it. Content was rephrased for compliance with licensing restrictions.

**Recommendation:** emit the acting admin as `user.id` and the affected account as `user.target.id`, both UUIDs. Where a role change is recorded, add `user.target.roles` (ECS types it as an array) with the **new** role only — never the username. Do not invent a local name such as `target.user.id` or `admin.target`; `user.target.id` is the ECS-sanctioned name and keeps us consistent with the schema's own stated basis.

**Two obligations this creates:**

- **Schema amendment.** `user.target.id` is not in `Log_Schema.md`, so using it is a documented extension. Raise it for addition to the org schema (the same route by which `error.category` exists as a documented custom extension — `Custom_Structured_Log_Encoder.md` §4.1 states `error.category` is not part of official ECS and is a custom field extension defined in `Log_Schema.md`). Record in the deferral register (ticket 17) and the audit catalogue (ticket 13). *Consolidated into the register (ticket 33): R-STD-008. Amend the table by ID, not this list.*
- **Encoder check.** `user` is **not** one of the objects the ECS formatter pre-seals — the sealed set is `error`, `service`, `log`, `process`, `ecs` (`Custom_Structured_Log_Encoder.md` §3.1 and `Log_Schema.md`). So `addKeyValue("user.target.id", uuid)` writes as a flat root-level key without a JSON writing error, the same way `user.id` already does. No underscore workaround needed, and no encoder change. **Verify this empirically on first implementation** — it is an inference from the documented sealed-object list, not something any recipe demonstrates. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-010. Amend the table by ID, not this list.*

The admin role-change line, complete:

```json
{
  "@timestamp": "2026-01-22T14:31:09.402+08:00",
  "log.level": "INFO",
  "message": "User role changed.",
  "log": { "logger": "audit" },
  "service": { "name": "secured-hello-world", "version": "0.0.1-SNAPSHOT", "environment": "dev" },
  "trace.id": "7ab3...", "span.id": "01f9...",
  "event.kind": "event",
  "event.category": ["process"],
  "event.type": ["change"],
  "event.action": "user-administration",
  "event.outcome": "success",
  "event.severity": "low",
  "user.id": "550e8400-e29b-41d4-a716-446655440000",
  "user.target.id": "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
  "user.target.roles": ["ADMIN"],
  "http.request.method": "PATCH",
  "url.path": "/api/admin/users/6f9619ff-8b86-d011-b42d-00cf4fc964ff/role",
  "source.ip": "203.0.113.25"
}
```

Two notes on that line. `url.path` contains the target UUID — acceptable, since UUIDs are non-PII and `url.path` is a defined schema field; had the route been keyed by username it would have leaked PII, which is an argument for UUID-keyed admin routes (**feeds ticket 11, admin module design**). And `url.query` must never be logged.

#### C9 — Enumeration-resistant password reset constrains the reset-request log

PRD Story 6 requires a generic success response regardless of whether the email is registered. The same reasoning that strips `user.id` from login failures applies: a `password-reset` request event carrying `user.id` reveals, to a log reader, which submitted addresses were real. Omit `user.id` on **reset-requested**; include it on **reset-completed**, where the token has already proved the account exists. The standards do not state this explicitly — it is derived from the recipe's stated principle (§3: do not log an identifier that confirms account existence) applied to the reset flow. Record as an ADR and cross-reference ticket 10 (credential flows) and ticket 15 (threat model). *Consolidated into the ADR routing (ticket 34): REJ-002. Amend by ID, not this list.*

---

### Summary of decisions this answer settles

1. `event.action` is a **closed enum**; every PRD event maps onto an existing value. Role change / enable / disable / delete all collapse onto `user-administration`, differentiated by `event.type`.
2. A **dedicated `AuditLogger` component** bound to a logger named `"audit"`, with a dedicated appender. Driven by an Enforced Constraint, not by taste.
3. **Spring Boot native ECS + a `StructuredLogEncoder` subclass.** No third-party encoder dependency. All versions BOM-managed; none pinned by the standard.
4. **Micrometer Tracing (OTel starter) produces `trace.id`/`span.id`** automatically via MDC. An `MdcUserFilter` inside the Security chain supplies `user.id`. No hand-rolled trace filter.
5. **`session.hash` = SHA-256 hex of the raw session ID** (recommend HMAC; schema example is wrong length), emitted on pre-auth events alongside `trace.id`. `user.hash` is never emitted.
6. **Masking is call-site prevention**, plus redaction inside our own encoder. The Logstash masking decorator is incompatible with our encoder and is not adopted.
7. **The target of an admin action is a UUID in `user.target.id`** (ECS-sanctioned reuse location), never a username or email. `user.id` remains the acting admin. Requires a documented org-schema extension.

Open items for the deferral register (ticket 17): C1 `source.ip` ADR; C3 lockout level discrepancy; C6 masking-encoder ADR; C7 schema defects; C8 `user.target.id` schema amendment; C9 reset-request identity omission ADR; `session.hash` HMAC-vs-plain-digest decision; platform-side 90-day retention configuration; dependency version pinning; retest-the-custom-encoder-on-every-Spring-Boot-upgrade obligation. *Consolidated into the register (ticket 33): R-AUD-001, R-AUD-011, R-BLD-003, R-STD-008, R-STD-009. Amend the table by ID, not this list.*

---

## Amendment from ticket 11

**C8's schema-amendment request is scoped to one field and owes three.**

C8 raises `user.target.id` as a documented extension because it is absent from `Log_Schema.md`. By the same
test, **`user.target.roles`** — used in this ticket's own worked example at the role-change event — is equally
absent and equally an extension. It is currently used without being registered. *Consolidated into the register (ticket 33): R-STD-008. Amend the table by ID, not this list.*

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
adds a third: **`user.target.unlockReason`**, a closed enum
(`USER_REQUEST | FALSE_POSITIVE | PASSWORD_RESET_COMPLETED | OTHER`) on the admin unlock event. It is an enum
and not free text deliberately — an admin typing "unlocking for alice@example.com, she called in" writes
cleartext PII into a stream this ticket bans it from, and a newline in that string breaks the "NDJSON to stdout,
one event per line" Enforced Constraint. An enum is also queryable, which free text in a log is not.

So the amendment package is **three fields**: `user.target.id`, `user.target.roles`, `user.target.unlockReason`. *Consolidated into the register (ticket 33): R-AUD-002, R-STD-008. Amend the table by ID, not this list.*

Ticket 11 also adds one field to an event this ticket already names: **`user.target.count`** on
`adminUserListed`, carrying the number of rows returned. That is the audit half of the admin list's
minimisation control — a hard page cap plus a recorded row count turns an unmonitored bulk export of every
email in the system into a bounded, recorded one. Whether `count` needs the same extension treatment is
ticket 13's call; `user.target` is an ECS-sanctioned reuse location but `count` is not an ECS `user` field. *Consolidated into the register (ticket 33): R-AUD-002. Amend the table by ID, not this list.*

Worth carrying into ticket 13's retention discussion: this ticket concluded the application is **not**
responsible for the 90-day TTL, which is the central platform's index lifecycle policy. That produces an
asymmetry ticket 11 relies on and cannot control — the accountability record for an admin unlock has a lifetime
set by a system nobody on this project configures, while the tombstone it relates to is retained indefinitely.

---

## Amendment from ticket 12 (data model reconciliation)

**The `uuid` mapping recorded in C8's cross-reference does not run on H2, and the column it describes no longer
exists.** Three faults in
`@Column(nullable = false, unique = true, columnDefinition = "UUID", insertable = false, updatable = false)` with a
database default of `gen_random_uuid()`:

- **`gen_random_uuid()` is not an H2 function.** H2's is `RANDOM_UUID()`; `GEN_RANDOM_UUID` is registered only inside
  H2's PostgreSQL compatibility mode (`org.h2.mode.FunctionsPostgreSQL`), and native support is still an open upstream
  request. It is a core PostgreSQL function from PG 13 onward, which is where the instruction came from.
- `columnDefinition = "UUID"` has no MySQL equivalent, so it cannot be one spelling for three databases.
- `insertable = false` means Hibernate never writes the column and needs a generation annotation to read it back.

**"This implies a `uuid` column distinct from the primary key" is withdrawn.** Ticket 12 makes the UUID the **sole**
primary key, named `id`, application-generated via `@UuidGenerator` with no database default anywhere. Nothing else in
this ticket changes: `user.id` and `user.target.id` both resolve to `users.id`, both are UUIDs, `AppUserDetails` still
carries the `UUID userId` because `Authentication.getName()` returns a username, and the UUID-keyed admin routes that
C4's worked example logs as `url.path` are unaffected.

One consequence worth noting for C8's "putting the target's UUID in `user.id` would silently corrupt every
'what did this user do' query": with a single identifier there is no longer a second value that could be logged by
mistake in either field, so the risk narrows from "wrong column" to "wrong entity".

---

## Amendment from ticket 13 (audit event catalogue)

[Build the audit event catalogue](13-audit-event-catalogue.md) consumed this extraction and reverses
two of its conflict resolutions, widens a third, and shrinks the extension package. The precedence rule
it recorded, and cites rather than re-argues: **governing user standard > logging standard > recipes.**

**C1 is withdrawn.** This ticket resolved the `source.ip` contradiction in favour of cleartext on
security events, specific-beats-general. Ticket 09 then specified a keyed HMAC, and ticket 13 settles
it as **hashed everywhere, cleartext nowhere** — which satisfies §3.3's prohibition *and* the recipe's
investigability intent, and has an ASVS basis (16.2.5, logging enforced by the data's protection level,
with session tokens hashed or masked as its example; ASVS does not name IPs, so the citation is the
general rule plus V16's control objective). C1's ADR is replaced by a narrower one: we deviate from the
AuthN recipe's `source.ip`, not from §3.3. *Consolidated into the ADR routing (ticket 34): ADR-054. Amend by ID, not this list.*

**The field name this ticket carried would have been rejected at ingest.** `source.ip.hash` is
prohibited: ECS types `source.ip` as `ip` (core), this schema types it `string`, and either way a
sub-field makes `source.ip` an object where every other platform producer emits a scalar. Use
**`source.ip_hash`**. *Consolidated into the register (ticket 33): R-AUD-002. Amend the table by ID, not this list.*

**C3 is reversed: lockout logs at WARN, not ERROR.** Three authorities agree against one recipe — the
Standalone standard §3.4 Log Levels states WARN, its test list repeats it, and §3.3's own definition
reserves ERROR for "operations that failed and could not recover", which a 20-minute auto-lifting
lockout is not. The AuthN recipe's `log.atError()` and `error_code: 423` lose. Ticket 06 also
guarantees the wire is always 401 on that path, so `error_code` is aligned to 401 and the log never
implies a status the wire never sent. *Consolidated into the register (ticket 33): R-STD-009. Amend the table by ID, not this list.*

**C4 is too narrow.** This ticket added `http.request.method` + `url.path` to admin audit events. §3.3
requires them on **every** audit event ("All audit events must include … Request path, HTTP method, and
correlation ID"), so they are mandatory context on every request-scoped row. One qualification:
`url.path` is the **matched route pattern** from `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, not
the requested URI — five admin endpoints carry a UUID in the path, so the raw URI is unique per target
rather than per endpoint, and the Standalone standard **externalises the API base path via application
properties**, so any saved query keyed on a literal path breaks when a deployer changes it.

**C2 and C9 stand unchanged.** C5 stands: `user.name` and `user.hash` are never emitted — and ECS
confirms `user.hash` is a legitimate field, so the ban is a security judgement, not a schema one.

**C8's extension package shrinks from a request for five fields to three.** Verified against the ECS
reference: **`user.target.*` is a documented field-reuse location** ("Targeted user of action taken")
and **`user.roles` is a real ECS field**. So `user.target.id` and `user.target.roles` are **valid ECS
that `Log_Schema.md` is behind on** — a materially easier amendment than "please add our invention". *Consolidated into the register (ticket 33): R-STD-008. Amend the table by ID, not this list.*
Genuinely custom: `user.target.unlock_reason`, `user.target.count`, `source.ip_hash`. Ticket 11's
`unlockReason` is **renamed to `unlock_reason`**, since camelCase matches neither ECS's underscore *Consolidated into the register (ticket 33): R-AUD-002. Amend the table by ID, not this list.*
convention nor this corpus; values stay upper-snake. `labels.*` was rejected for `count` because ECS
stores every `labels` value as keyword, which would strip a numeric count of numeric aggregation, and
that count is half a control. `query.count` reuse was rejected because its description is MPDS-scoped.

This ticket's inference that `user` is not pre-sealed and so `addKeyValue("user.target.id", …)` writes
cleanly **remains an inference** and is now a named first-implementation test rather than a note. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-010. Amend the table by ID, not this list.*

**Two additions to the emitter picture.** The sixteen named `AuditLogger` methods this ticket proposed
are replaced by a closed `AuditEvent` enum plus one `emit(event, context)`, because 40 rows would be 40
near-identical builder chains; the `"audit"` logger name, the dedicated appender and the underscore
`error_*` keys all survive, so no Enforced Constraint is touched. And **`emit` exposes no throwable
parameter**: because `.setCause(e)` auto-populates `error.message` and `error.stack_trace`, any
exception whose message carries request content would reach the audit stream with no call site naming
it — including a malformed credential body. Record `error.type` where the class matters; throwables stay
on the application logger.

**`session.hash` and `MdcUserFilter`.** `session.hash` is confirmed as specified here. One constraint *Consolidated into the register (ticket 33): R-STD-006. Amend the table by ID, not this list.*
added: `user.id` on a failure row is set **explicitly at the listener, never via MDC**, because
`MdcUserFilter` puts it on every line in a request and on a pre-auth path it must stay absent or the
UUID leaks onto unrelated rows.

**Retention is answered, not deferred.** This ticket's reading — the app is not responsible for the TTL
— stands, and ticket 13 adds the in-app half: a dedicated rolling file appender with a daily pattern
and `max-history: 90` and **no `total-size-cap`**, the omission being deliberate and asserted, because a
cap silently deletes the oldest archives while every config value still reads compliant. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-011. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-011. Amend the table by ID, not this list.*

---

## Amendment from ticket 21 (observability signals)

**The `spring-boot-starter-opentelemetry` chosen here for `trace.id` also turns on OTLP *metrics* export,
to `http://localhost:4318/v1/metrics`, every 60 seconds, with a WARN per failed publish.** Nobody decided
that, and it becomes live behaviour the moment ticket 21's actuator starter lands. Verified in
[the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md)
§5, §9, §10, §11.

**Why it was invisible, which is the part worth recording.** The three OTLP signals are gated
asymmetrically, and this is structural rather than a documentation oddity:

- **Traces** need `management.opentelemetry.tracing.export.otlp.endpoint`. Its `ConnectionDetails` bean is
  `@ConditionalOnProperty(endpoint)` and the exporters are `@ConditionalOnBean(ConnectionDetails)`, so no
  property means no exporter.
- **Logs** need `management.opentelemetry.logging.export.otlp.endpoint` **and** a Logback appender Boot does
  not ship, declared in `logback-spring.xml` and handed an `OpenTelemetry` instance programmatically at
  startup.
- **Metrics** need nothing. Their `ConnectionDetails` bean carries **no `@ConditionalOnProperty`** — only
  `@ConditionalOnMissingBean` — so it always exists, and `OtlpMetricsPropertiesConfigAdapter#url()` falls
  through to Micrometer's `OtlpConfig.super::url`, which hardcodes the loopback address after checking
  `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` and `OTEL_EXPORTER_OTLP_ENDPOINT`.

So this ticket adopted the starter for the one signal that requires an explicit property and silently
acquired the one that requires none. The Boot properties appendix shows a blank default for
`management.otlp.metrics.export.url`, because the default lives one layer down in Micrometer — which is how
it hides from exactly the kind of config review this map performs.

Two consequences beyond the noise itself. The WARN lands once a minute in the stream
[ticket 13](13-audit-event-catalogue.md) spent a whole ticket keeping clean, where "unexpected spikes in
ERROR or WARN volume" is a named §6 alert signal — so the exporter would be alerting on itself. And it is a
periodic outbound connection from an application whose egress
[ticket 24](24-secrets-and-configuration-handling.md) never scoped.

No change to this ticket's tracing decision: the starter is still the right choice, and
`management.tracing.propagation.type: w3c` is unaffected. What changes is that the starter is no longer a
single-signal decision, and the metrics signal has to be switched off explicitly — see ticket 21 and
ticket 24. The kill switch is `management.otlp.metrics.export.enabled: false`; a property with no value
does **not** work, because absence is precisely what triggers the fallback.

---

## Amendment from ticket 27 (inbound trace context)

**Every inbound trace is restarted at the boundary.** A header-stripping wrapper at `Ordered.HIGHEST_PRECEDENCE`
removes `traceparent`, `tracestate`, `b3`, `X-B3-*` and `baggage` before `ServerHttpObservationFilter` reads them.
So the `trace.id` this ticket adopted the OTel starter for is always server-generated.
`management.tracing.propagation.*` stays at its defaults. Detail is in
[ticket 27](27-inbound-trace-context.md) §4.

**Baggage is off.** `management.tracing.baggage.enabled=false` applies in every profile. Nothing uses baggage, and
it becomes caller-controlled MDC the moment a name is added to `correlation.fields`. The test is behavioural:

- set a probe key in `correlation.fields` / `remote-fields`;
- send it as a `baggage:` entry and as its own header;
- assert it reaches neither MDC nor the log line. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-012. Amend the table by ID, not this list.*

**Reopening trigger:** any ticket that needs a correlation field. *Consolidated into the register (ticket 33): R-OBS-014. Amend the table by ID, not this list.*

**`correlation.id` is dropped**, as struck in place above (Tier D, and line 277 part (a) with the `@Order` note
after it). The `MDCInsertingServletFilter` ban stays.

**Line 276 note:** the test suite's sampling probability of `1.0` must be overridden to `0.0` for ticket 16 row 4's
sampling case. Otherwise the assertion passes whatever the caller sends.
