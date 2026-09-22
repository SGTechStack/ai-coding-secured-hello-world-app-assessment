# 26. High-Volume Record Ingestion (Spring Batch — Future)

**Goal**: Use Spring Batch for very large files with chunk-based commits (deferred to v2).


**Current Architecture**: Custom `Scheduled` + `ShedLock` (sufficient for current volume).

**Migration Strategy**: When ingestion volume requires transactional chunking (>10k rows or >10s processing), swap `DirectRecordProcessor` → `SpringBatchRecordProcessor` without changing the domain `RecordProcessor` port interface.

```java
@Bean
public Step ingestionStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager) {
    return new StepBuilder("ingest", jobRepository)
        .<Row, Row>chunk(500, transactionManager)
        .reader(itemReader())
        .writer(itemWriter())
        .build();
}
```

