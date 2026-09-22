# 19. Verify File Management with Tests

**Goal**: Define the test baseline that applies to every profile: validation, upload metadata, dirty/clean invariants, download guards, ingestion, and RFC 9457 error shape.

## Step 1: Verify upload validation rules

```java
@Test
void rejectsFileExceedingMaxSize() {
    MultipartFile oversized = mockFile("large.pdf", "application/pdf", maxSize + 1);

    assertThatThrownBy(() -> validator.validate(oversized))
            .isInstanceOf(FileValidationException.class)
            .hasMessageContaining("size");
}

@Test
void rejectsMagicBytesMismatch() {
    byte[] pngHeader = HexFormat.of().parseHex("89504E470D0A1A0A");
    MultipartFile spoofed = mockFileWithContent("fake.pdf", "application/pdf", pngHeader);

    assertThatThrownBy(() -> validator.validate(spoofed))
            .isInstanceOf(FileValidationException.class)
            .hasMessageContaining("MAGIC_BYTES");
}

@Test
void rejectsEncryptedPdf() {
    InputStream encryptedPdfStream = loadTestResource("encrypted-sample.pdf");

    assertThat(pdfEncryptionDetector.isEncrypted(encryptedPdfStream)).isTrue();
}
```

## Step 2: Verify upload metadata and dirty storage

```java
@Test
void uploadPersistsMetadataAndDirtyArtifact() {
    UUID fileId = uploadTestFile();

    FileMetadata meta = metadataRepository.findById(fileId).orElseThrow();
    assertThat(meta.getStatus()).isEqualTo(FileStatus.PENDING_SCAN);
    assertThat(meta.getHash()).hasSize(64);
    assertThat(meta.getUploadedBy()).isEqualTo("tester");
    assertThat(blobStorage.existsInDirtyStore(fileId)).isTrue();
}
```

## Step 3: Verify clean finalization and dirty cleanup

```java
@Test
void transitionToDownloadedStoresCleanArtifactAndDeletesDirty() {
    UUID fileId = uploadTestFile();
    InputStream cleanContent = new ByteArrayInputStream("clean-content".getBytes(UTF_8));

    stateMachineService.transitionToDownloaded(fileId, cleanContent, "text/plain");

    assertThat(blobStorage.existsInDirtyStore(fileId)).isFalse();
    assertThat(blobStorage.existsInCleanStore(fileId)).isTrue();
    assertThat(metadataRepository.findById(fileId).get().getStatus())
            .isEqualTo(FileStatus.DOWNLOADED);
}
```

## Step 4: Verify retained download guards

```java
@Test
void rejectsDownloadBeforeDownloaded() {
    UUID fileId = uploadTestFile();

    assertThatThrownBy(() -> downloadService.openDownload(fileId, "tester"))
            .isInstanceOf(FileNotReadyException.class);
}

@Test
void nonOwnerSeesNotFoundBehavior() {
    UUID fileId = uploadAndFinalizeForOwner("owner-a");

    assertThatThrownBy(() -> downloadService.openDownload(fileId, "owner-b"))
            .isInstanceOf(FileNotFoundException.class);
}
```

## Step 5: Verify record-level ingestion

```java
@Test
void ingestionProcessesAllRowsEvenWhenSomeFail() {
    UUID fileId = uploadAndFinalizeToDownloaded("mixed-rows.csv");

    ingestionService.processFile(fileId, "text/csv");

    FileMetadata meta = metadataRepository.findById(fileId).orElseThrow();
    assertThat(meta.getIngestionStatus()).isIn(
            IngestionStatus.COMPLETED);
    assertThat(meta.getProcessedRows()).isEqualTo(meta.getTotalRows());
}
```

## Step 6: Verify RFC 9457 error response shape

```java
@Test
void validationFailureReturnsRfc9457ProblemDetail() throws Exception {
    MockMultipartFile bad = new MockMultipartFile(
            "file", "bad.exe", "application/x-msdownload", new byte[]{0x00});

    mockMvc.perform(multipart("/upload").file(bad))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").exists())
            .andExpect(jsonPath("$.title").value("Validation Failed"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").exists());
}
```

**Profile-specific test recipes**:

* Standalone acceptance tests belong in [19. Standalone Verification Tests](../standalone/19-standalone-verification-tests.md).
* AWS SFS lifecycle tests belong in [19. AWS Verification Tests](../aws/19-aws-verification-tests.md).
