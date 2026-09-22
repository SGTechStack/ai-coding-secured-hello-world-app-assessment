# 28. Logging, Audit, and Observability

**Goal**: Define the shared structured log tags, audit event contract, and observability rules that apply across deployment profiles.

## Shared Log Tag Baseline

| Tag | Level | Template | Used By |
|:---|:---|:---|:---|
| `[FILE_UPLOADED]` | INFO | `File {} uploaded. Size={}, Type={}, By={}.` | Upload service |
| `[STATE_TRANSITION]` | INFO | `File {} transitioned to {}.` | StateMachineService |
| `[FILE_DOWNLOADED]` | INFO | `FileId={} finalized as DOWNLOADED.` | Clean-store finalization |
| `[DIRTY_STORE_CLEANUP]` | INFO | `Deleted dirty-store artifact for fileId={} (Reason: {}).` | StateMachineService |
| `[CLEAN_STORE_CLEANUP]` | INFO | `Deleted clean-store artifact for fileId={} (Reason: {}).` | IngestionService / retention cleanup |
| `[PDF_VALIDATION_FAIL]` | ERROR | `IOException during PDF encryption check: {}.` | PdfEncryptionDetector |
| `[VALIDATION_ERROR]` | WARN | `File validation failed for displayName='{}'. Rule: {}. Reason: {}.` | Validators |
| `[INGESTION_STARTED]` | INFO | `Starting record-level ingestion for fileId={} ({} rows).` | IngestionService |
| `[INGESTION_COMPLETED]` | INFO | `Completed record-level ingestion for fileId={}. Processed: {}/{} rows. Status: {}.` | IngestionService |
| `[INGESTION_FAILED]` | ERROR | `Record-level ingestion failed for fileId={}. Error: {}.` | IngestionService |
| `[ROW_PARSE_ERROR]` | ERROR | `Record {} parsing failed in fileId={}. Error: {}.` | RecordProcessor |

## Audit Event Contract

All profiles emit `FileScanEvent` for lifecycle transitions that occur in that profile:

```java
public record FileScanEvent(
        UUID fileId,
        FileStatus status,
        Instant eventTimestamp
) {}
```

**Shared event publication points**:

| Transition | Event Status |
|:---|:---|
| Upload complete | `PENDING_SCAN` |
| Clean file finalized | `DOWNLOADED` |

## Logging Rules

| Rule | Detail |
|:---|:---|
| Log levels | `INFO` for normal transitions, `WARN` for recoverable or delayed processing, `ERROR` for terminal failures |
| No secrets | Logs must not contain raw file content, private keys, presigned URLs, credentials, or sensitive locators |
| Trace correlation | Preserve trace IDs across upload, lifecycle, retrieval, and ingestion handling |
| Principal context | Include the uploading principal in upload and retrieval log entries for audit |
| Structured format | Use the bracketed tag format (`[TAG]`) consistently so log aggregation tools can filter by lifecycle phase |

**Profile-specific observability recipes**:

* Standalone logging belongs in [28. Standalone Logging, Audit, and Observability](../standalone/28-standalone-logging-audit-observability.md).
* AWS logging belongs in [28. AWS Logging, Audit, and Observability](../aws/28-aws-logging-audit-observability.md).
