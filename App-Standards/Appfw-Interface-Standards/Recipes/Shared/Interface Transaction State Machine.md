# Interface Transaction State Machine

## 1. Introduction

Every inbound file in the starter has a lifecycle tracked by `InboundFileStatus`. Understanding this state machine — which states exist, which transitions are automatic, and which require querying or action — is essential for building monitoring dashboards, writing operational queries, debugging stuck files, and reasoning about the audit trail.

By the end of this recipe, you will:
- Understand all 13 states in `InboundFileStatus` and what triggers each one
- Follow a file's journey through the four batch job steps
- Know which states are terminal and which allow resubmission
- Query `InboundFile` by status for monitoring and recovery
- Understand how job execution context carries state between batch steps

## 2. Prerequisites

- `interface-management-inbound-starter` on the classpath
- A configured inbound directory and database connection
- Familiarity with Spring Batch job steps (reader → processor → writer)

---

## 3. Steps

### Step 1: Know Every State and What It Means

`InboundFileStatus` has 13 values. They fall into four groups:

**Pre-processing failures** — set before the batch job launches, by the scheduler or `InboundFileValidationService`:

| Status | Meaning | Terminal? |
|---|---|---|
| `UNRECOGNIZED` | Filename prefix does not match any registered `@MAGEntity.filenamePrefix` | Yes — file is archived, no ACK |
| `MISSING_PAYLOAD` | A companion `.sha3` or `.signed` file arrived without the data file | No — promotes to `RECEIVED` when data file arrives |
| `STALE` | File remained in `RECEIVED` status for longer than `app.interface.cleanUp.maxDuration` | Yes — cleaned up by the cleanup job |
| `DUPLICATE` | Same filename was already successfully processed (ACK code 200 or 204 exists) | Yes — no reprocessing |
| `INVALID_HASH_FILE` | The SHA-256 hash in the `.sha3` file did not match the data file | No — resubmit corrected file |
| `INVALID_SIGNATURE_FILE` | The RSA signature in the `.signed` file failed verification | No — resubmit with corrected signature |
| `ZERO_BYTE_FILE` | Data file is present but contains zero content bytes | No — resubmit a non-empty file |

**In-progress states** — set during the batch job run:

| Status | Meaning | Terminal? |
|---|---|---|
| `RECEIVED` | File discovered, persisted to `INBOUND_FILE`, awaiting batch job launch | No — batch job will pick it up |
| `PROCESSING` | Batch job is actively reading records from the file | No — transitions on job completion |

**Terminal processing outcomes** — set when the batch job finishes:

| Status | Meaning | Terminal? |
|---|---|---|
| `SCHEMA_VALIDATION_FAILED` | Jakarta Validation errors found **and** `haltOnError: true` — batch halted | Yes — ACK 462 sent |
| `PROCESSING_FAILED` | Unhandled exception thrown from `BatchJobCommand.process()` | Yes — ACK 500 sent |
| `PROCESSED_WITH_CONTENT_VALIDATION_ERROR` | Jakarta Validation errors on some records **and** `haltOnError: false` — processing continued | Yes — ACK 462 with error details |
| `PROCESSED` | All records processed without error | Yes — ACK 200 sent |

> **Terminal** means the batch job will not retry the file. The file can be resubmitted by the sender (same filename) only if the status is not `PROCESSED` or `ZERO_BYTE_FILE` — those two trigger duplicate detection on resubmission.

---

### Step 2: Trace a File Through the Full Lifecycle

A typical successful file passes through these transitions:

```
[File arrives in FSX inbox]
        │
        ▼
  Scheduler polls FSX → transfers file to local directory
        │
        ├─ Prefix not registered ──────────────────────────► UNRECOGNIZED (archived, no ACK)
        │
        ├─ Companion arrives first (no data file) ─────────► MISSING_PAYLOAD
        │     └─ Data file arrives later ──────────────────► RECEIVED
        │
        ├─ Same filename + prior ACK is 200/204 ───────────► DUPLICATE (file renamed to .bak_*)
        │
        ├─ Hash validation fails ───────────────────────────► INVALID_HASH_FILE → ACK 460
        │
        ├─ Signature validation fails ──────────────────────► INVALID_SIGNATURE_FILE → ACK 461
        │
        ├─ File is zero bytes ──────────────────────────────► ZERO_BYTE_FILE → ACK 204
        │
        ▼
      RECEIVED   ◄── also set if file previously MISSING_PAYLOAD and data file now present
        │
        ▼ Batch job Step 1: InboundFileFlatFileItemReader
      PROCESSING  ◄── set in @BeforeStep before reading starts
        │
        ▼ Batch job Step 1: InboundFileItemProcessor
        │
        ├─ haltOnError=true + validation error ─────────────► SCHEMA_VALIDATION_FAILED → ACK 462
        │
        ├─ FileContentValidationException thrown ───────────► PROCESSING_FAILED → ACK 500
        │
        ├─ haltOnError=false + validation errors on ≥1 record► PROCESSED_WITH_CONTENT_VALIDATION_ERROR → ACK 462
        │
        └─ All records valid ────────────────────────────────► PROCESSED → ACK 200
              │
              ▼ Batch job Step 2: ArchiveInboundFileTasklet
        (InboundFile.archived = true, file moved to archive directory)
              │
              ▼ Batch job Step 3: GenerateAckFileTasklet
        (ACK_FILE row created and written to disk)
              │
              ▼ Batch job Step 4: ArchiveAndDepositAckFileTasklet
        (ACK file moved to final output location)
```

---

### Step 3: Understand How Cross-Step State Is Passed

The batch job consists of four steps, and they share state through the Spring Batch `JobExecutionContext`. Two keys are used by the framework:

| Key | Constant | Type | Set by | Read by |
|---|---|---|---|---|
| `"InboundFileNames"` | `InboundFileService.INBOUND_FILENAMES_KEY` | `List<String>` | `InboundFileFlatFileItemReader` (`@BeforeStep`) | `GenerateAckFileTasklet`, `ArchiveInboundFileTasklet` |
| `"ValidationErrors"` | `AckFileService.VALIDATION_ERRORS_KEY` | `String` | `InboundFileItemProcessor` (accumulated per record) | `GenerateAckFileTasklet` (written into ACK file content) |

The `InboundFileNames` list tells downstream steps which files were processed in this job execution, so they know which rows to archive and which ACK files to generate. The `ValidationErrors` string is the formatted list of per-record errors that appears in the ACK body.

You do not write to these keys directly in `BatchJobCommand`. They are managed by the framework. If you need to read them in a custom step, inject `JobExecution` and retrieve from its `ExecutionContext`:

```java
// File: src/main/java/com/example/batch/listener/CustomJobListener.java
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import com.example.interface.inbound.service.InboundFileService;

@Component
public class CustomJobListener implements JobExecutionListener {

    @Override
    public void afterJob(JobExecution jobExecution) {
        List<String> processedFilenames = (List<String>) jobExecution
            .getExecutionContext()
            .get(InboundFileService.INBOUND_FILENAMES_KEY);

        String validationErrors = (String) jobExecution
            .getExecutionContext()
            .get(AckFileService.VALIDATION_ERRORS_KEY);

        // e.g. send a notification, update an external system, etc.
    }
}
```

---

### Step 4: Query by Status for Monitoring and Recovery

`InboundFileRepository` exposes `findByStatus(InboundFileStatus status)` for status-based queries. Use this to build monitoring checks or manual recovery workflows:

```java
// File: src/main/java/com/example/monitor/InterfaceMonitor.java
import com.example.interface.inbound.entity.InboundFile;
import com.example.interface.inbound.entity.InboundFileStatus;
import com.example.interface.inbound.repository.InboundFileRepository;

@Component
public class InterfaceMonitor {

    private final InboundFileRepository inboundFileRepository;

    public InterfaceMonitor(InboundFileRepository inboundFileRepository) {
        this.inboundFileRepository = inboundFileRepository;
    }

    /** Files awaiting processing — should be empty outside of active processing windows. */
    public List<InboundFile> getPendingFiles() {
        return inboundFileRepository.findByStatus(InboundFileStatus.RECEIVED);
    }

    /** Files stuck mid-job — indicates a crashed or timed-out batch run. */
    public List<InboundFile> getStuckFiles() {
        return inboundFileRepository.findByStatus(InboundFileStatus.PROCESSING);
    }

    /** Files that failed with a system error — require operator investigation. */
    public List<InboundFile> getSystemFailures() {
        return inboundFileRepository.findByStatus(InboundFileStatus.PROCESSING_FAILED);
    }

    /** Files that failed hash or signature check — sender must resubmit. */
    public List<InboundFile> getIntegrityFailures() {
        List<InboundFile> result = new ArrayList<>();
        result.addAll(inboundFileRepository.findByStatus(InboundFileStatus.INVALID_HASH_FILE));
        result.addAll(inboundFileRepository.findByStatus(InboundFileStatus.INVALID_SIGNATURE_FILE));
        return result;
    }
}
```

**SQL equivalent for operational dashboards:**

```sql
-- Current status summary
SELECT status, COUNT(*) AS file_count
FROM INBOUND_FILE
GROUP BY status
ORDER BY file_count DESC;

-- Files stuck in PROCESSING (may indicate a crashed job)
SELECT name, datetime_created, datetime_processed
FROM INBOUND_FILE
WHERE status = 'PROCESSING'
  AND datetime_created < NOW() - INTERVAL 30 MINUTE;

-- Files awaiting processing older than 15 minutes (possible scheduler issue)
SELECT name, datetime_created
FROM INBOUND_FILE
WHERE status = 'RECEIVED'
  AND datetime_created < NOW() - INTERVAL 15 MINUTE;

-- All failed files in the last 7 days
SELECT name, status, datetime_created
FROM INBOUND_FILE
WHERE status IN (
    'PROCESSING_FAILED', 'SCHEMA_VALIDATION_FAILED',
    'INVALID_HASH_FILE', 'INVALID_SIGNATURE_FILE'
)
  AND datetime_created >= NOW() - INTERVAL 7 DAY
ORDER BY datetime_created DESC;
```

---

### Step 5: Update File Status Explicitly (Where Required)

For the batch job steps, status transitions are managed automatically by the framework. However, `InboundFileService.updateFileStatus()` is available when you need to update status from outside a batch job — for example, in a manual recovery step or a custom scheduled task:

```java
// File: src/main/java/com/example/recovery/ManualRecoveryService.java
import com.example.interface.inbound.entity.InboundFileStatus;
import com.example.interface.inbound.service.InboundFileService;

@Service
public class ManualRecoveryService {

    private final InboundFileService inboundFileService;

    public ManualRecoveryService(InboundFileService inboundFileService) {
        this.inboundFileService = inboundFileService;
    }

    /**
     * Resets a PROCESSING_FAILED file back to RECEIVED so the scheduler
     * picks it up on the next cycle. Use only after root cause is resolved.
     */
    public void resetForReprocessing(String filename) {
        inboundFileService.updateFileStatus(filename, InboundFileStatus.RECEIVED);
    }
}
```

> **Caution:** Manually setting a file back to `RECEIVED` will cause the scheduler to reprocess it. Ensure the underlying issue is resolved first, or the file will fail again and accumulate multiple error rows. Also ensure the batch job is not currently running for this file — check that no `PROCESSING` row exists with the same filename before resetting.

---

### Step 6: Know Which States Allow Archival

The `InboundFileArchivingService` checks status groups before deciding the archive destination:

| Status Group | Method | Archive Location |
|---|---|---|
| `PROCESSED`, `PROCESSED_WITH_CONTENT_VALIDATION_ERROR`, `ZERO_BYTE_FILE` | `isProcessedStatus()` | Success archive: `app.interface.inbound.archive.localDir` or S3 |
| `INVALID_HASH_FILE`, `INVALID_SIGNATURE_FILE`, `SCHEMA_VALIDATION_FAILED`, `PROCESSING_FAILED`, `UNRECOGNIZED` | `isFailedStatus()` | Failure archive path |
| `STALE`, `MISSING_PAYLOAD` | `isStaleStatus()` | Stale archive path |
| `PROCESSING`, `RECEIVED` | (not archived) | Remains in local directory until job completes |

Archival happens automatically in Step 2 (`ArchiveInboundFileTasklet`). When archival completes, `InboundFile.archived` is set to `true`. The file is no longer in the inbound directory after this point.

---

## 4. Examples

### Example: Status Progression for a Successful File

```
datetime_created: 2026-03-27T14:00:01Z  status: RECEIVED
(scheduler fires, batch job starts)
datetime:         2026-03-27T14:00:02Z  status: PROCESSING
(all 1,250 records processed without error)
datetime_processed: 2026-03-27T14:02:35Z  status: PROCESSED
(archive step runs)
archived: true
(ACK generated)
ACK_FILE.name: PAYMENT_TXN_20260327_001.csv.ack_200_20260327T140235
ACK_FILE.status_code: SUCCESS
```

### Example: Status Progression for a Partial Failure

```
datetime_created: 2026-03-27T14:00:01Z  status: RECEIVED
datetime:         2026-03-27T14:00:02Z  status: PROCESSING
(1,248 records processed OK; 2 records fail Jakarta Validation; haltOnError=false)
datetime_processed: 2026-03-27T14:02:35Z  status: PROCESSED_WITH_CONTENT_VALIDATION_ERROR
archived: true
ACK_FILE.name: PAYMENT_TXN_20260327_002.csv.ack_462_20260327T140235
ACK_FILE.status_code: SCHEMA_VALIDATION_EXCEPTION
(ACK body contains error details for the 2 failed records)
```

### Example: Detecting a Stuck File

```sql
-- File has been PROCESSING for over 1 hour — job likely crashed mid-run
SELECT name, datetime_created
FROM INBOUND_FILE
WHERE status = 'PROCESSING'
  AND datetime_created < NOW() - INTERVAL 1 HOUR;

-- Recovery: reset to RECEIVED after confirming the job is not running
UPDATE INBOUND_FILE
SET status = 'RECEIVED'
WHERE name = 'PAYMENT_TXN_20260327_003.csv'
  AND status = 'PROCESSING';
```

---

## 5. Verification

1. Place a valid CSV file in the inbox and trace its status through `RECEIVED → PROCESSING → PROCESSED` by querying `INBOUND_FILE` at each scheduler cycle.
2. Place a file with an unrecognised prefix — confirm status `UNRECOGNIZED` and that no batch job is launched.
3. Place a file with an invalid hash (when signature validation is enabled) — confirm status `INVALID_HASH_FILE` and an `ack_460_*` file in the ACK output directory.
4. Submit a file with some invalid records and `haltOnError: false` — confirm `PROCESSED_WITH_CONTENT_VALIDATION_ERROR` and an `ack_462_*` with error details.
5. Query `SELECT archived, ack_file_id FROM INBOUND_FILE WHERE name = 'your-file.csv'` after a successful run — confirm `archived=true` and a non-null `ack_file_id`.

---

## 6. Conclusion

The `InboundFileStatus` state machine gives you a complete, queryable record of every file's lifecycle from arrival to archival. The thirteen states map cleanly onto the four batch job steps and the pre-processing validation pipeline. By querying the `INBOUND_FILE` table by status, you can build operational health checks, detect stuck jobs, and identify files that need manual recovery — all without custom instrumentation code.

## 7. References

- `com.example.interface.inbound.entity.InboundFileStatus` — all 13 status values
- `com.example.interface.inbound.entity.InboundFile` — JPA entity with status, archived, ackFile fields
- `com.example.interface.inbound.repository.InboundFileRepository` — `findByStatus()`, `findByName()`
- `com.example.interface.inbound.service.InboundFileService` — `updateFileStatus()`, `getFileStatus()`
- `com.example.interface.inbound.service.InboundFileArchivingService` — status-based archive routing
- `com.example.interface.inbound.autoconfigure.batch.InboundFileFlatFileItemReader` — sets `PROCESSING` in `@BeforeStep`
- `com.example.interface.inbound.autoconfigure.batch.InboundFileItemProcessor` — sets terminal statuses
- `com.example.interface.inbound.autoconfigure.batch.tasklet.GenerateAckFileTasklet` — reads `InboundFileNames` and `ValidationErrors` from job context
- [App Standard §4.2 State Model](../Interface_And_Batch_Application_Standard.md#42-state-model)
- [Error Handling, Retry, and Recovery](../Inbound/Error%20Handling%2C%20Retry%2C%20and%20Recovery.md)
- [Idempotency and Duplicate Detection](../Inbound/Idempotency%20and%20Duplicate%20Detection.md)
