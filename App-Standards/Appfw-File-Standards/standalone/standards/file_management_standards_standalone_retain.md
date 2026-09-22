# File Management — Retain-for-Download Mode (Standalone Profile)

Parent: [Standalone File Management Index](../index.md)
Prerequisite: [Core Lifecycle](./file_management_standards_standalone_core.md)

## 1. Mode Definition

* **Per-component choice**: Expressed through which beans and storage configuration are wired at startup. A single upload endpoint implements one mode, but an application may host multiple upload components with different modes.
* **Behavior**: After reaching `DOWNLOADED`, the clean artifact stays in the Clean Store and is downloadable through the application API by the owning principal.
* **Clean Store retention**: Clean artifacts persist indefinitely until explicitly removed by the owner.

## 2. Application Retrieval and Removal Flow

### 2.1 Authentication
Require an authenticated principal for list, single-download, bulk-download, and remove operations.

### 2.2 Owner Scope Enforcement
Limit retrieval and removal to files uploaded by the current principal.
* *Decision*: If the file is not owned by the caller, treat the request as not found. Do not disclose whether a non-owned file exists.

### 2.3 Single Download
Return the clean file as an attachment only when the file is on the retain-for-download path, and validate the response header values before writing `Content-Type` and `Content-Disposition`.

### 2.4 Bulk Download
When bulk download is exposed, package the files into a ZIP attachment named `files.zip`.

* *Decision*: Every requested file must belong to the current principal and must already be in the retained final downloadable state. Do not return a partial archive.

### 2.5 Removal
Allow deletion only when the current principal owns the file.

## 3. Inputs / Outputs (Mode-Specific Additions)

* **Single Download Response**: Return the file as an attachment with validated `Content-Type` and `Content-Disposition` header values.
* **Bulk Download Response**: When bulk download is supported, return a ZIP attachment named `files.zip`.
* **Bulk Download Rule**: Every requested file must be owned by the caller and already be in the retained final downloadable state.

## 4. Error Contract (Mode-Specific Additions)

These codes supplement the shared error contract defined in Core §3.2:

| Code | Meaning |
|:---|:---|
| **403 Forbidden** | Attempt to download a file not in retained `DOWNLOADED` state. |
| **404 Not Found** | File ID does not exist, or the file is outside the caller's owner scope. |

## 5. Enforced Constraints (Negative Requirements)

These supplement the shared negative requirements in Core §4.8:

| Constraint | Standard Expectation |
|:---|:---|
| Disclosing non-owned file existence | Return the same not-found behavior used for missing files; do not reveal whether a non-owned file exists |

## 6. Observability (Mode-Specific Addition)

| Observable Signal | Meaning | Action |
|:---|:---|:---|
| Clean-store cleanup absent on retain paths | Clean artifacts may be incorrectly deleted | Verify retain-mode bean wiring; clean artifacts should persist until owner removes them |

## 7. Test & Validation (Mode-Specific)

### 7.1 Integration Tests

* **Owner Scope Enforcement**: Verify that a cross-principal download or delete attempt returns the same 404 as a missing file.
* **Single Download**: Verify clean content is returned with validated headers when file is in `DOWNLOADED` state.
* **Non-Downloadable State**: Verify HTTP 403 when attempting to download a file not in `DOWNLOADED` state.
* **Bulk Download Success**: Verify success when all files are owned and retained; verify a valid ZIP named `files.zip` is produced.
* **Bulk Download Rejection**: Verify rejection when any file is non-owned or not in retained state; verify no partial archive is returned.
* **Error Code Paths**: Verify HTTP 403 (non-downloadable state) and HTTP 404 (owner scope) in addition to the shared error codes tested in Core §5.2.
