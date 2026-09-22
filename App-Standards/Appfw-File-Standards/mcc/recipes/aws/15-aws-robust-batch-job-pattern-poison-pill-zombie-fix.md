# 15. AWS Robust Batch Job Pattern (Per-File Isolation & Retry Persistence)

**Goal**: Process scheduled batches without aborting on single-file failures and ensure `jobAttempts` is persisted.

**Recommended implementation**: Wrap each file in its own `try-catch` inside the batch loop so one failure cannot abort the rest. Persist `jobAttempts` in a `finally` block guarded by a `transitioned` flag, guaranteeing the counter advances on every failure regardless of exception type.

**Java Implementation**:

```java
@Scheduled(cron = "${file.scanner.polling-interval-cron}")
@SchedulerLock(name = "scanJob")
public void executeBatch() {
    cleanupZombies();

    int inFlight = repository.countByStatus(FileStatus.PENDING_SCAN_RESPONSE);
    int availableCapacity = Math.max(0, scannerFilesLimit - inFlight);
    if (availableCapacity == 0) {
        return;
    }

    List<FileMetadata> batch = repository.findByStatusWithLock(
        FileStatus.PENDING_SCAN, PageRequest.of(0, availableCapacity));

    for (FileMetadata file : batch) {
        try {
            processFile(file);
        } catch (Exception e) {
            log.error("[SFS_UPLOAD_FAIL] Failed to process fileId={}: {}", file.getId(), e.getMessage());
        }
    }
}
```

> Per-file isolation, `jobAttempts` persistence, and capacity throttling rules are defined in §4.4 and §4.7 of the [AWS Standard](../../standards/file_management_standards_aws_core.md).
