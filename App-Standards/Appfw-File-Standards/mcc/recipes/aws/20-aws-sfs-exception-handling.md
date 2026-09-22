# 20. AWS SFS Exception Handling

**Goal**: Preserve scanner failure diagnostics while keeping HTTP responses and logs safe.

| Exception | HTTP | Phase | Recovery |
|:---|:---|:---|:---|
| `SfsUploadException` | 502 | Phase 1 | Catch per file, persist `jobAttempts`, retry next cycle |
| `SfsPollingException` | 502 | Phase 2 | Catch per file, continue processing unrelated files |
| `SfsDownloadException` | 502 | Phase 3 | Catch per file, retry next cycle while SFS retention allows |

**Constructor requirements**:

All SFS exceptions should include:

* `fileId`
* lifecycle operation: `upload`, `poll`, or `download`
* HTTP status when available
* safely truncated response body, maximum 1 KB
* root cause

**Rule**:

The scanner adapter must not return `boolean` for HTTP operations. It should return deserialized responses on success and throw typed exceptions on failure.
