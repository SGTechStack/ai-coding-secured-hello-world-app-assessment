# Logging Schema for Batch and Interface Applications

This document defines a standardized logging schema tailored for **batch and interface applications**. The primary goal is to produce consistent, machine-readable logs that enable robust **observability**, **automated monitoring**, and **efficient analysis**.

By adhering to this schema, we can:

- **Improve Consistency:** Standardize the log format across all jobs and interfaces, making it simple to search, correlate, and analyze data from different systems.
- **Enhance Observability:** Gain deep insights into operational health by capturing detailed context on job performance, data volumes, and error conditions.
- **Power Dashboards and Alerting:** Produce reliable, structured data perfect for ingestion by logging platforms like Splunk. This allows for the creation of meaningful dashboards, widgets, and automated alerts on key metrics like failure rates, processing times, and data volumes.

### Key Sections

- **Core:** Captures the essential "what" and "when" of every log event.
- **Host & Source:** Pinpoints the exact server or machine where the event occurred, and the source of the request.
- **Tracing/Correlation:** Links related logs to trace a single transaction from end to end.
- **User:** Identifies the user associated with an event without exposing PII.
- **Service:** Provides context about the application and its environment.
- **Event:** Describes the specific action, its outcome, and duration.
- **Trigger:** Shows what initiated the job, whether it was a schedule, a user, or another system.
- **Batch:** Contains specific details for batch job executions, including name, run ID, status, and recovery information.
- **Interface:** Describes interactions with external systems via files or APIs, with specific fields for HTTP and File interfaces.
- **File Summary:** Provides a summary of file-level processing results.
- **Record Summary:** Provides a detailed, structured summary of record processing, with breakdowns by record type including total, success, and failure counts.
- **File Management:** Contains lifecycle-specific fields for services implementing the File Management Application Standard — including file status, display name, MIME type, hash value, scanner diagnostics (verdict, UUID, submission timestamp), record-level ingestion outcome, and standalone storage quota.
- **Error:** Provides detailed diagnostics to accelerate root cause analysis when failures occur.
- **Report:** Contains specific metadata details for generated business reports, including security classification and sensitivity.
- **MCC Shared Authentication:** Contains specific metadata details for machine-to-machine authentication, token acquisition, and key rotation.
- **MCNS Notification:** Contains specific metadata details for MCNS (Multi-Channel Notification Service) sent notifications, including tag, reference, and sender IDs.
- **MPDS Personnel Retrieval:** Contains specific metadata details for personnel data query and retrieval operations.

---

## ECS Auto-Populated Fields — Do Not Set Manually

When using Spring Boot's Elastic Common Schema (ECS) structured logging format, the following fields are automatically populated by the formatter. **Do not set these fields manually via `addKeyValue`**, as this will create duplicate field conflicts or JSON writing errors.

| Field | Auto-populated from | When |
|---|---|---|
| `@timestamp` | Log event instant | Always |
| `message` | Formatted log message | Always |
| `ecs.version` | ECS schema version constant | Always |
| `log.level` | Log event level | Always |
| `log.logger` | Logger name | Always |
| `process.pid` | JVM process ID | Always (if `spring.application.pid` is set) |
| `process.thread.name` | Current thread name | Always |
| `error.type` | `exception.getClass().getName()` | When throwable is present via `.setCause(e)` |
| `error.message` | `exception.getMessage()` | When throwable is present via `.setCause(e)` |
| `error.stack_trace` | Exception stack trace | When throwable is present via `.setCause(e)` |
| `service.name` | `logging.structured.ecs.service.name` or `spring.application.name` | When configured in `application.yaml` |
| `service.version` | `logging.structured.ecs.service.version` or `spring.application.version` | When configured in `application.yaml` |
| `service.environment` | `logging.structured.ecs.service.environment` | When configured in `application.yaml` |
| `service.node.name` | `logging.structured.ecs.service.node-name` | When configured in `application.yaml` |

**Guidelines:**

- **For exceptions:** Always use `.setCause(e)` to capture exception details. Never use `addKeyValue("error.message", ...)`, `addKeyValue("error.type", ...)`, or `addKeyValue("error.stack_trace", ...)`.
- **For service metadata:** Configure static service fields (name, version, environment) in `application.yaml` under `logging.structured.ecs.service.*`. Do not set them at log time via `addKeyValue`.
- **For log/process fields:** Never use `addKeyValue` for `log.*` or `process.*` fields. These are derived from the log event and runtime context.

<note>

> **Note:** Because the ECS formatter pre-seals nested objects (`error`, `service`, `log`, `process`, `ecs`) before processing custom fields, attempting to set nested fields like `addKeyValue("error.code", ...)` will cause a JSON writing error. To add custom fields to these sealed objects, use a workaround key with a non-dot separator (e.g., `error_code`) and implement a custom encoder to remap them. See [Custom Structured Log Encoder](Recipes/Custom_Structured_Log_Encoder.md).

</note>

---

## Core

> Fields marked **★** are auto-populated by the ECS formatter. Do not set them via `addKeyValue`.

| Field Key  | Type     | Description                                                                                                | Example                                  | Allowed Values / Enum                              |
| ---------- | -------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------- | -------------------------------------------------- |
| @timestamp ★ | datetime | Event timestamp in **ISO-8601 format**, including timezone.                                              | `2025-09-04T01:30:45.123Z`               | –                                                  |
| log.level ★  | string   | The severity level of the log, used for filtering and alerting.                                          | `INFO`                                   | `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` |
| message ★    | string   | A concise, human-readable description of the event. Must be sanitized to remove PII or sensitive data.   | `Outbound API request sent successfully` | –                                                  |

---

## Host & Source

| Field Key | Type   | Description                                                            | Example        | Allowed Values / Enum |
| --------- | ------ | ---------------------------------------------------------------------- | -------------- | --------------------- |
| host.name | string | The hostname of the machine where the event occurred.                  | `app-server-1` | –                     |
| host.ip   | string | The IP address of the machine where the event occurred.                | `10.0.0.15`    | –                     |
| source.ip | string | The IP address of the client initiating a network request to the host. | `203.0.113.25` | –                     |

---

## Tracing / Correlation

| Field Key      | Type   | Description                                                                                                                                                                                                                                                                          | Example                                | Allowed Values / Enum |
| -------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------- | --------------------- |
| trace.id       | string | A unique identifier that correlates all events within a single transaction or workflow. For batch jobs, this should be the `batch.job.run.id`.                                                                                                                                       | `4f5a5b5e-3e4a-4c5d-8a7b-6f78901c2d3e` | –                     |
| span.id        | string | A unique identifier for a specific operation or unit of work within a trace, such as a single API call or a database query.                                                                                                                                                          | `a1b2c3d4-e5f6-7890-1234-567890abcdef` | –                     |
| correlation.id | string | A unique identifier to correlate related events within a business process or transaction. Unlike `trace.id` which tracks technical request flows, `correlation.id` provides end-to-end visibility for a business process that may span multiple technical traces, systems, and time. | `txn-abc-123-xyz`                      | –                     |

---

## User

| Field Key  | Type   | Description                                                                                                | Example                                | Allowed Values / Enum |
| ---------- | ------ | ---------------------------------------------------------------------------------------------------------- | -------------------------------------- | --------------------- |
| user.id       | string | The system-generated UUID for the user. Always use `user.id` (UUID) for logging user context — UUIDs are non-PII and directly debuggable. Never log raw usernames or emails (PII) in cleartext. For pre-authentication entry points where the UUID is not yet resolved, omit user identity entirely and rely on `session.hash` and `trace.id` for correlation. | `550e8400-e29b-41d4-a716-446655440000` | –                     |
| user.name     | string | The username or display name. Should only be logged in authentication/authorization contexts where explicitly permitted by policy. | `john.doe`                             | –                     |
| session.hash  | string | A one-way hash (SHA-256) of the raw session identifier, used for correlating pre-authentication log entries where `user.id` is not yet resolved. Never log the raw session ID. | `5d41402abc4b2a76b9719d911017c592`     | –                     |

---

## Service

> Fields marked **★** are auto-populated from `application.yaml` (`logging.structured.ecs.service.*` or `spring.application.*`). Do not set them via `addKeyValue`.

| Field Key                 | Type          | Description                                                                                                                                                                  | Example                                                                                                                     | Allowed Values / Enum                          |
| ------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| service.id                | string        | A unique identifier for a specific running instance of a service.                                                                                                            | `payment-processor-pod-abc12`                                                                                               | –                                              |
| service.name ★            | string        | The name identifying the service or application.                                                                                                                             | `PaymentProcessor`                                                                                                          | –                                              |
| service.system            | string        | The highest-level system or business domain to which the service belongs.                                                                                                    | `EnterpriseFinance`                                                                                                         | –                                              |
| service.subsystem         | string        | A sub-system or component within the `service.system`, which may contain multiple services. If no distinct subsystem exists, set this to the same value as `service.system`. | `InvoiceProcessing`                                                                                                         | –                                              |
| service.version ★         | string        | The version of the service that generated the log.                                                                                                                           | `2.1.5-SNAPSHOT`                                                                                                            | –                                              |
| service.environment ★     | string        | The deployment environment where the service is running.                                                                                                                     | `dev`                                                                                                                       | `dev`, `test`, `staging`, `prod`               |
| service.connection        | array[object] | Details about dependent service connections, typically logged during heartbeat checks (`event.type: connection`). Each object represents a single connection's health.       | `[{"name": "CustomerDB", "type": "database", "status": "up"}, {"name": "ExternalTaxAPI", "type": "api", "status": "down"}]` | –                                              |
| service.connection.name   | string        | The name of the dependent service connection being checked.                                                                                                                  | `CustomerDB`                                                                                                                | –                                              |
| service.connection.type   | string        | The type of the dependent service connection.                                                                                                                                | `database`                                                                                                                  | `api`, `database`, `messaging`, `file-storage` |
| service.connection.status | string        | The status of the dependent service connection heartbeat.                                                                                                                    | `down`                                                                                                                      | `up`, `down`                                   |

---

## Event

| Field Key         | Type          | Description                                                                                                                                                                                     | Example                    | Allowed Values / Enum                                                                                                                                                                                                                                                   |
| ----------------- | ------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| event.kind        | string        | The nature of the log. An `event` is a point-in-time occurrence (e.g., a job starting, an API call being made), while a `state` represents a snapshot of an entity (e.g., connection status).   | `event`                    | `event`, `state`                                                                                                                                                                                                                                                        |
| event.category    | array[string] | The primary category of the event, used for high-level classification.                                                                                                                          | `["batch"]`                | `configuration`, `network`, `database`, `batch`, `interface`, `process`                                                                                                                                                                                                 |
| event.type        | array[string] | A more specific event type that provides context about the lifecycle of the event category.                                                                                                     | `["interface-start"]`      | `access`, `admin`, `allowed`, `change`, `connection`, `creation`, `deletion`, `denied`, `end`, `error`, `group`, `indicator`, `info`, `installation`, `interface-end`, `interface-start`, `job-end`, `job-start`, `protocol`, `start`, `step-end`, `step-start`, `user` |
| event.action      | string        | The specific action being performed during the event.                                                                                                                                           | `file-generation`          | `api-push`, `api-pull`, `file-generation`, `file-generation-retry`, `file-ack-process`, `file-retrieval`, `file-read`, `file-process`, `file-cleanup`, `application-startup`, `application-shutdown`, `user-authentication`, `user-logout`, `user-provisioning`, `user-administration`, `profile-read`, `password-reset`, `password-change-enforcement`, `session-start`, `session-end`, `totp-enrol`, `totp-remove`, `sms-otp-enrol`, `sms-otp-remove`, `hardware-token-enrol`, `hardware-token-remove`, `backup-code-enrol`, `backup-code-remove`, `data-export`, `access-control`, `REPORT_PRE_FILL`, `REPORT_FILL`, `REPORT_EXPORT`, `REPORT_BACKUP`, `ATTEMPTS_EXCEEDED`, `PIN_CREATED`, `TOTP_SETUP`, `ACCESS_DENIED`, `CRITICAL_TRANSACTION`, `OTP_DELIVERY`, `OTP_VERIFICATION`, `KMS_ENCRYPT`, `KMS_DECRYPT`, `NOTIFICATION_SEND`, `NOTIFICATION_SEND_RETRY`, `NOTIFICATION_QUARANTINE` |
| event.outcome     | string        | The result of the event. `partial-success` is used when some records failed (e.g., in a full data load), but the overall process is considered successful and the errors are recoverable.       | `success`                  | `success`, `failure`, `partial-success`                                                                                                                                                                                                                                 |
| event.severity    | string        | The operational severity of the event, which can be used for routing alerts independently of `log.level`.                                                                                       | `high`                     | `low`, `medium`, `high`, `critical`                                                                                                                                                                                                                                     |
| event.reason      | string        | A short, machine-readable string explaining the reason for the `event.outcome`.                                                                                                                 | `Rate limited`             | –                                                                                                                                                                                                                                                                       |
| event.start       | datetime      | The start time of an event, in ISO-8601 format. It should be logged on a `*-start` event.                                                                                                       | `2025-09-04T01:30:45.123Z` | –                                                                                                                                                                                                                                                                       |
| event.end         | datetime      | The end time of an event, in ISO-8601 format. It should be logged on an `*-end` event.                                                                                                          | `2025-09-04T01:30:46.653Z` | –                                                                                                                                                                                                                                                                       |
| event.duration_ms | integer       | The duration of an operation in milliseconds, with its context determined by `event.type`. It should be logged on an `*-end` event to measure the time since the corresponding `*-start` event. | `1530`                     | –                                                                                                                                                                                                                                                                       |

---

## Trigger

| Field Key               | Type          | Description                                                                                                 | Example                                                                                    | Allowed Values / Enum                   |
| ----------------------- | ------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | --------------------------------------- |
| trigger.id              | string        | The unique ID from the master scheduler or orchestrator, used to correlate logs across the entire workflow. | `a1b2c3d4-e5f6-7890-1234-567890abcdef`                                                     | –                                       |
| trigger.type            | array[string] | The mechanism that initiated the run.                                                                       | `["scheduled", "job-dependency"]`                                                          | `scheduled`, `job-dependency`, `ad-hoc` |
| trigger.by              | array[object] | An array of all principals (users, systems, or jobs) that initiated the run.                                | `[{"name":"system-scheduler","type":"system"}, {"name":"upstream-job-name","type":"job"}]` | –                                       |
| trigger.by.name         | string        | The name of the initiator within the `trigger.by` array object.                                             | `system-scheduler`                                                                         | –                                       |
| trigger.by.type         | string        | The type of the initiator within the `trigger.by` array object.                                             | `system`                                                                                   | `system`,`user`,`job`                   |
| trigger.cron.expression | string        | The cron expression used for a scheduled trigger.                                                           | `0 */15 * * * ?`                                                                           | –                                       |
| trigger.cron.timezone   | string        | The timezone in which the cron expression is evaluated (IANA format).                                       | `Asia/Singapore`                                                                           | IANA timezone                           |

---

## Batch

| Field Key                         | Type       | Description                                                                                        | Example                                | Allowed Values / Enum                                                          |
| --------------------------------- | ---------- | -------------------------------------------------------------------------------------------------- | -------------------------------------- | ------------------------------------------------------------------------------ |
| batch.job.id                      | string     | A unique identifier for the job definition, consistent across all runs.                            | `e4b2c3d4-f5a6-7890-1234-567890abcdef` | –                                                                              |
| batch.job.name                    | string     | A human-readable name for the batch job.                                                           | `daily-export`                         | –                                                                              |
| batch.job.run.id                  | string     | A unique identifier for a specific execution of the job. This ID should be used as the `trace.id`. | `4f5a5b5e-3e4a-4c5d-8a7b-6f78901c2d3e` | –                                                                              |
| batch.job.run.parameter           | object/map | Key-value pairs representing the parameters used for this specific job run.                        | `{"date":"2025-09-04"}`                | –                                                                              |
| batch.job.run.retry_count         | integer    | The number of times this specific job run has been retried.                                        | `1`                                    | –                                                                              |
| batch.job.run.recovery.mode       | string     | Indicates the recovery mode if this job run is a recovery attempt for another job.                 | `automatic`                            | `automatic`, `manual`                                                          |
| batch.job.run.recovery.reason     | string     | The reason for initiating this recovery job run.                                                   | `previous-job-failed`                  | –                                                                              |
| batch.job.run.recovery.job.id     | string     | The ID of the original job definition that this job run is recovering.                             | `e4b2c3d4-f5a6-7890-1234-567890abcdef` | –                                                                              |
| batch.job.run.recovery.job.name   | string     | The name of the original job that this job run is recovering.                                      | `daily-export`                         | –                                                                              |
| batch.job.run.recovery.job.run.id | string     | The run ID of the original job execution that this job run is recovering.                          | `4f5a5b5e-3e4a-4c5d-8a7b-6f78901c2d3e` | –                                                                              |
| batch.job.status                  | string     | The current status of the batch job at the time of the log event.                                  | `running`                              | `started`, `running`, `completed-with-error`, `completed`, `failed`, `unknown` |
| batch.step.id                     | string     | A unique identifier for a step within the batch job.                                               | `a1b2c3d4-e5f6-7890-1234-567890abcdef` | –                                                                              |
| batch.step.name                   | string     | A human-readable name for the batch step.                                                          | `load-orders`                          | –                                                                              |
| batch.chunk.id                    | string     | A unique identifier for a specific chunk of records being processed within a step.                 | `f1e2d3c4-b5a6-7890-1234-567890abcdef` | –                                                                              |

---

## Interface

| Field Key           | Type   | Description                                                             | Example      | Allowed Values / Enum |
| ------------------- | ------ | ----------------------------------------------------------------------- | ------------ | --------------------- |
| interface.system    | string | The name of the external system or partner involved in the transaction. | `PartnerCrm` | –                     |
| interface.type      | string | The transport protocol or mechanism used for the interface.             | `file`       | `file`, `api`         |
| interface.direction | string | The direction of the data flow relative to the logging service.         | `inbound`    | `inbound`, `outbound` |

### HTTP (when `interface.type = api`)

| Field Key                    | Type    | Description                                                                                             | Example                                         | Allowed Values / Enum                        |
| ---------------------------- | ------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | -------------------------------------------- |
| url.full                     | string  | The full, unsanitized request URL. Be cautious about logging PII in URL parameters.                     | `https://api.example.com/v1/orders?status=open` | –                                            |
| url.domain                   | string  | The domain of the request URL.                                                                          | `api.example.com`                               | –                                            |
| url.path                     | string  | The path of the request URL.                                                                            | `/v1/orders`                                    | –                                            |
| url.query                    | string  | The query string of the request, excluding the leading `?`. Sanitize to remove sensitive data.          | `status=open`                                   | –                                            |
| http.version                 | string  | The HTTP version used in the request.                                                                   | `1.1`                                           | `1.0`, `1.1`, `2`, `3`                       |
| http.request.method          | string  | The HTTP request method.                                                                                | `POST`                                          | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, ... |
| http.request.bytes           | integer | The total size of the full HTTP request in bytes, including headers and body.                           | `2048`                                          | –                                            |
| http.request.body.bytes      | integer | The size of the HTTP request body in bytes.                                                             | `1536`                                          | –                                            |
| http.request.idempotency_key | string  | A unique key provided by the client to ensure that a request can be retried safely without duplication. | `c7a7c9e8-5a5b-4c4d-8e8f-9a9b9c9d9e9f`          | –                                            |
| http.request.record_count    | integer | The number of logical records included in the request payload.                                          | `500`                                           | –                                            |
| http.response.status_code    | integer | The HTTP status code returned in the response.                                                          | `201`                                           | `100–599`                                    |
| http.response.bytes          | integer | The total size of the full HTTP response in bytes, including headers and body.                          | `512`                                           | –                                            |
| http.response.body.bytes     | integer | The size of the HTTP response body in bytes.                                                            | `256`                                           | –                                            |
| http.response.record_count   | integer | The number of logical records included in the response payload.                                         | `500`                                           | –                                            |
| http.response.next_token     | string  | A pagination token or cursor returned by the server to retrieve the next page of results.               | `eyJhbGciOi...`                                 | –                                            |
| http.rate_limit.status       | string  | The status of a rate limit check, indicating if the request was throttled.                              | `exceeded`                                      | `ok`, `exceeded`                             |

### File (when `interface.type = file`)

| Field Key            | Type    | Description                                                                                   | Example                                           | Allowed Values / Enum |
| -------------------- | ------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------- | --------------------- |
| file.id              | string  | The unique identifier of the file (random UUID).                                               | `550e8400-e29b-41d4-a716-446655440000`            | –                     |
| file.name            | string  | The name of the file being processed, including its extension.                                | `orders_20250904123000_00001.csv`                 | –                     |
| file.extension       | string  | The file extension, without the leading dot.                                                  | `csv`                                             | –                     |
| file.path            | string  | The full, absolute path to the file. Ensure this path does not contain sensitive information. | `/local/outbound/orders_20250904123000_00001.csv` | –                     |
| file.size            | integer | The size of the file in bytes.                                                                | `2048000`                                         | –                     |
| file.record_count    | integer | The total number of records read from or written to the file.                                 | `1530`                                            | –                     |
| file.retry_count     | integer | A counter for the number of times processing has been attempted for this file.                | `1`                                               | –                     |
| file.unzip.total     | integer | The total number of files extracted from an archive (e.g., a .zip file).                      | `2`                                               | –                     |
| file.hash.algorithm  | string  | The algorithm used for generating the file hash.                                              | `SHA-256`                                         | –                     |
| file.hash.exists     | boolean | Indicates whether a file hash exists for validation.                                          | `true`                                            | –                     |
| file.hash.valid      | boolean | Indicates whether the file hash validation passed.                                            | `true`                                            | –                     |
| file.hash.timestamp  | datetime | The timestamp when the file hash was generated (ISO 8601 format).                             | `2025-09-04T12:30:00Z`                            | –                     |
| file.code_signature.digest_algorithm | string  | The algorithm used for generating the code signature.                                         | `SHA256withRSA`                                   | –                     |
| file.code_signature.exists           | boolean | Indicates whether a code signature exists for the file.                                       | `true`                                            | –                     |
| file.code_signature.valid            | boolean | Indicates whether the code signature verification passed.                                     | `true`                                            | –                     |
| file.code_signature.timestamp        | datetime | The timestamp of the code signature (ISO 8601 format).                                        | `2025-09-04T12:30:00Z`                            | –                     |
| file.ack.name        | string  | The filename of the acknowledgment (ACK) file.                                                | `orders_20250904123000_00001.csv.ack`             | –                     |
| file.ack.extension   | string  | The extension of the ACK file.                                                                | `ack`                                             | –                     |
| file.ack.path        | string  | The full path to the ACK file.                                                                | `/local/ack/orders_20250904123000_00001.csv.ack`  | –                     |
| file.ack.size        | integer | The size of the ACK file in bytes.                                                            | `256`                                             | –                     |
| file.ack.status_code | integer | A status code recorded in the ACK file to indicate the processing outcome.                    | `200`                                             | –                     |
| file.ack.status      | string  | Canonical ACK processing status corresponding to the ACK status code (maximum 15 characters). | `ACK_SCHEMA_INVALID`                              | –                     |

---

## File Summary

| Field Key                                      | Type          | Description                                                                                                                                                                                                                                                                                                           | Example                                                                                      | Allowed Values / Enum |
| ---------------------------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | --------------------- |
| interface.file.data.total                      | integer       | The total number of data files attempted or generated in the transaction.                                                                                                                                                                                                                                             | `2`                                                                                          | –                     |
| interface.file.data.success                    | integer       | The number of data files that were handled successfully.                                                                                                                                                                                                                                                              | `2`                                                                                          | –                     |
| interface.file.data.failure                    | integer       | The number of data files that failed to be handled.                                                                                                                                                                                                                                                                   | `0`                                                                                          | –                     |
| interface.file.data.partial_success            | integer       | The number of data files that were partially successful. This indicates that the file was processed, but some internal operations (e.g., record-level processing) within the file encountered recoverable errors, often in a full data load scenario, where the overall file handling is still considered successful. | `0`                                                                                          | –                     |
| interface.file.zip.total                       | integer       | The number of zipped artifacts (e.g., .zip files) created or processed.                                                                                                                                                                                                                                               | `1`                                                                                          | –                     |
| interface.file.ack.total                       | integer       | The number of ACK files processed.                                                                                                                                                                                                                                                                                    | `1`                                                                                          | –                     |
| interface.file.summary                         | array[object] | A summary list providing details for each file processed in the transaction.                                                                                                                                                                                                                                          | `[{"filename":"orders_20250904123000_00001.zip","record_name":"order","record_count":1530}]` | –                     |
| interface.file.summary.filename                | string        | The filename within the `interface.file.summary` array object.                                                                                                                                                                                                                                                        | `orders_20250904123000_00001.zip`                                                            | –                     |
| interface.file.summary.record_name             | string        | The logical record name within the `interface.file.summary` array object.                                                                                                                                                                                                                                             | `order`                                                                                      | –                     |
| interface.file.summary.record_count            | integer       | The record count within the `interface.file.summary` array object.                                                                                                                                                                                                                                                    | `1530`                                                                                       | –                     |
| interface.file.summary.retry_count             | integer       | The number of retries within the `interface.file.summary` array object.                                                                                                                                                                                                                                               | `2`                                                                                          | –                     |
| interface.file.pending_ack.summary             | array[object] | A summary of files that have been sent and are currently awaiting an acknowledgment.                                                                                                                                                                                                                                  | `[{"filename":"orders_20250904123000_00001.csv","retry_count":1}]`                           | –                     |
| interface.file.pending_ack.summary.filename    | string        | The filename within the `interface.file.pending_ack.summary` array object.                                                                                                                                                                                                                                            | `orders_20250904123000_00001.csv`                                                            | –                     |
| interface.file.pending_ack.summary.retry_count | integer       | The number of retries within the `interface.file.pending_ack.summary` array object.                                                                                                                                                                                                                                   | `1`                                                                                          | –                     |

---

## Record Summary

| Field Key      | Type          | Description                                                                             | Example                                                                                                                      | Allowed Values / Enum |
| -------------- | ------------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| record         | array[object] | An array of objects, where each object represents a summary of a record type processed. | `[{"name":"order","total":1500,"success":1490,"failure":10},{"name":"order_item","total":3000,"success":2980,"failure":20}]` | –                     |
| record.name    | string        | The name of the record type in the summary.                                             | `order`                                                                                                                      | –                     |
| record.total   | integer       | The total number of records of this type.                                               | `1500`                                                                                                                       | –                     |
| record.success | integer       | The number of successfully processed records of this type.                              | `1490`                                                                                                                       | –                     |
| record.failure | integer       | The number of failed records of this type.                                              | `10`                                                                                                                         | –                     |

---

## File Management

Fields specific to the file management lifecycle. These complement the generic `file.*` fields in the [Interface](#interface) section and apply when a service implements the File Management Application Standard (AWS or Standalone profile).

| Field Key                         | Type     | Description                                                                                                                                                                         | Example                                                                             | Allowed Values / Enum                                                                                                                                                                                                                                                     |
| --------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| file.status                       | string   | The lifecycle state of the file at the time of the log event.                                                                                                                       | `DOWNLOADED`                                                                        | `PENDING_SCAN`, `PENDING_SCAN_RESPONSE`, `PENDING_DOWNLOAD`, `DOWNLOADED`, `PENDING_SCAN_TIMEOUT`, `PENDING_SCAN_RETRY_EXCEEDED`, `PENDING_SCAN_RESPONSE_TIMEOUT`, `BAD_RESULT`, `DOWNLOADED_FILE_MISMATCH`, `PENDING_DOWNLOAD_RETRY_EXCEEDED`, `PENDING_DOWNLOAD_TIMEOUT` |
| file.display_name                 | string   | The user-provided display name for the file, as submitted in the upload request. Distinct from `file.name`, which is the stored filename.                                           | `report_2025.pdf`                                                                   | –                                                                                                                                                                                                                                                                         |
| file.mime_type                    | string   | The detected or declared MIME type of the file.                                                                                                                                     | `application/pdf`                                                                   | –                                                                                                                                                                                                                                                                         |
| file.hash.value                   | string   | The computed hash digest of the file content. Used for integrity verification and hash-mismatch diagnostics (e.g., expected vs. actual in `[HASH_MISMATCH]` log events).            | `a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3`                 | –                                                                                                                                                                                                                                                                         |
| file.phase3_retry_count           | integer  | The number of Phase 3 clean-file retrieval attempts. Tracked separately from `file.retry_count`, which covers Phase 1 SFS submission retries.                                       | `2`                                                                                 | –                                                                                                                                                                                                                                                                         |
| file.storage_backend              | string   | The storage backend type for dirty or clean file artifacts. Logged during orphan-cleanup diagnostics (`[ORPHAN_DIRTY_ARTIFACT]`).                                                    | `DATABASE`                                                                          | `DATABASE`, `FILESYSTEM`, `S3`                                                                                                                                                                                                                                            |
| file.scanner.uuid                 | string   | The scanner-assigned reference UUID returned by the SFS scanner upon file submission.                                                                                               | `b8a9c4d2-1e3f-4a5b-6c7d-8e9f0a1b2c3d`                                             | –                                                                                                                                                                                                                                                                         |
| file.scanner.verdict              | string   | The scan verdict returned by the SFS scanner.                                                                                                                                       | `Sanitized`                                                                         | `Sanitized`, `Unchanged`, `Quarantined`                                                                                                                                                                                                                                   |
| file.scanner.submission_timestamp | datetime | The timestamp when the file was submitted to the SFS scanner (ISO 8601). Used to enforce the 24-hour SFS retention window.                                                          | `2025-09-04T12:30:00Z`                                                              | –                                                                                                                                                                                                                                                                         |
| file.ingestion.status             | string   | The outcome of record-level ingestion processing for the file.                                                                                                                      | `COMPLETED_WITH_ERRORS`                                                             | `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED`                                                                                                                                                                                                                            |
| file.ingestion.attempts           | integer  | The number of ingestion attempts made for the file.                                                                                                                                 | `2`                                                                                 | –                                                                                                                                                                                                                                                                         |
| storage.quota.current_bytes       | integer  | The current aggregate storage usage in bytes at the time of a quota-exceeded event.                                                                                                 | `5000000000`                                                                        | –                                                                                                                                                                                                                                                                         |
| storage.quota.limit_bytes         | integer  | The configured maximum storage quota in bytes.                                                                                                                                      | `5368709120`                                                                        | –                                                                                                                                                                                                                                                                         |
| storage.quota.requested_bytes     | integer  | The size in bytes of the upload request that triggered the quota check.                                                                                                             | `10485760`                                                                          | –                                                                                                                                                                                                                                                                         |

---

## Error

> Fields marked **★** are auto-populated when a throwable is attached via `.setCause(e)`. Do not set them via `addKeyValue`.

| Field Key              | Type    | Description                                                                                                                                                                   | Example                           | Allowed Values / Enum                                                         |
| ---------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------- | ----------------------------------------------------------------------------- |
| error.code             | integer | An error code, often aligned with HTTP status codes, to provide a machine-readable classification of the error.                                                               | `429`                             | `100–599`                                                                     |
| error.type ★           | string  | The type or class of the error (e.g., the exception class name). Auto-populated from `exception.getClass().getName()` via `.setCause(e)`.                                     | `java.net.SocketTimeoutException` | –                                                                             |
| error.message ★        | string  | The error message. Auto-populated from `exception.getMessage()` via `.setCause(e)`. Sanitize at the throw site to remove PII, credentials, or sensitive data.                 | `connection timed out after 30s`  | –                                                                             |
| error.stack_trace ★    | string  | The full exception stack trace. Auto-populated via `.setCause(e)`. Sanitize at the throw site to prevent leaking sensitive code paths or data.                                | `java.net...`                     | –                                                                             |
| error.category         | string  | A high-level category for the error, which helps in routing alerts and building dashboards.                                                                                   | `network`                         | `server`, `network`, `cert/auth`, `database`, `application`, `data`, `others` |
| error.follow_up_action | boolean | A flag indicating whether this error requires manual intervention or follow-up. When `true`, this enables alerting rules in the log platform or monitoring system.            | `true`                            | `true`, `false`                                                               |

---

## Report

| Field Key | Type | Description | Example | Allowed Values / Enum |
|:---|:---|:---|:---|:---|
| report.classification | string | The security classification of the generated report. | `RESTRICTED` | `PUBLIC`, `INTERNAL`, `RESTRICTED`, `CONFIDENTIAL`, `SECRET` |
| report.sensitivity | string | The sensitivity sub-category of the generated report. | `SENSITIVE_NORMAL` | `NON_SENSITIVE`, `SENSITIVE_NORMAL`, `SENSITIVE_PERSONAL` |
| report.fields_accessed | string | Comma-separated list of field/column names accessed during the report generation (values must never be logged). | `user_id,email,phone_number` | – |

---

## MCC Shared Authentication

| Field Key | Type | Description | Example | Allowed Values / Enum |
|:---|:---|:---|:---|:---|
| registration.id | string | The OAuth2 client registration ID configured in the application. | `mcc-sso-client-credentials` | – |
| client.id | string | The OAuth2 client ID used for authentication. | `client-xyz-123` | – |
| token.uri | string | The token endpoint URI of the Identity Provider. | `https://sso.company.com/oauth2/token` | – |
| execution.mode | string | The mode of execution (e.g., synchronous request-context vs. background). | `request-context`, `background` | `request-context`, `background` |
| key.source | string | The source of the signing/validation keys. | `vault`, `local-config`, `kms` | `vault`, `local-config`, `kms` |
| key.id | string | The Key ID (`kid`) of the active cryptographic signing key. | `key-2026-06-abc` | – |
| published.key.count | integer | The number of public keys currently published in the JWKS endpoint. | `2` | – |
| node.id | string | The unique identifier of the node or server instance. | `pod-xyz-890` | – |

---

## MCNS Notification

| Field Key | Type | Description | Example | Allowed Values / Enum |
|:---|:---|:---|:---|:---|
| reference.id | string | The correlation ID or unique reference ID of the individual notification request. | `a1b2c3d4-e5f6-7890-1234-567890abcdef` | – |
| tag.id | string | The unique identifier of the notification tag or configuration group. | `user-welcome` | – |
| sender.id | string | The identifier of the sender (e.g., sender address alias or shortcode). | `noreply@company.com` | – |

---

## MPDS Personnel Retrieval

| Field Key | Type | Description | Example | Allowed Values / Enum |
|:---|:---|:---|:---|:---|
| query.template.id | string | The identifier of the MPDS template or query being run. | `personnel-by-id` | – |
| query.cardinality | string | The cardinality of the personnel query. | `single`, `multiple` | `single`, `multiple` |
| query.count | integer | The number of personnel records searched or returned. | `5` | – |

