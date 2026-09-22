# 28. AWS Logging, Audit, and Observability

**Goal**: Cover AWS SFS scanner diagnostics, scheduled lifecycle timing, and scanner-specific audit events.

| Tag | Level | Template | Used By |
|:---|:---|:---|:---|
| `[SFS_UPLOAD_FAIL]` | ERROR | `Failed to upload fileId={} to SFS. Attempt {}/{}. Error: {} (Response body truncated to 1 KB max).` | AwsScanWorkflow (Phase 1) |
| `[SFS_POLL_FAIL]` | ERROR | `Failed to poll SFS for fileId={}. Status: {}. Error: {}.` | AwsScanWorkflow (Phase 2) |
| `[SFS_TIMEOUT]` | WARN | `FileId={} timed out waiting for SFS scan result.` | AwsScanWorkflow / cleanup |
| `[SFS_DOWNLOAD_FAIL]` | ERROR | `Failed to retrieve clean content for fileId={}. Error: {}.` | AwsScanWorkflow (Phase 3) |
| `[SCAN_BLOCKED]` | WARN | `File {} blocked by SFS: {}.` | AwsScanWorkflow (Phase 2) |
| `[HASH_MISMATCH]` | ERROR | `Hash mismatch for fileId={}. Expected: {}. Got: {}.` | Phase 3 finalization |
| `[SCAN_TIMEOUT]` | WARN | `File {} timed out after {} days.` | Phase 0 cleanup |
| `[RETRY_EXCEEDED]` | ERROR | `File {} exceeded retry limit ({} attempts).` | Phase 0 cleanup |

**AWS event publication points**:

| Transition | Event Status |
|:---|:---|
| Upload complete | `PENDING_SCAN` |
| Cleanup: timeout | `PENDING_SCAN_TIMEOUT` |
| Cleanup: retries exhausted | `PENDING_SCAN_RETRY_EXCEEDED` |
| Scanner submission success | `PENDING_SCAN_RESPONSE` |
| Scanner response timeout | `PENDING_SCAN_RESPONSE_TIMEOUT` |
| Scanner verdict: allowed | `PENDING_DOWNLOAD` |
| Scanner verdict: blocked/error | `BAD_RESULT` |
| Clean file retrieval: hash verified | `DOWNLOADED` |
| Clean file retrieval: hash mismatch | `DOWNLOADED_FILE_MISMATCH` |

**Rules**:

* SFS error response bodies must be truncated to 1 KB max before logging.
* Logs must not include raw file content, credentials, private keys, or presigned URLs.
* Scheduled phases should preserve trace/correlation context so a file can be traced across batch cycles.
