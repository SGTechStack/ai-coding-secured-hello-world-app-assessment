# Logging Batch and Scheduled Job Operations

## 1. Introduction
Batch and scheduled jobs run asynchronously outside of user request flows, span multiple phases, and may execute for minutes or hours. Without dedicated logging, it is difficult to correlate which log entries belong to a specific job run, determine how long each phase took, or identify at which step a failure occurred.

This guide covers structured logging for each phase of batch and scheduled operations: job schedule registration at startup, job and step lifecycle events, file operations, and external service calls. It demonstrates using Spring Batch's listener model—as well as plain `@Scheduled` methods—along with MDC to carry job and step context to all log entries within a job boundary, making every entry queryable by job run, step, and outcome.

## 2. Prerequisites
- Spring Boot 4.0+
- Spring Batch on the classpath (required for batch job lifecycle logging)
- Structured logging enabled so log fields appear in structured output. See [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md) for setup instructions
- Familiarity with Mapped Diagnostic Context (MDC). See [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)
- Schema compliance: Use field values from [Log_Schema.md](../Log_Schema.md) for `event.category`, `event.type`, `event.action`, and `error_category` to ensure consistency across services

<note>

> **Note on Advanced Tracing Fields:** This guide focuses on essential batch job logging using `trace.id` (set to `batch.job.run.id`). For advanced distributed tracing scenarios:
> - **`span.id`** — Use when integrating with OpenTelemetry or Micrometer Tracing for step/operation-level spans within a job
> - **`correlation.id`** — Use when tracking business processes that span multiple batch jobs across systems and time (e.g., end-to-end order fulfillment spanning import → processing → export jobs)
> 
> These fields are optional and outside the scope of this guide. See [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md) for implementation details.

</note>

## 3. Log job schedules at startup
Log the job name, cron expression, and timezone at startup so operators can verify schedules without inspecting source code or configuration files. This allows teams to spot misconfigurations (e.g., wrong timezone, incorrect cron syntax) before the first execution.

```java
# File: src/main/java/com/example/BatchJobScheduleLogger.java
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@Component
public class BatchJobScheduleLogger {

    @Value("${batch.job.cron.expression:0 */1 * * * *}")
    private String cronExpression;

    @Value("${batch.job.cron.timezone:Asia/Singapore}")
    private String cronTimezone;

    @EventListener(ApplicationReadyEvent.class)
    public void logScheduledJobs() {
        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("configuration"))
            .addKeyValue("event.type", List.of("info"))
            .addKeyValue("event.severity", "low")
            .addKeyValue("batch.job.name", "fileProcessingJob")
            .addKeyValue("trigger.cron.expression", cronExpression)
            .addKeyValue("trigger.cron.timezone", cronTimezone)
            .log("Scheduled job registered.");
    }
}
```

## 4. Spring Batch built-in logging
**Note:** Sections 4-7 apply to Spring Batch applications. For plain `@Scheduled` methods, skip to Section 8.

Spring Batch logs basic lifecycle events (job launch, step execution, completion) but does not:
- Add structured key-value fields at `INFO` level
- Set MDC context (job/step IDs won't appear in logs automatically)
- Log item counts as structured fields (only in plain-text `DEBUG` summaries)

The custom `JobExecutionListener` and `StepExecutionListener` in this guide fill these gaps by logging structured fields, enriching MDC for automatic context propagation, and surfacing item counts at step completion.

## 5. Log job lifecycle

### Before the job starts
Set job context in MDC so every log entry emitted during the job—from within steps, file processors, or external service calls—automatically carries job identifiers. Without MDC, only the job-start log would contain these fields, leaving all subsequent entries uncorrelated. Log job parameters with masked values to avoid exposing sensitive data.

<note>

> **Note:** Several fields are read from job parameters and are caller-provided:
> - `trigger.id` — Unique ID from the master scheduler/orchestrator for cross-system correlation (optional)
> - `trigger.type`, `trigger.by.name`, `trigger.by.type` — Set by the scheduler/orchestrator that launches the job (defaults to "ad-hoc"/"unknown"/"system" if not provided)
> - `batch.job.run.retry_count` — Set by the scheduler when retrying (see Section 7a)
> - `batch.job.run.recovery.*` — Set when launching a recovery job (see Section 7b)
> 
> If these parameters are not passed, the listener uses defaults or omits them from logs.

</note>

```java
# File: src/main/java/com/example/FileProcessingJobListener.java
import org.slf4j.MDC;
import org.slf4j.event.LoggingEventBuilder;
import org.springframework.batch.core.*;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class FileProcessingJobListener extends JobExecutionListenerSupport {
    
    @Override
    public void beforeJob(JobExecution jobExecution) {
    String jobId = String.valueOf(jobExecution.getJobId());
    String runId = String.valueOf(jobExecution.getId());
    String jobName = jobExecution.getJobInstance().getJobName();
    String jobStatus = jobExecution.getStatus().name();
    
    // Read retry count from job parameters. Defaults to 0 if not supplied by the caller
    Long retryCountParam = jobExecution.getJobParameters().getLong("batch.job.run.retry_count");
    
    // Read trigger metadata from job parameters
    String triggerId = jobExecution.getJobParameters().getString("trigger.id");  // Master scheduler/orchestrator ID
    String triggerType = jobExecution.getJobParameters().getString("trigger.type", "ad-hoc");
    String triggerByName = jobExecution.getJobParameters().getString("trigger.by.name", "unknown");
    String triggerByType = jobExecution.getJobParameters().getString("trigger.by.type", "system");
    
    // Read recovery metadata if this is a recovery job
    String recoveryMode = jobExecution.getJobParameters().getString("batch.job.run.recovery.mode");
    String recoveryReason = jobExecution.getJobParameters().getString("batch.job.run.recovery.reason");
    String recoveryJobId = jobExecution.getJobParameters().getString("batch.job.run.recovery.job.id");
    String recoveryJobName = jobExecution.getJobParameters().getString("batch.job.run.recovery.job.name");
    String recoveryJobRunId = jobExecution.getJobParameters().getString("batch.job.run.recovery.job.run.id");
    
    // Mask parameter values to avoid exposing sensitive data
    Map<String, String> maskedParameters = new HashMap<>();
    jobExecution.getJobParameters().getParameters().keySet()
        .forEach(key -> maskedParameters.put(key, "***"));

    // Set MDC context for this job execution
    MDC.put("batch.job.id", jobId);
    MDC.put("batch.job.run.id", runId);
    MDC.put("batch.job.name", jobName);
    MDC.put("batch.job.status", jobStatus);
    MDC.put("trace.id", runId);  // Use run ID as trace ID for correlation
    
    if (retryCountParam != null) {
        MDC.put("batch.job.run.retry_count", String.valueOf(retryCountParam));
    }
    
    if (recoveryMode != null) {
        MDC.put("batch.job.run.recovery.mode", recoveryMode);
    }

    // Build log entry with mandatory fields
    LoggingEventBuilder logBuilder = log.atInfo()
        .addKeyValue("event.kind", "event")
        .addKeyValue("event.category", List.of("batch"))
        .addKeyValue("event.type", List.of("job-start"))
        .addKeyValue("event.start", jobExecution.getStartTime().toString())
        .addKeyValue("event.severity", "low")
        .addKeyValue("batch.job.id", jobId)
        .addKeyValue("batch.job.run.id", runId)
        .addKeyValue("batch.job.name", jobName)
        .addKeyValue("batch.job.status", jobStatus)
        .addKeyValue("batch.job.run.parameter", maskedParameters)
        .addKeyValue("trigger.id", triggerId)  // Master scheduler ID (optional)
        .addKeyValue("trigger.type", List.of(triggerType))
        .addKeyValue("trigger.by", List.of(Map.of("name", triggerByName, "type", triggerByType)))
        .addKeyValue("batch.job.run.retry_count", retryCountParam != null ? retryCountParam : 0);
    
    // Add recovery fields only if this is a recovery job
    if (recoveryMode != null) {
        logBuilder
            .addKeyValue("batch.job.run.recovery.mode", recoveryMode)
            .addKeyValue("batch.job.run.recovery.reason", recoveryReason)
            .addKeyValue("batch.job.run.recovery.job.id", recoveryJobId)
            .addKeyValue("batch.job.run.recovery.job.name", recoveryJobName)
            .addKeyValue("batch.job.run.recovery.job.run.id", recoveryJobRunId);
    }
    
    logBuilder.log("Job started.");
}
```

### After the job completes
Log the final status and total duration. For failures, also include the error code, type, category, message, stack trace, and whether follow-up action is required. Ensure all job-related MDC fields are explicitly removed in a `finally` block so they do not leak into the next scheduled execution.

<note>

> **Note:** When logging custom error fields, you must use underscore keys (e.g., `error_code` instead of `error.code`). Spring Boot's ECS formatter pre-seals the `error` object, so using dotted keys causes a JSON writing error. A custom encoder safely remaps the underscore keys back to their proper nested schema fields. See [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md).

</note>

```java
# File: src/main/java/com/example/FileProcessingJobListener.java
@Override
public void afterJob(JobExecution jobExecution) {
    try {
        long duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("batch"))
                .addKeyValue("event.type", List.of("job-end"))
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("event.end", jobExecution.getEndTime().toString())
                .addKeyValue("event.duration_ms", duration)
                .log("Job completed successfully.");
        } else {
            Throwable rootCause = jobExecution.getAllFailureExceptions().isEmpty()
                ? new RuntimeException("Unknown failure")
                : jobExecution.getAllFailureExceptions().get(0);
            log.atError()
                .setCause(rootCause)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("batch"))
                .addKeyValue("event.type", List.of("job-end"))
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("event.end", jobExecution.getEndTime().toString())
                .addKeyValue("event.duration_ms", duration)
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .log("Job failed.");
        }
    } finally {
        // Execute in finally to guarantee cleanup even if logging throws an exception
        MDC.remove("batch.job.id");
        MDC.remove("batch.job.run.id");
        MDC.remove("batch.job.name");
        MDC.remove("batch.job.status");
        MDC.remove("batch.job.run.retry_count");
        MDC.remove("batch.job.run.recovery.mode");
        MDC.remove("trace.id");
    }
}
}  // End of FileProcessingJobListener class
```

## 6. Log step lifecycle
Log at each step transition to capture granular progress and item counts.

### Before the step starts
Include the step identifier, name, and start time in MDC.

<note>

> **Note:** This example shows basic step logging without file or interface context. If your step processes files, see Section 9 for how to add file and interface context to MDC so they automatically appear in all logs during step execution (ItemReader, ItemProcessor, ItemWriter).

</note>

```java
# File: src/main/java/com/example/FileProcessingStepListener.java
@Override
public void beforeStep(StepExecution stepExecution) {
    String stepId = String.valueOf(stepExecution.getId());
    String stepName = stepExecution.getStepName();
    
    MDC.put("batch.step.id", stepId);
    MDC.put("batch.step.name", stepName);
    
    log.atInfo()
        .addKeyValue("event.kind", "event")
        .addKeyValue("event.category", List.of("batch"))
        .addKeyValue("event.type", List.of("step-start"))
        .addKeyValue("event.severity", "low")
        .addKeyValue("event.start", stepExecution.getStartTime().toString())
        .addKeyValue("batch.step.id", stepId)
        .addKeyValue("batch.step.name", stepName)
        .log("Step started.");
}
```

### After the step completes
Do not log inside per-item processing loops. For a job processing 100,000 items, per-item logging generates massive noise and inflates storage costs. Instead, log aggregated counts at the step boundary to provide a complete picture without the noise.

The `afterStep` callback runs on both success and failure. For success, include a `record` summary derived from step execution metrics (read count as total, write count as success, sum of skip counts as failure). For failure, include the same summary alongside the error fields.

<note>

> **Note on Multiple Record Types:** 
> 
> **Single step, multiple record types:** If one step processes multiple distinct record types (e.g., "order" and "order_item" in the same step) and you need per-type counts, you must:
> 1. Manually track counts in your `ItemProcessor` or `ItemWriter` (e.g., `orderSuccessCount`, `orderItemFailureCount`)
> 2. Store them in `StepExecutionContext` using `stepExecution.getExecutionContext().put("orderSuccessCount", count)`
> 3. Retrieve them in `afterStep` and build the multi-type `record` array
> 
> Example multi-type record array:
> ```java
> .addKeyValue("record", List.of(
>     Map.of("name", "order", "total", 1500, "success", 1490, "failure", 10),
>     Map.of("name", "order_item", "total", 3000, "success", 2980, "failure", 20)
> ))
> ```
> 
> **Multiple steps, different record types:** If each step processes a different record type (step 1 = orders, step 2 = order_items, step 3 = payments), each step's `afterStep` listener automatically logs its own record type. No manual tracking needed—just set the appropriate `record.name` in each step's listener based on what that step processes.

</note>

```java
# File: src/main/java/com/example/FileProcessingStepListener.java
import org.slf4j.MDC;
import org.springframework.batch.core.*;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class FileProcessingStepListener extends StepExecutionListenerSupport {
    
    private final String recordTypeName;
    
    // Constructor: Pass the record type name based on what this step processes
    public FileProcessingStepListener(String recordTypeName) {
        this.recordTypeName = recordTypeName; // e.g., "order", "customer", "payment"
    }
    
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            long duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
            int failureCount = stepExecution.getReadSkipCount()
                + stepExecution.getProcessSkipCount()
                + stepExecution.getWriteSkipCount();
            
            if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
                log.atInfo()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("batch"))
                    .addKeyValue("event.type", List.of("step-end"))
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.severity", "low")
                    .addKeyValue("event.end", stepExecution.getEndTime().toString())
                    .addKeyValue("event.duration_ms", duration)
                    .addKeyValue("record", List.of(Map.of(
                        "name", recordTypeName,  // Set based on actual data processed
                        "total", stepExecution.getReadCount(),
                        "success", stepExecution.getWriteCount(),
                        "failure", failureCount)))
                    .log("Step completed successfully.");
            } else {
                Throwable rootCause = stepExecution.getFailureExceptions().isEmpty()
                    ? new RuntimeException("Unknown failure")
                    : stepExecution.getFailureExceptions().get(0);
                log.atError()
                    .setCause(rootCause)
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("batch"))
                    .addKeyValue("event.type", List.of("step-end"))
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("event.severity", "high")
                    .addKeyValue("event.end", stepExecution.getEndTime().toString())
                    .addKeyValue("event.duration_ms", duration)
                    .addKeyValue("record", List.of(Map.of(
                        "name", recordTypeName,  // Set based on actual data processed
                        "total", stepExecution.getReadCount(),
                        "success", stepExecution.getWriteCount(),
                        "failure", failureCount)))
                    .addKeyValue("error_code", 500)
                    .addKeyValue("error_category", "application")
                    .addKeyValue("error_follow_up_action", true)
                    .log("Step failed.");
            }
        } finally {
            // Wrap cleanup in finally to guarantee execution even if logging throws an exception
            MDC.remove("batch.step.id");
            MDC.remove("batch.step.name");
        }
        return stepExecution.getExitStatus();
    }
}
```

## 7. Register and launch the job
To activate the custom logging, register the `JobExecutionListener` and `StepExecutionListener` implementations with the Spring Batch job and step builders. Then, use Spring's `@Scheduled` and `JobLauncher` to trigger the job. Because the listeners handle all logging and MDC context, the scheduled method itself does not require any logging logic.

```java
# File: src/main/java/com/example/BatchConfiguration.java
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfiguration {

    @Bean
    public Job fileProcessingJob(JobRepository jobRepository, Step processOrdersStep, Step processPaymentsStep, FileProcessingJobListener jobListener) {
        return new JobBuilder("fileProcessingJob", jobRepository)
            .listener(jobListener)
            .start(processOrdersStep)
            .next(processPaymentsStep)
            .build();
    }

    @Bean
    public Step processOrdersStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("processOrdersStep", jobRepository)
            .listener(new FileProcessingStepListener("order"))  // Record type: "order"
            // .chunk(100, transactionManager)
            // .reader(orderReader).processor(orderProcessor).writer(orderWriter)
            .build();
    }
    
    @Bean
    public Step processPaymentsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("processPaymentsStep", jobRepository)
            .listener(new FileProcessingStepListener("payment"))  // Record type: "payment"
            // .chunk(100, transactionManager)
            // .reader(paymentReader).processor(paymentProcessor).writer(paymentWriter)
            .build();
    }
}
```

## 7a. Retry Strategies: External Parameter vs Spring Batch Restart

Spring Batch supports two valid approaches for retrying failed jobs. The choice depends on whether jobs should resume from the failure point or re-run from scratch, and whether orchestration is handled externally or by Spring Batch.

### Option 1: External Scheduler with retry_count Parameter
- **Use case:** Jobs orchestrated by external systems (Kubernetes CronJobs, Quartz, AWS Step Functions)
- **How it works:** Each retry creates a new `JobInstance` with incremented `retry_count` parameter
- **Job behavior:** Re-runs from scratch on each retry
- **Benefits:** Scheduler controls retry logic (max attempts, backoff), simpler for stateless jobs
- **Limitation:** Cannot resume from failure point—always full re-run

**Example:**
```java
// Scheduler detects failure and increments retry count
JobParameters retryParams = new JobParametersBuilder()
    .addLong("batch.job.run.id", System.currentTimeMillis())
    .addLong("batch.job.run.retry_count", previousRetryCount + 1L)
    .addString("trigger.id", "workflow-abc-123")  // Master scheduler workflow ID
    .addString("trigger.type", "scheduled")
    .addString("trigger.by.name", "kubernetes-cronjob")
    .addString("trigger.by.type", "system")
    .addString("processingDate", "2025-09-04")
    .toJobParameters();

jobLauncher.run(fileProcessingJob, retryParams);
```

### Option 2: Spring Batch Built-in Restart
- **Use case:** Long-running, restartable jobs with step-level checkpointing
- **How it works:** Uses `JobOperator.restart(executionId)` to resume the same `JobInstance` from the last failed step
- **Job behavior:** Resumes from failure point using saved `ExecutionContext`
- **Benefits:** Avoids reprocessing completed steps, true restart semantics
- **Limitation:** Cannot change job parameters on restart

**Example:**
```java
// Spring Batch tracks execution history and resumes from failure
JobOperator jobOperator = ...;
Long failedExecutionId = jobExecution.getId();

// Restart uses original parameters, cannot be changed
jobOperator.restart(failedExecutionId);
```

### Recommendation
- **Use Option 1** (retry_count parameter) for most batch jobs: simpler orchestration, external scheduler control, full re-run semantics
- **Use Option 2** (JobOperator.restart) for complex ETL jobs where step-level resumption provides significant value (e.g., multi-hour data loads)

This guide documents **Option 1** as the primary pattern. If using Option 2, the job listener can auto-calculate retry count from `JobRepository` execution history instead of reading it from parameters.

## 7b. Logging Recovery Jobs

**Recovery** differs from **retry**:
- **Retry:** Re-running the SAME job with SAME parameters (automatic on transient failure)
- **Recovery:** Running a DIFFERENT job to fix/compensate for a previous failure (manual intervention after exhausted retries)

### When to Use Recovery
When a job fails repeatedly and automated retries are exhausted, operators may need to:
1. Clean up partial data from the failed run
2. Apply corrective actions (fix data quality, adjust parameters)
3. Re-run with a recovery job that links back to the original failure for audit trail

### Logging Recovery Context
When launching a recovery job, pass the original failed job's metadata as parameters so the recovery execution is traceable back to the root cause:

```java
# File: src/main/java/com/example/RecoveryJobLauncher.java
// After daily-export job fails 3 times, operator triggers recovery
JobExecution failedJobExecution = ...; // The original failed execution

JobParameters recoveryParams = new JobParametersBuilder()
    .addLong("batch.job.run.id", System.currentTimeMillis())
    .addString("trigger.id", "manual-recovery-" + UUID.randomUUID())  // Manual trigger ID
    .addString("trigger.type", "ad-hoc")
    .addString("trigger.by.name", "operator-john")
    .addString("trigger.by.type", "user")
    .addString("batch.job.run.recovery.mode", "manual")  // or "automatic"
    .addString("batch.job.run.recovery.reason", "previous-job-failed")
    .addString("batch.job.run.recovery.job.id", String.valueOf(failedJobExecution.getJobId()))
    .addString("batch.job.run.recovery.job.name", failedJobExecution.getJobInstance().getJobName())
    .addString("batch.job.run.recovery.job.run.id", String.valueOf(failedJobExecution.getId()))
    // Add recovery-specific parameters
    .addString("processingDate", "2025-09-04")
    .addString("cleanupMode", "full")
    .toJobParameters();

jobLauncher.run(recoveryJob, recoveryParams);
```

The `FileProcessingJobListener.beforeJob()` method (Section 5) automatically reads and logs these recovery fields when present, creating full traceability from recovery back to original failure.

## 8. Log @Scheduled job execution
For plain `@Scheduled` methods (without Spring Batch), manually log job start/end and set MDC context. Generate a unique `batch.job.run.id` (UUID) at the start to correlate all logs within that execution. Clean up MDC in a `finally` block to prevent leakage across executions.

```java
# File: src/main/java/com/example/ScheduledTaskLogger.java
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class ScheduledTaskLogger {

    @Scheduled(cron = "${batch.job.cron.expression:0 */1 * * * *}")
    public void runScheduledTask() {
        long startTimeMillis = System.currentTimeMillis();
        Instant startTime = Instant.ofEpochMilli(startTimeMillis);
        
        String jobName = "simpleScheduledTask";
        String runId = UUID.randomUUID().toString();
        
        MDC.put("batch.job.name", jobName);
        MDC.put("batch.job.run.id", runId);
        MDC.put("trace.id", runId);  // Use run ID as trace ID for correlation

        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("batch"))
            .addKeyValue("event.type", List.of("job-start"))
            .addKeyValue("event.severity", "low")
            .addKeyValue("event.start", startTime.toString())
            .addKeyValue("batch.job.name", jobName)
            .addKeyValue("batch.job.run.id", runId)
            .addKeyValue("trigger.type", List.of("scheduled"))
            .addKeyValue("trigger.by", List.of(Map.of("name", "spring-scheduler", "type", "system")))
            .log("Scheduled job started.");

        try {
            // ... task execution logic ...

            long duration = System.currentTimeMillis() - startTimeMillis;
            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("batch"))
                .addKeyValue("event.type", List.of("job-end"))
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("event.end", Instant.now().toString())
                .addKeyValue("event.duration_ms", duration)
                .log("Scheduled job completed successfully.");
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTimeMillis;
            log.atError()
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("batch"))
                .addKeyValue("event.type", List.of("job-end"))
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("event.end", Instant.now().toString())
                .addKeyValue("event.duration_ms", duration)
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .log("Scheduled job failed.");
        } finally {
            MDC.remove("batch.job.name");
            MDC.remove("batch.job.run.id");
            MDC.remove("trace.id");
        }
    }
}
```

## 9. Log file operations in batch jobs
When a Spring Batch step processes files, add file metadata to MDC so it automatically appears in all logs during that step's execution—from ItemReader through ItemProcessor to ItemWriter. The step's existing start/end logs (Section 6) provide the lifecycle boundaries; file context enriches those logs with file-specific details.

<note>

> **Note:** This section extends the basic step listener from Section 6 by adding file and interface context to MDC. If your steps don't process files, use the simpler version from Section 6.

</note>

### Add file context to MDC in beforeStep
When a step processes a file, add file metadata to MDC at the start of the step. This context will automatically appear in all subsequent logs (reader, processor, writer, any custom business logic) without requiring explicit logging in each component.

```java
# File: src/main/java/com/example/FileProcessingStepListener.java
import org.apache.commons.io.FilenameUtils;
import java.io.File;

@Slf4j
@Component
public class FileProcessingStepListener extends StepExecutionListenerSupport {
    
    private final String recordTypeName;
    private final File fileToProcess;  // Inject or determine file at step start
    
    public FileProcessingStepListener(String recordTypeName, File fileToProcess) {
        this.recordTypeName = recordTypeName;
        this.fileToProcess = fileToProcess;
    }
    
    @Override
    public void beforeStep(StepExecution stepExecution) {
        String stepId = String.valueOf(stepExecution.getId());
        String stepName = stepExecution.getStepName();
        
        // Set step context
        MDC.put("batch.step.id", stepId);
        MDC.put("batch.step.name", stepName);
        
        // Add file context to MDC for automatic inclusion in all logs during this step
        MDC.put("file.name", fileToProcess.getName());
        MDC.put("file.extension", FilenameUtils.getExtension(fileToProcess.getName()));
        MDC.put("file.size", String.valueOf(fileToProcess.length()));
        MDC.put("file.path", fileToProcess.getAbsolutePath());
        
        // Add interface context to MDC
        MDC.put("interface.direction", "inbound");  // or "outbound"
        MDC.put("interface.type", "file");
        MDC.put("interface.system", "EHR");  // External system name
        
        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("batch"))
            .addKeyValue("event.type", List.of("step-start"))
            .addKeyValue("event.severity", "low")
            .addKeyValue("event.start", stepExecution.getStartTime().toString())
            .addKeyValue("batch.step.id", stepId)
            .addKeyValue("batch.step.name", stepName)
            // File and interface fields logged explicitly for queryability
            .addKeyValue("file.name", fileToProcess.getName())
            .addKeyValue("file.extension", FilenameUtils.getExtension(fileToProcess.getName()))
            .addKeyValue("file.size", fileToProcess.length())
            .addKeyValue("file.path", fileToProcess.getAbsolutePath())
            .addKeyValue("interface.direction", "inbound")
            .addKeyValue("interface.type", "file")
            .addKeyValue("interface.system", "EHR")
            .log("Step started processing file.");
    }
    
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            long duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
            int failureCount = stepExecution.getReadSkipCount()
                + stepExecution.getProcessSkipCount()
                + stepExecution.getWriteSkipCount();
            
            if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
                log.atInfo()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("batch"))
                    .addKeyValue("event.type", List.of("step-end"))
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.severity", "low")
                    .addKeyValue("event.end", stepExecution.getEndTime().toString())
                    .addKeyValue("event.duration_ms", duration)
                    .addKeyValue("file.record_count", stepExecution.getReadCount())  // Total records in file
                    .addKeyValue("record", List.of(Map.of(
                        "name", recordTypeName,
                        "total", stepExecution.getReadCount(),
                        "success", stepExecution.getWriteCount(),
                        "failure", failureCount)))
                    // file.* and interface.* fields auto-included from MDC
                    .log("Step completed processing file successfully.");
            } else {
                Throwable rootCause = stepExecution.getFailureExceptions().isEmpty()
                    ? new RuntimeException("Unknown failure")
                    : stepExecution.getFailureExceptions().get(0);
                log.atError()
                    .setCause(rootCause)
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("batch"))
                    .addKeyValue("event.type", List.of("step-end"))
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("event.severity", "high")
                    .addKeyValue("event.end", stepExecution.getEndTime().toString())
                    .addKeyValue("event.duration_ms", duration)
                    .addKeyValue("file.record_count", stepExecution.getReadCount())
                    .addKeyValue("record", List.of(Map.of(
                        "name", recordTypeName,
                        "total", stepExecution.getReadCount(),
                        "success", stepExecution.getWriteCount(),
                        "failure", failureCount)))
                    .addKeyValue("error_code", 500)
                    .addKeyValue("error_category", "application")
                    .addKeyValue("error_follow_up_action", true)
                    .log("Step failed processing file.");
            }
        } finally {
            // Clean up step and file context
            MDC.remove("batch.step.id");
            MDC.remove("batch.step.name");
            MDC.remove("file.name");
            MDC.remove("file.extension");
            MDC.remove("file.size");
            MDC.remove("file.path");
            MDC.remove("interface.direction");
            MDC.remove("interface.type");
            MDC.remove("interface.system");
        }
        return stepExecution.getExitStatus();
    }
}
```

<note>

> **Note:** With file context in MDC, any logs written by ItemReader, ItemProcessor, or ItemWriter will automatically include `file.*` and `interface.*` fields. You don't need to add these fields explicitly in your business logic—they propagate automatically.

</note>

### File summary at job completion
After all files are processed, log an aggregated summary in the `afterJob` listener. This provides high-level metrics without requiring queries across individual step logs.

```java
# File: src/main/java/com/example/FileProcessingJobListener.java (afterJob method)
// Track file outcomes during job execution, then log summary
List<Map<String, Object>> fileSummary = List.of(
    Map.of("filename", "orders_20250904123000_00001.csv", "record_name", "order", "record_count", 1500),
    Map.of("filename", "orders_20250904123000_00002.csv", "record_name", "order", "record_count", 1200)
);

log.atInfo()
    .addKeyValue("event.kind", "event")
    .addKeyValue("event.category", List.of("interface"))
    .addKeyValue("event.type", List.of("info"))
    .addKeyValue("event.severity", "low")
    .addKeyValue("interface.direction", "inbound")
    .addKeyValue("interface.type", "file")
    .addKeyValue("interface.system", "EHR")
    .addKeyValue("interface.file.data.total", 2)
    .addKeyValue("interface.file.data.success", 2)
    .addKeyValue("interface.file.data.failure", 0)
    .addKeyValue("interface.file.data.partial_success", 0)
    .addKeyValue("interface.file.summary", fileSummary)
    .log("File processing summary.");
```

**Important:** Exclude or mask PII and secrets from file log entries. Never log file content or record-level data. See [Sensitive Data Masking for Logs](Sensitive_Data_Masking_For_Logs.md).

### 9.1 Log file integrity verification

When processing files from external systems, validate file integrity to detect corruption or tampering. Log both hash generation (when sending files) and hash validation (when receiving files). Hash validation is a best practice for data files (CSV, JSON, XML) in file-based integrations with external systems to detect corruption or tampering.

```java
# File: src/main/java/com/example/FileIntegrityValidator.java
package com.example;

import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
public class FileIntegrityValidator {

    public void validateFileHash(File file, String expectedHash, String algorithm) {
        try {
            String computedHash = computeHash(file, algorithm);
            boolean valid = computedHash.equalsIgnoreCase(expectedHash);

            if (valid) {
                log.atInfo()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("file"))
                    .addKeyValue("event.type", List.of("info"))
                    .addKeyValue("event.action", "file-read")
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.severity", "low")
                    .addKeyValue("file.name", file.getName())
                    .addKeyValue("file.path", file.getAbsolutePath())
                    .addKeyValue("file.size", file.length())
                    .addKeyValue("file.hash.algorithm", algorithm)
                    .addKeyValue("file.hash.exists", true)
                    .addKeyValue("file.hash.valid", true)
                    .log("File hash validation succeeded.");
            } else {
                log.atError()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("file"))
                    .addKeyValue("event.type", List.of("error"))
                    .addKeyValue("event.action", "file-read")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("event.severity", "high")
                    .addKeyValue("file.name", file.getName())
                    .addKeyValue("file.path", file.getAbsolutePath())
                    .addKeyValue("file.size", file.length())
                    .addKeyValue("file.hash.algorithm", algorithm)
                    .addKeyValue("file.hash.exists", true)
                    .addKeyValue("file.hash.valid", false)
                    .addKeyValue("error_code", 422)
                    .addKeyValue("error_category", "data")
                    .addKeyValue("error_follow_up_action", true)
                    .log("File hash validation failed: integrity check did not match.");
            }
        } catch (Exception e) {
            log.atError()
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("file"))
                .addKeyValue("event.type", List.of("error"))
                .addKeyValue("event.action", "file-read")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("file.name", file.getName())
                .addKeyValue("file.path", file.getAbsolutePath())
                .addKeyValue("file.size", file.length())
                .addKeyValue("file.hash.algorithm", algorithm)
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .log("Failed to compute file hash for validation.");
        }
    }

    public void generateAndLogHash(File file, String algorithm) {
        try {
            String hash = computeHash(file, algorithm);
            
            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("file"))
                .addKeyValue("event.type", List.of("creation"))
                .addKeyValue("event.action", "file-generation")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("file.name", file.getName())
                .addKeyValue("file.path", file.getAbsolutePath())
                .addKeyValue("file.size", file.length())
                .addKeyValue("file.hash.algorithm", algorithm)
                .addKeyValue("file.hash.exists", true)
                .addKeyValue("file.hash.timestamp", Instant.now().toString())
                .log("File hash generated.");
        } catch (Exception e) {
            log.atError()
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("file"))
                .addKeyValue("event.type", List.of("error"))
                .addKeyValue("event.action", "file-generation")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("file.name", file.getName())
                .addKeyValue("file.path", file.getAbsolutePath())
                .addKeyValue("file.size", file.length())
                .addKeyValue("file.hash.algorithm", algorithm)
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .log("Failed to generate file hash.");
        }
    }

    private String computeHash(File file, String algorithm) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
```

**Usage:**
- **When sending files**: Call `generateAndLogHash()` after file generation to compute and log the hash
- **When receiving files**: Call `validateFileHash()` before file processing to verify integrity

<note>

> **Note:** Hash validation failures indicate file corruption or tampering and should trigger immediate investigation. Use `event.severity: high` and `error_follow_up_action: true` to ensure operators are alerted. Common hash algorithms include SHA-256 (recommended) and MD5 (legacy, not recommended for security-sensitive scenarios).

</note>

<note>

> **Note on Code Signature Verification:** For executable files or scripts in regulated environments (healthcare, finance, government), use code signature verification instead of hash validation. The pattern is similar but uses `file.code_signature.*` fields (`file.code_signature.exists`, `file.code_signature.valid`, `file.code_signature.digest_algorithm`, `file.code_signature.timestamp`) with `event.severity: critical` and `error_category: "cert/auth"` for signature failures. Never execute files that fail signature verification.

</note>

### 9.2 Log file acknowledgment processing

For file-based integrations, acknowledgment (ACK) files confirm successful processing. Log both ACK generation (after processing received files) and ACK reception (after sending files) to track end-to-end file delivery.

```java
# File: src/main/java/com/example/FileAckProcessor.java
package com/example;

import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

@Slf4j
public class FileAckProcessor {

    public void generateAck(File processedFile, String ackStatus, int statusCode) {
        String ackFileName = processedFile.getName() + ".ack";
        File ackFile = new File(processedFile.getParent(), ackFileName);
        
        try (FileWriter writer = new FileWriter(ackFile)) {
            writer.write("status=" + ackStatus + "\n");
            writer.write("statusCode=" + statusCode + "\n");
            writer.write("processedFile=" + processedFile.getName() + "\n");
            
            log.atInfo()
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("file"))
                .addKeyValue("event.type", List.of("creation"))
                .addKeyValue("event.action", "file-ack-process")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.severity", "low")
                .addKeyValue("file.name", processedFile.getName())
                .addKeyValue("file.path", processedFile.getAbsolutePath())
                .addKeyValue("file.size", processedFile.length())
                .addKeyValue("file.ack.name", ackFileName)
                .addKeyValue("file.ack.status_code", statusCode)
                .addKeyValue("file.ack.status", ackStatus)
                .log("ACK file generated after processing.");
        } catch (Exception e) {
            log.atError()
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("file"))
                .addKeyValue("event.type", List.of("error"))
                .addKeyValue("event.action", "file-ack-process")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("file.name", processedFile.getName())
                .addKeyValue("file.path", processedFile.getAbsolutePath())
                .addKeyValue("file.size", processedFile.length())
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "application")
                .addKeyValue("error_follow_up_action", true)
                .log("Failed to generate ACK file.");
        }
    }

    public void processReceivedAck(File ackFile, File originalFile) {
        try {
            String content = Files.readString(ackFile.toPath());
            String status = extractValue(content, "status");
            int statusCode = Integer.parseInt(extractValue(content, "statusCode"));
            
            boolean success = statusCode >= 200 && statusCode < 300;
            
            if (success) {
                log.atInfo()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("file"))
                    .addKeyValue("event.type", List.of("info"))
                    .addKeyValue("event.action", "file-ack-process")
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.severity", "low")
                    .addKeyValue("file.name", originalFile.getName())
                    .addKeyValue("file.path", originalFile.getAbsolutePath())
                    .addKeyValue("file.size", originalFile.length())
                    .addKeyValue("file.ack.name", ackFile.getName())
                    .addKeyValue("file.ack.status_code", statusCode)
                    .addKeyValue("file.ack.status", status)
                    .log("ACK file received: file processed successfully by receiver.");
            } else {
                log.atError()
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", List.of("file"))
                    .addKeyValue("event.type", List.of("error"))
                    .addKeyValue("event.action", "file-ack-process")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("event.severity", "high")
                    .addKeyValue("file.name", originalFile.getName())
                    .addKeyValue("file.path", originalFile.getAbsolutePath())
                    .addKeyValue("file.size", originalFile.length())
                    .addKeyValue("file.ack.name", ackFile.getName())
                    .addKeyValue("file.ack.status_code", statusCode)
                    .addKeyValue("file.ack.status", status)
                    .addKeyValue("error_code", statusCode)
                    .addKeyValue("error_category", "application")
                    .addKeyValue("error_follow_up_action", true)
                    .log("ACK file indicates processing failure at receiver.");
            }
        } catch (Exception e) {
            log.atError()
                .setCause(e)
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", List.of("file"))
                .addKeyValue("event.type", List.of("error"))
                .addKeyValue("event.action", "file-ack-process")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.severity", "high")
                .addKeyValue("file.ack.name", ackFile.getName())
                .addKeyValue("error_code", 500)
                .addKeyValue("error_category", "data")
                .addKeyValue("error_follow_up_action", true)
                .log("Failed to process received ACK file.");
        }
    }

    private String extractValue(String content, String key) {
        for (String line : content.split("\n")) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1);
            }
        }
        return "";
    }
}
```

<note>

> **Note:** ACK file timeout (no ACK received within expected timeframe) should trigger retry logic or manual investigation. Track pending ACK files using `interface.file.pending_ack.summary` in job completion logs to identify files awaiting acknowledgment. See Section 9 file summary example for the field structure.

</note>

## 10. Log external service calls
An external service call can fail in two distinct ways: it can return an error, or it can succeed slowly. Without duration logging, a call that takes 30 seconds is indistinguishable from one that takes 30 milliseconds. Logging `event.duration_ms` on every call provides the data needed to set latency alerts and distinguish timeouts from failures.

Log every outbound call with complete HTTP context: URL, method, request/response sizes, status code, outcome, and duration.

```java
# File: src/main/java/com/example/ExternalApiClient.java
import java.net.http.*;
import java.time.Instant;

public void callExternalApi(String url, String requestBody) {
    Instant startTime = Instant.now();
    long startTimeMillis = System.currentTimeMillis();
    
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();
    
    int requestBodySize = requestBody.getBytes().length;
    
    // Log before the call
    log.atInfo()
        .addKeyValue("event.kind", "event")
        .addKeyValue("event.category", List.of("interface"))
        .addKeyValue("event.type", List.of("interface-start"))
        .addKeyValue("event.action", "api-push")
        .addKeyValue("event.severity", "low")
        .addKeyValue("event.start", startTime.toString())
        .addKeyValue("interface.direction", "outbound")
        .addKeyValue("interface.type", "api")
        .addKeyValue("interface.system", "PartnerAPI")
        .addKeyValue("url.full", url)
        .addKeyValue("http.request.method", "POST")
        .addKeyValue("http.request.body.bytes", requestBodySize)
        .log("Calling external API.");
    
    try {
        // Make the API call
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        long duration = System.currentTimeMillis() - startTimeMillis;
        int responseBodySize = response.body().getBytes().length;
        
        // Log success
        log.atInfo()
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("interface"))
            .addKeyValue("event.type", List.of("interface-end"))
            .addKeyValue("event.action", "api-push")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("event.severity", "low")
            .addKeyValue("event.end", Instant.now().toString())
            .addKeyValue("event.duration_ms", duration)
            .addKeyValue("interface.direction", "outbound")
            .addKeyValue("interface.type", "api")
            .addKeyValue("interface.system", "PartnerAPI")
            .addKeyValue("url.full", url)
            .addKeyValue("http.request.method", "POST")
            .addKeyValue("http.request.body.bytes", requestBodySize)
            .addKeyValue("http.response.status_code", response.statusCode())
            .addKeyValue("http.response.body.bytes", responseBodySize)
            .log("External API call succeeded.");
            
    } catch (Exception e) {
        long duration = System.currentTimeMillis() - startTimeMillis;
        
        // Log failure
        log.atError()
            .setCause(e)
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", List.of("interface"))
            .addKeyValue("event.type", List.of("interface-end"))
            .addKeyValue("event.action", "api-push")
            .addKeyValue("event.outcome", "failure")
            .addKeyValue("event.severity", "high")
            .addKeyValue("event.end", Instant.now().toString())
            .addKeyValue("event.duration_ms", duration)
            .addKeyValue("interface.direction", "outbound")
            .addKeyValue("interface.type", "api")
            .addKeyValue("interface.system", "PartnerAPI")
            .addKeyValue("url.full", url)
            .addKeyValue("http.request.method", "POST")
            .addKeyValue("http.request.body.bytes", requestBodySize)
            .addKeyValue("error_code", 500)
            .addKeyValue("error_category", "network")
            .addKeyValue("error_follow_up_action", true)
            .log("External API call failed.");
    }
}
```

<note>

> **Note:** Additional HTTP fields from the schema can be added based on your use case:
> - `http.request.record_count` / `http.response.record_count` - For bulk API operations
> - `http.request.idempotency_key` - For retry-safe operations
> - `http.response.next_token` - For paginated responses
> - `http.rate_limit.status` - When rate limiting is detected
> - `url.domain`, `url.path`, `url.query` - For more granular URL analysis

</note>

## 11. Verification
Trigger a Spring Batch job execution, a plain `@Scheduled` job execution, a file processing operation, and an outbound service call.

Verify that:

### Schema Compliance
- `event.category`, `event.type`, `event.action`, `error_category` values match those defined in [Log_Schema.md](../Log_Schema.md)

### Startup Logs
- Job name, `trigger.cron.expression`, and `trigger.cron.timezone` for every registered scheduled job

### Spring Batch Job Lifecycle
- **Job-start logs** include:
  - Core: `event.kind`, `event.category`, `event.type: job-start`, `event.severity`, `event.start`
  - Batch: `batch.job.id`, `batch.job.run.id`, `batch.job.name`, `batch.job.status`
  - Parameters: `batch.job.run.parameter` (map with masked values `***`)
  - Trigger: `trigger.id` (optional), `trigger.type`, `trigger.by` array
  - Trace: `trace.id` matching `batch.job.run.id`
  - Optional: `batch.job.run.retry_count` (if provided by scheduler)
  - Optional: `batch.job.run.recovery.*` fields (if recovery job)
- **Job-end logs** include:
  - Core: `event.type: job-end`, `event.outcome`, `event.end`, `event.duration_ms`
  - Failure: `error_code`, `error_category`, `error_follow_up_action`, exception via `.setCause(e)`

### Plain @Scheduled Job Lifecycle
- **Job-start logs** include:
  - `batch.job.name`, `batch.job.run.id` (UUID), `trace.id`
  - `trigger.type: ["scheduled"]`, `trigger.by: [{"name":"spring-scheduler","type":"system"}]`
  - `event.start`
- **Job-end logs** include: `event.outcome`, `event.end`, `event.duration_ms`
- **MDC cleanup**: No leakage to next execution (verify via `finally` block)

### Step Lifecycle (File Processing Steps)
- **Step-start logs** include:
  - `batch.step.id`, `batch.step.name` (logged explicitly, not just MDC)
  - `event.start`
  - File context: `file.name`, `file.extension`, `file.size`, `file.path` (when processing files)
  - Interface context: `interface.direction`, `interface.type`, `interface.system`
- **Step-end logs** include:
  - `event.outcome`, `event.end`, `event.duration_ms`
  - `file.record_count` (total records in file)
  - `record` array with configurable `name` (e.g., "order", "payment"), `total`, `success`, `failure`

### File Summary (Job Completion)
- `interface.file.data.total`, `interface.file.data.success`, `interface.file.data.failure`, `interface.file.data.partial_success`
- `interface.file.summary` array with per-file details

### File Security (Hash and Code Signature)
- **Hash validation success**: `event.outcome: success`, `event.severity: low`, `file.name`, `file.path`, `file.size`, `file.hash.algorithm`, `file.hash.exists: true`, `file.hash.valid: true`
- **Hash validation failure**: `event.outcome: failure`, `event.severity: high`, `file.hash.valid: false`, with `error_code: 422`, `error_category: "data"`, `error_follow_up_action: true`
- **Hash computation failure**: `event.severity: high`, with `error_code: 500`, `error_category: "application"`, `error_follow_up_action: true`, exception attached via `.setCause(e)`
- **Hash generation**: `event.type: ["creation"]`, `event.action: "file-generation"`, `file.hash.algorithm`, `file.hash.exists: true`, `file.hash.timestamp`
- **Signature validation success**: `file.code_signature.exists: true`, `file.code_signature.valid: true`, `file.code_signature.digest_algorithm`
- **Signature validation failure**: `event.severity: critical`, `file.code_signature.valid: false`, with `error_code`, `error_category: "cert/auth"`, `error_follow_up_action: true`
- **Signature generation**: `file.code_signature.digest_algorithm`, `file.code_signature.timestamp`

### File Acknowledgment Processing
- **ACK generation success**: `event.action: file-ack-process`, `file.ack.name`, `file.ack.status_code`, `file.ack.status`
- **ACK generation failure**: `event.severity: high`, with `error_code`, `error_category`, `error_follow_up_action: true`
- **ACK reception success** (2xx status): `event.outcome: success`, with ACK status details
- **ACK reception failure** (non-2xx status): `event.outcome: failure`, `event.severity: high`, with `error_code` matching ACK status code

### Outbound API Calls
- **Start log** includes:
  - `event.type: interface-start`, `event.action: api-push`, `event.start`
  - `url.full`, `http.request.method`, `http.request.body.bytes`
  - `interface.direction: outbound`, `interface.type: api`, `interface.system`
- **End log** includes:
  - `event.type: interface-end`, `event.outcome`, `event.end`, `event.duration_ms`
  - `http.response.status_code`, `http.response.body.bytes`
  - Failure: `error_code`, `error_category`, `error_follow_up_action`

### Conditional Fields
- `batch.job.run.recovery.*` fields appear ONLY in recovery job logs
- `batch.job.run.retry_count` appears ONLY when passed by scheduler
- `trigger.id` appears ONLY when provided by master scheduler/orchestrator

### MDC Cleanup
- `trace.id`, `batch.job.*`, `batch.step.*`, `file.*`, `interface.*` removed in `finally` blocks
- No field leakage between executions
- Step-level MDC cleaned up even if logging throws exception

## 12. Conclusion
This guide covers structured logging for batch and scheduled jobs—from startup schedule registration through job/step lifecycle, file operations, and external service calls—for both Spring Batch and plain `@Scheduled` implementations. For file-based integrations with external systems, it includes patterns for hash validation, code signature verification, and ACK processing to ensure data integrity and reliable delivery.

### Key Takeaways
- **Record schedules at startup**: Log cron expression and timezone so operators can verify schedules without inspecting configuration
- **Set trace.id for correlation**: Use `batch.job.run.id` as `trace.id` so all logs from a job execution share the same trace
- **Generate run IDs for @Scheduled tasks**: Use UUID for `batch.job.run.id` since Spring doesn't provide one for plain scheduled methods
- **Mask job parameters**: Log `batch.job.run.parameter` with masked values (`***`) to avoid exposing sensitive data
- **Include trigger context**: Log `trigger.id` (master scheduler ID), `trigger.type`, and `trigger.by` to show who/what initiated the job
- **Distinguish retry from recovery**: 
  - Retry = same job re-run automatically (use `batch.job.run.retry_count`)
  - Recovery = different job for manual intervention (use `batch.job.run.recovery.*` fields)
- **Configure record type names**: Pass record type name ("order", "payment") to step listener constructor based on actual data processed, not generic "item"
- **Capture item counts at step boundary**: Log `record` summary with total/success/failure counts at step completion. Don't log inside per-item loops to avoid flooding logs
- **Track multiple record types**: For steps processing multiple types within one step, manually track counts in ItemProcessor and store in StepExecutionContext
- **Add file context to MDC**: When steps process files, add `file.*` and `interface.*` to MDC in `beforeStep` so they auto-propagate to ItemReader/ItemProcessor/ItemWriter logs
- **Log file hash validation**: For file-based integrations with external systems, hash validation is a best practice to detect corruption or tampering. Log hash algorithm, validation result with `file.hash.valid`, and use `event.severity: high` with `error_code: 422` and `error_category: "data"` for hash mismatches. Handle hash computation failures separately with `error_code: 500` and `error_category: "application"`
- **Log code signature verification**: For executable files or scripts in regulated environments (healthcare, finance, government), log signature validation with `event.severity: critical` and `error_category: "cert/auth"` for signature failures. Never execute files with invalid signatures
- **Log ACK file processing**: Generate ACK files after processing received files, and process received ACK files for sent files. Track pending ACKs using `interface.file.pending_ack.summary` for files awaiting acknowledgment
- **Log complete HTTP context**: Include `http.request.body.bytes`, `http.response.body.bytes`, `http.response.status_code`, and `event.action` for external API calls
- **Clean up MDC in finally blocks**: Wrap all MDC cleanup in `finally` to guarantee execution even if logging throws. Remove `trace.id`, `batch.job.*`, `batch.step.*`, `file.*`, `interface.*` fields

## 13. References

Related guides:
- [Custom Structured Log Encoder](Custom_Structured_Log_Encoder.md)
- [Enriching Logs with MDC](Enriching_Logs_With_MDC.md)
- [Sensitive Data Masking for Logs](Sensitive_Data_Masking_For_Logs.md)
- [Structured Logging, Trace Correlation, and Context Propagation](Structured_Logging_Trace_Correlation_And_Context_Propagation.md)

Spring Batch:
- [Spring Batch Reference Documentation](https://docs.spring.io/spring-batch/reference/index.html)
- [Spring Boot Observability: Spring Batch Jobs — Trifork Blog](https://trifork.nl/blog/spring-boot-observability-spring-batch-jobs/)

Spring Framework & Boot:
- [Spring Boot Reference: Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Boot Reference: Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Framework Javadoc: @Scheduled](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)

Libraries:
- [Apache Commons IO Javadoc: FilenameUtils](https://commons.apache.org/proper/commons-io/apidocs/org/apache/commons/io/FilenameUtils.html)
- [SLF4J Javadoc: MDC](https://www.slf4j.org/apidocs/org/slf4j/MDC.html)
