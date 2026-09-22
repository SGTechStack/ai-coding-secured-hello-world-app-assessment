# 21. Configuration Properties Classes

**Goal**: Keep shared configuration type-safe without mixing profile-specific scanner or local-storage settings into the common contract.

**Upload and validation properties** (`file.upload.*`):

```java
@ConfigurationProperties(prefix = "file.upload")
@Validated
public record FileValidationProperties(
    @Positive long minSize,
    @Positive long maxSize,
    List<String> acceptedMimeTypes,
    List<String> rowIngestionMimeTypes
) {}
```

**Shared processing properties** (`file.processing.*`):

```java
@ConfigurationProperties(prefix = "file.processing")
@Validated
public record FileProcessingProperties(
    long transactionTimeoutUploadMs,
    long transactionTimeoutIngestionMs,
    int ingestionBatchFlushSize
) {}
```

**Profile-specific property recipes**:

* Standalone local storage and quota settings belong in [21. Standalone Configuration Properties](../standalone/21-standalone-configuration-properties.md).
* AWS scanner, scheduler, S3, and MCC settings belong in [21. AWS Configuration Properties](../aws/21-aws-configuration-properties.md).
