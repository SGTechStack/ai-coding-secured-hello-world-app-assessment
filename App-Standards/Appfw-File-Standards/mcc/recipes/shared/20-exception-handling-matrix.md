# 20. Exception Handling Matrix

**Goal**: Keep shared exception handling consistent and RFC 9457-friendly across profiles.

| Exception | HTTP | Thrown By | Recovery |
|:---|:---|:---|:---|
| `FileValidationException` | 400 | Validators | Reject upload |
| `FileNotFoundException` | 404 | Services | Log and return not found |
| `FileNotReadyException` | 403 | Download guard | Reject download because the file is not in retained `DOWNLOADED` state |
| `RowParsingException` | 422 | `RecordProcessor` | Mark row failed; continue when row isolation is enabled |
| `RowProcessingException` | 422 | Ingestion service | Mark row failed; persist error; continue when row isolation is enabled |
| `StorageQuotaExceededException` | 507 | `StorageQuotaChecker` | Reject upload |
| `BlobNotFoundException` | 404 or 403 | `BlobStorage` | Treat missing/non-owned metadata as 404; treat missing retained clean artifact as not downloadable |
| `BlobStorageIOException` | 500 | `BlobStorage` | Fail the current operation and leave recovery to the selected profile's lifecycle |

**Rules**:

* Problem Details responses include `type`, `title`, `status`, `detail`, `instance`, and `traceId`.
* Owner-scope misses return the same not-found behavior as missing files.
* Storage exceptions must include safe context such as `fileId`, operation, backend type, and cause. They must not include raw content or sensitive locators.
* AWS SFS exception handling belongs in [20. AWS SFS Exception Handling](../aws/20-aws-sfs-exception-handling.md).
