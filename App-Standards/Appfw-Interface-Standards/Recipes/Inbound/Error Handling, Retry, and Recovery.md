# Error Handling, Retry, and Recovery

## 1. Introduction

The starter provides a layered error handling model where failures at different stages — file-level validation, schema validation, and business logic processing — map to distinct statuses and ACK response codes. Understanding this model lets you implement `BatchJobCommand` correctly, configure the right behavior for your use case, and produce meaningful ACKs to senders.

By the end of this recipe, you will:
- Understand every failure status in `InboundFileStatus` and when each is assigned
- Know how `haltOnError` changes the processing outcome for record-level errors
- Implement `BatchJobCommand.process()` to handle both recoverable and fatal errors
- Understand how `AckFileStatusCode` is derived from the final file status
- Know how validation errors surface in ACK files

## 2. Prerequisites

- `interface-management-inbound-starter` on the classpath
- A registered `@InboundMAGEntity` DTO and `BatchJobCommand` implementation (see [Inbound Batch Reception and Validation](Inbound%20Batch%20Reception%20and%20Validation.md))
- Familiarity with Spring Batch chunk processing

---

## 3. Steps

### Step 1: Understand the Error Status Taxonomy

The starter uses `InboundFileStatus` to record exactly where and why a file failed. The table below maps each failure status to its cause and the ACK code that the `GenerateAckFileTasklet` will produce.

| Status | Cause | ACK Code |
|---|---|---|
| `UNRECOGNIZED` | Filename prefix does not match any `@MAGEntity.filenamePrefix` | No ACK generated — file is archived |
| `MISSING_PAYLOAD` | A `.sha3` or `.signed` companion file arrived with no corresponding data file | No ACK generated |
| `INVALID_HASH_FILE` | SHA-256 hash in the `.sha3` file does not match the computed hash of the data file | `CHECKSUM_EXCEPTION (460)` |
| `INVALID_SIGNATURE_FILE` | RSA digital signature in the `.signed` file failed verification | `DIGITAL_SIGNATURE_EXCEPTION (461)` |
| `ZERO_BYTE_FILE` | Data file exists but contains zero content bytes | `SUCCESS_NO_DATA (204)` |
| `SCHEMA_VALIDATION_FAILED` | Jakarta Validation errors on one or more records **and** `haltOnError=true` | `SCHEMA_VALIDATION_EXCEPTION (462)` + error details |
| `PROCESSING_FAILED` | An uncaught exception was thrown from `BatchJobCommand.process()` | `PROCESSING_EXCEPTION (500)` |
| `PROCESSED_WITH_CONTENT_VALIDATION_ERROR` | Jakarta Validation errors on one or more records **and** `haltOnError=false` | `SCHEMA_VALIDATION_EXCEPTION (462)` + error details |
| `PROCESSED` | All records passed validation and were processed successfully | `SUCCESS (200)` |

Statuses `DUPLICATE` and `STALE` are handled before the batch job runs and do not reach ACK generation. See [Idempotency and Duplicate Detection](Idempotency%20and%20Duplicate%20Detection.md) for details on `DUPLICATE`.

---

### Step 2: Configure `haltOnError`

The `haltOnError` property controls what happens when a record fails Jakarta Validation during processing.

```yaml
# File: src/main/resources/application.yml
app:
  interface:
    haltOnError: false   # default — continue processing remaining records, ACK with 462
    # haltOnError: true  # stop the entire batch on first validation failure, ACK with 462
```

**When `haltOnError: false` (default):**
- Records with validation errors are passed to `BatchJobCommand.process()` with `isContentValid=false` and the formatted `validationErrors` string
- Processing continues for all remaining records
- Final status: `PROCESSED_WITH_CONTENT_VALIDATION_ERROR`
- ACK contains the full list of per-record errors

**When `haltOnError: true`:**
- The first record with a validation error causes the batch step to throw an exception
- The batch job stops immediately; no further records are processed
- Final status: `SCHEMA_VALIDATION_FAILED`
- ACK contains the errors collected up to the point of failure

Choose `haltOnError: false` for interfaces where partial success is acceptable. Choose `haltOnError: true` for all-or-nothing interfaces where even a single invalid record must cause the entire batch to be rejected.

---

### Step 3: Implement `BatchJobCommand.process()` for Error Cases

`InboundFileItemProcessor` calls your `BatchJobCommand.process()` for every record, passing the result of Jakarta Validation as parameters. Your implementation receives:

- `dto` — the parsed record
- `isContentValid` — `true` if all `@NotNull`, `@Size`, and other Jakarta Validation constraints passed
- `validationErrors` — a formatted string of all violation messages for the record (empty string when `isContentValid` is `true`)

You have three possible responses:

**Option A — Return `null` to skip the record silently (not recommended for most cases):**
```java
// File: src/main/java/com/example/batch/command/PaymentBatchCommand.java
@Component
public class PaymentBatchCommand implements BatchJobCommand<PaymentFileDTO, PaymentRecord> {

    @Override
    public PaymentRecord process(PaymentFileDTO dto, boolean isContentValid, String validationErrors) {
        if (!isContentValid) {
            // Returning null skips the record — it will NOT be written
            // The processor still tracks the error; status will be PROCESSED_WITH_CONTENT_VALIDATION_ERROR
            log.atWarn()
                .setMessage("Skipping invalid record: {}")
                .addArgument(validationErrors)
                .log();
            return null;
        }
        return mapToRecord(dto);
    }
}
```

**Option B — Process the record regardless, attaching the error context to the output entity:**
```java
@Override
public PaymentRecord process(PaymentFileDTO dto, boolean isContentValid, String validationErrors) {
    PaymentRecord record = mapToRecord(dto);
    if (!isContentValid) {
        record.setValidationErrors(validationErrors);
        record.setStatus(RecordStatus.INVALID);
    }
    return record;
}
```

**Option C — Throw `FileContentValidationException` to force `PROCESSING_FAILED` status:**

Use this when a business rule failure is so severe that the entire batch must be marked as a system error rather than a validation error. This bypasses the normal `SCHEMA_VALIDATION_FAILED` path.

```java
// File: src/main/java/com/example/batch/command/PaymentBatchCommand.java
import com.example.interface.core.exception.FileContentValidationException;

@Override
public PaymentRecord process(PaymentFileDTO dto, boolean isContentValid, String validationErrors)
        throws FileContentValidationException {

    if (!isContentValid) {
        // Normal validation failure — let the processor handle it
        return null;
    }

    // Fatal business rule: sender account must exist
    if (!accountRepository.existsByCode(dto.getSenderCode())) {
        throw new FileContentValidationException(
            "Sender account not found: " + dto.getSenderCode()
        );
    }

    return mapToRecord(dto);
}
```

> **Important:** Only throw `FileContentValidationException` for unrecoverable business faults. Normal schema errors should be communicated via the `isContentValid`/`validationErrors` parameters, not exceptions.

---

### Step 4: Understand How Validation Errors Flow to the ACK

The processor accumulates validation errors across all records and stores them in the Spring Batch `JobExecutionContext` under the key `AckFileService.VALIDATION_ERRORS_KEY` (`"ValidationErrors"`).

The `GenerateAckFileTasklet` reads this context at the end of the job and writes the error string into the ACK file content. No additional coding is required — this is automatic.

The format of the error string stored in context is built by the processor. When writing `BatchJobCommand.process()`, you do not need to re-format errors; they are already collected from Jakarta Validation results before your code is called.

If you need to append additional context to the errors from within `process()`, you can write to the job execution context directly via `StepContribution`'s execution context if you inject `StepExecution`. In most cases this is unnecessary — the Jakarta Validation messages on your DTO fields are sufficient.

**Setting meaningful validation messages on your DTO:**
```java
// File: src/main/java/com/example/batch/dto/PaymentFileDTO.java
@MAGEntity(filenamePrefix = "PAYMENT_TXN", fileType = FileType.CSV)
public class PaymentFileDTO extends InboundFileContentDTO {

    @Position(1)
    @NotBlank(message = "Record ID must not be blank")
    private String recordId;

    @Position(2)
    @NotBlank(message = "Sender code must not be blank")
    @Size(min = 4, max = 10, message = "Sender code must be 4–10 characters")
    private String senderCode;

    @Position(3)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    // getters / setters
}
```

These violation messages become the `validationErrors` string passed to `process()` and appear verbatim in the ACK file.

---

### Step 5: Handle File-Level Validation Failures (Hash and Signature)

Hash and signature validation happen before the batch job runs, in `InboundFileValidationService`. These failures are set automatically by the framework — you do not implement anything for them. However, you must ensure the companion files are present if signature validation is enabled.

```yaml
# File: src/main/resources/application.yml
app:
  interface:
    signature:
      enabled: true
      privateKeyDirectoryPath: /etc/secrets/keys/private
      publicKeyDirectoryPath: /etc/secrets/keys/public
      hashExtension: .sha3         # companion hash file suffix
      signatureExtension: .signed  # companion signature file suffix
      hashAlgorithm: SHA-256
      signatureAlgorithm: SHA256withRSA
```

When `signature.enabled: true`, the scheduler expects to find:
- `PAYMENT_TXN_001.csv` — the data file
- `PAYMENT_TXN_001.csv.sha3` — the SHA-256 hash file
- `PAYMENT_TXN_001.csv.signed` — the RSA signature file

If the hash does not match → `INVALID_HASH_FILE` → ACK `460`.
If the signature fails → `INVALID_SIGNATURE_FILE` → ACK `461`.
If the data file is missing but the companion arrived → `MISSING_PAYLOAD` → no ACK, no processing.

---

### Step 6: Configure Chunk Size for Transactional Recovery

The batch job processes records in chunks. Each chunk is a single database transaction. The `chunkSize` parameter on `FileProcessingJobFactory.create()` determines how many records are committed at once.

The factory is called internally by the auto-configuration; you do not call it directly. However, you control chunk size via the `batchJobCommand` configuration if you customise job creation, or accept the default from the framework.

**All-or-nothing (full file rollback on any failure):**
```java
// Use Integer.MAX_VALUE as chunk size — entire file is one transaction
// Any processing failure rolls back all records
fileProcessingJobFactory.create(filename, Integer.MAX_VALUE);
```

**Partial success (commit per chunk, tolerate some failures):**
```java
// Default chunk size — records are committed in batches
// Failed records within a chunk cause that chunk to roll back; prior chunks are already committed
fileProcessingJobFactory.create(filename, 100);
```

> When `haltOnError: false` and a small chunk size is used, some records will be committed and some will not. This produces `PROCESSED_WITH_CONTENT_VALIDATION_ERROR` and the ACK will list the failed records. Ensure your domain logic is idempotent if the same file might be resubmitted after a partial failure.

---

### Step 7: Configure Restartability and Restart Failed Jobs

When processing massive batch files, restarting the entire job from record 1 after a mid-batch crash is inefficient and risks duplicating database entries. The standard mandates **chunk-based restartability**, where the job picks up exactly from the last committed chunk checkpoint.

#### 1. Configure the Step as Restartable
In your job configuration, ensure the step allows restarts (this is enabled by default in Spring Batch but can be custom-tuned):

```java
// File: src/main/java/com/example/batch/config/BatchJobConfig.java
@Bean
public Step processingInboundFileStep(JobRepository jobRepository, 
                                      PlatformTransactionManager transactionManager,
                                      ItemReader<PaymentFileDTO> reader,
                                      ItemProcessor<PaymentFileDTO, PaymentRecord> processor,
                                      ItemWriter<PaymentRecord> writer) {
    return new StepBuilder("processingInboundFileStep", jobRepository)
        .<PaymentFileDTO, PaymentRecord>chunk(100, transactionManager) // Chunk size checkpoint
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .allowStartIfComplete(false) // Prevent re-running successfully completed steps
        .build();
}
```

#### 2. Execute a Restart
If a job fails, Spring Batch records a status of `FAILED` in the `BATCH_JOB_EXECUTION` metadata table. You can restart the job by executing a restart command passing the `jobExecutionId` of the failed run. The `JobOperator` will automatically retrieve the step execution context, locate the last committed chunk index, and skip already-processed records:

```java
// File: src/main/java/com/example/batch/service/JobRecoveryService.java
package com.example.batch.service;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

@Service
public class JobRecoveryService {
    
    private final JobOperator jobOperator;

    public JobRecoveryService(JobOperator jobOperator) {
        this.jobOperator = jobOperator;
    }

    /**
     * Restart a previously failed job execution.
     * Spring Batch will start reading from the last checkpoint chunk automatically.
     */
    public Long restartFailedJob(long failedExecutionId) throws Exception {
        return jobOperator.restart(failedExecutionId);
    }
}
```

---

### Step 8: Resolve Stale and Missing Payload Files

Files that remain in `RECEIVED` or `MISSING_PAYLOAD` status beyond the configured cutoff window are considered stale (usually due to a container crash or unexpected shutdown). 

To ensure system health, configure and run the periodic stale file cleanup job:
1. **Detection**: Query `INBOUND_FILE` rows where `status = 'RECEIVED'` or `status = 'MISSING_PAYLOAD'` and `created_at < (now - maxDuration)`.
2. **Transition**: Promote `RECEIVED` files to `STALE` status.
3. **Archiving**: Move stale files to `{archiveRoot}/inbound/{prefix}/stale/` and mark as archived in the DB.

Detailed scheduling properties, ShedLock distributed concurrency controls, and cron task tuning details for this cleanup process are covered in the shared guide [Batch Job Orchestration and Monitoring](../Shared/Batch%20Job%20Orchestration%20and%20Monitoring.md#step-1-understand-the-two-built-in-scheduled-jobs).

---

## 4. Examples

### Example: ACK File Content for Schema Validation Failure

Given a file with 3 records where record 2 fails validation:

```
# ACK file: PAYMENT_TXN_001.csv.ack_462_20260327T143045
STATUS_CODE: 462
STATUS: SCHEMA_VALIDATION_EXCEPTION
INBOUND_FILE: PAYMENT_TXN_001.csv
ERRORS:
  Record [PAYMENT_TXN_001.csv, line 3]: Sender code must be 4–10 characters; Amount must be greater than zero
```

The status code `462` maps directly to `AckFileStatusCode.SCHEMA_VALIDATION_EXCEPTION`.

### Example: Zero-Byte File ACK

When an empty file arrives:

```
# ACK file: PAYMENT_TXN_002.csv.ack_204_20260327T143100
STATUS_CODE: 204
STATUS: SUCCESS_NO_DATA
INBOUND_FILE: PAYMENT_TXN_002.csv
```

The sender is informed the file was received but contained no records. No data is persisted.

---

## 5. Verification

1. Place a CSV file with an intentionally blank required field in the FSX inbound directory.
2. Wait for the scheduled transfer (`app.interface.inbound.cron.expression.transfer-fsx-to-local`) to pick it up.
3. Check the ACK output directory (`app.interface.ack.outputDirectory`) for an `.ack_462_*` file.
4. Query the `INBOUND_FILE` table: `SELECT status FROM INBOUND_FILE WHERE name = 'your-file.csv'` — expect `PROCESSED_WITH_CONTENT_VALIDATION_ERROR` (or `SCHEMA_VALIDATION_FAILED` if `haltOnError: true`).
5. Place a file with a corrupted `.sha3` companion (if signature validation is enabled) and confirm an `.ack_460_*` is generated and the row shows `INVALID_HASH_FILE`.

---

## 6. Conclusion

The starter's error model maps every failure mode to a specific `InboundFileStatus` and a corresponding `AckFileStatusCode`, removing the need to write custom error routing logic. By implementing `BatchJobCommand.process()` correctly — returning `null` to skip records, returning an enriched entity to persist errors inline, or throwing `FileContentValidationException` for fatal faults — you get a fully populated ACK file and a consistent audit trail with no additional infrastructure code.

## 7. References

- `com.example.interface.inbound.entity.InboundFileStatus` — all 13 status values
- `com.example.interface.inbound.entity.AckFileStatusCode` — ACK numeric codes and their meanings
- `com.example.interface.inbound.autoconfigure.batch.command.BatchJobCommand` — processor interface
- `com.example.interface.core.exception.FileContentValidationException` — fatal processing exception
- `com.example.interface.inbound.autoconfigure.batch.InboundFileItemProcessor` — processor implementation
- `com.example.interface.inbound.autoconfigure.batch.tasklet.GenerateAckFileTasklet` — status → ACK mapping
- [App Standard §3.2 Error Contract](../Interface_And_Batch_Application_Standard.md#32-error-contract)
- [App Standard §2.1 Failure Paths](../Interface_And_Batch_Application_Standard.md#21-inbound-interface-flow)
