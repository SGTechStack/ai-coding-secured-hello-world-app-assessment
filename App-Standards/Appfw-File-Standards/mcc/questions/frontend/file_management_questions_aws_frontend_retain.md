# File Management — Frontend Questions, Retain-for-Download (AWS Profile)

Retain-for-download mode — clean artifacts stay downloadable by the owning principal.

Standard reference: [Retain-for-Download Mode](../../standards/file_management_standards_aws_retain.md)

## Hard rule

- NEVER allow bulk download to partially succeed; never select non-ready files for a ZIP archive.

**Note:** S9 and S14 apply only when the bulk-download endpoint is exposed (backend B5 = Yes). Skip them if bulk download is not supported.

---

### S9. Bulk-download selection UX (if B5 = Yes)

**Question:** When a user selects files for bulk download, but some selected files are not actually eligible for download yet, should the invalid ones be automatically un-ticked, or left ticked with the problem highlighted?

**Default:** Leave ticked and highlight the problem with a per-row reason.

**Context:** Silently un-ticking hides what happened; highlighting the reason lets the user make an informed decision.

---

### S13. Download action visibility

**Question:** When should the download action be available?

**Default:** Hide until status is `DOWNLOADED`.

**Context:** Hard rule: dirty content must never appear downloadable. The download button doesn't appear until the file is fully ready.

---

### S14. Bulk-download selection rules (if B5 = Yes)

**Question:** What should bulk-download selection allow?

**Default:** Only `DOWNLOADED` files owned by the caller. No partial archives. The ZIP is always named `files.zip`.

**Context:** If the server rejects the request with HTTP 400 (because one or more files no longer pass the owner-scope or retained-state check), the frontend re-fetches the file list, compares against the current selection, and highlights rows whose status has changed — prompting the user to deselect them before retrying. The backend does not disclose which specific file caused the rejection (owner-scope non-disclosure).

---

### S23. Delete confirmation UX

**Question:** Should the UI show a confirmation dialog before deleting a file?

**Default:** Yes — a modal confirmation naming the file's `displayName`. Single-click delete without confirmation risks accidental irreversible loss.

**Context:** Hard delete (the standard default per B14) is irreversible — clean artifact and metadata are gone on confirm. If soft delete is configured (B14 override), the confirmation can be lighter since recovery is possible.

**If overriding (no confirmation):**
- Is undo or recovery available within a time window after deletion?
- Has this been confirmed acceptable with the UX team given the irreversibility?

---

### ARIA note

Bulk-download checkboxes (S14) each need `aria-describedby` linking to their row's current status so screen readers convey eligibility.
