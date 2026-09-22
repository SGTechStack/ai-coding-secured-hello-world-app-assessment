# File Management — Retain-for-Download Mode (AWS Profile)

Parent: [File Management Standards — AWS Profile](../index.md)
Prerequisite: [Core Lifecycle](./file_management_standards_aws_core.md)

## 1. Mode Definition

* **Per-component choice**: Expressed through which beans and storage configuration are wired at startup. A single upload endpoint implements one mode, but an application may host multiple upload components with different modes.
* **Behavior**: The clean artifact stays in the Clean Store after reaching `DOWNLOADED` and is downloadable through the application API by the owning principal.
* **Clean Store retention**: Clean artifacts persist indefinitely until explicitly removed by the owner.

## 2. Application Retrieval and Removal Flow

### 2.1 Authentication
Require an authenticated principal for list, single-download, bulk-download, and remove operations.

### 2.2 Owner Scope Enforcement
Limit retrieval and removal to files uploaded by the current principal.
* *Decision*: If the file is not owned by the caller, treat the request as not found. Do not disclose whether a non-owned file exists.

### 2.3 Single Download
Return the clean file as an attachment only when the file is in `DOWNLOADED` state. Validate the response header values before writing `Content-Type` and `Content-Disposition`. When the scanner verdict is `Sanitized`, include an `X-File-Sanitized: true` response header. This is a framework responsibility; acting on it (e.g. surfacing a user-facing notice that the file was modified during scanning) is an integrator choice.

### 2.4 Bulk Download
When bulk download is exposed, package the files into a ZIP attachment named `files.zip`.

* *Decision*: Every requested file must belong to the current principal and must already be in the retained final downloadable state. Do not return a partial archive. If any file fails either check, reject the entire request with HTTP 400. The response body must not identify which file ID failed or whether that file exists — exposing per-file failure detail leaks ownership information and violates the owner-scope non-disclosure rule.
* *Memory model*: Write all artifacts to a bounded server-side temp file first, then stream the completed archive as the response. Do not stream artifacts directly into the response output stream — once the HTTP response is committed, mid-stream artifact failures (concurrent removal, S3 fault) produce a truncated ZIP with no recoverable error signal to the client. The temp file approach enforces the partial-archive invariant before any response bytes are written. Worst-case disk footprint is bounded by `fileCount × maxFileSize`; with the configured file count limit and an enforced per-file size limit this ceiling is known and configurable. The temp file must be deleted after the response is written or on any failure path.

* **File Count Limit**: Bulk download requests must be subject to a configurable maximum file count. The standard default is **10 files per request**. Applications may override this limit, but must document the override and verify that the resulting worst-case disk footprint (`fileCount × maxFileSize`) remains acceptable.

### 2.5 Removal
Allow deletion only when the current principal owns the file.

## 3. Inputs / Outputs (Mode-Specific Additions)

* **Single Download Response**: Return the file as an attachment with validated `Content-Type` and `Content-Disposition` header values. When the scanner verdict is `Sanitized`, include `X-File-Sanitized: true` in the response headers.
* **Bulk Download Response**: When bulk download is supported, return a ZIP attachment named `files.zip`.
* **Bulk Download Rule**: Every requested file must be owned by the caller and already be in the retained final downloadable state.

## 4. Error Contract (Mode-Specific Additions)

These codes supplement the shared error contract defined in Core §3.2:

| Code | Meaning |
|:---|:---|
| **400 Bad Request** | Bulk-download requests where any requested file fails the owner-scope or retained-state check. The 400 must not identify which file failed — the response body states only that the request was rejected as a whole. |
| **400 Bad Request** | Bulk-download requests that exceed the configured maximum file count limit. |
| **403 Forbidden** | Attempt to download a file not in retained `DOWNLOADED` state. |

## 5. Enforced Constraints (Negative Requirements)

These supplement the shared negative requirements in Core §4.7:

| Constraint | Standard Expectation |
|:---|:---|
| Streaming artifacts directly into bulk-download ZIP response | Write all artifacts to a bounded temp file first; streaming commits the HTTP response before all artifacts are verified, making the partial-archive invariant unenforceable |
| Bulk download without a configured maximum file count | A maximum file count must be configured; without it the bounded disk footprint guarantee in §2.4 does not hold |

## 6. Observability (Mode-Specific Addition)

| Observable Signal | Meaning | Action |
|:---|:---|:---|
| Clean-store cleanup absent on retain paths | Clean artifacts may be incorrectly deleted | Verify retain-mode bean wiring; clean artifacts should persist until owner removes them |

## 7. Test & Validation (Mode-Specific)

### 7.1 Integration Tests

* **Owner Scope Enforcement**: Verify that a cross-principal access attempt returns the same not-found response as a missing file.
* **Single Download**: Verify clean content is returned with validated headers when file is in `DOWNLOADED` state.
* **Non-Downloadable State**: Verify HTTP 403 when attempting to download a file not in `DOWNLOADED` state.
* **Bulk Download Rejection**: Verify HTTP 400 when any file in the bulk request is non-owned or not in `DOWNLOADED` state. Verify the response does not identify which file failed.
* **Bulk Download Success**: Verify a valid ZIP named `files.zip` is produced when all files pass checks.
* **Temp File Cleanup**: Verify temp file is deleted on both success and failure paths.
* **Error Code Paths**: Verify HTTP 403 (non-downloadable state) in addition to the shared error codes tested in Core §5.2.
* **Bulk Download Count Limit**: Verify HTTP 400 when the request exceeds the configured maximum file count.
* **Sanitization Header**: Verify `X-File-Sanitized: true` is present in single-download responses when the scanner verdict is `Sanitized`, and absent when the verdict is `Unchanged`.

## 8. Changelog

| Version | Date | Description | Files Changed |
|:---|:---|:---|:---|
| 1.0.2 | 2026-06-29 | Decoupled bulk download file count justification from SFS concurrency cap; limit is now justified independently by bounded temp disk footprint. | Retain |
| 1.0.1 | 2026-06-23 | Added `X-File-Sanitized` response header for sanitized file downloads; added default bulk download file count limit of 10 files per request; added enforced constraint and error code for count limit violations. | Retain |
