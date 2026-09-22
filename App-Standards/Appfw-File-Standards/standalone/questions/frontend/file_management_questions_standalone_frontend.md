# File Management — Frontend Questions (Standalone Profile)

This profile has **no external scanner**. Promotion happens locally and near-instantly after validation, so the lifecycle has only two operational states and no scan-verdict UX.

## Hard rules

### Security & Privacy
- NEVER reveal that a file exists if the caller does not own it; always return a generic "Not Found" message.
- NEVER surface raw error details for ownership misses; use generic messages with a `traceId`.
- NEVER display or log raw file content, credentials, or internal storage paths.

### Architecture & Data Integrity
- NEVER imply that dirty content is downloadable.
- NEVER offer download actions for any file that is not in a fully ready/downloadable state.
- NEVER allow bulk download to partially succeed; never select non-ready files for a ZIP archive.
- NEVER submit to transport without performing mandatory client-side checks (file size, zero-byte file, required fields).
- NEVER perform authoritative MIME or Magic-Byte detection on the client; let the backend determine the true file type.
- NEVER surface errors without using RFC 9457 Problem Details.
- NEVER imply that retries are automatic on the server side (standalone has no auto-retry).
- NEVER imply that storage quota does not apply.
- NEVER imply that 'stuck' or dirty files will be automatically cleaned up; standalone requires manual admin intervention.

---

### S1. Multi-file upload

**Question:** Should the upload entry accept multiple files at once with a per-file `displayName` field?

**Default:** Yes — multi-file with per-file `displayName`.

**Context:** Backend takes one file per request; `displayName` is required per the contract. Users can drag in several files at once, and each file gets its own `displayName` field before submitting.

---

### S2. displayName pre-fill (if S1 = Yes)

**Question:** Should the displayName field be pre-filled from the filename, or left blank?

**Default:** Pre-filled from the filename.

**Context:** The displayName regex (`^[a-zA-Z0-9_\- ]+$`) means most filenames need editing, but pre-filling is less friction than typing from scratch.

---

### S3. displayName uniqueness (if S1 = Yes)

**Question:** Can two files share the same displayName, or is uniqueness enforced?

**Default:** Confirm with backend (B8). If uniqueness is enforced, validate client-side and show an inline error when a duplicate name is detected.

**Context:** Uniqueness is a backend policy; the UI must mirror it to avoid confusing post-upload errors.

**If overriding:**
- Does the backend return a standard 409 Conflict for duplicate names, or a 400 Validation error?
- Should the duplicate check run across all existing files in the list, or only within the current upload batch?

---

### S4. displayName character rules (if S1 = Yes)

**Question:** Should the displayName character rules be shown as inline help all the time, or only after the user types something that fails the regex?

**Default:** Show only after the first invalid character — inline error below the field.

**Context:** Always-on help adds visual noise; on-error display reaches the user at the moment of confusion without cluttering the form.

---

### S5. Owner-scope miss UX

**Question:** How should owner-scope misses appear to users?

**Default:** Identical to missing files; one generic "not found" message.

**Context:** If a user requests a file they don't own, the UI displays the same "not found" message as for a file that doesn't exist. Hard rule: don't reveal that a non-owned file exists.

---

### S6. Ingest-and-delete file appearance (if B4 = delete)

**Question:** Can the files shown in this UI go through the ingest-and-delete pipeline, where the clean artifact is deleted after ingestion?

**Default:** If the pipeline applies, the UI must show ingested-and-deleted files distinctly and hide/disable their download action. If it doesn't apply, treat `DOWNLOADED` as permanently downloadable.

**Context:** Once a file is ingested and its clean artifact removed, the download button is no longer active. The UI explicitly states the file has been processed. Hard rule: never imply a non-downloadable file is downloadable.

**If overriding:**
- Which specific backend state indicates "Ingested and Deleted"?
- Once ingested, should the file row remain in the list with a disabled download button, or be removed entirely?

---

### S7. Partial batch submission

**Question:** When one file in a multi-file batch fails client-side validation, should the remaining valid files be submitted or the whole batch be blocked?

**Default:** Submit valid files and flag only the failing ones in place.

**Context:** Blocking the whole batch on one failure forces the user to re-select all files after fixing one — disproportionate friction in a multi-file flow.

---

### S8. Trace ID surfacing

**Question:** How should the trace ID be surfaced in the error UI?

**Default:** Behind a "Show details" toggle, with a "Copy trace ID" button revealed on expand.

**Context:** Most users won't need the trace ID; displaying it by default adds noise. A toggle keeps the primary error clean while making the ID accessible for support escalations.

**If overriding:**
- If not using a toggle, should the trace ID be a simple copy-to-clipboard icon, or displayed as small gray text below the error title?

---

### S9. Bulk-download selection UX (if B5 = Yes)

**Question:** When a user selects files for bulk download, but some selected files are not actually eligible for download yet, should the invalid ones be automatically un-ticked, or left ticked with the problem highlighted?

**Default:** Leave ticked and highlight the problem with a per-row reason.

**Context:** Silently un-ticking hides what happened; highlighting the reason lets the user make an informed decision.

---

### S10. Post-submit behavior

**Question:** After the last file in a batch is submitted, what should the page do?

**Default:** Navigate to the file list.

**Context:** The upload form has nothing left to show once submitted; the file list is where progress and next actions live.

**If overriding:**
- Should the upload form clear for a new batch, or show a "Success" summary of the uploaded files?

---

### S11. Client-side validations

**Question:** Which validations should run client-side before submit?

**Default:** Pre-check size, zero-byte, required `displayName`, regex match; show accepted-type guidance. Let the backend decide MIME/Magic-Bytes.

**Context:** Backend is authoritative on type detection; client pre-checks reduce wasted requests but cannot replace backend checks.

---

### S12. File list pagination

**Question:** How should the file list handle large result sets?

**Default:** Cursor-based pagination with a "load more" button. Default page size 25. Confirm the backend supports cursor-based pagination; fall back to offset if it doesn't.

**Context:** Cursor-based pagination handles insertions/deletions at the top gracefully. Page size 25 is a good default for a lifecycle UI.

**If overriding:**
- Confirm with your backend: if sorting or filtering is wanted, does the list endpoint support the required query parameters?

---

### S13. Download action visibility

**Question:** When should the download action be available?

**Default:** Hide until status is `DOWNLOADED`.

**Context:** Hard rule: dirty content must never appear downloadable. The download button doesn't appear until the file is fully ready.

---

### S14. Bulk-download selection rules (if B5 = Yes)

**Question:** What should bulk-download selection allow?

**Default:** Only `DOWNLOADED` files owned by the caller. No partial archives. The ZIP is always named `files.zip`.

**Context:** Users can only tick files that are fully ready and that they own. If any selected file isn't ready, the bulk download fails — never a half-empty ZIP.

---

### S15. Error surfacing

**Question:** How should errors be surfaced?

**Default:** RFC 9457 Problem Details: show safe `title`/`detail` and expose `traceId`.

**Context:** Backend already returns RFC 9457; `traceId` lets users get support without leaking internals.

---

### S16. Classification notice

**Question:** For internal applications serving public officers, what is the highest permitted security and sensitivity classification for input data for file uploads?

**Default:** Confirm with policy/security, then display the classification at or near the file upload input field.

**Context:** The classification notice is mandatory per the standard. The upload form must not ship to production until the classification is confirmed.

---

### ST1. Lifecycle state labels

**Question:** Should users see raw lifecycle states or friendly stage labels?

**Default:** Friendly stages for users; raw states only in admin/details. Suggested mapping:
- Processing → `PENDING_SCAN` (brief — promotion is near-instant)
- Ready → `DOWNLOADED`
- Failed → terminal failure

**Context:** Raw states leak backend implementation. The standalone state machine is simple (two states), but admins still need raw values for diagnostics. "Scanning" is **not** an appropriate label — there is no external scanner.

---

### ST2. Status update strategy

**Question:** How should the UI track file promotion and async outcomes (like ingestion or quota warnings)?

**Default:** Refetch once 1s after upload, using `queryClient.invalidateQueries`. If the file is being ingested row-by-row, show progress on the file row or details panel.

**Context:** Standalone promotion is instant (milliseconds), so one refetch is enough. No persistent polling loop is needed.

**If overriding (push updates):**
- Does the backend already expose an SSE stream or WebSocket channel, or does this feature need to add one?

---

### ST3. Retry button

**Question:** Should users see a retry button after a failure?

**Default:** Manual retry only for transient infra failures (5xx, network errors). No retry for validation (400), permission (403), not-found (404), record ingestion (422), or quota (507). Never auto-retry.

**Context:** Standalone has no retry infrastructure — transient server errors have no automatic recovery path, so the UI must provide a manual retry for those. Bad input or quota issues require the user to fix something first.

---

### ST4. Storage quota UX

**Question:** How should storage-quota (HTTP 507) be handled?

**Default:** Pre-check the upload against known quota usage before submitting (if the API exposes it). On 507, show a clear "Storage full" message that explains current usage vs. limit and tells the user to delete or archive files first. Show a quota indicator (used / available / total) and warn at ~80% used.

**Context:** Standalone enforces a local quota (default 5 GB). Hitting 507 with no pre-check or usage feedback feels like an outage; users will retry the same upload repeatedly.

---

### ST5. Admin diagnostics

**Question:** What should admin diagnostics show?

**Default:** Lifecycle state, error category (validation / persistence / promotion / ingestion / quota), file metadata (size, MIME, extension, SHA-256, upload timestamp, principal), event history (`PENDING_SCAN`, `DOWNLOADED`), safe error summary. Hide raw file content, credentials, full content payloads.

**Context:** Admins need diagnostics; the standalone state machine is small, so value comes from metadata, error categorization, and event history.

---

### ST6. Batch quota handling

**Question:** For multi-file uploads where some files would fit within the remaining quota and some would not, should the UI upload as many as fit or block the entire batch?

**Default:** Block the entire batch and flag which files would exceed the quota.

**Context:** Partial uploads create confusing incomplete state; surfacing which specific files exceed the limit lets the user make an informed decision.

---

### ST7. HTTP error code mapping

**Question:** How should specific HTTP error codes be mapped to user-facing copy?

**Default:** Map known statuses to specific copy:
- 400: Validation failure (specific reason from Problem Details)
- 403: Download not allowed (file not in downloadable state)
- 404: Not found (or owner-scope miss — same message)
- 422: Record ingestion failure
- 507: Storage quota exceeded
- 500: Server error

**Context:** Tailored copy helps users fix problems instead of guessing why an operation failed.

---

### ARIA note

Bulk-download checkboxes (S14) each need `aria-describedby` linking to their row's current status so screen readers convey eligibility.
