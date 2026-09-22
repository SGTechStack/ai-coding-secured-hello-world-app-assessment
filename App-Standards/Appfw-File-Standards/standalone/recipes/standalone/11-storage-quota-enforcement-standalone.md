# 11. Storage Quota Enforcement (Standalone)

**Goal**: Enforce the configured 5 GB local storage quota for Standalone deployments. AWS quota policy depends on the configured storage backend.


**Java Implementation**:

```java
public interface StorageQuotaChecker {
    /** @throws StorageQuotaExceededException if current usage + incomingBytes > quota */
    void assertCapacity(long incomingBytes);
}

// Standalone implementation
@Component
@Profile("standalone")
public class LocalStorageQuotaChecker implements StorageQuotaChecker {

    private final FileMetadataRepository repository;
    private final long quotaBytes; // 5 GB default local quota

    @Override
    public void assertCapacity(long incomingBytes) {
        long currentUsage = repository.sumActiveFileSize(); // metadata-based aggregation
        if (currentUsage + incomingBytes > quotaBytes) {
            log.error("[STORAGE_QUOTA_EXCEEDED] Current: {} MB, Limit: {} MB, Requested: {} MB",
                currentUsage / 1_048_576, quotaBytes / 1_048_576, incomingBytes / 1_048_576);
            throw new StorageQuotaExceededException("Quota exceeded");
        }
    }
}

```

**Metadata Aggregation Query**:

```sql
SELECT COALESCE(SUM(fm.fileSize), 0) FROM FileMetadata fm
WHERE fm.status = 'PENDING_SCAN'
   OR EXISTS (
       SELECT 1 FROM FileBlob clean
       WHERE clean.id = fm.id
   )
```

**Implementation Note**:

This recipe uses metadata-owned file sizes so the same quota rule works for the default database-backed Standalone reference implementation, while still remaining valid if a filesystem-backed variant is enabled. The quota check should not depend on summing database BLOB sizes directly. Filesystem-backed variants should use the equivalent retained-artifact predicate from their storage-location table or managed clean directory index.

