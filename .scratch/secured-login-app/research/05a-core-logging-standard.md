# 05a — Core logging standard, schema, encoder, exceptions, questions

Part of ticket [05 — Which logging standards actually bind a two-process app](../issues/05-logging-standards-applicability.md).
Scope of this file: `Structured_Logging_Application_Standard.md` (SLAS), `Log_Schema.md` (LS),
`Structured_Logging_Application_Standard_Questions.md` (Q), `Recipes/Custom_Structured_Log_Encoder.md` (CSLE),
`Recipes/Logging_Exceptions_With_Enhanced_Details.md` (LEED), `index.md`, `Recipes/index.md`.
**Not** covered here: masking, AuthN/AuthZ event, typed audit module, MDC, OTel/trace-propagation, lifecycle,
batch recipes — those are sibling research files under the same ticket.

App shape assumed throughout: React SPA + **one** Spring Boot service; session-cookie auth; registration, login,
lockout, IP throttling, logout, password reset, admin user management; **no** batch/scheduled jobs, **no** message
queues, **no** downstream service hops, **no** MFA, **no** real SMTP (stubbed `EmailService` that logs).

Convention: `SLAS:126` means `Structured_Logging_Application_Standard.md` line 126. Modal words are quoted
verbatim where the mandatory/recommended split turns on them. "**I infer**" marks every step that is mine and
not the standard's text.

---

## 0. Two documents, one authority chain

- `index.md:1-4` lists all four top-level artefacts as one standard set; `Recipes/index.md:1-10` lists ten recipes.
  Nothing in either index marks any file optional.
- `LS:1` titles itself "Logging Schema for **Batch and Interface Applications**". Taken alone that would exclude this
  app. But `SLAS:121` — "Log fields, their canonical definitions, allowed values, and ECS auto-population behaviour
  **are specified in** [Log Schema]" — binds the schema for *all* logging in scope of SLAS, whose scope (`SLAS:9-18`)
  covers API request handling, authN/authZ, business logic and audit. **I infer**: `LS` binds this app in full,
  its title notwithstanding; its batch/interface *sections* are vacuous (§6), its Core/User/Event/Error sections are not.

---

## 1. The mandatory field set

### 1.1 Always required on every event (SLAS:123-129)

`SLAS:123` — "Required fields (**always present in every log event**)". All of the following are MANDATORY:

| Field | How populated | Citation |
|---|---|---|
| `@timestamp` | auto by ECS formatter — "do not set manually" | SLAS:124, LS:39, LS:74 |
| `log.level` | auto | SLAS:124, LS:41, LS:75 |
| `message` | auto | SLAS:124, LS:40, LS:76 |
| `service.name` | auto from `application.yaml`; "do not set at log time" | SLAS:125, LS:49, LS:117 |
| `service.version` | auto from `application.yaml` | SLAS:125, LS:50, LS:120 |
| `service.environment` | auto from `application.yaml`; enum `dev`/`test`/`staging`/`prod` | SLAS:125, LS:51, LS:121 |
| `trace.id` | auto by Micrometer Tracing "when a span is active" | SLAS:126, LS:94 |
| `span.id` | auto by Micrometer Tracing | SLAS:126, LS:95 |
| `thread.name` | auto (emitted as `process.thread.name`, LS:45) | SLAS:127 |
| `logger.name` | auto (emitted as `log.logger`, LS:43) | SLAS:127 |

Also unconditionally required, not a named field:
- `SLAS:128` — "Each event **must** include enough metadata to reconstruct what happened, including when, where,
  who, what, and the outcome (success or failure) when applicable."
- `SLAS:129` — "**Limit** MDC to a small set of fixed, known-safe fields such as `trace.id`, `span.id`, and job or
  request IDs. **Avoid** storing domain objects, collections, or sensitive data in MDC." (Q:458 sharpens this to
  "**Limit to 5-10** application-defined fields maximum".)

Note the naming mismatch: SLAS:127 says `thread.name`/`logger.name`; the ECS formatter actually emits
`process.thread.name` and `log.logger` (LS:43,45). **I infer** the ECS emitted names govern, since both docs agree
these are auto-populated and unsettable.

Also auto-emitted and therefore present, though SLAS:123-129 does not list them: `ecs.version` (LS:41),
`process.pid` (LS:44, "if `spring.application.pid` is set"), `service.node.name` (LS:52, if configured).

### 1.2 Conditional / contextual fields (SLAS:131-138)

`SLAS:131` — "Contextual fields (**include when applicable**)". Each is mandatory *within its trigger condition*:

| Field | Trigger condition | Citation |
|---|---|---|
| `correlation.id` | "when a single `trace.id` does not span the full workflow" | SLAS:132, LS:96 |
| `host.name`, `host.ip` | "**required for startup events**" | SLAS:133, LS:84-85 |
| `user.id` | "authenticated user context (UUID). **Never** log raw user identifiers such as usernames or email addresses" | SLAS:134, LS:104 |
| `event.kind` | event classification — `event` \| `state` | SLAS:135, LS:133 |
| `event.category` | array; enum `configuration`,`network`,`database`,`batch`,`interface`,`process` | SLAS:135, LS:134 |
| `event.type` | array; 24-value enum incl. `access`,`admin`,`allowed`,`change`,`creation`,`deletion`,`denied`,`end`,`error`,`info`,`start`,`user` | SLAS:135, LS:135 |
| `event.action` | large enum; applicable values listed in §1.3 | SLAS:135, LS:136 |
| `event.outcome` | `success` \| `failure` \| `partial-success` | SLAS:135, LS:137 |
| `event.start` | "**should be** logged on a `*-start` event" | SLAS:136, LS:140 |
| `event.end`, `event.duration_ms` | "timing for significant operations; log on `*-end` events" / "**should be** logged on an `*-end` event" | SLAS:136, LS:141-142 |
| `batch.*`, `trigger.*` | "**required for** batch job and step lifecycle events" — **vacuous** (§6) | SLAS:137 |
| `interface.*`, `http.*`, `url.*`, `file.*` | "**required for** outbound API calls and file-based integrations" — **mostly vacuous** (§6) | SLAS:138 |

Schema-defined fields that are optional (no SLAS requirement attaches them to any event this app produces):
`source.ip` (LS:86), `user.name` (LS:105), `session.hash` (LS:106), `service.id` (LS:116), `service.system`/
`service.subsystem` (LS:118-119), `service.connection*` (LS:122-125), `event.severity` (LS:138), `event.reason` (LS:139).

### 1.3 `event.action` values that map onto PRD stories (LS:136)

From the enum at LS:136, these are the only values that fit this app's surface: `application-startup`,
`application-shutdown`, `user-authentication`, `user-logout`, `user-provisioning`, `user-administration`,
`profile-read`, `password-reset`, `password-change-enforcement`, `session-start`, `session-end`, `access-control`,
`data-export`, `ACCESS_DENIED`, `ATTEMPTS_EXCEEDED`.

**I infer** the mapping: registration → `user-provisioning`; login → `user-authentication`; logout →
`user-logout`; session cookie issue/expiry → `session-start`/`session-end`; password reset request and completion →
`password-reset`; lockout → `ATTEMPTS_EXCEEDED`; authorisation denial → `ACCESS_DENIED` (or `access-control`);
admin role change / enable / disable / delete → `user-administration`; self-read (`GET /currentUser`) →
`profile-read`. **Gap**: there is no enum value for *IP throttling* and none for *account unlock*; and
`event.category` (LS:134) has **no** authentication/security/iam value at all — the nearest is `process`.
That gap is question **QX3** in §5.

### 1.4 Emission requirements (SLAS:162-170) — all MANDATORY

- `SLAS:163` — "Emit newline-delimited JSON to standard output with one event per line"
- `SLAS:164` — "If required fields are absent, the application **MUST** still emit the event and **MUST NOT**
  suppress or drop it silently"
- `SLAS:165` — "Use UTF-8 encoding"
- `SLAS:166` / `SLAS:141` — "Use ISO-8601 / RFC-3339 timestamps with timezone"; `@timestamp` "**MUST** conform to
  ISO-8601 / RFC-3339 format (e.g., `2026-01-22T14:07:51.080+08:00`)"
- `SLAS:167` — "**Standardise on Singapore Time Zone (UTC+8)**" (note: every `LS` example uses `…Z`, LS:74 —
  a cosmetic inconsistency; SLAS:167 is the normative sentence)
- `SLAS:168` — synchronise time sources (NTP) — platform-level, outside the app
- `SLAS:169` — "Propagate and log trace context (W3C Trace Context) **when available**"
- `SLAS:170` — "Log duration for significant operations"
- `SLAS:142` — "`log.level` **MUST** be one of: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`"

### 1.5 Contradictions inside my file set (flagged, resolution given)

1. **`FATAL`.** `LS:75` lists `FATAL` in the `log.level` enum. `SLAS:142` forbids it; `SLAS:152` and `SLAS:211`
   explain Logback has no `FATAL` and maps it to `ERROR`. **Resolution**: SLAS wins; never emit `FATAL`. Not a
   human question.
2. **`user.name`.** `LS:105` — `user.name` "**Should only be** logged in authentication/authorization contexts
   where **explicitly permitted by policy**." `SLAS:56` — log "`user.id` UUID (**never** the raw username, email,
   or credential)"; `SLAS:278` and `SLAS:368` repeat the prohibition absolutely. **Not** self-resolving: LS:105
   creates a policy escape hatch that SLAS closes. This is question **Q17b** / **QX1** in §5 and it bites hard on
   failed-login logging (see below).
3. **Client IP.** `SLAS:230` lists "Client IP addresses" under "What must **NOT** be logged". `LS:86` defines
   `source.ip`; `Q:37` says "Include source IP **only if required for security monitoring** (consider privacy
   regulations)". The PRD requires IP throttling, which is security monitoring by definition. Genuine conflict →
   question **Q2b** in §5.
4. **Pre-auth identity.** `LS:104` — "For pre-authentication entry points where the UUID is not yet resolved,
   **omit user identity entirely** and rely on `session.hash` and `trace.id` for correlation." But `Q:94` —
   "**Never log**: Session IDs or access tokens (**in any form, including hashed**)" and `Q:461` — "**Never store**
   session IDs in MDC (even hashed)". So LS:106's `session.hash` is the *only* sanctioned pre-auth correlator and
   Q:94 forbids it. This leaves a failed login against a non-existent username with **no** subject identifier at
   all. → question **Q5b** in §5. This is the sharpest contradiction in the set.

---

## 2. Level semantics (SLAS:203-220)

MANDATORY definitions (`SLAS:203` — "Log levels and conditions"):

- `SLAS:204` **INFO** — "normal milestones and outcomes"
- `SLAS:205` **WARN** — "unexpected or degraded conditions the application handled without failing (e.g., a retry
  that succeeded, a fallback that triggered). Requires monitoring but not immediate intervention"
- `SLAS:206` **ERROR** — "operations that failed and could not recover"
- `SLAS:207` **DEBUG** — "temporary diagnostics, not for permanent production use"
- `SLAS:208` **TRACE** — "deep internal detail, short term only"

Level rules — MANDATORY (modal "must"/"do not"):
- `SLAS:215` — "**Do not** log the same event at multiple levels"
- `SLAS:216` — "**Avoid** enabling DEBUG or TRACE permanently in production" (RECOMMENDED by modal, but see :396)
- `SLAS:217` — "Changes to log levels **must not** suppress required security or audit events"
- `SLAS:218` — "**Do not** suppress log entries for security or audit events"
- `SLAS:219` — "Write log messages as **static**, human-readable descriptions of the event. Dynamic values (IDs,
  codes, durations) belong in key-value fields, **not** embedded in the message string"
- `SLAS:220` — "**Do not** log inside nested loops without rate limiting"
- `SLAS:396` — "**Do not** use [`/actuator/loggers`] to change log levels in production. Log levels **must** remain
  as configured"

Level assignments that bind specific events in this app:
- Authentication failure → **WARN**, with a generic reason (`SLAS:57`, `Q:34`)
- Authorisation failure → **WARN** (`Q:77`)
- Definitive, non-retryable rejection (e.g. missing required field) → **ERROR** (`SLAS:97`)
- Business rule violation → **ERROR** (`SLAS:98`)
- Uncaught/unexpected exception at the global boundary → **ERROR**, "**must** include all error fields" (`SLAS:105`)
- Retryable / safely-skippable failure → **WARN** (`SLAS:97`)
- Exceptions: `LEED:27` — "`.atError()` for unrecoverable failures or unexpected system errors… `.atWarn()` for
  recoverable or expected failures… **Avoid** `.atInfo()` for exceptions"

---

## 3. Privacy policy — the prohibition list

`SLAS:222` — "What **must NOT** be logged". MANDATORY, verbatim list (`SLAS:223-236`):
passwords/credentials/payment details/OTP codes/TOTP secrets/backup codes/recovery phrases/other secrets;
session IDs or access tokens in raw form; encryption keys; authentication headers; personal data and sensitive IDs;
database connection strings, database names and schema IDs; dependency versions of libraries or frameworks;
**client IP addresses**; **URL query parameters**; **request and response bodies**; application source code or
proprietary business information; data exceeding the logging system's approved classification; data collected
without consent or legal basis. `SLAS:236` — "If any sensitive value **must** be logged for correlation, **mask or
hash it**."

Reinforced elsewhere, all MANDATORY:
- `SLAS:52` — on request start, "**Do not** log the client IP address, query parameters, authentication headers, or
  request body"
- `SLAS:57` — on auth failure, "**Never** log the internal exception message, the submitted credential, or a reason
  that reveals whether the account exists"
- `SLAS:68` — "**Do not** log SQL statements or query payloads in production"; JPA SQL logging only in
  non-production "with parameter values redacted"
- `SLAS:46` — at startup, "**Never** log credentials, connection strings (e.g., `spring.datasource.url`), database
  names, timeout or retry values for authentication flows, or any secrets"
- `SLAS:278` — "prefer `user.id` UUID. **Never** log raw usernames or emails. Hashed PII is permitted **only if**
  secured via a system-wide salt or HMAC"
- `SLAS:279` — "**Encode and sanitize** untrusted user input (e.g., stripping CRLF characters) to prevent log
  injection, log forging (CWE-117)"; `Q:381-383` sharpens: strip `\r`/`\n`, encode control characters
- `SLAS:280` — "**Enforce** automated data masking or redaction **at the application logging boundary** as a
  defense-in-depth measure"
- `SLAS:326` (Enforced Constraint) — "Apply masking rules for sensitive fields at the application logging boundary…
  Fields to mask include passwords, tokens, secrets, PAN, SSN, and **user identifiers used for correlation**"
- `SLAS:336` (Enforced Constraint, anti-pattern) — never pass "domain objects directly as log arguments via
  `toString()`"
- `LS:76` — `message` "**Must** be sanitized to remove PII or sensitive data"
- `LS:303-304` — `error.message` and `error.stack_trace` must be sanitised "**at the throw site**"
- `LEED:186` — "**Do not** place sensitive values such as passwords, tokens, full personal data, or raw payloads
  in exception messages or structured fields"

RECOMMENDED (modal "should"/"prefer"/"consider"):
- `Q:385` — "Log user input in **structured fields**… not in message text"
- `Q:363` — "**Complete masking** — Replace entire value with `***MASKED***` (**recommended**)"; partial visibility
  "only for industry-standard cases"
- `Q:350` — "If unsure, assume **Confidential** and implement hashing + masking"

The PRD's "plaintext password is never logged" criterion is therefore the *floor*, not the ceiling: `SLAS:223`
covers it, but `SLAS:227` (personal data), `SLAS:230` (client IP), `SLAS:232` (request bodies) and `SLAS:278`
(raw usernames/emails) each add prohibitions the PRD does not state.

---

## 4. The custom encoder, and exception logging

### 4.1 Is a custom encoder mandatory?

**Conditionally mandatory — and the condition is a choice this effort has not yet made.** The chain:

1. `SLAS:186` (a Note under §3.2 Error Contract) — "For `ERROR` events, **set** `error_code` and `error_category`
   from the defined enum values, **set** `error_follow_up_action`…, and **pass** the exception to `.setCause(e)`."
   These three fields are therefore required on every ERROR event.
2. `SLAS:188` — "These fields use underscores… because Spring Boot's ECS formatter **pre-seals** the `error` object.
   Attempting to set dotted keys directly causes a JSON writing error. **A custom encoder intercepts the underscore
   keys and maps them** to their proper nested schema fields."
3. `LS:62` — "attempting to set nested fields like `addKeyValue("error.code", …)` **will cause a JSON writing
   error**… use a workaround key with a non-dot separator (e.g., `error_code`) and **implement a custom encoder**
   to remap them."
4. `LEED:10` lists the custom encoder as a **prerequisite**: "[Custom Structured Log Encoder] configured for
   underscore-to-dot field name mapping (**required** for `error_code`, `error_category`, and similar fields)".
5. `LEED:23` — "You **must** use underscore keys… and a custom encoder to map them correctly."

But: `CSLE:11` — "This issue is **specific to the ECS format** (`logging.structured.format.console=ecs`). Spring
Boot's other built-in formats (`logstash` and `gelf`) write all fields flat without true nested JSON objects, so
dotted keys **do not clash** in those configurations." `CSLE:511` repeats: "**Logstash and GELF are immune**."
And `SLAS:300`/`SLAS:319` leave the format open — "JSON formats such as ECS, GELF, and Logstash", "the **agreed**
structured logging format (e.g., `ecs`)".

**Conclusion.** The encoder is mandatory **iff** ECS is the chosen format. It is not one of several ways to satisfy
a single requirement — under ECS it is the only stated way. Choosing `logstash` or `gelf` removes the requirement
entirely, at the cost of losing nested `error.*` grouping. That format choice is question **QX2** in §5, and it is
the single highest-leverage logging decision in this ticket, because `CSLE:24` warns: "This solution extends
`StructuredLogEncoder`, an **internal Spring Boot class**… **not a documented public extension point**, meaning its
API may change in future Spring Boot releases without a migration guide. You **must** retest this custom encoder
after every Spring Boot version upgrade." Prerequisite: `CSLE:29` — **Spring Boot 4.0+** (also `LEED:7`).

Separately, `SLAS:326` lists the encoder as *one of three* options for **masking** — "a custom Logback converter,
a `PatternLayout` masking rule, **or** a structured log encoder extension". So for masking it is optional; for
nested `error.*` under ECS it is not. (Masking mechanism selection belongs to the sibling masking file.)

### 4.2 What the encoder must emit

If built (`CSLE:35-60`, `CSLE:114-124`, `CSLE:475-500`):

- Strip every root-level key matching `(error|service|log|process|ecs)_.+` and nest the remainder inside the
  matching ECS object, creating the object if absent (`CSLE:256-259`, regex at `CSLE:306`).
- Required mappings (`CSLE:116-124` table): `error_code`→`error.code`, `error_category`→`error.category`,
  `error_follow_up_action`→`error.follow_up_action`, `service_system`→`service.system`,
  `service_subsystem`→`service.subsystem`, `service_connection_type`→`service.connection.type`,
  `service_connection_status`→`service.connection.status`.
- Auto-resolve `error.category` from the throwable when not explicitly set (`CSLE:257`, `CSLE:326-336`), via a
  separate testable `ErrorCategoryResolver` class (`CSLE:150`), rules ordered subclass-before-parent
  (`CSLE:154` — "**More specific exception types must appear before their parent types**").
- Explicit `error_category` **must override** the auto-resolved value (`CSLE:499-500`).
- Category values **must** match `LS:305`'s enum (`CSLE:33`, `CSLE:134`, `CSLE:478`): `server`, `network`,
  `cert/auth`, `database`, `application`, `data`, `others`. `CSLE:134` warns `error.category` is "**not part of the
  official ECS specification**" — it is an Appfw extension.
- Fail soft: on any post-processing exception, return the original bytes (`CSLE:286-292`).
- `CSLE:369` — the encoder and resolver "**must remain independent and should not be annotated as Spring beans**"
  because logging initialises before the Spring context.
- `CSLE:381` — register in `logback-spring.xml` by fully-qualified class name **and** include `<format>ecs</format>`
  so the parent initialises the ECS formatter. Reconcile with `SLAS:320`, which is an **Enforced Constraint**:
  "reference the `CONSOLE_LOG_STRUCTURED_FORMAT` and `FILE_LOG_STRUCTURED_FORMAT` environment variables to select
  the encoder **rather than hardcoding it** in the XML". `CSLE:389` hardcodes `<format>ecs</format>`. **I infer** a
  tension the implementation must resolve (parameterise the `<format>` element from the env var).
- `CSLE:408` — "if **no throwable** is passed via `.setCause(e)`, the `error` object is never created, so there is
  nowhere to inject the value. The key is **removed** and does not appear in the final output." So `error_code` on a
  *non-exception* ERROR event is **silently dropped**. **I infer** this collides with `SLAS:186`, which requires
  `error_code` on ERROR events generally — including business-rule failures logged without a throwable. Flagged as
  an implementation trap for ticket 12, not a human decision.

### 4.3 Exception-logging requirements

MANDATORY:
- `LEED:17` — "Omitting `.setCause(e)` loses the stack trace. Omitting the structured fields makes the entry
  unsearchable by code or action. **Both are required.**"
- `LS:56` — "**Always** use `.setCause(e)`… **Never** use `addKeyValue("error.message", …)`,
  `addKeyValue("error.type", …)`, or `addKeyValue("error.stack_trace", …)`" (auto-populated, `LS:46-48`)
- `LEED:21` / `LEED:100` — same prohibition; manual setting "produces a duplicate field conflict"
- `LEED:23` — dotted `error.code`/`error.category` "**will cause** Spring to throw a JSON writing error"; underscore
  keys + custom encoder are required
- `LEED:27` — level: `.atError()` unrecoverable/unexpected; `.atWarn()` recoverable/expected; avoid `.atInfo()`
- `LEED:118` — "**Always** pass the original exception as a constructor argument when wrapping" — `LEED:121-131`
  gives the AVOID/PREFER pair; `LEED:134` — ECS traverses the chain only if every wrapper chains
- `LEED:139` — "Log the exception **once** at the point where it is definitively handled… **Do not** log it in
  intermediate layers that simply catch and rethrow" (= `SLAS:337`, Enforced Constraint; `SLAS:193`, `SLAS:325`)
- `SLAS:325` (Enforced Constraint) — "Implement a **global exception handler** to catch and log unexpected,
  unhandled exceptions **once at the boundary**. Business-specific exceptions **should** be logged at the point
  they are handled in the business logic, **not** delegated to the global handler"
- `SLAS:338` (Enforced Constraint) — never catch and silently discard
- `SLAS:330` (Enforced Constraint) — never `System.out.println` or `e.printStackTrace()`
- `SLAS:331` (Enforced Constraint) — never string concatenation in log statements; use parameterised/structured form
- `SLAS:332` (Enforced Constraint) — never mix plain text with JSON in the same stream
- `SLAS:199` — "Sanitize error context before logging, as exception messages and stack traces may expose sensitive
  internals such as SQL statements, internal file paths, or business logic"
- `LEED:186` — no sensitive values in exception messages or structured fields

Error contract, MANDATORY (`SLAS:173-182` table): map each failure to an `error.category` and the paired HTTP
status — `server`→503, `network`→503, `cert/auth`→401/403, `database`→500, `application`→500, `data`→400,
`others`→502/503. Plus `SLAS:193-200`: one global handler logging each error once; sufficient who/what/where/when/
outcome context; generic messages to clients; "For authentication, return a **consistent response regardless of
account existence or credential validity** to prevent user enumeration" (`SLAS:195`); "**Fail securely and stop
processing** when validation or security controls fail" (`SLAS:196`); classify errors for alerting (`SLAS:200`).

RECOMMENDED for exceptions: `LEED:103` — MDC context is picked up automatically; add fields directly "if that
context is not already in MDC". `LEED:165-183` — an intermediate layer *may* log context fields (no `.setCause`)
before rethrowing, if the context is unavailable at the boundary.

### 4.4 Verification checklist that lands on ticket 14

`SLAS:343-380` is the standard's own pre-release checklist; the items with surface here:
structure parsable (`:346`); required fields present in all events (`:347`); timestamps consistent (`:348`);
startup log has service metadata + profiles + host and **no secrets** (`:349`); MDC set for request lifecycle and
**cleared after** (`:354`); request logs include method, path, status, duration, outcome (`:358`); request logs
**exclude** client IP, query params, auth headers, bodies (`:359`); error paths logged **once** with all error
fields (`:362`); auth-failure message generic and non-enumerating (`:365`); all audit-contract events present
(`:366`); no sensitive data (`:367`); "User identifiers appear **only** as `user.id` UUID; raw usernames, emails,
and credentials are **absent**" (`:368`); audit events present in the **dedicated audit destination**, not only the
application log (`:369`); log-injection attempts do not corrupt output (`:370`); logging failures do not crash the
app (`:375`); log-destination outage does not break application flow (`:376`).
Test-data rules: `:379` — deterministic synthetic IDs, "**Do not** use real user data, email addresses, or IP
addresses in test scenarios"; `:380` — assert on the **sanitized** form of injection payloads, not the raw payload.
`LEED:191-198` adds the exception-specific assertions, including "verify that `error.code`, `error.category`, and
`error.follow_up_action` appear… with proper **dotted** names (not underscore names)".

---

## 5. Questions needing a HUMAN decision

Questions the app shape answers by itself, recorded for completeness with no ticket needed:
**Q1** = REST API / Web Service (`Q:13`). **Q2** = Yes (`Q:26`). **Q4** = Yes, `USER`/`USER_MANAGER` (`Q:69`).
**Q7** = Yes, the user account is a core entity (`Q:124`; criterion "audit compliance requirements", `Q:131`).
**Q9** = No — the only "external" call is the stubbed `EmailService`, so `Q:171-189` is vacuous.
**Q11** = No (`Q:216`). **Q12** = No (`Q:241`). **Q16** = Yes, and mandatory anyway (`SLAS:325`).
**Q18** = Yes — usernames, emails and reset tokens arrive from clients (`Q:374`), so `Q:380-383` sanitisation is
mandatory. **Q20** = Yes (`Q:472`), which makes `Q:478` "Route audit events to dedicated appender" mandatory,
matching `SLAS:327`.

The rest need a human. `Qn` numbers are the Questions file's; `QXn` are questions my file set raises that the
Questions file does not ask.

**Q3 — Which credential-management features are in scope, and is there an authenticated "change my password" flow
separate from reset?** (`Q:43-64`) — MFA and API keys are out of scope per the map, so only "Password
changes/resets" is selected; `Q:60` then requires logging `user.id`, credential type and outcome, and `Q:61`
forbids logging old/new credential, reset token or temporary password. The human part: the map records that
**password history enters scope**, which implies a change-password path, and `LS:136` offers a distinct
`password-change-enforcement` action. Options: (a) reset-only, one `password-reset` action; (b) reset + authenticated
change, two distinct `event.action` values. **Recommendation: (b)** — password history is meaningless without a
change flow, and the enum already anticipates it.

**Q2b — May the client IP be logged, given the PRD mandates IP throttling?** (`Q:37` — "Include source IP **only
if required for security monitoring** (consider privacy regulations)" vs `SLAS:230`, which lists "Client IP
addresses" among things that **must NOT** be logged, and `SLAS:52`/`SLAS:359` which forbid it on request logs; vs
`LS:86` which defines `source.ip`.) A throttling or lockout event that cannot name the IP it throttled is not
forensically useful, yet `SLAS:230` is unqualified. Options: (a) never log IP — throttle events carry only counts;
(b) log `source.ip` **only** on throttle/lockout/auth-failure audit events, never on request logs; (c) log a
salted HMAC of the IP everywhere (`SLAS:236`/`SLAS:278` permit hashed values "only if secured via a system-wide
salt or HMAC"). **Recommendation: (c) with (b)'s scoping** — HMAC the IP, emit it only on security events, never on
ordinary request logs. Needs a human because it is a privacy ruling, not a technical one, and it fixes the shape of
the throttling audit record.

**Q5 — Session-lifecycle logging, and Q5b: what identifies a pre-authentication failure?** (`Q:84-96`) Q5 = Yes,
stateful sessions (JDBC session store per ticket 01), so `Q:92` makes session start/end/logout/timeout logging
mandatory and `Q:93` requires logging invalid-session and hijack attempts. The human part is the contradiction at
§1.5(4): `LS:104` says rely on `session.hash` for pre-auth correlation, `LS:106` defines it, but `Q:94` forbids
session identifiers "**in any form, including hashed**" and `Q:461` bars them from MDC. A failed login for an
unknown username therefore has **no** permitted subject field — yet the PRD requires lockout and repeated-failure
auditing, and `SLAS:249` requires logging "Repeated input validation failures, which may indicate brute force".
Options: (a) follow `Q:94` strictly — pre-auth failures carry only `trace.id` and are correlated by time; (b) follow
`LS:104`/`LS:106` — emit `session.hash`; (c) emit a salted HMAC of the **submitted username** as an opaque
`user.id`-shaped correlator (permitted by `SLAS:278` for hashed PII with a system-wide salt/HMAC), which correlates
brute force per account without storing the username. **Recommendation: (c)**, with the salt externalised as a
secret. This is the sharpest unresolved contradiction in the set and must be a ticket.

**Q6 — Do lockout and throttling count as "significant business decisions"?** (`Q:102-116`) `Q:114` excludes
"routine internal logic" and anything "occurring >100 times/min"; `SLAS:64` requires logging key decisions with
outcome and reason. Lockout is irreversible-ish and has compliance implications (`Q:116` criteria), so it plausibly
qualifies — but it is *already* required as an audit event by `SLAS:244`. The decision is whether a *second*,
decision-flavoured INFO log is also emitted (risking `SLAS:215`, "do not log the same event at multiple levels", and
duplicate-event noise). **Recommendation: No separate decision log** — lockout/throttle are audit events only.
Human because it trades off against ticket 12's audit contract.

**Q8 — Is admin user management "privileged access to sensitive data", and is there any bulk export?**
(`Q:143-158`) A `USER_MANAGER` listing users reads other users' PII, which is exactly `Q:153`'s "admin viewing
another user's account". `Q:155` would then require logging `user.id` of the actor, resource type, and **count or
scope**. Options: (a) audit only *mutations* (role change, enable/disable/delete); (b) also audit every admin
*read* of the user list, with a record count, per `Q:155`. **Recommendation: (b)**, since `SLAS:243` requires
logging "access to sensitive data" and `SLAS:246` requires logging bulk queries; the volume is trivial at this
scale. Human because it adds an audit event the PRD's audit list does not mention.

**Q10 — Is the database connection an "external system" whose connect/fail is logged?** (`Q:193-207`) `Q:195`
explicitly lists "databases (connection-level)" as in scope; `Q:203-204` would require logging connection outcome
and duration, and `Q:205` forbids logging host/URL/database name/credentials. `LS:122-125` provides
`service.connection.*` for exactly this. Options: (a) No — treat HikariCP's own logging as sufficient and declare
Q10 vacuous; (b) Yes — emit a startup/heartbeat `event.type: connection` state event using an
environment-configured identifier per `Q:207` ("`DB_IDENTIFIER=primary-db`"). **Recommendation: (b)**, minimal form:
one connection-state event at startup. Human because it adds a component and a config key.

**Q13 — Will anything run `@Async`, in particular the stubbed `EmailService`?** (`Q:266-280`) If yes, `Q:276` and
`SLAS:324` (Enforced Constraint) require a `TaskDecorator` copying the **full** MDC map across the boundary, plus
MDC clearing; `SLAS:314` explains Micrometer Context Propagation carries trace/span only. If no, the whole
`TaskDecorator` requirement and `SLAS:353`'s async test are vacuous. Options: (a) synchronous stub — no
`TaskDecorator`; (b) `@Async` stub — `TaskDecorator` becomes mandatory. **Recommendation: (a)** for the assessment
scope; a logging stub has no reason to be async, and (a) deletes a whole requirement branch. Human because it is an
architecture choice with a logging consequence.

**Q14 — At what level are Bean Validation failures logged?** (`Q:286-298`) `SLAS:97` says WARN when "not yet
actionable or can be safely skipped", **ERROR** when "definitive and non-retryable (e.g., a missing required
field)"; `Q:295` repeats it. Read literally, every `400` from `@Valid` on registration is an **ERROR**, which will
dominate the ERROR volume of a login app and defeat `SLAS:200` ("Classify errors for alerting so only actionable
failures trigger alerts"). `Q:298` then *recommends* "Log client validation failures at **WARN** once per request
with field names only" — contradicting `SLAS:97`. Options: (a) ERROR per `SLAS:97`; (b) WARN per `Q:298`;
(c) WARN for client-input validation, ERROR for business-rule violations (`Q:296`). **Recommendation: (c)** — it
satisfies `Q:298` and `SLAS:98` simultaneously and keeps ERROR actionable. Needs a human because it is a direct
standard-vs-question conflict.

**Q15 — Does anything retry?** (`Q:302-317`) With no outbound calls and no queues, the only candidates are
database-transaction retries. If yes, `Q:312-316` mandates a four-log pattern (first failure ERROR, each attempt
WARN with attempt number, final ERROR with total attempts, retry success INFO) and `Q:317` requires reading the
retry count from configuration at runtime, not hard-coding it. **Recommendation: No retries** — declare `Q:302`
answered "No" and `SLAS:101-102` vacuous. Human, because answering "No" is a design commitment.

**Q17 — Data classification, and the masking format.** (`Q:337-366`) The app stores usernames, emails and password
hashes, so by `Q:345` ("Contains PII (names, emails, phone numbers, addresses)? → **Confidential**") it is at least
**Confidential**; whether Singapore/IM8 obligations push it to **Highly Restricted** (`Q:342`) is a human call, and
both tiers trigger the same `Q:355-360` requirement block, so the practical fork is the checkbox at `Q:363`:
**Complete masking** (`***MASKED***`, "recommended") vs **Partial visibility** ("only for industry-standard cases").
**Recommendation: Confidential + complete masking.** Human because it is a classification declaration that other
controls (`Q:359` WORM storage, `Q:360` TLS transmission) inherit from.

**Q19 Step 1 — Is a trace id required at all when there is one service and no hop?** (`Q:393-412`) This is the
ticket's headline question, and my files give a **split** answer. `Q:404-406` says you do **NOT** need distributed
tracing if "Your application is a standalone service with no downstream dependencies" and "All operations are
self-contained within one service" — which describes this app exactly. But `SLAS:126` lists `trace.id` and
`span.id` under "**Required fields (always present in every log event)**"; `SLAS:321` is an **Enforced Constraint**
— "**Add** Micrometer Tracing to the classpath so that `traceId` and `spanId` are written to MDC automatically
whenever a span is active"; `SLAS:352` tests that they propagate; and `LS:94` makes `trace.id` the identifier that
"correlates all events within a single transaction". Options: (a) treat `Q:404` as permission to omit tracing —
`trace.id`/`span.id` absent, and rely on `SLAS:164` ("if required fields are absent, the application MUST still
emit the event") to make their absence legal; (b) add Micrometer Tracing anyway, satisfying `SLAS:126`/`SLAS:321`
literally — the trace id then correlates the log lines of a **single request within one service**, which is still
useful (it groups the ~6 lines a login produces) and costs one dependency and zero application code;
(c) add Micrometer Tracing *and* a custom `correlation.id` (see Step 2). **Recommendation: (b).** `SLAS:126` and
`SLAS:321` are unconditional and use "required"/"Enforced Constraint"; `Q:404` speaks only to whether *distributed*
tracing is *needed*, not to whether the required field may be dropped. **I infer** that the requirement does have
surface here — intra-request correlation — and is cheap; but a human must record the ruling because the ticket asks
for it explicitly and because (a) is defensible.

**Q19 Step 2 — Does the SPA send a correlation id, and does the backend accept one?** (`Q:416-444`) `Q:428` says
trace id alone suffices for "single-request operations within your service mesh". There is no upstream gateway
here, so no id arrives unless the SPA invents one. But `SLAS:132` reserves `correlation.id` for workflows a single
trace cannot span — and this app has exactly one: **password reset**, where "requested" and "completed" are two
independent requests minutes apart, plus the stubbed email in between. Options: (a) no `correlation.id` at all;
(b) SPA generates `X-Correlation-ID` per user action and the backend echoes it into MDC per `SLAS:323`/`Q:436-441`;
(c) backend-only — mint a `correlation.id` for the reset flow keyed to the reset token's identity, so the two
requests join. **Recommendation: (c)**, plus generating one on demand per `Q:444` ("Always generate a correlation ID
if upstream doesn't provide one"). Human because (b) changes the SPA's HTTP layer and ticket 01's bootstrap
sequence.

**Q19 Step 3 — Which application-defined MDC fields exist?** (`Q:448-464`) Constraints if any are added: `Q:458`
"**Limit to 5-10** application-defined fields maximum"; `Q:459` scalar fixed values only; `Q:460` `user.id` UUID
only; `Q:461` "**Never store** session IDs in MDC (even hashed)"; `Q:463` clear in `finally`. `SLAS:323` is an
**Enforced Constraint** requiring an MDC filter that writes a custom correlation id and "**the `user.id` UUID after
authentication**" into MDC and clears all fields in a `finally` block — so `user.id` in MDC is effectively already
mandated. Options: (a) `user.id` only; (b) `user.id` + `correlation.id`; (c) add a per-request `request.id`
distinct from `trace.id`. **Recommendation: (b)** — smallest set that satisfies `SLAS:323` and Step 2. Human, but
low-stakes; it should ride along with the Step 1/2 ruling.

**Q20b — How far do the audit-separation obligations go when there is no log platform in scope?** (`Q:477-481`,
`SLAS:257-262`, `SLAS:267-284`) `SLAS:327` (Enforced Constraint) mandates a dedicated appender and destination, and
that is an application-level obligation this app can meet. But `Q:479-481` and `SLAS:259`, `SLAS:262`, `SLAS:269`,
`SLAS:275`, `SLAS:284` demand independent retention, tamper-proofing (WORM or cryptographic signatures), **an alert
when audit logging itself fails**, local disk buffering for a forwarding agent, and a documented log inventory —
and `SLAS:287` explicitly assigns retention TTL to the platform, not the app, while the map puts containerisation,
CI/CD and hosting **out of scope**. Options: (a) implement the app-side half only — separate `audit` appender +
rolling file per `SLAS:304` — and document the platform-side controls as accepted gaps, the way the map already
handles local HTTP; (b) also implement an in-app audit-write-failure alert path per `SLAS:262`; (c) attempt
integrity controls in-app (signed audit records). **Recommendation: (a) + (b)** — (b) is a Logback status-listener
or appender-error hook and is cheap; (c) has no home without a platform. Human because it decides what goes in the
"accepted gaps" note that ticket 15 owns.

**QX1 — May `user.name` ever be logged?** (`LS:105` — "**Should only be** logged in authentication/authorization
contexts where **explicitly permitted by policy**" vs `SLAS:56`, `SLAS:278`, `SLAS:368` — "**never** the raw
username", "raw usernames, emails, and credentials are **absent**".) `LS:105` requires a *policy* to exist before
the field is usable; this effort is the policy. Options: (a) blanket prohibition — `user.name` never appears,
`SLAS:368` becomes a testable architecture rule; (b) permit `user.name` on authentication events only.
**Recommendation: (a)** — three SLAS lines say never, one LS line says maybe, and (a) yields a clean assertable
invariant for ticket 14. Must be a ticket because ticket 12's audit contract cannot be written without it, and
because it interacts with Q5b.

**QX2 — Which structured format: `ecs`, `logstash`, or `gelf`?** (`SLAS:300`, `SLAS:319` — "the **agreed**
structured logging format (e.g., `ecs`)"; `CSLE:11` and `CSLE:511` — logstash and gelf are immune to the
dotted-key problem; `CSLE:24` — the ECS workaround extends an **internal** Spring Boot class and "**must** be
retested after every Spring Boot version upgrade".) This choice determines whether §4.1's custom encoder is built
at all. Options: (a) `ecs` + custom encoder — full `LS` nesting fidelity, ~200 lines of fragile infrastructure
pinned to Spring Boot 4.0+ (`CSLE:29`), plus resolving the `SLAS:320` env-var-vs-hardcoded-`<format>` tension;
(b) `logstash` or `gelf` — flat output, no encoder, `error.code` etc. emitted as flat keys, which **I infer**
technically deviates from `LS`'s nested field names; (c) `ecs` **without** the encoder, accepting flat
`error_code`/`error_category` keys in the output. **Recommendation: (a)** — `LS` is written in dotted/nested terms
throughout and `SLAS:186-188` presupposes ECS; the recipe supplies the code almost verbatim. But a human must own
this, because it is the ticket's largest build-cost item and `CSLE:24` is an explicit maintenance warning.

**QX3 — Which `event.category` do authentication and authorisation events use?** (`LS:134` enum is
`configuration`, `network`, `database`, `batch`, `interface`, `process` — there is **no** `authentication`,
`iam`, or `security` value, yet `SLAS:135` makes `event.category` a contextual-required field and `SLAS:55-61`
requires classifying auth events.) Similarly `LS:136`'s `event.action` enum has no value for **IP throttling** or
**account unlock** (nearest: `ATTEMPTS_EXCEEDED`, `access-control`). Options: (a) use `process` for all auth events
and reuse `ATTEMPTS_EXCEEDED` for both lockout and throttle, distinguishing them by `event.action` + a separate
field; (b) extend the enums locally and document the extension; (c) raise it upstream as a standard gap.
**Recommendation: (a) now, (c) as a note** — the enums are closed lists and inventing values breaks cross-service
dashboards, which is the schema's whole purpose (`LS:7`). Human because it is a schema-conformance ruling that
ticket 12 encodes into the typed audit module.

**QX4 — Is a local rolling JSON file required, given hosting is out of scope?** (`SLAS:275` — "**Implement** local
log buffering by writing logs to a durable local disk (e.g., a rolling JSON file) for a forwarding agent to
transmit… **Avoid** sending logs directly over the network"; `SLAS:304` offers `logging.file.name` +
`logging.logback.rollingpolicy.*`; `SLAS:260` — "Configure rotation for local file logging"; but `SLAS:163`
requires stdout NDJSON and `SLAS:332` forbids mixing formats in one stream.) Options: (a) stdout only — buffering is
the platform's job, declared an accepted gap; (b) stdout **and** a rolling ECS-JSON file, satisfying `SLAS:275` and
`SLAS:304` and giving the audit appender of Q20b/`SLAS:327` somewhere to write. **Recommendation: (b)** — it is
pure configuration, and the dedicated audit destination needs a file anyway. Human because it touches ticket 15's
configuration surface.

---

## 6. Vacuous in my file set — no surface in this app

Stated explicitly so these are seen to be ruled out rather than skipped.

**Whole sections of `SLAS`:**
- `SLAS:71-86` **Asynchronous Operations** — context propagation on enqueue, dispatch logs, task lifecycle, ACK/NACK,
  dead-lettering (`:73-77`), and the entire scheduled-batch block (`:79-86`: schedules at startup, job identity in
  MDC, step item counts, file metadata, MDC cleanup). No queues, no schedulers, no Spring Batch. Conditionally
  revived only if Q13 answers "yes" to `@Async`, which revives `:324`'s `TaskDecorator` but none of the batch items.
- `SLAS:87-91` **External Integrations** — outbound call logging, successful/failed response logging, non-HTTP
  connection logging. No outbound HTTP (`Q:171-189` vacuous) and no SFTP/broker. `:91` survives *only* if Q10
  answers "yes" for the database connection.
- `SLAS:100-102` **Downstream System Failures** — retryable and permanent downstream errors. No downstream system.
- `SLAS:110` **Feature flags** and `SLAS:111` **dynamic configuration** logging — no feature-flag or
  dynamic-configuration mechanism in the PRD.
- `SLAS:197` circuit breakers / graceful degradation for external resources, and `Q:185-189`'s circuit-breaker state
  logging — nothing external to break.
- `SLAS:322` (Enforced Constraint) auto-configured HTTP client builders for trace propagation — no outbound calls,
  so there is no client to configure and no `traceparent` to inject. Likewise `SLAS:355` ("Correlation ID is
  injected into outbound requests and propagates across service boundaries") has no boundary to cross.
- `SLAS:58` and `SLAS:240-241` **MFA logging** (enrol/remove, factor type, OTP/TOTP/backup-code prohibitions) — MFA
  is a PRD exclusion per the map; `Q:47`/`Q:54-57` correspondingly vacuous.
- `SLAS:246` **Data exports and bulk data access** — partly vacuous: there is no report or CSV export feature. It
  revives only as the admin user-list read, which is Q8.
- `SLAS:247` **Significant business decisions** (approvals, credit decisions, transaction authorisations) — no such
  domain; see Q6.
- `SLAS:334` anti-pattern "Logging inside per-item loops in batch processing" — no batch. (`SLAS:220`'s general
  nested-loop rule still binds.)

**Whole sections of `LS`:**
- `LS:146-156` **Trigger** (`trigger.id`, `trigger.type`, `trigger.by*`, `trigger.cron.*`) — nothing triggers
  anything; no cron.
- `LS:160-177` **Batch** (all 14 `batch.*` fields) — no jobs, steps or chunks.
- `LS:181-235` **Interface** — `interface.system`/`type`/`direction` and the File subsection (`LS:210-235`,
  21 `file.*` fields incl. hashes, code signatures, ACK files). No file interface, no partner system. *Partial
  exception*: the HTTP subsection `LS:189-208` is scoped "when `interface.type = api`" for **outbound** calls, but
  `SLAS:358` requires inbound request logs to carry method, path, status code and duration. **I infer** those four
  are emitted using `http.request.method` (`LS:198`), `url.path` (`LS:195`), `http.response.status_code` (`LS:203`)
  and `event.duration_ms` (`LS:142`), with `interface.direction: inbound` (`LS:187`) if the interface block is used
  at all. Everything else in `LS:189-208` — byte counts, idempotency keys, record counts, pagination tokens,
  `http.rate_limit.status` — has no producer here. `url.full` (`LS:193`) and `url.query` (`LS:196`) are additionally
  barred by `SLAS:231`.
- `LS:239-256` **File Summary** (all 14 fields) — no files.
- `LS:260-268` **Record Summary** (`record.*`) — no record-batch processing.
- `LS:272-291` **File Management** (`file.status`, scanner verdict/UUID, ingestion status, storage quota) — the File
  Management Application Standard is out of scope per the map.
- `LS:310-316` **Report** (`report.classification`, `report.sensitivity`, `report.fields_accessed`) — the Report
  standard is out of scope per the map.
- `LS:320-331` **MCC Shared Authentication** (OAuth2 client registration, token URI, key rotation, JWKS) — no
  machine-to-machine auth; `Appfw-Mcc` out of scope.
- `LS:335-341` **MCNS Notification** (`reference.id`, `tag.id`, `sender.id`) — no MCNS; email is a logging stub.
- `LS:345-351` **MPDS Personnel Retrieval** (`query.template.id`, `query.cardinality`, `query.count`) — no MPDS.
- `LS:122-125` `service.connection*` — vacuous unless Q10 is answered "yes".

**In `CSLE`:** the `service_system`/`service_subsystem`/`service_connection_*` mappings (`CSLE:121-124`) are
vacuous unless `service.system`/`subsystem` are adopted (`LS:118-119` — both optional; `LS:119` says set subsystem
equal to system if none exists). The generic regex handles them at no cost, so **I infer** the encoder should
keep them even though nothing emits them today.

**In the Questions file:** Q9 (`:164-189`), Q11 (`:213-234`), Q12 (`:238-263`) are answered "No" and their entire
requirement blocks fall away. Q3's MFA branch (`:47`, `:54-57`) and API-key branch (`:49`, `:64`) likewise.

---

## 7. What this file hands to ticket 12

The audit and logging contract needs, from here: the always-required field list (§1.1), the `event.*` value
mapping (§1.3) subject to **QX3**, the level assignments (§2), the prohibition list (§3), the ERROR-event field
triple `error_code`/`error_category`/`error_follow_up_action` plus `.setCause(e)` (§4.3), the
`error.category`→HTTP-status table (`SLAS:173-182`) which also feeds ticket 08's error contract, and the resolution
of **QX1** (may `user.name` be logged) and **Q5b** (what identifies a pre-auth failure) before any audit-event
schema can be typed.
