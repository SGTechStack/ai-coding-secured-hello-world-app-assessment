# 19. Standalone Verification Tests

**Goal**: Verify the standalone lifecycle: local promotion, local quota, no scanner calls, and retained-download or ingest-and-delete behavior.

**Required tests**:

* Upload validation rejects size, display-name, MIME, Magic Bytes, and encrypted PDF failures.
* Upload persists metadata and dirty content atomically.
* Local promotion transitions `PENDING_SCAN -> DOWNLOADED`.
* Local promotion writes the clean artifact and deletes the dirty artifact.
* No SFS client, scanner scheduler, polling, or remote clean-file retrieval bean is invoked.
* Storage quota rejection returns HTTP 507 when aggregate local storage exceeds `standalone.storage-quota-bytes`.
* Promotion failure does not leave a false `DOWNLOADED` state.
* Orphan dirty artifacts after a crash are handled by the selected explicit recovery procedure.
* Retained files are downloadable only by the owner.
* Ingest-and-delete files are read from the Clean Store and have the clean artifact deleted after ingestion.

```java
@Test
void standaloneUploadPromotesImmediately() {
    UUID fileId = uploadTestFile();

    FileMetadata meta = metadataRepository.findById(fileId).orElseThrow();
    assertThat(meta.getStatus()).isEqualTo(FileStatus.DOWNLOADED);
    assertThat(blobStorage.existsInDirtyStore(fileId)).isFalse();
    assertThat(blobStorage.existsInCleanStore(fileId)).isTrue();
}

@Test
void rejectsUploadWhenQuotaExceeded() {
    fillStorageTo(quotaBytes - 100);

    MultipartFile oversized = mockFile("big.pdf", "application/pdf", 200);

    assertThatThrownBy(() -> fileUploadService.upload(oversized, null, "tester"))
            .isInstanceOf(StorageQuotaExceededException.class);
}
```
