# File Management — Frontend Questions, Ingest-and-Delete (AWS Profile)

Ingest-and-delete mode — clean artifacts are processed record-by-record after reaching `DOWNLOADED`, then deleted. Files are NOT downloadable after ingestion.

Standard reference: [Ingest-and-Delete Mode](../../standards/file_management_standards_aws_ingest.md)

---

### S24. During-ingestion state display

**Question:** How should files appear in the file list while ingestion is in progress — after reaching `DOWNLOADED` but before the clean artifact is deleted?

**Default:** Show a "Processing" badge. Suppress any download action — the file is not downloadable during ingestion even though the backend state is `DOWNLOADED`. Once ingestion completes, transition to the post-ingestion appearance defined in S6.

**Context:** In ingest mode, `DOWNLOADED` means "clean file retrieved, ingestion pending" — not "ready to download." Showing a download action at this point violates the hard rule against implying non-downloadable files are downloadable. Status polling (S20) drives the badge transition once ingestion completes.

**If overriding:**
- If the backend exposes a dedicated ingestion-in-progress state distinct from `DOWNLOADED`, map the "Processing" badge to that state instead.

---

### S25. Ingestion failure state display

**Question:** How should files that reach the ingestion failure terminal state appear in the file list?

**Default:** Distinct "Processing Failed" badge — visually different from scan terminal failures (BAD_RESULT, DOWNLOADED_FILE_MISMATCH). Safe error message: "This file could not be processed. Contact support with the trace ID." Trace ID behind a "Show details" toggle.

**Context:** B3 requires the ingestion failure terminal state to be surfaced distinctly from scan failures. Users need to understand the file failed during processing (after scanning succeeded), not during security scanning. Do not expose internal ingestion error details.

**If overriding:**
- Should different failure sub-states (total failure vs partial row rejection with non-zero `rejectionCount`) show different messages or badges?

---

### S6. Ingested file appearance

**Question:** How should ingested-and-deleted files appear in the file list?

**Default:** Ingest-mode files appear in their own dedicated list (e.g. `/records`), separate from the retain-for-download documents list. The records list shows a "Processed" badge with `processedRows` and `rejectionCount`. No download action exists — the records list is a pure ingest-only view. The documents list (`/files`) excludes ingest-mode files entirely.

**Context:** When both retain-for-download and ingest-and-delete components coexist in the same application, the endpoint separation rule (B3) implies distinct upload endpoints AND distinct list views — each scoped to its own component. The documents list returns only `componentId="retain-for-download"` files; the records list returns only `componentId="ingest-and-delete"` files. Once a file is ingested and its clean artifact removed, the row remains visible in the records list so the user can confirm the file existed and was processed. Hard rule: never imply a non-downloadable file is downloadable.

**If overriding (unified list):**
- If a single unified file list is preferred, ingest-mode files must show with a distinct badge and no download affordance (not just disabled — absent). The backend `GET /api/files` endpoint must include `rowIngestionRequired` and `ingestionStatus` fields for badge rendering.
- Which specific backend state indicates "Ingested and Deleted"?
- Once ingested, should the file row remain in the list with no download action, or should it be removed from the UI entirely?
