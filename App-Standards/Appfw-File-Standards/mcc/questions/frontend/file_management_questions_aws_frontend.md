# File Management — Frontend Questions (AWS Profile)

## Hard rules

### Security & Privacy
- NEVER reveal that a file exists if the caller does not own it; always display a generic "Not Found" message.
- NEVER surface raw error details for ownership misses; use generic messages with a `traceId`.
- NEVER display or log internal storage paths, credentials, or signed URLs.

### Architecture & Data Integrity
- NEVER imply that dirty content is downloadable.
- NEVER offer download actions for any file that is not in a fully ready/downloadable state.
- NEVER imply that `DOWNLOADED_FILE_MISMATCH` is safe to download.
- NEVER submit to transport without performing mandatory client-side checks (file size, zero-byte file, required fields).
- NEVER perform authoritative MIME or Magic-Byte detection on the client; let the backend determine the true file type.
- NEVER surface errors without using RFC 9457 Problem Details.
- NEVER provide a user-facing retry button for async lifecycle failures.

Questions S6, S9, S13, S14 are mode-specific and live in `file_management_questions_aws_frontend_retain.md` and `file_management_questions_aws_frontend_ingest.md`.

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
- Should a detected duplicate block the specific file or the entire batch?

---

### S4. displayName character rules (if S1 = Yes)

**Question:** Should the displayName character rules be shown as inline help all the time, or only after the user types something that fails the regex?

**Default:** Show only after the first invalid character — inline error below the field.

**Context:** Always-on help adds visual noise; on-error display reaches the user at the moment of confusion without cluttering the form.

---

### S5. Owner-scope miss UX

**Question:** How should owner-scope misses appear to users?

**Default:** Identical to missing files; one generic "not found" message.

**Context:** If a user requests a file they don't own, the UI displays the same "not found" message as for a file that doesn't exist — never "you don't have access." Hard rule: don't reveal that a non-owned file exists.

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

### S10. Post-submit behavior

**Question:** After the last file in a batch is submitted, what should the page do?

**Default:** Navigate to the file list. While an upload is in progress, attach a `beforeunload` listener to show the browser's native "Leave site?" dialog. Remove the listener once all in-flight requests complete.

**Context:** The upload form has nothing left to show once submitted; the file list is where progress and next actions live. `beforeunload` is the only reliable cross-browser mechanism for guarding against mid-upload navigation.

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
- Confirm with your backend: if sorting or filtering (by date, status, name) is wanted, does the list endpoint support the required query parameters?

---

### S15. Error surfacing

**Question:** How should errors be surfaced?

**Default:** RFC 9457 Problem Details: show safe `title`/`detail` and expose `traceId`.

**Context:** Backend already returns RFC 9457; `traceId` lets users get support without leaking internals.

**If overriding:**
- Does an admin diagnostic view exist for users with that role to link out to?

---

### S16. Classification notice

**Question:** For internal applications serving public officers, what is the highest permitted security and sensitivity classification for input data for file uploads?

**Default:** Confirm with policy/security, then display the classification at or near the file upload input field. Use IM8's two-dimension scheme — do not substitute an informal term like "Unclassified":
- **Security Classification:** `OFFICIAL_OPEN`, `OFFICIAL_CLOSED`, `RESTRICTED`, `CONFIDENTIAL`
- **Sensitivity Classification:** `NON_SENSITIVE`, `SENSITIVE_NORMAL`, `SENSITIVE_HIGH`

**Context:** The classification notice is mandatory per the standard — treat unconfirmed classification as a release blocker. The upload form must not ship to production until the classification is confirmed in writing by the policy/security team.

---

### S17. Accessibility

**Question:** What accessibility standard must the file management UI meet, and which components require explicit ARIA decisions before implementation?

**Default:** WCAG 2.1 AA. Confirm with the team.

**Context:** Before implementation, confirm ARIA labels for: (1) the multi-file input and per-file `displayName` fields — each input needs an associated `<label>`; (2) status badges — use `role="status"` or `aria-live="polite"` for polling updates; (3) the trace-ID "Show details" toggle — use `aria-expanded` to reflect open/closed state.

---

### S18. Lifecycle state labels

**Question:** Should users see raw lifecycle states or friendly stage labels?

**Default:** Friendly stages for users; raw states only in admin/details. Suggested mapping:
- Queued → `PENDING_SCAN`
- Scanning → `PENDING_SCAN_RESPONSE`
- Finalizing → `PENDING_DOWNLOAD`
- Ready → `DOWNLOADED` (retain-for-download components)
- Processed → `DOWNLOADED` (ingest-and-delete components)
- Failed → all terminal failures

**Context:** Raw states leak backend implementation; admins still need them for diagnostics. The label for `DOWNLOADED` should reflect the component's mode to avoid confusing users.

**If overriding:**
- If showing raw states to regular users, should they be displayed as stylized status badges or plain text?
- Should the UI include a tooltip explaining what the technical state means?

---

### S19. Security outcome messaging

**Question:** Should `BAD_RESULT` (blocked) and `DOWNLOADED_FILE_MISMATCH` (integrity failure) share copy or be distinct?

**Default:** Distinct, with user-safe wording:
- BAD_RESULT: "This file was blocked during security scanning and cannot be downloaded."
- DOWNLOADED_FILE_MISMATCH: "This file failed integrity verification — re-upload the original file."

**Context:** BAD_RESULT implies potentially malicious content — a security decision. DOWNLOADED_FILE_MISMATCH means the file was corrupted in transit (the scanner verdict was `Unchanged` but the hash doesn't match). Note: `Sanitized` files (where the scanner intentionally modified content) are accepted and finalized as `DOWNLOADED` — they are downloadable and do not produce this state.

**If overriding:**
- Should the message explicitly mention security threats (e.g., "Malicious content blocked") or remain neutral (e.g., "File could not be verified")?
- If using a shared message for both outcomes, what is the single, security-approved phrase?

---

### S20. Status update strategy

**Question:** How should users receive file status updates while viewing the file list?

**Default:** HTTP polling every 3s, checking only the current page. Stop polling when all visible rows are terminal. Pause in background tabs and resume on refocus. Show "Taking longer than usual" after 30s. On poll failure: retry silently (3 attempts, exponential backoff), then surface a single non-blocking banner: "Status updates paused — refresh to check progress." On 401, redirect to re-authentication.

**Context:** Polling avoids adding real-time infrastructure. Reusing the existing data layer for polling keeps caching, deduping, and refocus behavior consistent. A single banner on exhaustion avoids toast spam on flapping networks.

**If overriding (push updates):**
- Does the backend already expose an SSE stream, WebSocket channel, or shared notifications channel, or does this feature need to add one?
- Which events should the push channel carry — only this user's file events, or all events the user is subscribed to?
- SSE is preferred for one-way server-to-browser feeds (file-status updates). WebSocket is only worth it if the backend already exposes one and reusing it is cheaper than adding SSE.

---

### S21. Retry button

**Question:** Should users see a retry button for async failures?

**Default:** No user-facing retry. Surface "system will retry automatically" (during transient failures) or terminal failure (after retry exhaustion).

**Context:** Hard rule: callers don't retry async lifecycle failures; scheduled work owns retries via `jobAttempts`. The backend standard provides no caller-driven retry endpoint — admin retry tooling requires a separate backend capability.

**If overriding (backend has a retry endpoint):**
- Which terminal failure states does it support, and which are permanently failed?

---

### S22. Admin diagnostics

**Question:** What should admin diagnostics show?

**Default:** Lifecycle state, `jobAttempts`, scanner operation category, timestamps, safe error summary. Hide raw content, presigned URLs, credentials.

**Context:** Admins need diagnostics; logs/UI must not leak sensitive material per standard. Presigned download URLs must never be displayed in any UI — the standard classifies them as credentials.
