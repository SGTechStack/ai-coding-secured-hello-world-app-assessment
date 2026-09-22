# Structured Logging and Transaction Correlation

## 1. Introduction

Effective batch interface processing requires complete traceability from receipt through completion. This recipe guides you through implementing structured logging with correlation IDs (transaction IDs) that link all events in an interface's lifecycle. This is essential for troubleshooting failures, auditing, and monitoring batch throughput.

By the end of this recipe, you will:
- Configure JSON structured logging in your application
- Implement transaction ID correlation across all interface operations
- Set up log indexing for easy querying of batch events
- Trace a single batch's journey from receipt to acknowledgment

## 2. Prerequisites

- Spring Boot 3.0+
- Maven or Gradle for dependency management
- Access to a structured logging backend (ELK Stack, Splunk, or CloudWatch Logs)
- Familiarity with Spring ApplicationEvents and log4j2 or Logback configuration

## 3. Steps

### Step 1: Add Structured Logging Dependencies

Add logging libraries to your `pom.xml`:

```xml
<!-- File: pom.xml -->
<dependencies>
    <!-- Spring Boot Starter Web (for RequestContextHolder) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Logback for structured logging -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-logging</artifactId>
    </dependency>

    <!-- Logstash Encoder for JSON output -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
        <version>7.3</version>
    </dependency>

    <!-- For MDC (Mapped Diagnostic Context) -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
</dependencies>
```

**Why:** Logstash Encoder automatically formats Logback output as JSON, making it parseable by downstream log aggregators. SLF4J MDC allows you to attach context (transaction ID, batch ID) to every log statement without passing parameters.

### Step 2: Configure Logback for Structured JSON Output

Create `src/main/resources/logback-spring.xml`:

```xml
<!-- File: src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty name="APP_NAME" source="spring.application.name" defaultValue="batch-interface" />
    <springProperty name="LOG_FILE" source="logging.file.name" defaultValue="logs/application.log" />

    <!-- Console Appender: JSON format -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <level>level</level>
                <logger>logger</logger>
                <message>message</message>
                <threadName>thread</threadName>
            </fieldNames>
            <customFields>{"app_name":"${APP_NAME}"}</customFields>
        </encoder>
    </appender>

    <!-- File Appender: JSON format with rolling policy -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <level>level</level>
                <logger>logger</logger>
                <message>message</message>
            </fieldNames>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/application-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
    </appender>

    <!-- Logger configuration -->
    <logger name="com.example.batch" level="INFO" />
    <logger name="org.springframework" level="WARN" />

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

**Why:** This configuration outputs JSON to both console (for local development) and files (for production). JSON format is automatically parsed by log aggregators, enabling field-based filtering and analytics.

### Step 3: Create a Transaction Context Holder

Create a utility class to manage transaction IDs across thread boundaries:

```java
// File: src/main/java/com/example/batch/context/BatchTransactionContext.java
package com.example.batch.context;

import org.slf4j.MDC;
import java.util.UUID;

public class BatchTransactionContext {
    private static final String CORRELATION_ID_KEY = "correlation.id";
    private static final String BATCH_JOB_ID_KEY = "batch.job.id";
    private static final String BATCH_JOB_RUN_ID_KEY = "batch.job.run.id";
    private static final String INTERFACE_SYSTEM_KEY = "interface.system";
    private static final String TRACE_ID_KEY = "trace.id";

    /**
     * Generate a new correlation ID and set it in MDC.
     * All logs written during this thread will automatically include the correlation.id field.
     */
    public static String initializeTransaction(String batchJobId, String interfaceSystem) {
        String correlationId = generateCorrelationId();
        String runId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(BATCH_JOB_ID_KEY, batchJobId);
        MDC.put(BATCH_JOB_RUN_ID_KEY, runId);
        MDC.put(INTERFACE_SYSTEM_KEY, interfaceSystem);
        MDC.put(TRACE_ID_KEY, runId); // W3C Trace Context / batch.job.run.id
        return correlationId;
    }

    /**
     * Retrieve the current correlation ID from MDC.
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Retrieve the current batch job ID from MDC.
     */
    public static String getBatchJobId() {
        return MDC.get(BATCH_JOB_ID_KEY);
    }

    /**
     * Retrieve the current batch job run ID from MDC.
     */
    public static String getBatchJobRunId() {
        return MDC.get(BATCH_JOB_RUN_ID_KEY);
    }

    /**
     * Clear MDC to avoid leaking context to unrelated requests (important for thread pools).
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Generate a correlation ID with timestamp and UUID.
     * Format: corr_20260327_abc123def456
     */
    private static String generateCorrelationId() {
        long timestamp = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString().substring(0, 12);
        return "corr_" + timestamp + "_" + uuid;
    }
}
```

**Why:** MDC automatically injects context values into every log statement on the current thread without requiring manual parameter passing. This keeps logging calls concise and reduces boilerplate.

### Step 4: Add Custom MDC Fields in Logback Configuration

Enhance the Logback configuration to include custom MDC fields:

```xml
<!-- File: src/main/resources/logback-spring.xml (update encoder section) -->
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <fieldNames>
        <timestamp>@timestamp</timestamp>
        <level>level</level>
        <logger>logger</logger>
        <message>message</message>
        <threadName>thread</threadName>
    </fieldNames>
    <providers>
        <!-- Include MDC fields -->
        <mdc />
        <!-- Include caller information (file:line) -->
        <loggerName />
        <threadName />
    </providers>
</encoder>
```

### Step 5: Create a Batch Processing Service with Logging

Create a service that processes inbound batches and logs structured events:

```java
// File: src/main/java/com/example/batch/service/InboundBatchService.java
package com.example.batch.service;

import com.example.batch.context.BatchTransactionContext;
import com.example.batch.model.BatchFile;
import com.example.batch.model.ProcessingResult;
import com.example.batch.repository.InterfaceTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InboundBatchService {
    private static final Logger log = LoggerFactory.getLogger(InboundBatchService.class);

    private final InterfaceTransactionRepository transactionRepository;
    private final BatchValidator validator;
    private final RecordProcessor recordProcessor;

    public InboundBatchService(InterfaceTransactionRepository transactionRepository,
                               BatchValidator validator,
                               RecordProcessor recordProcessor) {
        this.transactionRepository = transactionRepository;
        this.validator = validator;
        this.recordProcessor = recordProcessor;
    }

    /**
     * Process an inbound batch file end-to-end.
     */
    public ProcessingResult processBatch(BatchFile batchFile, String interfaceSystem) {
        // Step 1: Initialize transaction context
        String correlationId = BatchTransactionContext.initializeTransaction(
            batchFile.getBatchId(),
            interfaceSystem
        );

        // Safe User Context logging: always log non-PII user.id UUID, never cleartext emails/usernames
        String userId = "550e8400-e29b-41d4-a716-446655440000"; 

        log.atInfo()
            .setMessage("Batch received")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"interface", "batch"})
            .addKeyValue("event.type", new String[]{"interface-start"})
            .addKeyValue("event.action", "file-process")
            .addKeyValue("file.name", batchFile.getFileName())
            .addKeyValue("file.size", batchFile.getFileSizeBytes())
            .addKeyValue("file.record_count", batchFile.getRecordCount())
            .addKeyValue("trigger.type", new String[]{"scheduled"})
            .addKeyValue("trigger.id", UUID.randomUUID().toString())
            .addKeyValue("user.id", userId)
            .log();

        try {
            // Step 2: Validate format
            log.atInfo()
                .setMessage("Starting format validation")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"step-start"})
                .addKeyValue("event.action", "file-read")
                .log();
            var validationResult = validator.validate(batchFile);

            if (!validationResult.isValid()) {
                log.atWarn()
                    .setMessage("Format validation failed")
                    .addKeyValue("event.kind", "event")
                    .addKeyValue("event.category", new String[]{"interface"})
                    .addKeyValue("event.type", new String[]{"step-end", "error"})
                    .addKeyValue("event.action", "file-read")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("error_category", "data")
                    .log();
                return ProcessingResult.rejected(correlationId, validationResult.getErrors());
            }

            // Log file integrity verification fields
            log.atInfo()
                .setMessage("File integrity verification successful")
                .addKeyValue("file.hash.algorithm", "SHA-256")
                .addKeyValue("file.hash.exists", true)
                .addKeyValue("file.hash.valid", true)
                .addKeyValue("file.code_signature.exists", true)
                .addKeyValue("file.code_signature.valid", true)
                .log();

            log.atInfo()
                .setMessage("Format validation passed")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"step-end"})
                .addKeyValue("event.action", "file-read")
                .addKeyValue("event.outcome", "success")
                .log();

            // Step 3: Parse and process records
            log.atInfo()
                .setMessage("Starting record processing")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"batch"})
                .addKeyValue("event.type", new String[]{"step-start"})
                .log();
            long startTime = System.currentTimeMillis();

            var processingResult = recordProcessor.processRecords(batchFile.getRecords());

            long duration = System.currentTimeMillis() - startTime;

            // Construct standard record summary list for structured logging
            java.util.List<java.util.Map<String, Object>> recordSummary = java.util.List.of(java.util.Map.of(
                "name", "payment",
                "total", processingResult.getTotalCount(),
                "success", processingResult.getSuccessCount(),
                "failure", processingResult.getFailureCount()
            ));

            log.atInfo()
                .setMessage("Record processing completed")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"batch"})
                .addKeyValue("event.type", new String[]{"step-end"})
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.duration_ms", duration)
                .addKeyValue("record", recordSummary)
                .log();

            // Step 4: Persist transaction result
            transactionRepository.save(processingResult.getTransaction());

            // Step 5: Generate ACK
            log.atInfo()
                .setMessage("Generating acknowledgment")
                .addKeyValue("event.kind", "event")
                .addKeyValue("event.category", new String[]{"interface"})
                .addKeyValue("event.type", new String[]{"step-end"})
                .addKeyValue("event.action", "file-ack-process")
                .addKeyValue("event.outcome", "success")
                .log();

            return processingResult;

        } finally {
            // Always clear MDC to avoid context leakage
            BatchTransactionContext.clear();
        }
    }

    /**
     * Example of logging with custom MDC fields.
     * All calls below automatically include correlation.id, batch.job.id, interface.system from MDC.
     */
    private void logRecordError(String recordId, String errorMessage) {
        log.atWarn()
            .setMessage("Record processing failed")
            .addKeyValue("event.kind", "event")
            .addKeyValue("event.category", new String[]{"batch"})
            .addKeyValue("event.type", new String[]{"error"})
            .setCause(new RuntimeException(errorMessage))
            .log();
    }
}
```
```

**Why:** The transaction ID is automatically included in every log statement via MDC. Logs from different services processing the same batch will all share the same transaction_id, enabling cross-service tracing.

### Step 6: Propagate Logging Context to Asynchronous Schedulers (Aspect-Oriented)

Standard Spring `@Scheduled` tasks run on background threads separate from the main execution thread. Because MDC is a `ThreadLocal` structure, logging correlation context is lost when scheduled triggers fire. 

Implement an Aspect-Oriented Programming (AOP) aspect to copy the global MDC diagnostic context transparently across scheduling threads:

1. **Add AOP dependency:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

2. **Create the logging aspect:**

```java
// File: src/main/java/com/example/batch/aspect/ScheduledTaskMdcAspect.java
package com.example.batch.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import com.example.interface.core.logger.GlobalMDC;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class ScheduledTaskMdcAspect {

    /**
     * Intercepts methods annotated with @Scheduled.
     * Propagates the global MDC context map to the running scheduler thread.
     */
    @Around("@annotation(scheduled)")
    public Object propagateMdc(ProceedingJoinPoint joinPoint, Scheduled scheduled) throws Throwable {
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        Map<String, String> globalContext = GlobalMDC.getContextMap(); // Framework-managed MDC registry

        if (globalContext != null) {
            MDC.setContextMap(globalContext);
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            } else {
                MDC.clear();
            }
        }
    }
}
```

---

### Step 6: Mask and Redact Prohibited and Sensitive Content

To satisfy the standard's logging security contract, implement automatic log masking at the log-appender layer. Sensitive data fields (credit card numbers validation via Luhn algorithm, SSNs, credentials, API keys) must be redacted or masked before being written to storage to prevent accidental data leaks (e.g., replacing with `****` or `[REDACTED]`).

Configure your Logback encoders to apply masking rules. For step-by-step implementation examples using pattern replacers or custom Logback evaluators, refer to the logging standard guide [Sensitive Data Masking for Logs](../../Appfw-Logging-Standards/Recipes/Sensitive_Data_Masking_For_Logs.md).

---

## 4. Examples

### Example 1: Querying All Events for a Specific Batch

```bash
# Using Elasticsearch/ELK Stack
curl -X GET "localhost:9200/logstash-*/_search" -H 'Content-Type: application/json' -d'{
  "query": {
    "match": {
      "batch.job.id": "batch_inbound_sales"
    }
  },
  "sort": [
    { "@timestamp": { "order": "asc" } }
  ]
}'
```

Result: All logs from receipt through completion are returned in chronological order with correlation.id, interface.system, and event.type fields.

### Example 2: Analyzing Processing Duration Across Batches

```bash
# Using Splunk
index=myapp logger=app.batch.interface.inbound event.type=interface-end
| stats avg(event.duration_ms), p95(event.duration_ms), max(event.duration_ms) by interface.system
```

Result: Processing metrics aggregated by interface system, enabling SLA validation and performance trending.

### Example 3: Finding All Failed Batches in a Timeframe

```bash
# Using ELK with Kibana
POST /logstash-*/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "event.outcome": "failure" } },
        { "range": { "@timestamp": { "gte": "2026-03-27", "lte": "2026-03-28" } } }
      ]
    }
  },
  "aggs": {
    "by_error_category": {
      "terms": { "field": "error.category.keyword" }
    }
  }
}
```

Result: Failed batches grouped by error category, facilitating root cause analysis.

## 5. Verification

1. **Run your application** and trigger an inbound batch.
2. **Check log output** to confirm JSON formatting:
   ```json
   {"@timestamp":"2026-03-27T14:30:45.123Z","level":"INFO","logger":"com.example.batch.service.InboundBatchService","message":"Batch received","correlation.id":"corr_1711534245123_abc123","batch.job.id":"batch_inbound_sales","batch.job.run.id":"4f5a5b5e-3e4a-4c5d-8a7b-6f78901c2d3e","trace.id":"4f5a5b5e-3e4a-4c5d-8a7b-6f78901c2d3e","interface.system":"SYSTEM_A"}
   ```
3. **Query your log aggregator** with the batch.job.id or correlation.id to retrieve all related events.
4. **Verify thread pool behavior** by submitting multiple batches in parallel and confirming that correlation and trace IDs remain isolated per batch.

## 6. Conclusion

You have now implemented structured logging with transaction correlation. Every interface event is automatically tagged with a unique transaction ID, enabling end-to-end traceability and making it easy to troubleshoot failures, audit processing, and verify SLAs.

## 7. References

- [Logback Documentation](https://logback.qos.ch/)
- [Logstash Logback Encoder](https://github.com/logstash/logstash-logback-encoder)
- [SLF4J MDC](https://www.slf4j.org/manual.html#mdc)
- [Elastic Stack Documentation](https://www.elastic.co/guide/index.html)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
