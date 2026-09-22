# File Management — Backend Questions, Retain-for-Download (AWS Profile)

> **Usage:** Defaults are the recipe reference values; confirm or override each. Applies only when a component is wired for retain-for-download.

Retain-for-download mode — the clean artifact stays in the Clean Store and is downloadable by the owning principal.

Standard reference: [Retain-for-Download Mode](../../standards/file_management_standards_aws_retain.md)

## Hard rule

- NEVER return a partial bulk-download archive; if any requested file fails the owner-scope or retained-state check, reject the whole request with HTTP 400 without disclosing which file failed.

---

### B15. Clean-artifact retention

**Question:** How long are retained clean artifacts kept?

**Default:** Indefinitely, until the owning principal removes them.

**Context:** Retain mode's contract is owner-controlled retention. If a time-based expiry is required, it is an application policy layered on top — not part of the base lifecycle.

**If overriding (time-based expiry):**
- What is the retention period, and does expiry hard-delete or soft-delete (see backend B14)?
- Is the owner notified before expiry?

---

### B16. Single-download sanitized signalling

**Question:** How is a sanitized file signalled on single download?

**Default:** Emit `X-File-Sanitized: true` when `originalHash != null` (the authoritative sanitized signal). This is a framework responsibility; acting on it (user-facing "modified during scanning" notice) is the integrator's choice.

**Context:** `originalHash` is populated only for `Sanitized` verdicts, so the header derives from metadata without interpreting verdict enums.

---

### B17. Bulk-download archive naming and assembly

**Question:** Confirm the bulk-download archive contract.

**Default:** ZIP named `files.zip`; every file must be owned by the caller and in retained `DOWNLOADED` state; assembled to a bounded server-side temp file first, then streamed; temp file deleted on success and every failure path; count limit per backend B5.

**Context:** Streaming artifacts directly into the committed response would allow a truncated ZIP on mid-stream failure with no recoverable error signal — prohibited.
