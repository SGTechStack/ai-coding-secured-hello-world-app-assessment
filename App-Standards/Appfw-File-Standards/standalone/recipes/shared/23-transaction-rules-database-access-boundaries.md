# 23. Transaction Rules & Database Access Boundaries

**Goal**: Enforce consistent transaction management across all processing phases to prevent data inconsistency, partial writes, and resource leaks.


**Transaction Boundary Rules**:

| Processing Phase | Transaction Scope | Timeout | Isolation |
|:---|:---|:---|:---|
| Upload API | `@Transactional` on `FileUploadService.upload()` | 30s | `READ_COMMITTED` |
| Phase 0 (Cleanup) | Per-file transition within batch cycle | 600s (batch) | `READ_COMMITTED` |
| Phase 1 (Scan Submission) | Per-file: `stateMachineService.transitionToPendingScanResponse()` | 60s | `READ_COMMITTED` |
| Phase 2 (Poll Results) | Per-file: `stateMachineService` transition | 60s | `READ_COMMITTED` |
| Phase 3 (Download Clean) | Per-file: `stateMachineService.transitionToDownloaded()` | 60s | `READ_COMMITTED` |
| Post-retrieval record-level ingestion flow | `@Transactional(timeout = 1800)` on `IngestionService.processFile()` | 30 min | `READ_COMMITTED` |

**Database Access Rules**:

1.  **Domain services** MAY inject `JpaRepository` interfaces directly — no DAO layer needed.
2.  **Adapters** (controllers, listeners, schedulers) MUST NOT access repositories directly; they delegate to domain services.
3.  All `@Transactional` boundaries exist in the **domain service layer** (never on controllers or adapters).
4.  `@Transactional(readOnly = true)` for query-only operations (download, status checks).
5.  LOB reads MUST occur within an active transaction (Hibernate session required for lazy loading).

**Per-File Transaction Pattern**:

```java
// Correct: Each file processed in its own transaction context
for (FileMetadata file : batch) {
    try {
        stateMachineService.transitionToTerminalFailure(file.getId(), ...);
        // ↑ method with own transaction per call
    } catch (Exception e) {
        // single failure does not abort batch
        log.error("[BATCH_ERROR] File {} failed: {}", file.getId(), e.getMessage());
    }
}
```

