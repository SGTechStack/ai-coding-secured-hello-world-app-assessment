# 22. Negative Requirements Compliance Checklist

**Goal**: Ensure known file-management anti-patterns are not introduced.


| Req ID | Anti-Pattern | Prevention |
|:---|:---|:---|
| 01 | Full file materialization during PDF validation | Stream-only reads via `Loader.loadPDF(RandomAccessReadBuffer)` |
| 02 | Dirty-store artifact surviving past terminal state | `deleteDirty()` on **every** terminal transition (see [09. DirtyFileBlob Lifecycle (Consolidated)](./09-dirtyfileblob-lifecycle-consolidated.md)) |
| 03 | Missing event publication on terminal transitions | `eventPublisher.publish()` on **every** transition (see [08. Transactional State Transitions (StateMachineService)](./08-transactional-state-transitions-statemachineservice.md)) |
| 04 | Error-swallowing preventing jobAttempts persistence | `finally` block with `transitioned` flag in AWS scheduled work (see [10. AWS Zombie File Detection & Cleanup](../aws/10-aws-zombie-file-detection-cleanup.md)) |
| 05 | Single-file errors aborting entire batch chunk | Per-file `try-catch` in AWS poll/scan loops (see [15. AWS Robust Batch Job Pattern](../aws/15-aws-robust-batch-job-pattern-poison-pill-zombie-fix.md)) |
| 06 | SFS client swallowing HTTP exceptions | Typed exceptions with `fileId`, HTTP status, truncated body (1 KB max) |
| 07 | Boolean return types for SFS operations | Return deserialized response or throw typed exception |
| 08 | Delete operations failing when target absent | `deleteDirty`/`deleteClean` are idempotent (no error if absent) |
| 09 | Full file materialization during record-level ingestion parsing | Iterator patterns only; batch flush every 500 rows |

**Risks Prevented by Design**:

| # | Bug | Resolution |
|:---|:---|:---|
| 01 | Zombie File Infinite Loop | `jobAttempts` persisted in `finally` block |
| 02 | Zombie Blob Resource Leak | Dirty-store artifact deleted on every terminal state |
| 03 | Black Hole SFS Client | Typed exceptions replace boolean returns |
| 04 | DoS via PDF Validation | Streaming-only PDF reads |
| 05 | Missing Timeout Events | Events published on all terminals |
| 06 | Phantom Event Class | `FileScanEvent` is sole event class (no `FileDownloadedEvent`) |
| 07 | Poison Pill Poll Job | Per-file try-catch in batch loops |
| 08 | Validation Exception Swallowing | `IOException` propagated as `FileValidationException` |
| 09 | JPEG Signature Incompleteness | All 5 JPEG variants in magic bytes registry |
| 10 | Filename Regex Mismatch | Broader pattern `^[a-zA-Z0-9_\- ]+$` adopted |

