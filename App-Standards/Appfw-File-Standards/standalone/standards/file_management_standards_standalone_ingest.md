# File Management — Ingest-and-Delete Mode (Standalone Profile)

Parent: [Standalone File Management Index](../index.md)
Prerequisite: [Core Lifecycle](./file_management_standards_standalone_core.md)

## 1. Mode Definition

* **Per-component choice**: Expressed through which beans and storage configuration are wired at startup. A single upload endpoint implements one mode, but an application may host multiple upload components with different modes.
* **Behavior**: After reaching `DOWNLOADED`, the file is read record-by-record from the Clean Store, processed, and the clean artifact is deleted. Files are NOT downloadable through the application API after ingestion.
* **MIME Type Configuration**: Ingest-and-delete components must configure `rowIngestionMimeTypes` to specify which MIME types are eligible for record-level ingestion. Only files whose MIME type is in `rowIngestionMimeTypes` and whose `rowIngestionRequired` flag is true will trigger the ingestion flow.

## 2. Ingestion Trigger

* Record-level ingestion must start ONLY after the file reaches `DOWNLOADED` status and a `FileScanEvent(DOWNLOADED)` is emitted.
* The ingestion flow reads the file from the Clean Store and deletes the clean artifact afterward when the ingest-and-delete path is selected.
* If `rowIngestionRequired` is true and the MIME type is in `rowIngestionMimeTypes`, trigger record-level ingestion only after the file reaches `DOWNLOADED`.

## 3. Record-Level Processing Rules

* Read the file from the Clean Store record by record using iterator/streaming patterns.
* Files MUST NOT be fully materialized in memory during ingestion — use streaming readers and flush in batches.
* **Fail-tolerant strategy**: When an individual record fails validation or parsing:
  * Skip it.
  * Log the failure with row identifier and reason.
  * Continue processing remaining records.
  * A single bad record must NOT abort the whole ingestion batch.
* After the full pass: determine the terminal `ingestionStatus` based on row outcomes:
  * `COMPLETED` — all rows loaded successfully (processedRows > 0, rejectionCount == 0)
  * `COMPLETED_WITH_ERRORS` — some rows loaded, some rejected (processedRows > 0, rejectionCount > 0)
  * `REJECTED` — all rows failed validation, zero loaded (processedRows == 0, rejectionCount > 0)
  * `REJECTED_EMPTY` — valid file structure but no data rows (header only, totalRows == 0)
* Persist `rejectionCount` (integer) and `rejectionLog` (structured summary) alongside the status.
* `FAILED` is reserved ONLY for file-level format failure (parser cannot advance past the file header — the entire file is unreadable, or I/O error). Individual record failures do NOT trigger `FAILED`.

## 4. Clean Artifact Deletion

* After successful ingestion (status reaches a terminal state), the clean artifact is deleted from the Clean Store when the ingest-and-delete path is selected.
* The clean artifact must be deleted ONLY after the ingestion outcome is committed — never prematurely.
* A delete that precedes the ingestion outcome commit violates the standard.

## 5. Event-Driven Architecture (Mode-Specific Addition)

This supplements the shared event architecture defined in Core §4.5:

* **Downstream Trigger** *(Enforced Constraint)*: Record-level ingestion should start only after the `DOWNLOADED` event is emitted, should read the file from the Clean Store, and should delete the clean artifact afterward when the ingest-and-delete path is selected.

## 6. Enforced Constraints (Negative Requirements)

These supplement the shared negative requirements in Core §4.8:

| Constraint | Standard Expectation |
|:---|:---|
| Full file materialization during row ingestion | Use iterators or streaming readers and flush in batches |
| Clean artifact deleted before ingestion outcome is committed | Deletion must be gated on the ingestion outcome commit |

## 7. Observability (Mode-Specific Additions)

| Observable Signal | Meaning | Action |
|:---|:---|:---|
| Clean-store cleanup absent on ingest-and-delete paths | Clean artifacts may be retained longer than intended | Verify post-download cleanup path for the selected storage backend |
| `[INGESTION_FAILED]` repeated for same `fileId` | Ingestion retries exhausting | Investigate file content or ingestion handler |

## 8. Test & Validation (Mode-Specific)

### 8.1 Unit Tests

* **Row Ingestion Isolation**: Verify one bad record does not abort the whole ingestion batch.

### 8.2 Integration Tests

* **Record-Level Ingestion**: Verify all-good rows (COMPLETED with rejectionCount=0), mixed good/bad rows (COMPLETED_WITH_ERRORS with non-zero rejectionCount), all-bad rows (REJECTED), empty file (REJECTED_EMPTY), and file-level format failure (FAILED) paths.
* **Streaming/Iterator Pattern**: Verify no full-file materialization during ingestion parsing.
* **Clean Artifact Deletion**: Verify the clean artifact is deleted only after ingestion outcome is committed.
* **Ingestion Trigger**: Verify ingestion starts only after the file reaches `DOWNLOADED` status and the ingestion status is not already terminal.
* **Error Code Paths**: Verify HTTP 422 (ingestion failure) in addition to the shared error codes tested in Core §5.2.

### 8.3 Integrator Responsibilities

The library consumer is responsible for:
* Testing downstream ingestion handlers and row-processing logic beyond the framework-provided ingestion port contract.
* **Mid-ingestion crash recovery test** *(Recommended)*: Simulate a process crash mid-file (e.g., after N records are committed but before `ingestionStatus = COMPLETED`), restart the application, allow the ingestion trigger to re-fire, and verify that no duplicate domain records are produced. This test surfaces the hard idempotency failure mode — duplicate records from partial re-execution — during development rather than in production after a mid-file crash months post-deployment.
