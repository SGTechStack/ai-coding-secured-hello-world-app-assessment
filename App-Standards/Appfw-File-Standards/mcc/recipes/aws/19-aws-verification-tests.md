# 19. AWS Verification Tests

**Goal**: Verify the AWS SFS lifecycle, scheduler behavior, retry handling, and scanner-specific failure paths.

**Required tests**:

* Phase 1 submits to SFS and persists scanner UUID/submission metadata before `PENDING_SCAN_RESPONSE`.
* Phase 1 respects the 10-file in-flight cap after counting `PENDING_SCAN_RESPONSE`.
* One file's SFS upload failure does not abort the batch cycle.
* Failed Phase 1 attempts increment and persist `jobAttempts`.
* `PENDING_SCAN` past the cleanup interval transitions to `PENDING_SCAN_TIMEOUT`.
* `PENDING_SCAN` with `jobAttempts >= jobRetryLimit` transitions to `PENDING_SCAN_RETRY_EXCEEDED`.
* `PENDING_SCAN_RESPONSE` past `scanDurationTimeout` transitions to `PENDING_SCAN_RESPONSE_TIMEOUT`.
* Allowed SFS verdict transitions to `PENDING_DOWNLOAD`.
* Blocked/error SFS verdict transitions to `BAD_RESULT`.
* Phase 3 stores clean content and finalizes `DOWNLOADED` when the scanner verdict is `Unchanged` AND the downloaded hash matches the original hash, OR when the verdict is `Sanitized` (accepted with audit trail — hash mismatch is expected).
* An `Unchanged` verdict whose downloaded hash differs from the original finalizes as `DOWNLOADED_FILE_MISMATCH`.

```java
@Test
void phase1RespectsInFlightCapacity() {
    for (int i = 0; i < scannerFilesLimit; i++) {
        createFileInStatus(FileStatus.PENDING_SCAN_RESPONSE);
    }
    UUID waitingFile = uploadTestFile();

    scanBatchScheduler.executeBatch();

    assertThat(metadataRepository.findById(waitingFile).get().getStatus())
            .isEqualTo(FileStatus.PENDING_SCAN);
}

@Test
void singleFileFailureDoesNotAbortBatch() {
    UUID good = uploadTestFile();
    UUID poison = uploadTestFile();

    doThrow(new SfsUploadException(poison, 500, "server error", "upload"))
            .when(sfsScannerClient).requestUpload(any());

    scanBatchScheduler.executeBatch();

    assertThat(metadataRepository.findById(good).get().getStatus())
            .isEqualTo(FileStatus.PENDING_SCAN_RESPONSE);
    assertThat(metadataRepository.findById(poison).get().getJobAttempts())
            .isGreaterThan(0);
}
```
