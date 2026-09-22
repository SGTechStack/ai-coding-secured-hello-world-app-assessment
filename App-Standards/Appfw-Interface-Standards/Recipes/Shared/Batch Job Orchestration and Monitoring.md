# Batch Job Orchestration and Monitoring

## 1. Introduction

The starter provides a built-in scheduled orchestration layer that polls for inbound files, transfers them to a local processing directory, and launches Spring Batch jobs — all without any scheduler code from the integrator. However, understanding how this orchestration is wired, how to tune its cron expressions, and how to observe its behaviour through logs and database queries is essential for production operations.

By the end of this recipe, you will:
- Understand the two built-in scheduled jobs and their responsibilities
- Configure cron expressions, cleanup, and ShedLock for your deployment
- Read the built-in batch logging output to observe job health
- Write operational queries and log searches to detect failures, backlogs, and performance issues
- Know how to wire custom listeners for post-processing notifications

## 2. Prerequisites

- `interface-management-inbound-starter` on the classpath
- The `SHEDLOCK` table present in your database (see [Idempotency and Duplicate Detection](../Inbound/Idempotency%20and%20Duplicate%20Detection.md), Step 7)
- A registered `@InboundMAGEntity` DTO and `BatchJobCommand` implementation
- A structured logging backend (ELK, Splunk, or CloudWatch Logs) to ingest JSON log output

---

## 3. Steps

### Step 1: Understand the Two Built-In Scheduled Jobs

The auto-configuration registers two scheduled tasks via `JobScheduler` (`@EnableScheduling`, `@EnableSchedulerLock`):

**Job 1: File Transfer (`scheduleFileTransferFromFsxToLocalDir`)**

Runs on `app.interface.inbound.cron.expression.transfer-fsx-to-local` (default: every 5 minutes).

Responsibilities:
1. Lists files in the FSX inbox directory (`app.interface.fsxInboxDirectory`)
2. Transfers each file to the local processing directory (`app.interface.localDirectory`)
3. For each transferred file:
   - Creates an `INBOUND_FILE` row with status `RECEIVED`
   - Detects duplicates — renames to `.bak_*` and sets status `DUPLICATE`
   - Detects unrecognised prefixes — sets status `UNRECOGNIZED`
   - Detects missing payload companions — sets status `MISSING_PAYLOAD`
4. Launches a `FileProcessingJobFactory` batch job for each `RECEIVED` file

**Job 2: Cleanup (`scheduleInboundFileCleanUpJob`)**

Runs on `app.interface.inbound.cron.expression.cleanup` (disabled by default — value `-`).

Responsibilities:
1. Finds `INBOUND_FILE` rows in `RECEIVED` status older than `app.interface.cleanUp.maxDuration`
2. Sets their status to `STALE`
3. Archives them to the stale archive path

This job addresses files that were transferred but never picked up by the batch job — typically caused by a crash or deployment during the transfer window.

---

### Step 2: Configure Cron Expressions

```yaml
# File: src/main/resources/application.yml
app:
  interface:
    # FSX inbox directory — source of incoming files
    fsxInboxDirectory: /mnt/fsx/inbound

    # Local processing directory — files are transferred here before batch job runs
    localDirectory: /data/interface/local

    inbound:
      cron:
        timezone: Asia/Singapore   # default

        # File transfer job — adjust to match your file delivery SLA
        # Default: every 5 minutes. Cron format: seconds minutes hours day month weekday
        expression:
          transfer-fsx-to-local: "0 */5 * * * *"   # every 5 minutes on the minute

          # Cleanup job — disabled by default. Enable when deploying to production.
          # Example: daily at 02:00
          cleanup: "0 0 2 * * *"

    cleanUp:
      # ISO-8601 duration — files in RECEIVED status older than this are marked STALE
      # Examples: PT30M (30 minutes), PT2H (2 hours), P1D (1 day)
      maxDuration: PT2H
```

**Choosing `transfer-fsx-to-local` frequency:**
- Set this shorter than your SLA for acknowledging inbound files
- If the SLA is "process within 30 minutes of receipt", a 5-minute poll is safe
- If you process high-volume files, ensure a single transfer cycle completes before the next one starts — ShedLock prevents overlapping runs, but a locked-out cycle means files wait an extra full interval

**Choosing `cleanup.maxDuration`:**
- Should be longer than the maximum expected processing time for a single file
- A file in `RECEIVED` status for longer than `maxDuration` indicates the batch job never ran — likely a deployment or crash event
- Set to at least 2× the `transfer-fsx-to-local` interval plus typical processing time

---

### Step 3: Prevent Concurrent Execution with ShedLock

In horizontally-scaled deployments, multiple application instances share the same FSX inbox and database. ShedLock ensures only one instance runs the transfer job at any time.

The lock is registered automatically. Ensure the `SHEDLOCK` table exists (DDL is in [Idempotency and Duplicate Detection](../Inbound/Idempotency%20and%20Duplicate%20Detection.md), Step 7).

The default lock duration is `PT5M`. If your transfer cycle regularly processes many files and takes more than 5 minutes, the lock expires before the job completes, and a second instance may start a conflicting cycle. To avoid this:

1. **Reduce the number of files per cycle** by increasing the cron interval or moving files in smaller batches from the sender
2. **Increase the lock duration** by customising `InterfaceShedLockAutoConfiguration` in your own configuration if the default is too short for your volume

Monitor for lock contention by querying the `SHEDLOCK` table:

```sql
-- See all active locks and when they were acquired
SELECT name, locked_by, locked_at, lock_until
FROM SHEDLOCK
WHERE lock_until > NOW();

-- Detect stale locks (lock_until in the past but row still present)
SELECT name, locked_by, locked_at, lock_until
FROM SHEDLOCK
WHERE lock_until < NOW();
```

---

### Step 4: Read the Built-In Batch Logging

The starter registers three listeners automatically:

- `LoggingJobExecutionListener` — logs job start and end with job name, status, and duration
- `LoggingStepExecutionListener` — logs step start and end with step name and read/write/skip counts
- `LoggingChunkListener` — logs chunk processing start, end, and errors

All logging uses the MDC context established in `GlobalMDC` and `ScheduledTaskMdcAspect`, which adds the interface metadata (id, name, type, source, destination) declared in `InterfaceProperties` to every log line.

**Typical log output for a successful job (JSON format):**

```json
{"@timestamp":"2026-03-27T14:00:02Z","log.level":"INFO","log.logger":"app.batch.job","event.kind":"event","event.category":["batch"],"event.type":["job-start"],"event.action":"file-process","batch.job.name":"inbound_PAYMENT_TXN_20260327_001","batch.job.run.id":"exec_001","trace.id":"exec_001","batch.job.id":"INT-001","interface.system":"Payment Inbound"}

{"@timestamp":"2026-03-27T14:00:03Z","log.level":"INFO","log.logger":"app.batch.step","event.kind":"event","event.category":["batch"],"event.type":["step-start"],"batch.step.name":"processingInboundFileStep","file.record_count":0}

{"@timestamp":"2026-03-27T14:02:35Z","log.level":"INFO","log.logger":"app.batch.step","event.kind":"event","event.category":["batch"],"event.type":["step-end"],"event.outcome":"success","batch.step.name":"processingInboundFileStep","file.record_count":1250,"event.duration_ms":152000}

{"@timestamp":"2026-03-27T14:02:36Z","log.level":"INFO","log.logger":"app.batch.step","event.kind":"event","event.category":["batch"],"event.type":["step-end"],"event.outcome":"success","batch.step.name":"archiveInboundFileStep","batch.job.status":"completed"}

{"@timestamp":"2026-03-27T14:02:37Z","log.level":"INFO","log.logger":"app.batch.step","event.kind":"event","event.category":["batch"],"event.type":["step-end"],"event.outcome":"success","batch.step.name":"generateAckFileStep","batch.job.status":"completed"}

{"@timestamp":"2026-03-27T14:02:38Z","log.level":"INFO","log.logger":"app.batch.job","event.kind":"event","event.category":["batch"],"event.type":["job-end"],"event.outcome":"success","batch.job.name":"inbound_PAYMENT_TXN_20260327_001","batch.job.status":"completed","event.duration_ms":156000}
```

**Log output for a chunk-level error:**

```json
{"@timestamp":"2026-03-27T14:01:15Z","log.level":"WARN","log.logger":"app.batch.chunk","event.kind":"event","event.category":["batch"],"event.type":["error"],"batch.step.name":"processingInboundFileStep","batch.chunk.id":"chunk-003","error":{"type":"org.springframework.batch.item.validator.ValidationException","message":"Sender code must be 4–10 characters"},"file.record_count":2}
```

---

### Step 5: Configure Interface Metadata for Log Correlation

The `InterfaceProperties` bean populates MDC fields that appear in every log line. Set these to identify your interface clearly in a shared logging backend:

```yaml
# File: src/main/resources/application.yml
app:
  interface:
    id: INT-PAYMENT-001
    name: Payment Transaction Inbound
    type: INBOUND
    source: PAYMENT_SYSTEM_A
    destination: CORE_BANKING
    interfacingSystem: PAYMENT_SYSTEM_A
    relevantParty: Finance Operations
```

These fields propagate automatically through `ScheduledTaskMdcAspect` to all log lines emitted during a scheduled task run. In ELK or Splunk, you can filter all logs for a specific interface using `batch.job.id = "INT-PAYMENT-001"` without needing to know individual file names.

---

### Step 6: Write Operational Monitoring Queries

**Database queries for operations dashboards:**

```sql
-- Hourly throughput: files processed in the last 24 hours
SELECT
    DATE_FORMAT(datetime_processed, '%Y-%m-%d %H:00') AS hour,
    COUNT(*) AS files_processed,
    SUM(record_count) AS total_records
FROM INBOUND_FILE
WHERE status = 'PROCESSED'
  AND datetime_processed >= NOW() - INTERVAL 24 HOUR
GROUP BY hour
ORDER BY hour;

-- Current queue depth: files waiting to be processed
SELECT COUNT(*) AS pending_files
FROM INBOUND_FILE
WHERE status = 'RECEIVED';

-- Files currently being processed (should be small; large number may indicate a hung job)
SELECT name, datetime_created
FROM INBOUND_FILE
WHERE status = 'PROCESSING'
ORDER BY datetime_created;

-- Failure rate in the last 7 days
SELECT
    status,
    COUNT(*) AS count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS pct
FROM INBOUND_FILE
WHERE datetime_created >= NOW() - INTERVAL 7 DAY
  AND status IN (
      'PROCESSED', 'PROCESSED_WITH_CONTENT_VALIDATION_ERROR',
      'PROCESSING_FAILED', 'SCHEMA_VALIDATION_FAILED',
      'INVALID_HASH_FILE', 'INVALID_SIGNATURE_FILE'
  )
GROUP BY status;

-- Oldest unprocessed file (detect stalled queue)
SELECT name, datetime_created, TIMESTAMPDIFF(MINUTE, datetime_created, NOW()) AS age_minutes
FROM INBOUND_FILE
WHERE status = 'RECEIVED'
ORDER BY datetime_created ASC
LIMIT 1;
```

**Log queries (ELK/Splunk format from App Standard §7.1):**

```
# All failures in the last 24 hours, grouped by cause
index=myapp log.logger=app.batch.job event.outcome=failure
| stats count by batch.job.id, error.category

# Processing duration percentiles — detect slow batches
index=myapp log.logger=app.batch.job event.type=job-end event.outcome=success
| stats p50(event.duration_ms), p95(event.duration_ms), p99(event.duration_ms) by batch.job.id

# Chunk errors — records failing validation
index=myapp log.logger=app.batch.chunk event.type=error
| stats count by batch.job.id, error.message

# Files stuck in PROCESSING for more than 15 minutes (job may have crashed)
index=myapp log.logger=app.batch.step event.type=step-start batch.step.name=processingInboundFileStep
| join batch.job.run.id [
    search index=myapp log.logger=app.batch.step event.type=step-end batch.step.name=processingInboundFileStep
]
| where isnull(step_completed_time) AND _time < now()-900
```

---

### Step 7: Alert Thresholds

Based on the App Standard's observable metrics (§7.1), configure the following alerts in your monitoring system:

| Metric | Query | Alert When |
|---|---|---|
| Pending file backlog | `SELECT COUNT(*) FROM INBOUND_FILE WHERE status = 'RECEIVED'` | > 0 for longer than 2× poll interval |
| Stuck processing | `SELECT COUNT(*) FROM INBOUND_FILE WHERE status = 'PROCESSING' AND datetime_created < NOW() - INTERVAL 30 MINUTE` | > 0 |
| System failures | `SELECT COUNT(*) FROM INBOUND_FILE WHERE status = 'PROCESSING_FAILED' AND datetime_created >= NOW() - INTERVAL 1 HOUR` | > 0 |
| Hash/signature failures | `SELECT COUNT(*) FROM INBOUND_FILE WHERE status IN ('INVALID_HASH_FILE','INVALID_SIGNATURE_FILE') AND datetime_created >= NOW() - INTERVAL 1 HOUR` | > threshold agreed with sender |
| Stale file accumulation | `SELECT COUNT(*) FROM INBOUND_FILE WHERE status = 'STALE' AND datetime_created >= NOW() - INTERVAL 24 HOUR` | > 0 (investigate why cleanup is not running) |

---

### Step 8: Add a Custom Job Execution Listener

If you need to send a notification, publish a metric, or update an external system when a batch job completes, implement `JobExecutionListener` and register it as a Spring bean. The auto-configuration will pick it up alongside the built-in `LoggingJobExecutionListener`.

```java
// File: src/main/java/com/example/batch/listener/NotifyOnCompletionListener.java
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.BatchStatus;
import com.example.interface.inbound.service.InboundFileService;

@Component
public class NotifyOnCompletionListener implements JobExecutionListener {

    private final NotificationService notificationService;

    public NotifyOnCompletionListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            List<String> filenames = (List<String>) jobExecution
                .getExecutionContext()
                .get(InboundFileService.INBOUND_FILENAMES_KEY);

            notificationService.alertOncall(
                "Batch job failed for files: " + filenames,
                jobExecution.getAllFailureExceptions()
            );
        }
    }
}
```

> `JobExecutionListener` beans are discovered automatically by Spring Batch. No explicit registration in a `@Configuration` class is needed if the bean is annotated with `@Component` and lives in a package scanned by the application.

---

## 4. Examples

### Example: Full Configuration for a Production Deployment

```yaml
# File: src/main/resources/application.yml
app:
  interface:
    id: INT-PAYMENT-001
    name: Payment Transaction Inbound
    type: INBOUND
    source: PAYMENT_SYSTEM_A
    destination: CORE_BANKING
    interfacingSystem: PAYMENT_SYSTEM_A
    relevantParty: Finance Operations

    fsxInboxDirectory: /mnt/fsx/payment/inbound
    localDirectory: /data/interface/local/payment
    haltOnError: false

    inbound:
      archive:
        localDir: /data/interface/archive/payment
      cron:
        timezone: Asia/Singapore
        expression:
          transfer-fsx-to-local: "0 */5 * * * *"
          cleanup: "0 0 2 * * *"

    cleanUp:
      maxDuration: PT2H

    ack:
      outputDirectory: /data/interface/ack/payment

    signature:
      enabled: true
      publicKeyDirectoryPath: /etc/interface/keys/public
      privateKeyDirectoryPath: /etc/interface/keys/private
      hashExtension: .sha3
      signatureExtension: .signed
      hashAlgorithm: SHA-256
      signatureAlgorithm: SHA256withRSA
      keystorePassword: ${KEYSTORE_PASSWORD}
```

### Example: Detecting a Hung Batch Job

A file has been in `PROCESSING` status for 45 minutes — longer than any normal processing run:

```sql
-- Identify the stuck file
SELECT name, datetime_created,
       TIMESTAMPDIFF(MINUTE, datetime_created, NOW()) AS stuck_minutes
FROM INBOUND_FILE
WHERE status = 'PROCESSING';

-- After confirming the batch job process is not running, reset for reprocessing
UPDATE INBOUND_FILE
SET status = 'RECEIVED'
WHERE name = 'PAYMENT_TXN_20260327_003.csv'
  AND status = 'PROCESSING';
```

After resetting, the next transfer cycle will relaunch the batch job for that file.

---

## 5. Verification

1. Start the application and observe the startup log for interface metadata lines (id, name, type, source, destination).
2. Drop a valid file into the FSX inbox and wait for the `transfer-fsx-to-local` cron to fire. Confirm a `RECEIVED` row appears in `INBOUND_FILE`.
3. Confirm the batch job launches and log lines for `job_started`, `step_completed`, and `job_completed` are emitted.
4. Enable the cleanup cron and seed a `RECEIVED` row older than `maxDuration`. Confirm it transitions to `STALE` on the next cleanup run.
5. In a multi-instance deployment, confirm that while one instance holds the ShedLock, a second instance's cron attempt is skipped (check `SHEDLOCK.lock_until` during an active run).

---

## 6. Conclusion

The starter's orchestration layer is entirely configuration-driven — you do not write scheduler code. The two built-in jobs (transfer and cleanup) cover the full file lifecycle from arrival to stale detection. Built-in batch listeners emit structured JSON logs at every job, step, and chunk boundary, which feed directly into your monitoring backend. The operational queries in this guide translate the App Standard's observable metrics into concrete SQL and log searches for your dashboards and alerts.

## 7. References

- `com.example.interface.inbound.autoconfigure.scheduler.JobScheduler` — the two scheduled tasks
- `com.example.interface.inbound.autoconfigure.batch.FileProcessingJobFactory` — batch job construction
- `com.example.interface.inbound.autoconfigure.batch.tasklet.CleanUpInboundFileTasklet` — stale file cleanup
- `com.example.interface.core.config.InterfaceProperties` — id, name, type, source, destination MDC fields
- `com.example.interface.core.config.InterfaceFileProperties` — `haltOnError`, `cleanUp.maxDuration`, directory paths
- `com.example.interface.inbound.autoconfigure.log.LoggingJobExecutionListener` — built-in job logger
- `com.example.interface.inbound.autoconfigure.log.LoggingStepExecutionListener` — built-in step logger
- `com.example.interface.inbound.autoconfigure.log.LoggingChunkListener` — built-in chunk logger
- [App Standard §6.1 Observable Logs and Metrics](../Interface_And_Batch_Application_Standard.md#61-observable-logs-and-metrics)
- [App Standard §6.2 Manual vs Self-Healing Error Modes](../Interface_And_Batch_Application_Standard.md#62-manual-vs-self-healing-error-modes)
- [Interface Transaction State Machine](Interface%20Transaction%20State%20Machine.md)
