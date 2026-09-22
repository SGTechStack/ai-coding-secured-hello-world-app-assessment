# 09. DirtyFileBlob Lifecycle (AWS)

See [09. DirtyFileBlob Lifecycle](../shared/09-dirtyfileblob-lifecycle-consolidated.md) for the shared invariant and requirements.

| Trigger / State Transition | DirtyFileBlob Action | Location |
|:---|:---|:---|
| Phase 0a: `PENDING_SCAN` -> `PENDING_SCAN_TIMEOUT` | DELETE | Cleanup listener |
| Phase 0b: `PENDING_SCAN` -> `PENDING_SCAN_RETRY_EXCEEDED` | DELETE | Cleanup listener |
| Phase 1: `PENDING_SCAN` -> `PENDING_SCAN_RESPONSE` (success) | DELETE | Scan job |
| Phase 2: `PENDING_SCAN_RESPONSE` -> `PENDING_SCAN_RESPONSE_TIMEOUT` | DELETE | Poll job |
| Phase 2: `PENDING_SCAN_RESPONSE` -> `PENDING_DOWNLOAD` | KEEP | Poll job |
| Phase 2: `PENDING_SCAN_RESPONSE` -> `BAD_RESULT` | DELETE | Poll job |
| Phase 3: `PENDING_DOWNLOAD` -> `DOWNLOADED` | DELETE | Clean-file retrieval job |
| Phase 3: `PENDING_DOWNLOAD` -> `DOWNLOADED_FILE_MISMATCH` | DELETE | Clean-file retrieval job |

**Requirements**:

* Phase 1 deletion happens only after the SFS handoff is confirmed and the scanner UUID/submission metadata have been persisted.
