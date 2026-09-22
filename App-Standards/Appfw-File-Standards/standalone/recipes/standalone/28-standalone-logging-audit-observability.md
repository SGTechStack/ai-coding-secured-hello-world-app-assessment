# 28. Standalone Logging, Audit, and Observability

**Goal**: Cover standalone local promotion, quota, and orphan cleanup signals without scanner-specific tags.

| Tag | Level | Template | Used By |
|:---|:---|:---|:---|
| `[LOCAL_PROMOTION_FAILED]` | ERROR | `Local promotion failed for fileId={}. Error: {}.` | StandaloneScanWorkflow |
| `[STORAGE_QUOTA_EXCEEDED]` | ERROR | `Storage quota exceeded. Current: {} MB, Limit: {} MB, Requested: {} MB.` | StorageQuotaChecker |
| `[ORPHAN_DIRTY_ARTIFACT]` | WARN | `Orphan dirty-store artifact detected for fileId={}. Backend={}.` | Manual or explicit recovery procedure |

**Standalone event publication points**:

| Transition | Event Status |
|:---|:---|
| Upload complete | `PENDING_SCAN` |
| Local promotion complete | `DOWNLOADED` |

Standalone has no SFS upload, poll, scanner timeout, or scanner download tags.
