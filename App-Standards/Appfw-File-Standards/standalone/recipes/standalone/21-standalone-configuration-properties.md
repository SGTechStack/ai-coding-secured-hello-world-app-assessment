# 21. Standalone Configuration Properties

**Goal**: Configure local standalone storage, quota, and promotion behavior without scanner-specific settings.

**Standalone properties** (`file.standalone.*`):

```java
@ConfigurationProperties(prefix = "file.standalone")
@Validated
public record StandaloneFileProperties(
    @Positive long storageQuotaBytes,
    StorageBackend storageBackend,
    Path dirtyDir,
    Path cleanDir
) {}
```

**Rules**:

* Do not model standalone as SFS bypass. Local promotion is the profile's normal lifecycle.
* `storageQuotaBytes` is enforced locally before upload persistence.
* Filesystem-backed variants must configure managed dirty and clean directories.
* Database-backed variants may ignore filesystem directory properties.
