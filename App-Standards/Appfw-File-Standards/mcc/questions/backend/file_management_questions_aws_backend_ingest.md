# File Management — Backend Questions, Ingest-and-Delete (AWS Profile)

> **Usage:** Defaults are the recipe reference values; confirm or override each. Applies only when a component is wired for ingest-and-delete.

Ingest-and-delete mode — after reaching `DOWNLOADED`, the file is read record-by-record, processed, and the clean artifact deleted. Files are NOT downloadable after ingestion.

Standard reference: [Ingest-and-Delete Mode](../../standards/file_management_standards_aws_ingest.md)

## Hard rules

- NEVER use the ingest-and-delete path when the domain operations cannot be guaranteed idempotent — use retain-for-download with a terminal-on-partial-failure strategy instead.
- NEVER delete the clean artifact before `ingestionStatus = COMPLETED` is committed.
- NEVER fully materialize the file in memory during ingestion — use streaming/iterator readers.
- NEVER include ZIP/OOXML in an ingest component's `acceptedMimeTypes`.

---

### B18. RecordProcessor idempotency

**Question:** Can the integrator's record-processing operations be safely re-executed (idempotent)?

**Default:** Required. The outbox relay delivers `DOWNLOADED` at-least-once, and a mid-file crash re-runs already-committed records on the next delivery. If idempotency cannot be guaranteed, the ingest-and-delete path MUST NOT be used.

**Context:** The `ingestionStatus = COMPLETED` guard prevents full re-runs after success but not re-processing of committed records after a mid-file crash. Non-idempotent processors produce duplicate domain records with no automatic recovery — a data-integrity violation that surfaces only after a crash.

**If overriding (cannot guarantee idempotency):**
- Switch the component to retain-for-download.
- Implement a terminal-on-partial-failure strategy (`ingestionStatus = FAILED`) with a dedicated operator remediation flow.

---

### B19. Accepted ingestion MIME types

**Question:** Which MIME types are eligible for record-level ingestion?

**Default:** The row-ingestion MIME set configured per component, explicitly excluding ZIP/OOXML (`50 4B 03 04`). Exclusion is enforced at the upload validation layer; the ingest-path skip is a last-resort safety net. The app refuses to start if an ingest component lists ZIP/OOXML in `acceptedMimeTypes`.

**Context:** Ingestion runs only when `rowIngestionRequired = true` AND `fileType` ∈ the configured ingestion MIME types. Accepting ZIP at upload and discarding it at ingestion would leave the user with no signal.

---

### B20. Record-failure tolerance and rejection log

**Question:** How are individual bad records handled, and how is the rejection log persisted?

**Default:** Fail-tolerant — skip the bad record, append to the per-file rejection log (row identifier, reason, truncated raw value), continue processing. After the full pass, determine terminal status: `COMPLETED` (all valid), `COMPLETED_WITH_ERRORS` (some valid, some rejected), `REJECTED` (all rows failed), or `REJECTED_EMPTY` (no data rows). `FAILED` is reserved for file-level format failure only (parser cannot advance past the header → HTTP 422). Persist `rejectionCount` and `rejectionLog` alongside the status.

**Context:** A single bad record must not discard valid records from the same file. A high `rejectionCount` is an observability signal for upstream data-quality issues.

---

### B21. Ingestion batch flush size and progress

**Question:** What batch flush size is used, and is progress tracked?

**Default:** Flush `IngestionRecord`s every 500 rows, updating `processedRows` on each flush and `totalRows` at completion. Ingestion runs in a single `@Transactional(timeout = 1800)` (30 min).

**Context:** Streaming iterator + periodic flush keeps memory bounded and gives the status endpoint live progress. The v2 Spring Batch migration (>10k rows / >10s) swaps `DirectRecordProcessor → SpringBatchRecordProcessor` behind the `RecordProcessor` port without changing the domain.

---

### B22. Post-ingestion event

**Question:** Confirm the post-ingestion event contract.

**Default:** Emit `FileIngestionEvent(fileId, ingestionStatus, rejectionCount, eventTimestamp)` via the outbox in the same transaction as the `ingestionStatus = COMPLETED` write. This is the primary push signal — consumers must not poll metadata for completion and must be idempotent. `rejectionCount = 0` is valid.

**Context:** Clean-artifact deletion happens only after the `COMPLETED` commit, never on event delivery count.
