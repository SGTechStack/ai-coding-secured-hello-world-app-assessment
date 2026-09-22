# Outbound Job Wiring: ItemReader and JobFactory

## 1. Introduction

The outbound framework handles file generation, writing, signing, archiving, and ACK processing automatically — but it cannot know where your domain data lives or which records are ready for transmission. That is your responsibility: implement an `ItemReader` per record type, register each reader with a `ReaderMapper`, and call `OutboundJobFactory.createJob()` to get a fully configured Spring Batch `Job`.

This guide walks through the complete wiring from data source to job execution. It also covers the ACK and retry job variants for handling post-transmission acknowledgment.

By the end of this guide, you will:
- Implement a paginated `ItemReader` for each outbound record type
- Register multiple readers for a single job using `ReaderMapper`
- Wire and launch the outbound job via `OutboundJobFactory`
- Set up the ACK and retry job to process acknowledgment files

## 2. Prerequisites

- `@MAGEntity`-annotated entity classes extending `OutboundInterfaceDataRecord` (see [Entity Modeling with @MAGEntity and @Position](../Shared/Entity%20Modeling%20with%20%40MAGEntity%20and%20%40Position.md))
- `interface-management-outbound-starter` on the classpath
- Spring Data JPA repository for each record type
- `OutboundJobFactory` auto-configured (available as a Spring bean once the starter is on the classpath)

## 3. Steps

### Step 1: Implement an ItemReader for Each Record Type

Each record type needs its own `ItemReader<OutboundInterfaceDataRecord>`. The reader is responsible for fetching records from your database and streaming them to the batch step. Use pagination to avoid loading the entire table into memory.

```java
// File: src/main/java/com/example/batch/PaymentOutboundDataRecordReader.java
package com.example.batch;

import com.example.model.PaymentOutboundDataRecord;
import com.example.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import com.example.interface.outbound.model.OutboundInterfaceDataRecord;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class PaymentOutboundDataRecordReader implements ItemReader<OutboundInterfaceDataRecord> {

    private final PaymentRepository paymentRepository;
    private Iterator<PaymentOutboundDataRecord> iterator;
    private int pageIndex = 0;
    private static final int PAGE_SIZE = 100;

    @Override
    public OutboundInterfaceDataRecord read() {
        if (iterator == null || !iterator.hasNext()) {
            fetchNextPage();
        }
        return iterator != null && iterator.hasNext() ? iterator.next() : null;
    }

    // Called before re-running the job to reset paging state
    public void reset() {
        this.pageIndex = 0;
        this.iterator = null;
    }

    private void fetchNextPage() {
        List<PaymentOutboundDataRecord> page =
            paymentRepository.findAll(PageRequest.of(pageIndex, PAGE_SIZE)).getContent();
        this.iterator = page.iterator();
        pageIndex++;
    }
}
```

**Why return `null` when exhausted?** Spring Batch treats a `null` return from `read()` as the end-of-input signal. The step stops processing and the job proceeds to the next step.

**Why `reset()`?** The reader holds mutable page state. If you run multiple jobs in the same application lifecycle (e.g., different runs in tests or back-to-back scheduled jobs), you must call `reset()` between runs. Otherwise the reader will immediately return `null` on the second run because `pageIndex` is already past the end.

**Filtering unprocessed records:** In production, you will typically only want records that have not yet been included in a transmitted file. Add a repository query to filter by `isProcessed = false`:

```java
private void fetchNextPage() {
    List<PaymentOutboundDataRecord> page =
        paymentRepository.findByIsProcessedFalse(PageRequest.of(pageIndex, PAGE_SIZE)).getContent();
    this.iterator = page.iterator();
    pageIndex++;
}
```

### Step 2: Implement the ItemProcessor

The `ItemProcessor` sits between the reader and the writer. It can enrich, transform, or validate each record before it is written to file. If no enrichment is needed, a pass-through processor is sufficient:

```java
// File: src/main/java/com/example/batch/OutboundDataRecordProcessor.java
package com.example.batch;

import com.example.interface.outbound.model.OutboundInterfaceDataRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

public class OutboundDataRecordProcessor
        implements ItemProcessor<OutboundInterfaceDataRecord, OutboundInterfaceDataRecord> {

    @Override
    public OutboundInterfaceDataRecord process(@NonNull OutboundInterfaceDataRecord record) {
        // Add enrichment logic here if needed, e.g. currency formatting, masking
        return record;
    }
}
```

Declare it as a `@StepScope` bean so Spring Batch creates a fresh instance per step execution:

```java
@Bean
@StepScope
public OutboundDataRecordProcessor outboundDataRecordProcessor() {
    return new OutboundDataRecordProcessor();
}
```

### Step 3: Wire Readers and Create the Job

Use `ReaderMapper` to associate each record class with its reader, then pass the map to `OutboundJobFactory.createJob()`:

```java
// File: src/main/java/com/example/scheduler/OutboundJobScheduler.java
package com.example.scheduler;

import com.example.batch.OutboundDataRecordProcessor;
import com.example.batch.PaymentOutboundDataRecordReader;
import com.example.model.PaymentOutboundDataRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.interface.outbound.batch.OutboundJobFactory;
import com.example.interface.outbound.batch.ReaderMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class OutboundJobScheduler {

    private final JobLauncher jobLauncher;
    private final OutboundJobFactory outboundJobFactory;
    private final PaymentOutboundDataRecordReader paymentReader;

    @Scheduled(cron = "0 0 2 * * *")   // Run at 02:00 daily
    public void runOutboundJob() throws Exception {
        paymentReader.reset();          // Reset pagination state before each run

        ReaderMapper readerMapper = new ReaderMapper();
        readerMapper.addReader(PaymentOutboundDataRecord.class, paymentReader);

        Job job = outboundJobFactory.createJob(
            "daily-payment-outbound",
            readerMapper.getReaderMap(),
            outboundDataRecordProcessor()
        );

        jobLauncher.run(job, new JobParameters());
    }

    @Bean
    @StepScope
    public OutboundDataRecordProcessor outboundDataRecordProcessor() {
        return new OutboundDataRecordProcessor();
    }
}
```

**Why `ReaderMapper`?** A single outbound job can generate files for multiple entity types in sequence. `ReaderMapper` is a thin wrapper around a `Map<Class, ItemReader>` that keeps the API readable and prevents you from managing raw `Map` generics.

### Step 4: Multiple Record Types in One Job

To generate separate files for two different record types in a single job run, add both readers to the `ReaderMapper`. The factory creates one step per entry, run in the order they were added:

```java
readerMapper.addReader(PaymentOutboundDataRecord.class, paymentReader);
readerMapper.addReader(SettlementOutboundDataRecord.class, settlementReader);

Job job = outboundJobFactory.createJob(
    "daily-finance-outbound",
    readerMapper.getReaderMap(),
    outboundDataRecordProcessor()
);
```

Each entity type produces its own output file named by its `@MAGEntity.filenamePrefix`.

### Step 5: Set Up the ACK and Retry Job

After a file is transmitted, the recipient sends back an ACK file. Use `OutboundJobFactory.createAckRetryJob()` to create a job that processes the ACK and retries any `PENDING_ACK` files that have timed out:

```java
// File: src/main/java/com/example/scheduler/AckJobScheduler.java
package com.example.scheduler;

import lombok.RequiredArgsConstructor;
import com.example.interface.outbound.batch.AckFileProcessingTasklet;
import com.example.interface.outbound.batch.OutboundJobFactory;
import com.example.interface.outbound.batch.RetryProcessingTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class AckJobScheduler {

    private final JobLauncher jobLauncher;
    private final OutboundJobFactory outboundJobFactory;
    private final AckFileProcessingTasklet ackFileProcessingTasklet;
    private final RetryProcessingTasklet retryProcessingTasklet;

    @Scheduled(cron = "0 30 * * * *")   // Run every hour at :30
    public void runAckJob() throws Exception {
        Job ackJob = outboundJobFactory.createAckRetryJob(
            "payment-ack-retry",
            ackFileProcessingTasklet,
            retryProcessingTasklet
        );

        JobParameters params = new JobParametersBuilder()
            .addDate("runAt", new Date())   // Ensures each run gets a unique JobInstance
            .toJobParameters();

        jobLauncher.run(ackJob, params);
    }
}
```

**Why `addDate("runAt", new Date())`?** Spring Batch prevents re-running a `JobInstance` that already completed successfully. Adding a unique parameter (timestamp) makes each scheduled execution a new `JobInstance`, allowing the job to run on every trigger.

**If you only need the ACK step** (no retry), use `createAckJob()` instead:

```java
Job ackJob = outboundJobFactory.createAckJob("payment-ack", ackFileProcessingTasklet);
```

### Step 6: Enable Scheduling

Ensure Spring's scheduling support is activated:

```java
// File: src/main/java/com/example/Application.java
@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 4. Flow Summary

```
ItemReader.read()          → Returns one OutboundInterfaceDataRecord per call; null signals end-of-step
ItemProcessor.process()    → Optionally enriches/transforms the record; return null to skip writing
OutboundFileItemWriter     → Framework-managed: writes records to file, manages file size limits and rolling
OutboundStepExecutionListener → Framework-managed: finalises file, applies signature if configured
OutboundJobExecutionListener  → Framework-managed: archives file, updates OutboundFile status
```

After the main job completes, outbound files have status `PENDING_ACK`. The ACK job polls for ACK files from the recipient and advances the status to `SUCCESS` or `EXCEEDED_RETRY_LIMIT`.

## 5. Verification

1. Start the application and trigger the outbound job manually or wait for the cron trigger.
2. Confirm that output files are created in the configured outbound directory with the correct prefix and extension.
3. Check `OutboundFile` records in the database — status should be `PENDING_ACK` after generation, `SUCCESS` after a valid ACK is received.
4. Place a mock ACK file in the configured ACK inbound directory and trigger the ACK job. Confirm the corresponding `OutboundFile` status transitions to `SUCCESS`.
5. Confirm that `isProcessed = true` is set on processed `OutboundInterfaceDataRecord` rows (if your reader filters on this flag).

## 6. Common Mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Not calling `reader.reset()` before re-run | Second job run produces empty file | Call `reset()` on each reader before constructing `ReaderMapper` |
| Using the same job name across different `JobParameters` | `JobInstanceAlreadyCompleteException` | Append a unique parameter (e.g., timestamp) to `JobParameters` for each run |
| Reader returns records with `isProcessed = true` | Duplicate records in output files | Filter by `isProcessed = false` in the repository query |
| Processor returns `null` | Record silently dropped from output | Return `null` only when intentionally skipping a record; otherwise return the record |
| Missing `@StepScope` on processor bean | Shared mutable state across parallel steps | Declare the processor bean with `@StepScope` |

## 7. References

- [`OutboundJobFactory`](../../interface-management-outbound-starter/src/main/java/com/example/interface/outbound/batch/OutboundJobFactory.java)
- [`ReaderMapper`](../../interface-management-outbound-starter/src/main/java/com/example/interface/outbound/batch/ReaderMapper.java)
- [`OutboundInterfaceDataRecord`](../../interface-management-outbound-starter/src/main/java/com/example/interface/outbound/model/OutboundInterfaceDataRecord.java)
- [`RetryProcessingTasklet`](../../interface-management-outbound-starter/src/main/java/com/example/interface/outbound/batch/RetryProcessingTasklet.java)
- [`AckFileProcessingTasklet`](../../interface-management-outbound-starter/src/main/java/com/example/interface/outbound/batch/AckFileProcessingTasklet.java)
- Demo: [`JobScheduler`](../../interface-management-demo/src/main/java/com/demo/scheduler/JobScheduler.java)
- Demo: [`OrderOutboundDataRecordReader`](../../interface-management-demo/src/main/java/com/demo/batch/OrderOutboundDataRecordReader.java)
