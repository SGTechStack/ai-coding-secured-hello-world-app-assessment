# App Standard: Structured Logging

## 1. Overview

### Purpose
This guide provides foundational principles for implementing effective application logging. Its core purpose is to help developers create logs that are structured, secure, and consistent, making them invaluable for operational monitoring, rapid troubleshooting, and day-to-day analysis.

### Scope
This guide applies to backend application logging across the following areas:
-   **Application lifecycle:** Startup and shutdown events
-   **API request handling:** Inbound HTTP request and response logging, correlation ID propagation
-   **Authentication and authorisation:** Login success and failure, logout, and access control events
-   **Business logic execution:** Key decisions, state changes, and conditional logic branches (e.g., feature flags, dynamic configuration)
-   **Data store interactions:** Significant data events; query logging is excluded from production
-   **Asynchronous operations:** Scheduled jobs, batch processing, message queue consumers, and background tasks
-   **External integrations:** Outbound HTTP calls and non-HTTP connections (e.g., SFTP, message brokers)
-   **Failure handling:** Business validation errors, downstream system failures, and unexpected exceptions
-   **Audit and security:** Privileged operations, sensitive data access, and security control events

It covers what to log, how to structure logs for operational use, and what must never be logged.

### Definitions
-   **Structured Logging:** Formatting log entries as machine-readable key-value pairs, not unstructured text, for effective searching and filtering
-   **Correlated Logging:** Tracing a single operation across services using a unique identifier, the correlation ID, for end-to-end visibility
-   **Contextual Logging:** Enriching logs with relevant data (e.g., business process ID) to enhance troubleshooting context
-   **Mapped Diagnostic Context (MDC):** A per-thread key-value store whose entries are automatically included in every log entry written on that thread. Application code writes values into MDC, which the logging framework reads and includes in each log entry
-   **Micrometer Tracing:** A tracing facade that creates and manages spans, and automatically writes `traceId` and `spanId` to MDC when a span is active, so trace context appears in log output without additional code
-   **Secure Logging:** Protecting user privacy and system security by never logging sensitive data (e.g., passwords, PII)
-   **Audit Logging:** A dedicated record of security-relevant and compliance-relevant events, such as authentication, privileged operations, and sensitive data access, used to support forensic investigation and regulatory requirements
-   **Log Injection:** An attack (CWE-117) where an adversary embeds control characters or forged entries into user input to corrupt log structure or mislead investigation. Prevented by encoding and sanitizing untrusted input before logging
-   **Consistent Schema:** Using standardised field names across applications to simplify cross-service analysis and dashboarding
-   **Actionable Logs:** Logging meaningful events, errors, and metrics to aid in diagnosing and resolving production issues

---

## 2. Standard Flow

### Normal Execution (Happy Path)
The goal of the normal execution flow is to produce a clear, sequential log that traces an operation's full lifecycle. This is achieved by assigning a unique identifier, the correlation ID, at the start of the operation and attaching it to every log entry so that the complete sequence of events can be traced across all stages and services. Logs should include only the minimum IDs required for tracing and operations, and must exclude request/response bodies, sensitive headers, and raw personal data, including on failure paths.

#### 0. Application Startup
Log a startup event when the application is ready to serve traffic. Include the following so operators can confirm the correct version and configuration are running without inspecting deployment artifacts:
-   **Host information:** The IP address and hostname of the machine running the application. This identifies which instance produced the log in distributed and containerised environments
-   **Active profiles:** The names of all active Spring profiles (e.g., `prod`, `feature-x`). This confirms which environment configuration is active
-   **Service metadata:** The service name, version number, and deployment environment (e.g., `production`, `staging`). These fields identify the exact build and deployment context for every log entry produced by this instance
-   **Key configuration values:** A minimal set of non-secret values needed to confirm the deployment is configured correctly, such as the active scheduler timezone or a feature flag name and its enabled state. Apply the principle of minimum necessary: log only what operators need to verify correct startup, not a dump of all configuration. Never log credentials, connection strings (e.g., `spring.datasource.url`), database names, timeout or retry values for authentication flows, or any secrets

#### 1. API Request Handling
-   **On Request Start:** 
    -   **Propagate Correlation ID:** If an incoming request includes a correlation ID from an upstream service, propagate it to maintain a consistent trace across services
    -   **Generate Correlation ID:** If no correlation ID is present in the request, generate a new unique identifier to start the trace for this operation
    -   **Log Request Metadata:** Log the start of the request with key details (e.g., HTTP method, path). Do not log the client IP address, query parameters, authentication headers, or request body as they may contain personal data or sensitive information
-   **On Request End:** Log request completion for both success and failure, including at minimum the HTTP status code, total duration, and an outcome field, and clear the correlation context afterward

#### 2. Authentication and Authorisation
-   **On Authentication Success:** After a user's identity is successfully verified, log the event. The log should include `user.id` UUID (never the raw username, email, or credential), the authentication method, and the MFA factor used when applicable (e.g., `totp`, `sms-otp`, `hardware-token`)
-   **On Authentication Failure:** Log the failure at `WARN` level with a generic reason (e.g., "Authentication failed"). Never log the internal exception message, the submitted credential, or a reason that reveals whether the account exists
-   **On MFA Setup:** When a user enrols or removes an MFA factor, log the event. Include `user.id` UUID, the MFA factor type, and the outcome (e.g., `enrolled`, `removed`). Never log OTP codes, TOTP secrets, backup codes, or recovery phrases
-   **On Logout:** Log the logout event with the user identifier so the end of the session is traceable
-   **On Authorisation Success:** When a user is confirmed to have the necessary permissions for an operation, log the event for operations that access or modify sensitive data, cross privilege boundaries, or are required for audit (e.g., admin actions, data exports, permission changes)
-   **On Authorisation Failure:** When a user is denied access to a resource or operation, log the event with `user.id` UUID and a generic reason. Never expose internal permission logic or role details in the log message

#### 3. Business Logic Execution
-   **Log Key Decisions:** When business logic makes a significant decision (e.g., choosing a specific calculation strategy), log the outcome and the reason
-   **Log State Changes:** When a core business entity changes state (e.g., `Order status changed from PENDING to FULFILLED`), log the event, including the entity ID, the previous state, and the new state

#### 4. Data Store Interactions
-   **Do Not Log Queries:** Do not log SQL statements or query payloads in production. For diagnostics, use database audit tooling or metrics, or enable ORM/JPA SQL logging only in non-production environments with parameter values redacted
-   **Log Significant Data Events:** Log when significant data operations are successfully completed, including the creation, update, and deletion of core business entities, along with the entity ID (e.g., `New user account created with user UUID: <uuid>`)

#### 5. Asynchronous Operations
Asynchronous operations include background jobs, scheduled tasks, message queue consumers, event handlers, and batch processing:
-   **Context Propagation:** When a task is enqueued or dispatched, propagate the current correlation ID in the task context or message headers
-   **Dispatch Log:** On enqueue, log the task name, the unique identifier assigned to the task or message (used to trace it if processing fails), and the target queue or topic name. If the task is deferred, also log the scheduled execution time so operators can confirm when it is expected to run
-   **Task Lifecycle:** Log task start and completion with task name, task ID, outcome, duration, and correlation ID when available
-   **Acknowledge Outcome:** Log whether the message was acknowledged or committed, or negatively acknowledged on failure
-   **Dead Lettering:** When a message is routed to a dead-letter queue or quarantine, log the reason, the final attempt count, and all error fields

For **scheduled batch jobs** specifically:
-   **Log schedules at startup:** Log each job's name, cron expression, human-readable schedule description, and timezone at application startup so the intended schedule is visible without inspecting configuration
-   **Log job identity before execution:** Include the job ID, name, status, and retry attempt count before the job runs. Log input parameter names but not their values if the values may contain personal data or secrets (e.g., customer IDs, file paths). Set these in MDC so they appear automatically on all log entries for that execution
-   **Log job completion:** After the job finishes, log the outcome and duration. On failure, also log all error fields
-   **Log step item counts on completion:** At the end of each step, log the item read count, write count, read skip count, process skip count, write skip count, and rollback count. These are the primary signal for diagnosing data processing failures
-   **Log file metadata when processing files:** Include file name, size, creation time, and the outcome of any integrity checks (hash validation, code signature validation) when a step processes a file
-   **Clean up MDC after each execution:** Remove all job and step MDC fields in a `finally` block. Job context must not leak into the next scheduled execution

#### 6. External Integrations
-   **Log Outbound Calls:** Before making a call to an external service, log the outgoing call, including the target base URL and path, HTTP method, and start time. Do not log URL query parameters as they may contain tokens or sensitive data. Inject the correlation ID into the outbound request
-   **Log Successful Responses:** Upon receiving a successful response from the external service, log the completion of the integration step including the HTTP status code and duration
-   **Log Failed Responses:** On failure, log the duration and all error fields. Do not log response bodies that may contain sensitive data
-   **Log Network Connections:** For non-HTTP integrations (e.g., SFTP, message broker, database), log the outcome and duration after a connection is established. On failure, also log all error fields. Do not include the host, URL, database name, or credentials in the log entry

### Failure Paths and Fallbacks
Failures should be logged at the point where they are handled (e.g., a `catch` block) to avoid redundant error logging. Failure logs should include the reason and any non-sensitive IDs where available.

#### 1. Business and Validation Errors
-   **Rejection:** Log at `WARN` when the failure is not yet actionable or can be safely skipped (e.g., a retry attempt that has not yet hit the maximum limit). Log at `ERROR` when the rejection is definitive and non-retryable (e.g., a missing required field), as no further recovery is possible. Include the reason for rejection in either case
-   **Business Rule Failure:** If an operation fails due to a business rule violation (e.g., insufficient funds), an `ERROR` level log should be generated detailing the failure

#### 2. Downstream System Failures
-   **Retryable Errors:** If a downstream service is unavailable (e.g., a connection timeout), an `ERROR` log should be generated. The log should indicate whether a retry will be attempted. Each retry attempt should log the attempt number and reason
-   **Permanent Errors:** If a downstream service returns a definitive failure (e.g., a `400 Bad Request`), an `ERROR` log should be generated, and no retries should be performed

#### 3. Internal System Failures
-   **Unexpected Exceptions:** Any uncaught or unexpected exceptions (e.g., `NullPointerException`) should be logged at an `ERROR` level at the global exception handling boundary. The log must include all error fields

### Conditional Logic and Decision Points
Logging should be used to record why the application took a certain path when conditional logic is evaluated.

-   **Feature Flags:** Log feature flag state at startup or when the flag value changes, not on every evaluation. A per-request evaluation log generates one entry per request and will flood the log under load
-   **Dynamic Configuration:** If the application's behavior changes based on dynamic configuration (e.g., a different processing threshold), log the chosen configuration value provided it is not sensitive (e.g., not a secret, token, or connection string)
-   **Decision Points:** For significant business logic branches (e.g., processing a "premium" vs. "standard" user), a log entry should indicate which path was executed and why

---

## 3. Best Practices

Each contract below defines required practices for application logging.

### 3.1 Inputs / Outputs
Log fields, their canonical definitions, allowed values, and ECS auto-population behaviour are specified in [Log Schema](Log_Schema.md). The lists below identify which fields are required for every event and which apply contextually.

Required fields (always present in every log event):
- `@timestamp`, `log.level`, `message` — auto-populated by the ECS formatter; do not set manually
- `service.name`, `service.version`, `service.environment` — auto-populated from `application.yaml` configuration; do not set at log time
- `trace.id`, `span.id` — auto-populated by Micrometer Tracing when a span is active; do not set manually
- `thread.name`, `logger.name` — auto-populated by the ECS formatter; do not set manually
- Each event must include enough metadata to reconstruct what happened, including when, where, who, what, and the outcome (success or failure) when applicable
- Limit MDC to a small set of fixed, known-safe fields such as `trace.id`, `span.id`, and job or request IDs. Avoid storing domain objects, collections, or sensitive data in MDC

Contextual fields (include when applicable):
- `correlation.id` — end-to-end business process correlation when a single `trace.id` does not span the full workflow (e.g., a process that triggers multiple independent traces across time or systems)
- `host.name`, `host.ip` — required for startup events; identifies the instance in distributed and containerised environments
- `user.id` — authenticated user context (UUID). Never log raw user identifiers such as usernames or email addresses
- `event.kind`, `event.category`, `event.type`, `event.action`, `event.outcome` — event classification and result; see [Log Schema — Event](Log_Schema.md#event) for allowed values
- `event.start`, `event.end`, `event.duration_ms` — timing for significant operations; log on `*-end` events
- Batch job fields (`batch.*`, `trigger.*`) — required for batch job and step lifecycle events; see [Log Schema — Batch](Log_Schema.md#batch) and [Log Schema — Trigger](Log_Schema.md#trigger)
- Interface fields (`interface.*`, `http.*`, `url.*`, `file.*`) — required for outbound API calls and file-based integrations; see [Log Schema — Interface](Log_Schema.md#interface)

Field validation rules:
- `@timestamp` MUST conform to ISO-8601 / RFC-3339 format (e.g., `2026-01-22T14:07:51.080+08:00`)
- `log.level` MUST be one of: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`

<note>

> **Note:** Spring Boot's default console logging already includes timestamp, level, logger name, thread name, and message in text form. Structured JSON fields and fields such as `service.name`, `service.version`, `service.environment`, `trace.id`, and `span.id` require explicit configuration (e.g., ECS JSON layout and tracing).

</note>

<note>

> **Note:** The valid `log.level` values are Logback-specific. Logback does not support `FATAL`. Any statement logged at `FATAL` is automatically mapped to `ERROR`.

</note>

Example default console log:
```text
2026-01-22T14:07:51.080Z  INFO 88932 --- [myapp] [           main] o.s.b.d.f.logexample.MyApplication       : Starting MyApplication using Java 25.0.2 with PID 88932 (/opt/apps/myapp.jar started by myuser in /opt/apps/)
2026-01-22T14:07:51.093Z  INFO 88932 --- [myapp] [           main] o.s.b.d.f.logexample.MyApplication       : No active profile set, falling back to 1 default profile: "default"
```

Log emission requirements:
- Emit newline‑delimited JSON to standard output with one event per line
- If required fields are absent, the application MUST still emit the event and MUST NOT suppress or drop it silently  
- Use UTF‑8 encoding  
- Use ISO‑8601 / RFC‑3339 timestamps with timezone  
- Standardise on Singapore Time Zone (UTC+8)  
- Synchronise time sources across services (e.g., Network Time Protocol, cloud time sync services such as AWS) to ensure consistent timestamps  
- Propagate and log trace context (W3C Trace Context) when available  
- Log duration for significant operations

### 3.2 Error Contract
Failure categories align with the `error.category` schema field:
| `error.category` | HTTP Status | Notes |
|---|---|---|
| `server` | 503 | Server unavailable or service down |
| `network` | 503 | Network unavailable or timeouts |
| `cert/auth` | 401 / 403 | Certificate or authentication failures |
| `database` | 500 | Database connection failures |
| `application` | 500 | Logic errors or unexpected exceptions |
| `data` | 400 | Invalid, missing, or inconsistent data |
| `others` | 502 / 503 | Resource and capacity failures (e.g., rate limiting, quota exceeded), or other dependency failures |

<note>

> **Note:** For `ERROR` events, set `error_code` and `error_category` from the defined enum values, set `error_follow_up_action` to indicate whether the error requires follow-up, and pass the exception to `.setCause(e)` to auto-populate `error.type`, `error.message`, and `error.stack_trace`. See [Log Schema — Error](Log_Schema.md#error) for the full error field definitions.
> 
> These fields use underscores (e.g., `error_code` rather than `error.code`) because Spring Boot's ECS formatter pre-seals the `error` object. Attempting to set dotted keys directly causes a JSON writing error. A custom encoder intercepts the underscore keys and maps them to their proper nested schema fields. For details, see Custom Structured Log Encoder.

</note>

Error handling requirements:
- Use consistent exception handling across the codebase, with a global error handler that catches unhandled exceptions and logs each error once  
- Include sufficient context in error logs to support investigation, including who, what, where, when, and outcome  
- Return generic error messages for unexpected or security‑sensitive failures without exposing internal details. For authentication, return a consistent response regardless of account existence or credential validity to prevent user enumeration  
- Fail securely and stop processing when validation or security controls fail. Do not continue with partial or invalid state  
- Handle external resource failures securely by applying timeouts and using circuit breakers that stop repeated calls to failing dependencies, or by using graceful degradation  
- Follow [Failure Paths and Fallbacks](#failure-paths-and-fallbacks) for retry behavior
- Sanitize error context before logging, as exception messages and stack traces may expose sensitive internals such as SQL statements, internal file paths, or business logic  
- Classify errors for alerting so only actionable failures trigger alerts  

### 3.3 Logging Contract
Log levels and conditions:
- **INFO**: normal milestones and outcomes  
- **WARN**: unexpected or degraded conditions the application handled without failing (e.g., a retry that succeeded, a fallback that triggered). Requires monitoring but not immediate intervention  
- **ERROR**: operations that failed and could not recover  
- **DEBUG**: temporary diagnostics, not for permanent production use  
- **TRACE**: deep internal detail, short term only  
<note>

> **Note:** Logback does not have a `FATAL` level. Any log statement made at `FATAL` is automatically mapped to `ERROR`. Use `ERROR` for all critical failures.

</note>

- Do not log the same event at multiple levels  
- Avoid enabling DEBUG or TRACE permanently in production  
- Changes to log levels must not suppress required security or audit events  
- Do not suppress log entries for security or audit events  
- Write log messages as static, human-readable descriptions of the event. Dynamic values (IDs, codes, durations) belong in key-value fields, not embedded in the message string  
- Do not log inside nested loops without rate limiting. Logging on every iteration multiplies log volume by the loop cardinality and can saturate the log pipeline under production load. Log a summary after the loop completes, or apply a counter-based gate (e.g., log every Nth iteration) if per-item visibility is required

What must **NOT** be logged:
- Passwords, credentials, payment details, OTP codes, TOTP secrets, backup codes, recovery phrases, or other secrets  
- Session IDs or access tokens in raw form  
- Encryption keys  
- Authentication headers  
- Personal data and sensitive IDs  
- Database connection strings, database names, and schema IDs  
- Dependency versions of libraries or frameworks  
- Client IP addresses  
- URL query parameters  
- Request and response bodies, as they may contain personal data or sensitive information  
- Application source code or proprietary business information  
- Data that exceeds the logging system’s approved classification  
- Data collected without required consent or legal basis, strictly adhering to regulatory compliance frameworks (e.g., GDPR, CCPA, HIPAA, PCI-DSS)  
- If any sensitive value must be logged for correlation, mask or hash it  

### 3.4 Audit Contract
Audit logs must record the following:
- Authentication operations, including success and failure, the authentication method (e.g., `password`, `sso`, `api-key`), and the multi-factor authentication (MFA) factor used when applicable (e.g., `totp`, `sms-otp`, `hardware-token`)  
- MFA factor changes, including enrolment and removal of any MFA factor (e.g., `totp`, `sms-otp`, `hardware-token`). Never log OTP codes, TOTP secrets, backup codes, or recovery phrases  
- Credential changes, including password changes, password resets, and API key rotation  
- Authorisation failures and access to sensitive data  
- Privileged and admin operations, including user account creation, deletion, and lockout, and role or permission changes  
- Modifications to sensitive data entities, including create, update, and delete operations on critical business records (e.g., financial records, user profiles, contracts)  
- Data exports and bulk data access, including report downloads, bulk queries, and any operation that extracts a large volume of records  
- Significant business decisions, including approvals, rejections, credit decisions, and large transaction authorisations  
- Security control bypass attempts, including validation, business logic, and anti‑automation  
- Repeated input validation failures, which may indicate brute force or automated attack attempts  
- Session management failures  
- Critical configuration changes  
- Application startup and shutdown  
- Unexpected errors and security control failures

Each audit event should include enough context to answer: who performed the action (`user.id` UUID), what action was performed, what resource was affected, when it occurred, and whether it succeeded or failed. For failures, include a generic reason without exposing internal detail.

Audit retention and integrity requirements:
- Retain logs based on organisational policy to support delayed forensic analysis  
- Ensure audit trails use integrity controls that prevent tampering or deletion  
- Configure rotation for local file logging  
- Monitor audit logs for suspicious activity and ensure alerts are actionable  
- Alert when audit logging fails. If audit events cannot be written (e.g., collector unavailable, disk full), raise an alert immediately rather than silently continuing. Operations that occurred without an audit record are a compliance risk  

### 3.5 Security Contract
To ensure log security, apply controls in the following areas:

- Access and integrity:
    - Store logs securely and restrict access to least‑privilege roles  
    - Protect logs at rest from tampering or deletion (e.g., using Write-Once-Read-Many (WORM) storage or cryptographic signatures for audit trails)  
    - Secure logging configurations and never hardcode credentials, API keys, or certificates used for log transmission  

- Storage and transmission:
    - Send logs only to approved logging systems  
    - Transmit logs over secure channels to the centralised logging system with separate access controls  
    - Implement local log buffering by writing logs to a durable local disk (e.g., a rolling JSON file) for a forwarding agent to transmit to a centralised logging server (e.g., Splunk, ELK, Datadog). Avoid sending logs directly over the network from the application to prevent losing critical audit trails during outages

- Data handling and correlation:
    - When identifying a user, prefer `user.id` UUID. Never log raw usernames or emails. Hashed PII is permitted only if secured via a system-wide salt or HMAC  
    - Encode and sanitize untrusted user input (e.g., stripping CRLF characters) to prevent log injection, log forging (CWE-117), and exploits targeting log viewing dashboards  
    - Enforce automated data masking or redaction at the application logging boundary as a defense‑in‑depth measure against accidental sensitive data leaks (see [What must NOT be logged](#33-logging-contract) in section 3.3)

- Retention and governance:
    - Enforce retention requirements defined in the Audit Contract  
    - Maintain a log inventory that documents what is logged, where it is stored, how it is used, how access is controlled, and how long logs are kept  
<note>

> **Note:** Log retention TTL is enforced by the centralised log management platform (e.g., Splunk, ELK index lifecycle policy), not by the application. Integrators must configure the applicable retention policy on the log management platform.

</note>

- Standards and formats:
    - Use standard formats when present, such as [RFC 3339](https://www.rfc-editor.org/rfc/rfc3339) timestamps and [W3C Trace Context](https://www.w3.org/TR/trace-context/)  
    - Emit logs in the agreed structured format (e.g., ECS JSON) so that automated masking, redaction, and pipeline parsing apply consistently. Unstructured or mixed-format log output bypasses these controls  

---

## 4. Architectural Design
Spring Boot platform capabilities:
- **[Design Choice]** Default logging via Logback when using Spring Boot starters, with console output by default  
- **[Design Choice]** Structured logging support with JSON formats such as ECS, GELF, and Logstash via `logging.structured.format.console` or `logging.structured.format.file`  
- **[Design Choice]** Structured formats include MDC key‑value pairs, and additional fields can be added with SLF4J key‑value logging  
- **[Design Choice]** Correlation IDs in logs when Micrometer Tracing is on the classpath. The default correlation uses `traceId` and `spanId` and can be customised with `logging.pattern.correlation`  
- **[Design Choice]** Auto‑configured HTTP client builders that propagate trace context (`RestTemplateBuilder`, `RestClient.Builder`, `WebClient.Builder`)  
- **[Design Choice]** Local file output and rolling rotation via `logging.file.name` and `logging.logback.rollingpolicy.*`, providing the durable local disk buffer required for log forwarding agents  
- **[Design Choice]** Spring Boot Actuator (`/actuator/loggers`) provides endpoints to inspect and modify log levels at runtime without application restarts
<note>

> **Note:** Micrometer Tracing is the Spring Boot-native tracing abstraction. It auto-configures with Spring Boot's structured logging pipeline, writes `traceId` and `spanId` to MDC automatically, and integrates with Spring Boot's HTTP client builders for context propagation. When OTel export is required (e.g., to an OTel Collector or a vendor backend), add `micrometer-tracing-bridge-otel` and the relevant OTel exporter — the application code remains unchanged. Use the OTel SDK directly only when the platform mandates OTel as the telemetry standard across polyglot services, or when OTel-specific features are needed that Micrometer does not expose (e.g., OTel semantic conventions, OTel log bridge, or OTel baggage propagation). For detailed guidance on how OpenTelemetry and Micrometer Tracing relate, see [Understanding OpenTelemetry and Micrometer Tracing](Recipes/Understanding_OpenTelemetry_And_Micrometer_Tracing.md).

</note>

<note>

> **Note:** Micrometer Context Propagation (when on the classpath) propagates trace and span IDs across `@Async` thread boundaries, but does not propagate application-defined MDC fields beyond trace context. A `TaskDecorator` is required to carry those fields across async boundaries.

</note>

Design decisions:
- **[Enforced Constraint]** **[[Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)]** Enable the agreed structured logging format. For Spring Boot's native structured logging (Spring Boot 3.4+), set `logging.structured.format.console` or `logging.structured.format.file` to the agreed format (e.g., `ecs`) and configure format-specific settings via `logging.structured.ecs.*` properties. When using a third-party encoder such as `logback-ecs-encoder`, declare the encoder in a custom Logback configuration file and configure it via Logback XML properties — not via Spring Boot application properties  
- **[Enforced Constraint]** **[[Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)]** When using a custom `logback-spring.xml`, reference the `CONSOLE_LOG_STRUCTURED_FORMAT` and `FILE_LOG_STRUCTURED_FORMAT` environment variables to select the encoder rather than hardcoding it in the XML. This allows the output format (e.g., plain text vs. ECS JSON) to be switched at deployment by changing an environment variable without editing the configuration file  
- **[Enforced Constraint]** **[[Spring Boot Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html), [W3C Trace Context](https://www.w3.org/TR/trace-context/)]** Add Micrometer Tracing to the classpath so that `traceId` and `spanId` are written to MDC automatically whenever a span is active. Customise the correlation pattern if needed with `logging.pattern.correlation` for text output  
- **[Enforced Constraint]** **[[Spring Boot Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)]** Use Spring Boot's auto-configured HTTP client builders (`RestTemplateBuilder`, `RestClient.Builder`, `WebClient.Builder`) to make outbound calls rather than constructing clients manually (e.g., `new RestTemplate()`). Manually constructed clients do not have the tracing interceptors applied and will not propagate trace context headers (e.g., `traceparent`) to the receiving service  
- **[Enforced Constraint]** **[[Spring Framework Reference](https://docs.spring.io/spring-framework/reference/)]** Register an MDC filter or interceptor to extract application-defined fields from each incoming request, such as a custom correlation ID from a request header (e.g., `X-Correlation-ID`) and the `user.id` UUID after authentication, and write them into MDC. Clear all MDC fields in a `finally` block at the end of the request. Trace and span IDs are written to MDC automatically by Micrometer Tracing and do not require a filter  
- **[Enforced Constraint]** **[[Micrometer Reference](https://docs.micrometer.io/micrometer/reference/)]** For `@Async` methods, register a `TaskDecorator` that copies and restores the full MDC map around task execution to preserve all application-defined MDC fields across async thread boundaries  
- **[Enforced Constraint]** **[[OWASP Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html)]** Implement a global exception handler to catch and log unexpected, unhandled exceptions once at the boundary. Business-specific exceptions should be logged at the point they are handled in the business logic, not delegated to the global handler  
- **[Enforced Constraint]** **[[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html), [OWASP ASVS v5.0 V16](https://cornucopia.owasp.org/taxonomy/asvs-5.0/16-security-logging-and-error-handling/)]** Apply masking rules for sensitive fields at the application logging boundary — for example, using a custom Logback converter, a `PatternLayout` masking rule, or a structured log encoder extension. Fields to mask include passwords, tokens, secrets, Primary Account Numbers (PAN), Social Security Numbers (SSN), and user identifiers used for correlation  
- **[Enforced Constraint]** **[[OWASP ASVS v5.0 V16](https://cornucopia.owasp.org/taxonomy/asvs-5.0/16-security-logging-and-error-handling/), [NIST SP 800-92](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-92.pdf)]** Separate critical audit logs from standard application logs by routing them to a dedicated appender and log destination (e.g., a separate file or log stream). This allows retention periods, access controls, and alerting rules to be applied independently for audit events

Anti‑patterns to avoid:
- **[Enforced Constraint]** **[[SLF4J Manual](https://www.slf4j.org/manual.html)]** Using standard output streams (`System.out.println`) or direct stack trace prints (`e.printStackTrace()`) instead of the logging framework
- **[Enforced Constraint]** **[[SLF4J Manual](https://www.slf4j.org/manual.html)]** Using string concatenation in log statements instead of parameterized formats or structured key-value additions
- **[Enforced Constraint]** **[[Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)]** Mixing plain text with JSON logs in the same stream  
- **[Enforced Constraint]** **[[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)]** Logging on every method entry and exit. This generates high volume with low signal value, makes meaningful events harder to identify, and can overwhelm the log pipeline under load. Use DEBUG or TRACE only for targeted, temporary diagnostics
- **[Enforced Constraint]** **[[Spring Batch Reference](https://docs.spring.io/spring-batch/reference/)]** Logging inside per‑item loops in batch processing  
- **[Enforced Constraint]** **[[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)]** Logging large payloads or binary blobs  
- **[Enforced Constraint]** **[[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)]** Passing domain objects directly as log arguments via `toString()`. Extract only the specific, non-sensitive fields needed for the log entry
- **[Enforced Constraint]** **[[OWASP Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html)]** Logging and rethrowing the same exception at multiple levels of the call stack. Log each error once at the handling boundary to avoid duplicate error entries
- **[Enforced Constraint]** **[[OWASP Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html)]** Catching and silently discarding exceptions without logging. Silent failures leave no trace, making incidents undetectable and impossible to diagnose
- **[Enforced Constraint]** **[[SLF4J Manual](https://www.slf4j.org/manual.html)]** Not clearing MDC after a request or task completes. In thread-pooled environments, MDC values set for one request will leak into the next request handled by the same thread. Always clear MDC in a `finally` block

## 5. Test & Validation Standard

Before release verify that:

Log structure and format:
- Log output is structured and parsable by the log pipeline  
- Required fields are present in all log events  
- Log timestamps are consistent with system time and time synchronisation  
- Startup log includes required service metadata, active profiles, and host information with no secrets present  

Context propagation:
- Trace and span IDs propagate across threads and async boundaries  
- Application-defined MDC fields (e.g., correlation ID, user context) propagate across `@Async` boundaries  
- MDC is set for the request lifecycle and cleared after completion  
- Correlation ID is injected into outbound requests and propagates across service boundaries  

Request handling:
- Request logs include HTTP method, path, status code, duration, and outcome  
- Request logs exclude client IP addresses, query parameters, authentication headers, and request/response bodies  

Error handling:
- Error paths are logged once and include all required error fields  

Audit and security:
- Authentication failure logs use a generic message that does not reveal account existence  
- All audit events defined in the audit contract are logged, including authentication, MFA factor changes, credential changes, authorisation failures, privileged operations, and sensitive data access  
- No sensitive data appears in logs (tokens, credentials, PII)  
- User identifiers appear only as `user.id` UUID; raw usernames, emails, and credentials are absent  
- Audit events are present in the dedicated audit log destination, not only in the standard application log  
- Log injection attempts do not corrupt output or structure  
- Access controls protect log data from unauthorised access  

Resilience:
- Logging performs under expected load without dropping events  
- Logging failures do not crash the application  
- Logging destination outages or network failures do not break application flow  

Test data guidelines:
- Use deterministic, synthetic IDs (e.g., fixed UUIDs, numeric IDs). Do not use real user data, email addresses, or IP addresses in test scenarios
- Log injection test payloads must be sanitized before asserting on the emitted output. Assert that the sanitized form appears, not the raw payload

---

## 6. Operational Runbook

Logs are the primary signal for production support. The platform handles rotation, forwarding to the central log system, retention, and encryption. Applications ensure correct structure and reliable timestamps.

Operational considerations:
- **Observability**: Correlate logs with metrics and traces where available. Monitor for unexpected spikes in ERROR or WARN volume, repeated authentication failures, and sudden drops in log throughput — these are actionable signals that should trigger alerts  
- **Incident investigation**: Use the correlation ID to retrieve all log entries for a specific request or operation across services. Use `trace.id` to link logs to distributed traces. Filter by `error_code` and `error_category` to scope failures to a specific type or service. The `error_follow_up_action` field indicates whether an error requires follow-up action  
- **Log delivery**: Logging failures must not crash the application and should self-recover. If the centralised log collector, log storage, or network becomes unavailable, logs are buffered to local disk for a forwarding agent to transmit once connectivity is restored. Verify local disk buffering is active during outages and monitor for delivery resumption. Manual action is required only if log delivery remains unavailable after automatic retries  
- **Runtime configuration**: Spring Boot Actuator (`/actuator/loggers`) exposes the ability to inspect and modify log levels at runtime.

<note>

> **Note:** Do not use this to change log levels in production. Log levels must remain as configured to prevent unintended exposure of sensitive debug output or suppression of required audit events.

</note>

---

## 7. References

Log Schema:
- [Log Schema](Log_Schema.md)

Spring Boot:
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot Reference: Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot Reference: Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html)

Spring Framework:
- [Spring Framework Reference](https://docs.spring.io/spring-framework/reference/)

Spring Security:
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)

Spring Batch:
- [Spring Batch Reference](https://docs.spring.io/spring-batch/reference/)

Micrometer:
- [Micrometer Reference](https://docs.micrometer.io/micrometer/reference/)

Logging frameworks:
- [Elastic Common Schema (ECS) Reference](https://www.elastic.co/guide/en/ecs/current/index.html)
- [SLF4J Manual](https://www.slf4j.org/manual.html)
- [Logback Documentation](https://logback.qos.ch/documentation.html)

Further reading:
- [OWASP ASVS v5.0 — Security Logging and Error Handling (V16)](https://cornucopia.owasp.org/taxonomy/asvs-5.0/16-security-logging-and-error-handling/)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html)
- [MITRE CWE-117 — Improper Output Neutralisation for Logs](https://cwe.mitre.org/data/definitions/117.html)
- [RFC 3339 — Date and Time on the Internet: Timestamps](https://www.rfc-editor.org/rfc/rfc3339)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [NIST SP 800‑92 — Guide to Computer Security Log Management](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-92.pdf)
