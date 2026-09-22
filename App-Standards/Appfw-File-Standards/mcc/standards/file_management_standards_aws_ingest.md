# File Management — Ingest-and-Delete Mode (AWS Profile)

Parent: [File Management Standards — AWS Profile](../index.md)
Prerequisite: [Core Lifecycle](./file_management_standards_aws_core.md)

## 1. Mode Definition

* **Per-component choice**: Expressed through which beans and storage configuration are wired at startup. A single upload endpoint implements one mode, but an application may host multiple upload components with different modes.
* **Behavior**: After reaching `DOWNLOADED`, the file is read record-by-record from the Clean Store, processed, and the clean artifact is deleted. Files are NOT downloadable through the application API after ingestion.
* **ZIP/OOXML exclusion**: ZIP and OOXML files (Magic Bytes `50 4B 03 04`) are explicitly excluded from record-level ingestion — they are accepted for upload and scanning but ZIP container extraction and per-entry ingestion are not supported. If a file with ZIP/OOXML Magic Bytes reaches the ingest-and-delete path, it must be treated as an unsupported MIME type and skipped without error.
* **MIME Type Configuration**: Ingest-and-delete components must configure `acceptedMimeTypes` to exclude ZIP/OOXML at the upload validation layer. The ingest-path skip described above is a last-resort safety net, not the primary guard — accepting ZIP at upload and discarding it at ingestion allows the file to be scanned, the clean artifact deleted, and the user to receive no indication that their content was not processed. Per-component `acceptedMimeTypes` configuration must be supported so this exclusion can be enforced without affecting co-hosted retain-mode components that may legitimately accept ZIP for scanning and retention.

## 2. Ingestion Trigger

* Record-level ingestion must start ONLY after a `DOWNLOADED` event delivered via the outbox relay.
* Because delivery is at-least-once, the ingestion handler must check `ingestionStatus` before processing: if `ingestionStatus` is in any terminal state (`COMPLETED`, `COMPLETED_WITH_ERRORS`, `REJECTED`, `REJECTED_EMPTY`, or `FAILED`), skip the handler entirely.

## 3. Record-Level Processing Rules

* Read the file from the Clean Store record by record using iterator/streaming patterns.
* Files MUST NOT be fully materialized in memory during ingestion — use streaming readers.
* **Fail-tolerant strategy**: When an individual record fails validation or parsing:
  * Skip it.
  * Append to the per-file rejection log (row identifier, reason, truncated raw value).
  * Continue processing remaining records.
  * A single bad record must NOT discard valid records from the same file.
* After the full pass: determine the terminal `ingestionStatus` based on row outcomes:
  * `COMPLETED` — all rows loaded successfully (processedRows > 0, rejectionCount == 0)
  * `COMPLETED_WITH_ERRORS` — some rows loaded, some rejected (processedRows > 0, rejectionCount > 0)
  * `REJECTED` — all rows failed validation, zero loaded (processedRows == 0, rejectionCount > 0)
  * `REJECTED_EMPTY` — valid file structure but no data rows (header only, totalRows == 0)
* Persist `rejectionCount` (integer) and `rejectionLog` (structured summary) alongside the status.
* `FAILED` is reserved ONLY for file-level format failure (parser cannot advance past the file header — the entire file is unreadable, or I/O error). Individual record failures do NOT trigger `FAILED`.

## 4. Clean Artifact Deletion

* After successful ingestion, `ingestionStatus` must be updated to the appropriate terminal status (`COMPLETED`, `COMPLETED_WITH_ERRORS`, `REJECTED`, or `REJECTED_EMPTY`) in the same transaction as ingestion writes.
* The clean artifact must be deleted ONLY after the terminal status is committed — never on every event delivery.
* A delete that precedes the terminal status commit violates the standard.

## 5. Idempotency Requirement

* Ingestion operations MUST be safe on re-execution. The outbox relay delivers `DOWNLOADED` events at-least-once.
* The ingestion handler must check `ingestionStatus` before processing and skip if already in a terminal state (`COMPLETED`, `COMPLETED_WITH_ERRORS`, `REJECTED`, `REJECTED_EMPTY`, or `FAILED`).
* The terminal status guard prevents full re-runs after completion but does NOT prevent re-processing of already-committed records after a partial failure mid-file. Ingestion operations must therefore be designed to be safe on re-execution.
* **If the integrator's domain logic cannot guarantee idempotency** (e.g., non-idempotent external API calls, side effects that cannot be safely repeated):
  * The ingest-and-delete path MUST NOT be used.
  * Use the retain-for-download path instead.
  * Implement a terminal-on-failure strategy (`ingestionStatus = FAILED`) with a dedicated operator remediation flow.
* **Failure Mode (non-idempotent operations)**: A non-idempotent `RecordProcessor` will produce duplicate records on any retry after a mid-file crash, with no automatic recovery path. The completion guard (`ingestionStatus = COMPLETED`) prevents re-runs after success but does not protect against re-processing of already-committed records during the first incomplete run. The symptom — duplicate records in the domain store — will not surface until a mid-file crash occurs, which may be long after deployment. This is a data integrity violation, not an infrastructure failure.

## 6. Persistence (Mode-Specific Additions)

These fields supplement the shared metadata defined in Core §4.6:

| Field | Description |
|:---|:---|
| `ingestionStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `REJECTED`, `REJECTED_EMPTY`, or `FAILED`. The ingestion handler must read this before processing and update it to the appropriate terminal status atomically with ingestion writes. |
| `rejectionCount` | Number of skipped records (integer). |
| `rejectionLog` | Structured summary of skipped records — row identifier, reason, truncated raw value per rejected record. |

## 7. Error Contract (Mode-Specific Additions)

These codes supplement the shared error contract defined in Core §3.2:

| Code | Meaning |
|:---|:---|
| **422 Unprocessable Entity** | File-level format failure — the ingestion parser cannot read the file structure at all (e.g. unreadable header, corrupt container format). Individual record validation failures are fail-tolerant and do NOT produce a 422; they result in `COMPLETED_WITH_ERRORS` or `REJECTED` with the per-file rejection log. |

## 8. Enforced Constraints (Negative Requirements)

These supplement the shared negative requirements in Core §4.7:

| Constraint | Standard Expectation |
|:---|:---|
| Full file materialization during record-level ingestion parsing | Files must not be fully materialized in memory; use iterator or streaming readers |
| Ingest-and-delete path used with non-idempotent ingestion operations | If operations cannot be safely re-executed, use retain-for-download and declare a terminal-on-partial-failure strategy |
| Clean artifact deleted before terminal ingestion status is committed | Deletion must be gated on the terminal status commit; never tied to event delivery count |

## 9. Observability (Mode-Specific Additions)

| Observable Signal | Meaning | Action |
|:---|:---|:---|
| Clean-store cleanup absent on ingest-and-delete paths | Clean artifacts may be retained longer than intended | Verify post-download cleanup path for the selected storage backend |
| Ingestion failure (non-idempotent path) | `ingestionStatus = FAILED`; no automatic retry | Operator must run dedicated remediation flow |
| High `rejectionCount` in `FileIngestionEvent` | A significant portion of records were skipped | Review `rejectionLog` for systematic validation failures; investigate upstream data quality |

## 10. Test & Validation (Mode-Specific)

### 10.1 Unit Tests

* **Ingestion Completion Guard**: Verify that a second delivery of the same `DOWNLOADED` event is a no-op when `ingestionStatus` is in any terminal state; verify that the clean artifact is deleted only after the terminal status commit, not on every event delivery.

### 10.2 Integration Tests

* **Ingestion Idempotency**: Verify that re-delivering a `DOWNLOADED` event when `ingestionStatus` is terminal results in a no-op — no duplicate records written and no second artifact deletion attempted.
* **Fail-Tolerant Processing**: Verify that a bad record is skipped, appended to the rejection log, and remaining valid records are processed.
* **Rejection Persistence**: Verify `rejectionCount` and `rejectionLog` are persisted correctly after ingestion completes.
* **FAILED**: Verify that `ingestionStatus = FAILED` is set only when the parser cannot advance past the file header (file-level format failure), not for individual record failures.
* **REJECTED**: Verify that `ingestionStatus = REJECTED` is set when all rows fail validation (processedRows == 0, rejectionCount > 0).
* **REJECTED_EMPTY**: Verify that `ingestionStatus = REJECTED_EMPTY` is set for valid files with zero data rows (header only).
* **COMPLETED_WITH_ERRORS**: Verify that `ingestionStatus = COMPLETED_WITH_ERRORS` is set when some rows are loaded and some rejected.
* **ZIP/OOXML Exclusion**: Verify that ZIP/OOXML files are skipped without error when they reach the ingest-and-delete path.
* **Streaming/Iterator Pattern**: Verify no full-file materialization during ingestion parsing.
* **Error Code Paths**: Verify HTTP 422 (ingestion failure) in addition to the shared error codes tested in Core §5.2.

### 10.3 Integrator Responsibilities

The library consumer is responsible for:
* Testing downstream ingestion handlers and row-processing logic beyond the framework-provided `RecordProcessor` contract.
* **Idempotency compliance**: If the ingest-and-delete path is used, the integrator must verify that ingestion operations are safe on re-execution. If idempotency cannot be guaranteed, the ingest-and-delete path must not be used; the integrator must implement a terminal-on-failure strategy (`ingestionStatus = FAILED`) and a dedicated remediation flow.
* **Mid-ingestion crash recovery test** *(Recommended)*: Simulate a process crash mid-file (e.g., after N records are committed but before terminal `ingestionStatus` is written), restart the application, allow the outbox relay to re-deliver the `DOWNLOADED` event, and verify that no duplicate domain records are produced. This test surfaces the hard idempotency failure mode — duplicate records from partial re-execution — during development rather than in production after a mid-file crash months post-deployment.

## 11. Event-Driven Architecture (Mode-Specific Addition)

This supplements the shared event architecture defined in Core §4.5:

* **Downstream Trigger** *(Enforced Constraint)*: Record-level ingestion must start only after a `DOWNLOADED` event delivered via the outbox relay. Because delivery is at-least-once, the ingestion handler must check `ingestionStatus` before processing: if `ingestionStatus` is in any terminal state, skip the handler entirely. After ingestion, `ingestionStatus` must be updated to the appropriate terminal status in the same transaction as ingestion writes. The clean artifact must be deleted only after the terminal status is committed — never on every event delivery.
* **Post-Ingestion Event** *(Enforced Constraint)*: After ingestion completes, the framework must emit a `FileIngestionEvent(fileId, ingestionStatus, rejectionCount, eventTimestamp)` via the outbox in the same transaction as the terminal status write. This is the primary push signal for ingestion outcomes — consumers must not poll metadata to discover completion. A `rejectionCount` of zero is a valid payload for clean ingestion runs. Consumers must be idempotent with respect to this event.
* **Failure Constraint** *(Enforced Constraint)*: The terminal status guard prevents full re-runs after completion but does not prevent re-processing of already-committed records after a partial failure mid-file. Ingestion operations must therefore be designed to be safe on re-execution. If the integrator cannot guarantee idempotency, the ingest-and-delete path is prohibited; use the retain-for-download path and declare a terminal-on-failure strategy (`ingestionStatus = FAILED`) with a dedicated operator remediation flow.
* **Implementation Recipes**: [12. Record-Level Ingestion & Processing](../recipes/shared/12-row-level-ingestion-processing-post-retrieval-flow.md).

## 12. Changelog

| Version | Date | Description | Files Changed |
|:---|:---|:---|:---|
| 1.0.2 | 2026-06-29 | Added mid-ingestion crash recovery test as recommended integrator responsibility in §10.3; surfaces partial-reexecution idempotency failures during development. | Ingest |
| 1.0.1 | 2026-06-23 | Added per-component MIME type configuration requirement excluding ZIP/OOXML at upload validation; documented non-idempotent failure mode explicitly; added `FileIngestionEvent` as enforced post-ingestion event; added rejection-count observability signal. | Ingest |
