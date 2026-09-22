# File Management — Backend Questions (Standalone Profile)

This profile has **no external scanner**. Validation and local promotion happen synchronously within the upload request; there is no polling phase, no background lifecycle scheduler, and no automatic retry.

> **Build tool:** Maven is the required build tool for all projects. Use `pom.xml` — Gradle is not supported.

---

### B1. Accepted MIME types

**Question:** Which MIME types will this component accept?

**Default:** PDF, PNG, JPEG, ZIP/OOXML — matching the standard's Magic Bytes registry. Confirm the full list with the business.

**Context:** Only files whose Magic Bytes match the configured registry pass validation (HTTP 400 otherwise). Any additions need corresponding Magic Bytes entries.

**If overriding:**
- For each MIME type added beyond the standard registry, what are the Magic Bytes sequences to register?
- If a MIME type supports ingestion, is a `RecordProcessor` implementation already available, or does it need to be written?

---

### B2. File size limits

**Question:** What are the minimum and maximum file sizes for this component?

**Default:** Min: 1 byte (enforces zero-byte rejection). Max: 10 MB — confirm with the business.

**Context:** Files outside the configured range are rejected with HTTP 400 before any persistence. Rejecting before persistence is a hard DoS-protection rule.

**If overriding:**
- If the max size is larger than the default, has the upload transaction timeout (`transaction-timeout-upload-ms`) been confirmed sufficient?
- If the max size is smaller, has client-side UI validation been updated to match?

---

### B3. Record-level ingestion

**Question:** Is record-level (row) ingestion required for this component?

**Default:** Confirm with the business. If yes, identify which MIME types trigger ingestion.

**Context:** When `rowIngestionRequired=true`, the application reads the clean file record-by-record after `DOWNLOADED` and routes results to the downstream ingestion handler. Row ingestion is optional and business-driven; enabling it changes the post-download flow, ingestion timeout settings, and clean-artifact retention.

**Endpoint separation rule:** If both retain-for-download and ingest-and-delete modes are needed in the same application, each MUST have its own dedicated upload endpoint (e.g. `POST /api/files/upload` for documents, `POST /api/records/upload` for data files). Do not use a single endpoint with a mode parameter.

**If overriding (yes):**
- What is the expected maximum row count per file? This informs `ingestion-batch-flush-size` and `transaction-timeout-ingestion-ms`.
- Should partial ingestion success (some rows valid, some invalid) be treated as `COMPLETED` with a non-zero `rejectionCount`, or should the entire ingestion batch be rolled back?

---

### B4. Clean artifact deletion after ingestion (if B3 = Yes)

**Question:** After ingestion completes, should the clean artifact be deleted (ingest-and-delete path)?

**Default:** Delete (ingest-and-delete path). Override to retain only when the business requires both ingestion and ongoing download access.

**Context:** On the ingest-and-delete path the clean file is removed from the Clean Store after ingestion; files are no longer downloadable afterward. On the retain path the clean artifact stays and the file remains downloadable. Once a clean artifact is deleted, re-download is impossible — the UI must reflect this.

**If overriding (retain after ingestion):**
- Has the frontend been updated to reflect that files on the ingest-and-delete path are no longer downloadable after ingestion?

---

### B5. Bulk-download endpoint

**Question:** Should the bulk-download endpoint be exposed?

**Default:** Yes, expose it if the component supports download at all.

**Context:** A bulk-download endpoint accepts a list of file IDs and returns a `files.zip` attachment containing all retained, caller-owned `DOWNLOADED` files. If any file fails the owner-scope or retained-state check, the server rejects the entire request — no partial archives.

---

### B6. Tags field

**Question:** Is the optional `tags` field needed for this component?

**Default:** Include the field, treat it as optional and unindexed.

**Context:** When present, `tags` is stored as a free-form string on file metadata and returned in list responses. No server-side validation of tag values is required by the standard. Adding indexed or validated tags requires additional schema work not covered by the standard.

---

### B7. Admin scope bypass

**Question:** Should admin-role principals bypass owner-scope restrictions for list, retrieve, or delete operations?

**Default:** No bypass unless explicitly required by the business and approved. Owner-scope enforcement is the default for all roles.

**Context:** By default, every caller (including admin roles) sees only their own files. Any admin-scope bypass is a non-standard extension and must be documented in an ADR.

**If overriding:**
- What authorization mechanism (role claim, permission, header) will control admin-scope access?
- Should admin-scope operations be logged separately from owner-scope operations?

---

### B8. displayName uniqueness

**Question:** Should `displayName` uniqueness be enforced at the application level?

**Default:** No uniqueness constraint beyond the regex `^[a-zA-Z0-9_\- ]+$`.

**Context:** If uniqueness is required, the backend must check for an existing file with the same `displayName` owned by the same principal and return HTTP 409 Conflict on collision. The frontend guide (S3) asks the same question from the UI side — both sides must agree.

**If overriding:**
- Should uniqueness be enforced across all files for the same principal, or scoped to a subset (e.g., by tag or category)?
- Should the HTTP 409 Conflict response body include the conflicting file's ID to assist deduplication on the client?

---

### B9. Inbound authentication for file endpoints

**Question:** Is inbound authentication required for file endpoints, or is public access acceptable for this use case?

**Default:** Inbound authentication required — file endpoints require an authenticated principal and owner-scope enforcement.

**Context:** The standard mandates owner-scope as the sole access-control rule. Overriding this to public access (`permitAll()`) is a deliberate deviation that must be documented with its consequences: no owner scope, no per-user isolation, UUID-only security. This question must be answered before implementation — it fundamentally changes the security model, the `SecurityFilterChain` configuration, the repository queries (`findById` vs `findByIdAndUploadedBy`), and the frontend auth guard wiring.

**If overriding (public access — no inbound auth):**
- Document this as a deliberate deviation in an ADR.
- Accept that anyone who knows a file UUID can read its status, download it, or delete it.
- Remove owner-scope repository methods and replace with `findById` throughout.

---

### B10. Bulk download maximum file count

**Question:** What is the configurable maximum number of files per bulk download request?

**Default:** 10 files per request. Exceeding the limit returns a 400 rejection with a single whole-request message that does not identify which file caused the issue.

**Context:** The count limit bounds the worst-case server-side temp-disk footprint (`fileCount × maxFileSize`). The archive is assembled to a bounded temp file before being returned — never streamed directly. Operators should size this limit based on available temp disk and expected file sizes.

**If overriding:**
- What count limit, and what is the resulting max disk footprint?
- Is bulk download needed at all for this use case? (If not, omit the endpoint entirely.)

---

### ST1. File-content storage backend

**Question:** Which file-content storage backend will be used?

**Default:** Database-backed. Use filesystem-backed only if retained files are large, long-lived, or if operational separation from the metadata database is preferred.

**Context:** In database mode, dirty and clean artifacts are stored as BLOBs alongside metadata. In filesystem mode, artifacts live in configured dirty and clean directories; metadata holds the path reference and remains the system of record. The database-backed path is simpler to operate and sufficient for low-to-moderate volume with small files.

**If overriding (filesystem-backed):**
- Are the dirty and clean directories on separate mount points, or do they share the same volume?
- What happens to clean-store artifacts if the configured directory is unavailable on restart?
- If running as a containerized process, are the dirty and clean directories mapped to persistent volume mounts that survive container restarts?
- What umask or permission model is applied to newly created artifact files?

---

### ST2. Storage quota

**Question:** What is the storage quota limit for this deployment?

**Default:** 5 GB (`standalone.storage-quota-bytes = 5368709120`) — confirm with the team and adjust if the target hardware constrains or allows more.

**Context:** The service rejects any upload that would cause aggregate stored file size to exceed this value, returning HTTP 507. The default 5 GB suits a typical field laptop. Deployments on hardware with more or less storage must adjust the limit explicitly.

**If overriding:**
- Is the quota enforced on aggregate stored bytes, or on file count?
- If the deployment has multiple principals, is the quota shared across all principals or per-principal?

---

### ST3. Promotion failure recovery

**Question:** How will operators detect and recover from dirty artifacts stranded by a promotion failure?

**Default:** Query for files stuck in `PENDING_SCAN` beyond the expected promotion window (promotion is normally sub-second; any file stuck for more than a few minutes is an orphan candidate). Document a manual cleanup procedure for the configured storage backend. Consider a startup check that logs the count of orphaned artifacts.

**Context:** Standalone has no background scheduler, so a stranded dirty artifact persists silently until an operator acts. Without a detection mechanism, orphans accumulate and consume quota unnoticed. The standard requires cleanup or recovery to be documented but leaves the detection mechanism to the implementer.

**If overriding:**
- Is there an admin or operator endpoint for listing files stuck in `PENDING_SCAN` beyond the expected promotion window?
- Should stranded dirty artifacts be purged automatically on application startup if the metadata record shows no corresponding clean artifact?

---

### ST4. Ingestion retry

**Question:** Should ingestion retry be implemented for record-level ingestion failures?

**Default:** No automatic retry — log the failure and mark `ingestionStatus` as failed. Re-triggering ingestion is an explicit operator action. Implement retry only if the integrator's `RecordProcessor` is idempotent.

**Context:** Standalone has no retry infrastructure. Ingestion retry risks double-processing unless idempotency is confirmed. Verify idempotency before enabling retry; document the retry endpoint as a non-standard extension.

**If overriding:**
- For each failure category in the `RecordProcessor`, is re-processing the same row safe without producing duplicate output?
- Should the retry trigger be an operator endpoint, a scheduled background job, or a UI action?

---

### ST5. Filesystem directories (if ST1 = filesystem-backed)

**Question:** What are the dirty and clean storage directories, and how are permissions managed?

**Default:** Set `standalone.storage.dirty-dir` and `standalone.storage.clean-dir` to separate, dedicated local directories owned by the application user with restricted access.

**Context:** Dirty and clean artifacts live in separate filesystem directories. Dirty/clean separation must be preserved regardless of backend. Shared directories or directories accessible to other processes violate the separation invariant.

**If overriding:**
- Are the configured directories excluded from any backup or sync process that might expose dirty artifacts before promotion?

---

### ST6. Quota-status endpoint

**Question:** Should the application expose a quota-status endpoint so the UI can pre-check available storage before uploading?

**Default:** Yes — expose a quota-status endpoint if the application includes a file-management UI.

**Context:** A quota-status response exposes `{ usedBytes, limitBytes }` so the frontend can warn users approaching capacity and prevent uploads that would exceed the quota without a round-trip failure.

---

### ST7. Event delivery mechanism

**Question:** Should in-process lifecycle events use Spring `ApplicationEvent` (the default) or an external event bus?

**Default:** Spring `ApplicationEvent` — the standard's named default for standalone in-process event decoupling. Use an external bus only if cross-process or cross-service event delivery is explicitly required.

**Context:** `FileScanEvent` publication uses Spring's in-process event mechanism. The listener that triggers record-level ingestion receives the `DOWNLOADED` event via the same in-process bus. Substituting an external bus is a non-standard extension that changes the transactional binding and adds infrastructure not covered by the standard.

**If overriding (external event bus required):**
- What broker or event infrastructure will be used?
- Who owns the relay mechanism — the file framework or the application?
