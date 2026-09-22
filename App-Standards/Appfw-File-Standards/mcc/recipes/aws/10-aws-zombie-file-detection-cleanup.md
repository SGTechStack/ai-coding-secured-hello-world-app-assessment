# 10. AWS Zombie File Detection & Cleanup

**Goal**: Fail-out files stuck in pending states due to infrastructure failure. Prevent infinite retry loops.


**Java Implementation (Phase 0a — Timeout Expiry)**:

```java
@Scheduled(cron = "${file.scanner.polling-interval-cron}")
public void cleanupZombies() {
    // Step 0a: Timeout Expiry
    List<FileMetadata> timedOut = repository.findByStatusAndUploadedOnBefore(
        FileStatus.PENDING_SCAN,
        Instant.now().minus(clearIntervalDays, ChronoUnit.DAYS));

    for (FileMetadata zombie : timedOut) {
        stateMachineService.transitionToTerminalFailure(
            zombie.getId(), FileStatus.PENDING_SCAN_TIMEOUT, null);
        log.warn("[SCAN_TIMEOUT] File {} timed out after {} days",
            zombie.getId(), clearIntervalDays);
    }

    // Step 0b: Retry Exhaustion
    List<FileMetadata> retryExceeded = repository.findByStatusAndJobAttemptsGreaterThanEqual(
        FileStatus.PENDING_SCAN, properties.getJobRetryLimit());

    for (FileMetadata zombie : retryExceeded) {
        stateMachineService.transitionToTerminalFailure(
            zombie.getId(), FileStatus.PENDING_SCAN_RETRY_EXCEEDED, null);
        log.error("[RETRY_EXCEEDED] File {} exceeded retry limit ({} attempts)",
            zombie.getId(), zombie.getJobAttempts());
    }

    // Step 0c: Scanner Response Timeout
    List<FileMetadata> scanTimedOut = repository.findByStatusAndSentToScannedOnBefore(
        FileStatus.PENDING_SCAN_RESPONSE,
        Instant.now().minus(properties.getScanDurationTimeout(), ChronoUnit.MINUTES));

    for (FileMetadata zombie : scanTimedOut) {
        stateMachineService.transitionToTerminalFailure(
            zombie.getId(), FileStatus.PENDING_SCAN_RESPONSE_TIMEOUT, null);
        log.warn("[SFS_TIMEOUT] File {} timed out waiting for SFS scan result",
            zombie.getId());
    }
}
```

**The Zombie Fix**:

```java
// Inside AwsScanWorkflow.processFile(FileMetadata file)
boolean transitioned = false;
try {
    byte[] content = blobStorage.readDirty(file.getId());
    SfsPutResponse response = sfsClient.requestUpload(
        new SfsPutRequest(file.getDisplayName(), file.getFileType()));
    sfsClient.uploadBinary(response.presignedUrl(), content);

    stateMachineService.transitionToPendingScanResponse(file.getId(), response.uuid());
    transitioned = true;

} catch (SfsUploadException | IOException e) {
    log.error("[SFS_UPLOAD_FAIL] Failed to upload fileId={} to SFS. Attempt {}/{}. Error: {}",
        file.getId(), file.getJobAttempts() + 1, properties.getJobRetryLimit(), e.getMessage());
} finally {
    // MANDATORY: increment and save jobAttempts on failure path only
    if (!transitioned) {
        file.setJobAttempts(file.getJobAttempts() + 1);
        metadataRepo.save(file);   // ← GUARANTEED persistence
    }
}
```

