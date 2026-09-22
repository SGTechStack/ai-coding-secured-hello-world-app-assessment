# File Management — Backend Questions (AWS Profile)

> **Usage:** Defaults below are the recipe reference values from `Appfw-File-Standards/mcc/`; confirm or override each before implementation.

Standard reference: [Core Lifecycle](../../standards/file_management_standards_aws_core.md)

> **Build tool:** Maven is the required build tool for all MCC projects. Use `pom.xml` — Gradle is not supported.

## Hard rules

### Security & Data Integrity
- NEVER let dirty content be downloadable; only files in retained `DOWNLOADED` state are served.
- NEVER trust client-sent content type; verify Magic Bytes server-side.
- NEVER delete the dirty blob before the state-transition transaction commits.
- NEVER publish `FileScanEvent` outside the transactional outbox (same transaction as the state change).
- NEVER write presigned URLs, tokens, credentials, or raw content to logs, events, or persistent diagnostic fields.
- NEVER disclose whether a non-owned file exists — return the same not-found response as a missing file.
- NEVER return `boolean` from SFS HTTP operations; return typed responses or throw typed exceptions.

### Architecture
- ALWAYS keep core lifecycle rules dependent on ports, not AWS transport/storage internals.
- ALWAYS use the MCC Shared Auth Foundation's background client for scheduled scanner work.
- ALWAYS validate the startup constraints (retry windows; ingest ZIP/OOXML exclusion) and refuse to start on violation.

---

### B1. Maximum file size

**Question:** What is the enforced maximum upload size, and is the Spring multipart `max-file-size` aligned to it exactly?

**Default:** Set one application-level limit and make the Spring multipart resolver `max-file-size` (and `max-request-size`) equal to it. A mismatch causes either false rejections (resolver lower) or unbounded buffering before validation (resolver higher/unlimited).

**Context:** Size is checked before `getBytes()`; full materialization after a bounded size check is acceptable. Pick a concrete value with your platform team (e.g. 25 MB) — there is no universal default.

**If overriding:**
- Do different upload components need different size limits, or one global limit?

---

### B2. Accepted formats and per-component MIME types

**Question:** Which file formats are accepted, and is accepted-type configuration global or per-component?

**Default:** Per-component `acceptedMimeTypes`. Supported Magic Bytes registry: PDF (`25 50 44 46 2D`), PNG (`89 50 4E 47 0D 0A 1A 0A`), JPEG (all five variants), ZIP/OOXML (`50 4B 03 04`, upload+scan only). Ingest-and-delete components MUST exclude ZIP/OOXML.

**Context:** Each upload component has its own endpoint and its own `acceptedMimeTypes` configuration. The retain endpoint validates against its MIME set (e.g. PDF, PNG, ZIP), and the ingest endpoint validates against its own set (e.g. CSV, plain text). This separation is enforced at the endpoint level, not by a shared validator with a mode flag.

**Endpoint separation rule:** When multiple upload components are co-hosted, each component MUST have its own dedicated upload endpoint with its own path. Do not use a single upload endpoint with a mode parameter — this mixes concerns, makes validation rules ambiguous, and confuses API consumers. Example:
- `POST /api/files/upload` → retain-for-download component (documents)
- `POST /api/records/upload` → ingest-and-delete component (data files)

Each endpoint enforces its own `acceptedMimeTypes`, returns its own error contract, and the frontend can present distinct UI for each mode (drag-drop documents vs import a data file).

---

### B3. Ingestion failure terminal state

**Question:** Is the ingestion failure terminal state surfaced distinctly from scan-security failures (`BAD_RESULT`, `DOWNLOADED_FILE_MISMATCH`)?

**Default:** Yes. Ingestion terminal states (`FAILED`, `REJECTED`, `REJECTED_EMPTY`, `COMPLETED_WITH_ERRORS`) are distinct from scan-lifecycle terminal states (`BAD_RESULT`, `DOWNLOADED_FILE_MISMATCH`), are reported by the status endpoint, and drive distinct frontend badges. `COMPLETED` means all rows loaded successfully. `COMPLETED_WITH_ERRORS` means some loaded, some rejected. `REJECTED` means all rows failed validation. `REJECTED_EMPTY` means no data rows. `FAILED` means unparseable file.

**Context:** Users must understand a file failed during processing (after scanning succeeded), not during security scanning. Applies only when ingest-and-delete mode is active.

---

### B4. Blob-store backend (dirty + clean)

**Question:** S3-backed or database-backed blob store?

**Default:** S3-backed (the standard default). Use dedicated dirty/clean buckets, private IAM access, and server-side encryption.

**Context:** `file.aws.storage-backend` selects the single `BlobStorage` implementation for both dirty and clean blobs. Choose `DATABASE` for low-to-moderate volume, small files, and simpler transactional persistence (no S3/LocalStack dependency). Choose `S3` for high retained-file volume, longer retention, or operational separation from the metadata database. The backend is replaceable behind the `BlobStorage` port without changing lifecycle rules.

**If overriding (database-backed):**
- Confirm the `FILE_CONTENT_DIRTY` / `FILE_CONTENT_CLEANED` tables and lazy LOB mapping (Hibernate 7) are used.
- The `dirty-bucket` and `clean-bucket` properties are unused in this mode.

---

### B5. Bulk download exposure

**Question:** Is the bulk-download endpoint (`POST /bulk-download`) exposed, and if so, what is the maximum file count?

**Default:** Confirm with product. If exposed, default maximum is **10 files per request**. Frontend retain questions S9/S14 are conditional on this being Yes.

**Context:** The count limit is justified independently by the bounded worst-case temp-disk footprint `fileCount × maxFileSize` (not by the SFS concurrency cap). Overrides must document the resulting footprint as acceptable. The archive is assembled to a bounded server-side temp file first, then streamed; never stream artifacts directly into the committed response.

**If overriding:**
- What count limit, and what is the resulting `fileCount × maxFileSize` ceiling?

---

### B6. Scheduler ownership and coordination

**Question:** Does scheduled lifecycle work run on a dedicated batch-owner node, or on a shared scheduler across multiple application nodes?

**Default:** If lifecycle jobs may run on more than one node, use ShedLock (`SHEDLOCK` table) for scheduler ownership plus `PESSIMISTIC_WRITE` row locking for per-row contention. A dedicated single batch owner needs neither.

**Context:** Do not add ShedLock to a dedicated batch-owner deployment "for consistency." Prefer DB-backed time so lock ownership does not depend on node-local clock drift. `SKIP LOCKED`/`READPAST` are prohibited — a locked row must raise an immediate exception.

---

### B7. Retry and timeout tuning

**Question:** What values for `jobRetryLimit`, `phase3RetryLimit`, `scanDurationTimeout`, the pending-scan cleanup interval, and the polling cron?

**Default:** `job-retry-limit: 3`, `scan-duration-timeout: 30` (min), `pending-scan-database-clear-interval-in-days: 1`, `polling-interval-cron: "0/30 * * * * *"`, transaction timeouts per recipe 21/23. The app validates and refuses to start unless `jobRetryLimit × cycleInterval < clearInterval` AND `phase3RetryLimit × cycleInterval < 24h`.

**Context:** The cycle interval is the effective retry delay (no intra-cycle backoff). The 24-hour SFS retention window is a hard external deadline; tuning must keep polling and Phase 3 retrieval inside it.

---

### B8. displayName uniqueness

**Question:** Is `displayName` unique per principal, or may a principal have duplicates?

**Default:** Not enforced — duplicates allowed. `displayName` must match `^[a-zA-Z0-9_\- ]+$` and is required.

**Context:** If uniqueness is enforced, the frontend mirrors it (S3). Decide the error contract if enforced.

**If overriding (uniqueness enforced):**
- Return 409 Conflict or 400 Validation for duplicates?
- Uniqueness scope: per principal across all files, or only within an upload batch?

---

### B9. Scanner bypass environments

**Question:** In which environments is scanner bypass (`file.scanner.enabled=false`) permitted?

**Default:** Local development only (`dev-mcc`). Never in SIT/UAT/PROD. The scheduler still runs in bypass mode; the bypass workflow promotes `PENDING_SCAN → DOWNLOADED` immediately, still writing the final artifact through the configured clean store.

**Context:** Bypass avoids external SFS connectivity for local work. It must not be used where real scanning is required.

---

### B10. Dirty / clean bucket names (S3 backend)

**Question:** What are the S3 dirty and clean bucket names per environment?

**Default:** Dev (LocalStack): `myapp-dirty-dev` / `myapp-clean-dev`. SIT: `appfw-dirty-sit` / `appfw-clean-sit`. These names must match `file.aws.*` config and the LocalStack init script (recipe 29).

**Context:** Dirty/clean separation is enforced by distinct buckets. In deployed environments the platform provisions them (Terraform/CloudFormation) with private IAM and server-side encryption; locally the recipe-29 init script creates them in LocalStack. The scanner must not hardcode a dirty-bucket URL — SFS owns the dirty destination and returns presigned URLs.

---

### B11. Storage locator strategy (S3 / filesystem)

**Question:** How are external clean/dirty locators tracked — flat columns on `FILE_METADATA`, or a `FILE_STORAGE_LOCATION` mapping table?

**Default:** Flat metadata fields (Strategy A) for a single stable backend (e.g. `storage_backend='S3'`, `s3_bucket`, `s3_key`).

**Context:** Use the `FILE_STORAGE_LOCATION` mapping table (Strategy B) for hybrid/multi-cloud environments or systems undergoing storage migration. Database-backed variants use the `FILE_CONTENT_*` blob tables instead.

---

### B12. Uploading principal source

**Question:** How is the uploading principal (`uploadedBy`) captured for owner-scope enforcement?

**Default:** From the authenticated security context (`@AuthenticationPrincipal`). All list/status/download/bulk/remove operations resolve via `findByIdAndUploadedBy`.

**Context:** Owner scope is the sole access-control rule for file operations; a non-owned file is indistinguishable from a missing one.

**If overriding (public access — no inbound auth):**
- If you choose to override owner-scope and make file endpoints publicly accessible (no authenticated principal required), document this as a deliberate deviation. Accept that anyone who knows a file UUID can read its status, download it, or delete it. Security becomes UUID unguessability, not access control. This override must be an explicit conscious decision recorded in an ADR, not a side-effect of omitting auth setup.

---

### B12a. Inbound authentication for file endpoints

**Question:** Is inbound authentication required for file endpoints, or is public access acceptable for this use case?

**Default:** Inbound authentication required — file endpoints require an authenticated principal and owner-scope enforcement.

**Context:** The standard mandates owner-scope as the sole access-control rule. Overriding this to public access (`permitAll()`) is a deliberate deviation that must be documented with its consequences: no owner scope, no per-user isolation, UUID-only security. This question must be answered before implementation — it fundamentally changes the security model, the `SecurityFilterChain` configuration, the repository queries (`findById` vs `findByIdAndUploadedBy`), and the frontend auth guard wiring.

---

### B13. Tags

**Question:** Is the optional `tags` field used, and does it need validation or structure?

**Default:** Optional free-text categorization string, persisted as-is (max length per DDL, `varchar(500)`). No enforced structure.

**Context:** `tags` is not used for owner scope or lifecycle decisions; treat it as opaque metadata unless the application needs otherwise.

---

### B14. Delete semantics

**Question:** Is removal a hard delete (artifact + metadata gone) or a soft delete (recoverable)?

**Default:** Hard delete — clean artifact and metadata are removed on confirm; irreversible. Delete is owner-scoped.

**Context:** The frontend shows a confirmation modal naming the `displayName` (S23) because hard delete is irreversible.

**If overriding (soft delete):**
- What is the recovery window and retention policy for soft-deleted artifacts?
- Does the list/status view hide soft-deleted files, and is there an admin restore path?

---

### B15. Event delivery pattern

**Question:** How are lifecycle events (`FileScanEvent`, `FileIngestionEvent`) delivered to downstream consumers?

**Default:** Transactional outbox — events are written to a `FILE_SCAN_OUTBOX` table in the same database transaction as the state change, then relayed at-least-once to idempotent consumers by a scheduled relay process.

**Context:** The transactional outbox eliminates the dual-write problem (DB commit succeeds but broker publish fails, or vice versa). It is the correct pattern when:
- Events must not be lost (compliance, audit, downstream processing triggers)
- No external message broker is available in the architecture
- At-least-once delivery with idempotent consumers is acceptable

**Alternatives (when outbox is not required):**
- **In-process Spring events** (`@TransactionalEventListener(phase = AFTER_COMMIT)`) — when the consumer is in the same JVM and crash-recovery durability is not needed. Simpler, no outbox table or relay. This is the most common alternative for typical "react after commit" use cases.

For MCC file lifecycle events, the transactional outbox is the **default recommendation** because state transitions must never be silently lost and the architecture does not assume an external broker.

**If overriding:**
- Document which alternative pattern you are using and why (ADR required).
- If using in-process Spring events: accept that events are lost on JVM crash between state commit and event handling. Confirm this is acceptable for your compliance/audit requirements.
- The hard rule "NEVER publish `FileScanEvent` outside the transactional outbox" applies only when the outbox pattern is selected. If you choose in-process events, replace this rule with: "events are published only after commit via `@TransactionalEventListener(AFTER_COMMIT)`".

---

### B16. Scanner bypass in local development

**Question:** Does the project need a local development scanner bypass, and in which profiles is it permitted?

**Default:** Yes — `file.scanner.enabled=false` in `dev-mcc` profile. The scheduler still runs and promotes `PENDING_SCAN → DOWNLOADED` immediately through the clean store so a developer can test the full upload-to-download flow without SFS connectivity.

**Context:** Without the bypass, local development requires a running mock-SFS container that simulates scan delays. The bypass is faster for UI and integration work. It is never permitted in SIT/UAT/PROD — the `LifecycleStartupValidator` enforces this at boot.

---

### B17. Bulk download limits

**Question:** What is the configurable maximum number of files per bulk download request, and how is it enforced?

**Default:** 10 files per request (`app.file.bulk-download.max-files`). Exceeding the limit returns a 400 rejection with a single whole-request message that does not identify which file caused the issue.

**Context:** The count limit bounds the worst-case server-side temp-disk footprint (`fileCount × maxFileSize`). The archive is assembled to a bounded temp file before being returned — never streamed directly. Operators should size this limit based on available temp disk and expected file sizes. If bulk download is not needed for the use case, confirm and omit the endpoint entirely.

**If overriding:**
- What count limit, and what is the resulting max disk footprint?
- Is bulk download needed at all for this use case?
