# Idempotency and Duplicate Detection

## 1. Introduction

Senders can and do retransmit files — due to network errors, application restarts, or manual resubmissions. Without duplicate detection, the same batch would be processed twice, creating duplicate domain records and confusing the sender with a second ACK. The starter guards against this by comparing the incoming filename against previously processed files and returning the prior ACK without re-running the batch job.

By the end of this recipe, you will:
- Understand the exact conditions the starter uses to classify a file as a duplicate
- Know what happens to a duplicate file at the file system and database level
- Understand how the prior ACK is preserved and returned idempotently
- Know the edge cases — partial failures, companion files, and the deduplication window — and how to reason about them

## 2. Prerequisites

- `interface-management-inbound-starter` on the classpath
- A registered `@InboundMAGEntity` DTO and its repository (see [Inbound Batch Reception and Validation](Inbound%20Batch%20Reception%20and%20Validation.md))
- A configured inbound directory and database connection

---

## 3. Steps

### Step 1: Understand What Makes a File a Duplicate

The scheduler calls `InboundFileValidationService.isDuplicateFile()` for every discovered file before any batch job is launched. A file is treated as a duplicate when **all three** of the following conditions hold:

1. A row exists in `INBOUND_FILE` with the same `name` (exact filename match)
2. The existing row's `status` is **not** `MISSING_PAYLOAD`
3. The `ACK_FILE` linked to that row has a `status_code` of `SUCCESS (200)` **or** `SUCCESS_NO_DATA (204)`

This means:
- Files that previously failed (`INVALID_HASH_FILE`, `SCHEMA_VALIDATION_FAILED`, `PROCESSING_FAILED`, etc.) are **not** duplicates. They can be resubmitted with a corrected file.
- Files with status `MISSING_PAYLOAD` are not duplicates — they are waiting for their companion data file to arrive.
- Only files that were fully and successfully processed trigger duplicate detection.

---

### Step 2: What Happens When a Duplicate Is Detected

When `isDuplicateFile()` returns `true`, the scheduler:

1. **Renames** the newly arrived file to `{filename}.bak_{yyyyMMddHHmmss}` in the local inbound directory. The original filename slot is freed so legitimate resubmissions after the dedup window can reuse it.
2. **Creates** a new `INBOUND_FILE` row with status `DUPLICATE`, linked to the same filename.
3. **Does not** launch a batch job.
4. **Does not** generate a new ACK file.

The sender's existing ACK is already in the ACK output directory from the original processing run. No action is needed to "replay" it — the sender simply fetches the already-present ACK file.

> **Note:** The starter does not automatically re-transmit the prior ACK to the sender. The ACK file remains in `app.interface.ack.outputDirectory/job-{jobId}/` from the original job run. If your interface requires the system to actively push the ACK again, implement that in your outbound transport layer using the `AckFile` entity retrieved via `InboundFileRepository`.

---

### Step 3: Understand the `INBOUND_FILE` and `ACK_FILE` Relationship

The `InboundFile` entity has a `@OneToOne` relationship with `AckFile`. After a successful batch job, this relationship is always populated. You can use it to retrieve the prior ACK when handling a duplicate scenario in your own code:

```java
// File: src/main/java/com/example/service/DuplicateAckReplayService.java
import com.example.interface.inbound.entity.InboundFile;
import com.example.interface.inbound.entity.InboundFileStatus;
import com.example.interface.inbound.repository.InboundFileRepository;

@Service
public class DuplicateAckReplayService {

    private final InboundFileRepository inboundFileRepository;

    public DuplicateAckReplayService(InboundFileRepository inboundFileRepository) {
        this.inboundFileRepository = inboundFileRepository;
    }

    /**
     * Returns the path to the prior ACK file for a duplicate submission.
     * Returns empty if no prior successful processing exists.
     */
    public Optional<String> getPriorAckFilePath(String filename) {
        return inboundFileRepository.findByName(filename)
            .filter(f -> f.getStatus() == InboundFileStatus.PROCESSED
                      || f.getStatus() == InboundFileStatus.ZERO_BYTE_FILE)
            .map(InboundFile::getAckFile)
            .map(ack -> ack.getName());
    }
}
```

---

### Step 4: Handle Resubmission of Previously Failed Files

If a sender fixes an error and resubmits the same filename, the starter will process it again — because the prior row's status is not `PROCESSED` (condition 3 in Step 1 is not met).

This works automatically. No configuration is needed. The new file:
1. Gets a new `INBOUND_FILE` row (or updates the existing row's status back to `RECEIVED`)
2. Goes through the full validation and processing pipeline
3. Produces a new ACK

**This means filenames are the deduplication key.** If a sender wants to resubmit a corrected version of a failed file, they must use the **same filename** — not a new one. Using a different filename bypasses dedup entirely and creates an independent transaction.

---

### Step 5: Handle Companion Files and `MISSING_PAYLOAD`

When signature validation is enabled (`app.interface.signature.enabled: true`), the scheduler expects a data file, a `.sha3` hash file, and a `.signed` signature file to arrive together. In practice, they often arrive at slightly different times.

When only the `.sha3` or `.signed` companion arrives (without the data file), the starter creates an `INBOUND_FILE` row with status `MISSING_PAYLOAD`. This status is explicitly excluded from duplicate detection (condition 2 in Step 1).

When the data file subsequently arrives, `saveNewInboundFile()` detects the prior `MISSING_PAYLOAD` row and promotes it to `RECEIVED`, linking the companion files. The batch job then proceeds normally.

**Implication:** If a data file arrives first and the companion is missing, the row is created as `RECEIVED` and processing is attempted. Whether this succeeds depends on whether validation is configured to require the companion files.

---

### Step 6: Understand the Deduplication Scope

The starter's duplicate detection is **filename-scoped** — it matches on `INBOUND_FILE.name` exactly. There is no configurable time window for this detection; as long as a `PROCESSED` row with the same filename exists in the database, the file is treated as a duplicate.

The cleanup job (`inboundFileCleanUpJob`) removes `INBOUND_FILE` rows in `RECEIVED` status that are older than `app.interface.cleanUp.maxDuration`. It does **not** remove `PROCESSED` rows. This means processed file records persist indefinitely until explicitly archived or cleaned by a database retention policy.

If your interface legitimately reuses filenames across time periods (e.g., `DAILY_EXPORT.csv` sent every day), your `@MAGEntity` must use a `sequenceType` that produces a unique filename per transmission:

```java
// File: src/main/java/com/example/dto/DailyExportDTO.java

// WRONG: filename is always DAILY_EXPORT.csv — second day's file is always a duplicate
@MAGEntity(
    filenamePrefix = "DAILY_EXPORT",
    sequenceType = SequenceType.NONE,  // no sequence suffix
    fileType = FileType.CSV
)

// CORRECT: filename includes date+sequence — DAILY_EXPORT_20260327_001.csv
@MAGEntity(
    filenamePrefix = "DAILY_EXPORT",
    sequenceType = SequenceType.DAILY_SEQUENCE,  // appends YYYYMMDD_NNN suffix
    fileType = FileType.CSV
)
```

**`SequenceType` options:**

| Value | Filename Suffix Format | Use When |
|---|---|---|
| `DAILY_SEQUENCE` | `_YYYYMMDD_NNN` | Files generated daily with a sequence counter |
| `TRANSACTION_SEQUENCE` | `_NNN` (global counter) | Files assigned a transaction number at generation |
| `CONTINUOUS_SEQUENCE` | `_NNN` (continuous counter) | Files in a continuous numbered series |
| `NONE` | (no suffix) | Filenames are already globally unique |

---

### Step 7: Preventing Concurrent Processing with ShedLock

ShedLock prevents two instances of the scheduled job from processing the same file concurrently. This is critical in horizontally-scaled deployments where multiple application instances share the same FSX inbox and database.

The lock is configured automatically by `InterfaceShedLockAutoConfiguration`. You must ensure the `SHEDLOCK` table exists in your database:

```sql
-- File: db/migration/V1__create_shedlock.sql
CREATE TABLE SHEDLOCK (
  name        VARCHAR(64)  NOT NULL,
  lock_until  TIMESTAMP(3) NOT NULL,
  locked_at   TIMESTAMP(3) NOT NULL,
  locked_by   VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);
```

The lock duration defaults to `PT5M` (5 minutes). If your file transfer step (`transfer-fsx-to-local`) regularly takes longer than 5 minutes, the lock will expire before the job completes and a second instance may start processing the same files. In that case, increase the lock duration in your ShedLock configuration or reduce the volume of files transferred per cycle.

---

## 4. Examples

### Example: Duplicate Resubmission Scenario

```
-- Initial submission (processed successfully)
INBOUND_FILE: name='PAYMENT_TXN_20260327_001.csv', status=PROCESSED
ACK_FILE:     name='PAYMENT_TXN_20260327_001.csv.ack_200_20260327T143045', status_code=SUCCESS

-- Sender retransmits the same file one hour later
File arrives: PAYMENT_TXN_20260327_001.csv (same name)

-- isDuplicateFile() returns true:
--   condition 1: row with name='PAYMENT_TXN_20260327_001.csv' exists ✓
--   condition 2: status is PROCESSED (not MISSING_PAYLOAD) ✓
--   condition 3: ACK status_code is SUCCESS ✓

-- Actions taken by scheduler:
Rename file: PAYMENT_TXN_20260327_001.csv → PAYMENT_TXN_20260327_001.csv.bak_20260327T153045
New row:     INBOUND_FILE name='PAYMENT_TXN_20260327_001.csv', status=DUPLICATE
Batch job:   NOT launched
New ACK:     NOT generated (prior ACK remains in output directory)
```

### Example: Resubmission After Failure (Not a Duplicate)

```
-- Initial submission (failed hash validation)
INBOUND_FILE: name='PAYMENT_TXN_20260327_001.csv', status=INVALID_HASH_FILE
ACK_FILE:     name='PAYMENT_TXN_20260327_001.csv.ack_460_20260327T143045', status_code=CHECKSUM_EXCEPTION

-- Sender fixes the hash file and resubmits the same filename
File arrives: PAYMENT_TXN_20260327_001.csv

-- isDuplicateFile() returns false:
--   condition 3 FAILS: ACK status_code is CHECKSUM_EXCEPTION (not SUCCESS or SUCCESS_NO_DATA)

-- Actions taken by scheduler:
New row:   INBOUND_FILE name='PAYMENT_TXN_20260327_001.csv', status=RECEIVED
Batch job: LAUNCHED — full processing pipeline runs
New ACK:   Generated based on new processing result
```

---

## 5. Verification

1. Process a valid file successfully. Confirm `INBOUND_FILE.status = PROCESSED` and an `.ack_200_*` file in the ACK output directory.
2. Copy the same file back into the FSX inbox. After the next scheduler cycle:
   - Check the local inbound directory for a `.bak_*` renamed file.
   - Query `SELECT status FROM INBOUND_FILE WHERE name = 'your-file.csv'` — a new `DUPLICATE` row should appear.
   - Confirm no new ACK file was generated.
3. Deliberately submit a file with a bad hash. Confirm status `INVALID_HASH_FILE`. Resubmit with a corrected hash — confirm the file is processed again (not treated as duplicate).
4. In a multi-instance deployment, confirm only one instance processes each file by checking `SHEDLOCK.locked_by` during an active run.

---

## 6. Conclusion

Duplicate detection in the starter is automatic and requires no implementation code beyond correctly naming your files using a unique `sequenceType` in `@MAGEntity`. The three-condition check (same filename, prior status is not `MISSING_PAYLOAD`, prior ACK was successful) provides idempotent protection against retransmissions while allowing legitimate resubmissions of previously failed files. The prior ACK is preserved in the output directory and is available to senders without any replay mechanism.

## 7. References

- `com.example.interface.inbound.service.InboundFileValidationService#isDuplicateFile` — duplicate detection logic
- `com.example.interface.inbound.service.InboundFileService#saveNewInboundFile` — file creation and `MISSING_PAYLOAD` promotion
- `com.example.interface.inbound.entity.InboundFile` — entity with `ackFile` OneToOne relationship
- `com.example.interface.inbound.entity.AckFile` — ACK entity with `statusCode` field
- `com.example.interface.inbound.entity.InboundFileStatus` — `DUPLICATE`, `MISSING_PAYLOAD` statuses
- `com.example.interface.inbound.entity.AckFileStatusCode` — `SUCCESS`, `SUCCESS_NO_DATA` used in dedup check
- `com.example.interface.core.annotation.MAGEntity#sequenceType` — filename uniqueness strategy
- [App Standard §3.5 Idempotency and Replay Protection](../Interface_And_Batch_Application_Standard.md#35-security-contract)
- [App Standard §2.1 Duplicate Detection Path](../Interface_And_Batch_Application_Standard.md#21-inbound-interface-flow)
