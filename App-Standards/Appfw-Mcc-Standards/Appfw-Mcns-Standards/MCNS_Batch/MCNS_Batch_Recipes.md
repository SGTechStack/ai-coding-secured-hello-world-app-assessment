# MCNS — Batch Retry Implementation Recipes

> These recipes show how to implement the durable batch retry pipeline for MCNS notifications using Spring Batch and ShedLock. All code targets Spring Boot 3.x and JDK 17+.
>
> **Prerequisite**: The core MCNS send flow must already be implemented. See MCNS_Core_Recipes.md.

---

## Recipe 1: Application Configuration

**Goal**: Set up the Spring configuration needed for the batch retry pipeline — async support, JPA repositories, the batch job, and the bounded executor.

```java
@Configuration
@EnableAsync
@EnableJpaRepositories(basePackageClasses = EmailNotificationRepository.class)
public class AppConfiguration {

    @Bean
    public Job retryJob(JobRepository jobRepository, Step retryStep) {
        return new JobBuilder("retryJob", jobRepository)
                .start(retryStep)
                .build();
    }

    @Bean(name = "mcnsBatchExecutor")
    public Executor mcnsBatchExecutor(MCNSRetryProperties retryProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(retryProperties.getExecutor().getCorePoolSize());
        executor.setMaxPoolSize(retryProperties.getExecutor().getMaxPoolSize());
        executor.setQueueCapacity(retryProperties.getExecutor().getQueueCapacity());
        executor.setThreadNamePrefix("mcns-batch-");
        executor.initialize();
        return executor;
    }
}
```

**Key rules**:
*   `@EnableAsync` activates `@Async` on the event listener — required so the listener does not block the batch writer thread.
*   A dedicated `mcnsBatchExecutor` must be declared. Do not rely on the JVM common `ForkJoinPool`. See Recipe 2 for sizing guidance.
*   `maxPoolSize` should be ≤ `chunkSize` — threads beyond `chunkSize` cannot all be used within a single chunk.

---

## Recipe 2: Sizing the Batch Executor

**Goal**: Configure a bounded thread pool that isolates MCNS batch send concurrency from the rest of the application.

Each item within a chunk is dispatched as a `CompletableFuture` on the `mcnsBatchExecutor`. Effective concurrency per chunk is `min(chunkSize, maxPoolSize)`.

| Property | Default | Guidance |
|:---|:---|:---|
| `corePoolSize` | `10` | Threads kept alive between chunks. Set to expected steady-state concurrency. |
| `maxPoolSize` | `20` | Hard concurrency ceiling. Must be ≤ `chunkSize`. |
| `queueCapacity` | `100` | Tasks that queue when all threads are busy. Set high enough to avoid rejection during bursts between chunk commits. |
| `rejectionPolicy` | `CallerRunsPolicy` | When the queue is full, the submitting thread runs the task itself — provides natural back-pressure instead of throwing a rejection exception. |

Configure via `application.yml` under the `executor` key:

```yaml
spring:
  eds:
    mcc:
      mcns:
        retry:
          chunkSize: 500
          executor:
            corePoolSize: 10
            maxPoolSize: 20
            queueCapacity: 200
```

If the MCNS service enforces a rate limit (see `rateLimitPerPeriod` in core config), size `maxPoolSize` to stay within that limit across all nodes. For example, with `rateLimitPerPeriod = 100/s` and 2 nodes, cap each node at 50 concurrent threads maximum.

---

## Recipe 3: Notification Request Entity

**Goal**: Define the entity that holds pending notification requests. This table is the source of truth for what the item reader supplies to the batch pipeline.

```java
// Base class — shared fields across all notification types
@MappedSuperclass
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class AppNotificationRequest {

    @Column(name = "REFERENCE_ID")
    private UUID referenceId;       // correlation key across the retry lifecycle; never change after creation

    @Column(name = "RECEIVER_ID")
    private String receiverId;

    @Column(name = "TEMPLATE_VALUES")
    private String templateValues;  // JSON string

    @Column(name = "SCHEDULE")
    private String schedule;        // optional deferred delivery time

    @Column(name = "IS_PROCESSED")
    private boolean isProcessed;    // false on creation; set to true by event listener on SUCCESS
}

// Concrete entity — one per notification type / tag ID
@Entity
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "EMAIL_NOTIFICATION_REQUEST")
public class EmailNotificationRequest extends AppNotificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID")
    private UUID id;
}
```

```java
@Repository
public interface EmailNotificationRepository
        extends JpaRepository<EmailNotificationRequest, UUID> {

    Optional<EmailNotificationRequest> findByReferenceId(UUID referenceId);
}
```

**Key rules**:
*   `referenceId` links this record to its `MCNSRequestStatusDetails` tracking record. Assign a fresh `UUID.randomUUID()` on creation and never change it.
*   `isProcessed` must be `false` when first persisted. The item reader must skip `true` records.
*   `findByReferenceId` is used by the event listener to look up the record when a batch outcome event arrives.

---

## Recipe 4: Notification Status Tracking Entity

**Goal**: Define the entity that tracks send status and attempt count per `referenceId` across batch runs.

```java
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "MCNS_REQUEST_STATUS_DETAILS")
public class MCNSRequestStatusDetails {

    @Id
    @Column(name = "REFERENCE_ID")
    private UUID referenceId;

    @Column(name = "TAG_ID")
    private String tagId;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column(name = "ATTEMPT_COUNT")
    private int attemptCount;

    @Column(name = "MSG_ID")
    private String msgId;       // MessageDetailsResponse.msgId — populated on SUCCESS; use for downstream delivery tracking

    @Column(name = "ERROR_MSG")
    private String errorMsg;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
```

```java
public enum NotificationStatus {
    SENDING, SUCCESS, FAILED, ATTEMPTS_EXCEEDED
}
```

```java
@Repository
public interface MCNSRequestStatusRepository
        extends JpaRepository<MCNSRequestStatusDetails, UUID> {

    Optional<MCNSRequestStatusDetails> findByReferenceId(UUID referenceId);
}
```

**Key rules**:
*   `attemptCount` is incremented before each send attempt and persists across application restarts.
*   `referenceId` is the primary key — one record per notification request. The processor creates it on the first attempt and updates it on subsequent runs.

---

## Recipe 5: RepositoryMap

**Goal**: Route batch outcome events to the correct notification repository when multiple tag IDs are active.

```java
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class RepositoryMap {

    private final EmailNotificationRepository emailNotificationRepository;
    // add one field per additional tag ID repository

    private final Map<String, JpaRepository> tagIdToRepositoryMap = new HashMap<>();

    @PostConstruct
    private void init() {
        tagIdToRepositoryMap.put("EMAIL_NOTIFICATIONS", emailNotificationRepository);
        // register all active tag IDs here
    }

    public JpaRepository getRepository(String tagId) {
        return tagIdToRepositoryMap.get(tagId);
    }
}
```

**Key rules**:
*   Every active tag ID must be registered. A missing entry causes the event listener to receive `null`, throw an exception, and emit an `ERROR` log with `tag.id`. Silent dropping is not permitted — add the tag ID to `RepositoryMap` and restart to fix.
*   If the application has only one tag ID, `RepositoryMap` may be omitted and the repository injected directly into the event listener.

---

## Recipe 6: Item Reader

**Goal**: Supply pending notification items to the batch pipeline. Returns `null` when no more pending items are available (standard Spring Batch `ItemReader` contract).

```java
@Component("emailNotificationReader")   // bean name must match readerBean in configuration
@JobScope                               // new instance per job execution; prevents stale iterator state
public class EmailNotificationReader implements ItemReader<NotificationRequestItem> {

    private final EmailNotificationRepository repository;
    private final String tagId;
    private Iterator<EmailNotificationRequest> iterator;

    @Autowired
    public EmailNotificationReader(EmailNotificationRepository repository,
                                   @Value("#{jobParameters['tagId']}") String tagId) {
        this.repository = repository;
        this.tagId = tagId;
    }

    @Override
    public NotificationRequestItem read() {
        if (iterator == null) {
            iterator = repository.findAll().iterator();
        }

        while (iterator.hasNext()) {
            EmailNotificationRequest record = iterator.next();

            if (!record.isProcessed()) {
                return NotificationRequestItem.builder()
                        .referenceId(record.getReferenceId())
                        .tagId(tagId)
                        .channel("email")
                        .receiverId(record.getReceiverId())
                        .templateId("<your-template-id>")
                        .templateValues(record.getTemplateValues())
                        .build();
            }
        }
        return null;
    }
}
```

```java
// App-defined DTO passed from reader to processor
@Data
@Builder
public class NotificationRequestItem {
    private UUID referenceId;
    private String tagId;
    private String channel;
    private String receiverId;
    private String templateId;
    private String templateValues;
}
```

**Key rules**:
*   `@JobScope` is mandatory — a new reader instance is created per job execution, preventing stale iterator state between scheduled runs.
*   The bean name must exactly match the `readerBean` value in configuration for the corresponding tag ID.
*   Skip `isProcessed = true` records. Returning them re-queues already-delivered notifications and increments attempt count unnecessarily.
*   Return `null` to signal end-of-input to Spring Batch.

---

## Recipe 7: Item Processor

**Goal**: For each item, check and update attempt count, then dispatch the MCNS send asynchronously via `CompletableFuture`.

```java
@Component
@RequiredArgsConstructor
public class MCNSItemProcessor
        implements ItemProcessor<NotificationRequestItem, CompletableFuture<NotificationResult>> {

    private final MCNSRequestStatusRepository statusRepository;
    private final MCNSSendService sendService;       // core send flow from MCNS_Core_Recipes
    private final Executor mcnsBatchExecutor;
    private final int maxAttempts;

    @Override
    public CompletableFuture<NotificationResult> process(NotificationRequestItem item) {
        MCNSRequestStatusDetails statusDetails = statusRepository
                .findByReferenceId(item.getReferenceId())
                .orElse(null);

        if (statusDetails == null) {
            statusDetails = MCNSRequestStatusDetails.builder()
                    .referenceId(item.getReferenceId())
                    .tagId(item.getTagId())
                    .status(NotificationStatus.SENDING)
                    .attemptCount(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } else if (statusDetails.getAttemptCount() >= maxAttempts) {
            statusDetails.setStatus(NotificationStatus.ATTEMPTS_EXCEEDED);
            statusDetails.setUpdatedAt(LocalDateTime.now());
            statusRepository.save(statusDetails);
            throw new MaxAttemptsExceededException(item.getReferenceId());
        } else {
            statusDetails.setAttemptCount(statusDetails.getAttemptCount() + 1);
            statusDetails.setStatus(NotificationStatus.SENDING);
            statusDetails.setUpdatedAt(LocalDateTime.now());
        }

        statusRepository.save(statusDetails);

        final MCNSRequestStatusDetails saved = statusDetails;
        return CompletableFuture.supplyAsync(
                () -> sendService.send(item.getChannel(), item.getReceiverId(),
                                       item.getTemplateId(), item.getTemplateValues()),
                mcnsBatchExecutor
        ).thenApply(response -> NotificationResult.success(item.getReferenceId(), response))
         .exceptionally(ex -> NotificationResult.failed(item.getReferenceId(), ex.getMessage()));
    }
}
```

---

## Recipe 8: Item Writer

**Goal**: Join all `CompletableFuture` results for a chunk, update `MCNSRequestStatusDetails`, and publish an outcome event per item.

```java
@Component
@RequiredArgsConstructor
public class MCNSItemWriter
        implements ItemWriter<CompletableFuture<NotificationResult>> {

    private final MCNSRequestStatusRepository statusRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void write(List<? extends CompletableFuture<NotificationResult>> futures) throws Exception {
        for (CompletableFuture<NotificationResult> future : futures) {
            NotificationResult result = future.join();    // blocks until this item's send completes

            MCNSRequestStatusDetails details = statusRepository
                    .findByReferenceId(result.getReferenceId())
                    .orElseThrow();

            if (result.isSuccess()) {
                details.setStatus(NotificationStatus.SUCCESS);
                details.setMsgId(result.getMcnsResponse().getMessageDetails().stream()
                        .findFirst().map(MessageDetailsResponse::getMsgId).orElse(null));
                details.setUpdatedAt(LocalDateTime.now());
                statusRepository.save(details);
                eventPublisher.publishEvent(
                        new MCNSRequestStatusUpdate(this, result.getReferenceId(),
                                details.getTagId(), NotificationStatus.SUCCESS, null));
            } else {
                details.setStatus(NotificationStatus.FAILED);
                details.setErrorMsg(result.getErrorMsg());
                details.setUpdatedAt(LocalDateTime.now());
                statusRepository.save(details);
                eventPublisher.publishEvent(
                        new MCNSRequestStatusUpdate(this, result.getReferenceId(),
                                details.getTagId(), NotificationStatus.FAILED, result.getErrorMsg()));
            }
        }
    }
}
```

---

## Recipe 9: Status Update Event

**Goal**: Define the Spring event published by the writer to notify the event listener of each item's outcome.

```java
public class MCNSRequestStatusUpdate extends ApplicationEvent {

    private final UUID referenceId;
    private final String tagId;
    private final NotificationStatus status;
    private final String errorMsg;     // null on SUCCESS

    public MCNSRequestStatusUpdate(Object source, UUID referenceId, String tagId,
                                    NotificationStatus status, String errorMsg) {
        super(source);
        this.referenceId = referenceId;
        this.tagId = tagId;
        this.status = status;
        this.errorMsg = errorMsg;
    }

    // getters
}
```

---

## Recipe 10: Event Listener

**Goal**: Consume `MCNSRequestStatusUpdate` events to update `isProcessed` on the notification record and write audit entries.

```java
@Component
@RequiredArgsConstructor
public class NotificationStatusListener {

    private final RepositoryMap repositoryMap;

    @EventListener
    @Async
    public void handleEvent(MCNSRequestStatusUpdate event) {
        JpaRepository repository = repositoryMap.getRepository(event.getTagId());

        if (repository == null) {
            log.error("tag.id={} not registered in RepositoryMap", event.getTagId());
            throw new IllegalStateException("Unknown tagId: " + event.getTagId());
        }

        Optional<AppNotificationRequest> recordOpt =
                ((EmailNotificationRepository) repository)
                        .findByReferenceId(event.getReferenceId());

        if (recordOpt.isEmpty()) return;

        AppNotificationRequest record = recordOpt.get();

        switch (event.getStatus()) {
            case SUCCESS:
                record.setProcessed(true);
                repository.save(record);
                // emit audit log: referenceId, tagId, SUCCESS
                break;
            case FAILED:
                record.setProcessed(false);
                repository.save(record);
                // emit audit log: referenceId, tagId, FAILED, errorMsg
                // record re-queued on next cron window
                break;
            case ATTEMPTS_EXCEEDED:
                // quarantine the record — move to a terminal, non-reprocessable state
                // implementation choice: separate quarantine table, dead-letter store, or in-place terminal status flag
                // must preserve: referenceId, attemptCount, failure reason
                // emit operational alert + audit log: referenceId, tagId, ATTEMPTS_EXCEEDED
                break;
            default:
                break;
        }
    }
}
```

**Key rules**:
*   `@Async` is mandatory — without it the listener blocks the batch writer thread and prevents the chunk from committing.
*   The `null` guard on `repository` must throw an exception and emit an `ERROR` log with `tag.id`. Silent dropping is not permitted — an unregistered tag ID is a configuration defect that requires a code fix and restart.

---

## Recipe 11: Providing the ShedLock LockProvider

**Goal**: Prevent concurrent batch job execution when multiple application nodes run simultaneously.

```java
@Configuration
public class ShedLockConfiguration {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()   // use database clock to avoid cross-node clock skew
                .build()
        );
    }
}
```

**Required table** — create via Liquibase or Flyway before application startup:

```sql
CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

**ShedLock timing guidance**:
*   `lockAtMostFor` — maximum duration a lock is held even if the node crashes and cannot release it. Must be longer than the job's maximum expected runtime, but shorter than the cron interval — so a crashed node's lock expires before the next window fires.
*   `lockAtLeastFor` — minimum duration a lock is held even if the job finishes early. Must be shorter than the cron interval — so the lock is released before the next window fires and the next scheduled run is not blocked. Its purpose is to absorb clock skew between nodes rather than controlling scheduling.

---

## Recipe 11a: Skippable Exception Policy for UNKNOWN_TAG_ID

**Goal**: Configure the Spring Batch step to skip items that fail with `UnknownTagIdException`, emit a loud ERROR log and operational alert, and continue processing the remaining chunk rather than aborting the job.

Define the exception:

```java
public class UnknownTagIdException extends RuntimeException {

    private final String tagId;

    public UnknownTagIdException(String tagId) {
        super("Tag ID not registered in RepositoryMap: " + tagId);
        this.tagId = tagId;
    }

    public String getTagId() {
        return tagId;
    }
}
```

Add a `SkipListener` that emits the ERROR log and operational alert on skip:

```java
@Component
@Slf4j
public class UnknownTagIdSkipListener
        implements SkipListener<NotificationRequestItem, CompletableFuture<NotificationResult>> {

    @Override
    public void onSkipInProcess(NotificationRequestItem item, Throwable t) {
        if (t instanceof UnknownTagIdException e) {
            emitAlert(e.getTagId());
        }
    }

    @Override
    public void onSkipInWrite(CompletableFuture<NotificationResult> item, Throwable t) {
        if (t instanceof UnknownTagIdException e) {
            emitAlert(e.getTagId());
        }
    }

    @Override
    public void onSkipInRead(Throwable t) { }

    private void emitAlert(String tagId) {
        log.error("tag.id={} not registered in RepositoryMap — item skipped. " +
                  "Configuration defect requires RepositoryMap fix and restart.", tagId);
        // emit operational alert here
    }
}
```

Configure the step with `faultTolerant()`, registering `UnknownTagIdException` as skippable and attaching the listener:

```java
@Bean
public Step retryStep(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      MCNSRetryProperties retryProperties,
                      UnknownTagIdSkipListener skipListener) {
    return new StepBuilder("retryStep", jobRepository)
            .<NotificationRequestItem, CompletableFuture<NotificationResult>>chunk(
                    retryProperties.getChunkSize(), transactionManager)
            .reader(/* injected per tagId */)
            .processor(/* MCNSItemProcessor */)
            .writer(/* MCNSItemWriter */)
            .faultTolerant()
            .skip(UnknownTagIdException.class)
            .skipLimit(Integer.MAX_VALUE)   // no cap — every UNKNOWN_TAG_ID item is a config defect
            .listener(skipListener)
            .build();
}
```

**Key rules**:
*   `skipLimit(Integer.MAX_VALUE)` — there is no sensible cap. Every occurrence is a configuration defect that emits its own alert; the job must keep running for other tag IDs.
*   The skip listener is mandatory — a silent skip violates the loud-skip requirement. The ERROR log must include `tag.id` and an operational alert must be emitted.
*   `isProcessed` is not modified on skip. The domain record stays in the table with `isProcessed = false` but will not be re-queued until the `RepositoryMap` is fixed and the application is restarted.
*   `UNKNOWN_TAG_ID` is distinct from `FAILED` — do not treat it as a transient failure or allow `attemptCount` to increment.

---

## Recipe 12: Seeding Notification Records

**Goal**: Persist notification records correctly so the item reader picks them up on the next batch run.

```java
@Component
@RequiredArgsConstructor
public class NotificationSeeder {

    private final EmailNotificationRepository repository;
    private final ObjectMapper objectMapper;

    public void queue(String receiverId,
                      Map<String, String> templateValues) throws JsonProcessingException {
        EmailNotificationRequest request = EmailNotificationRequest.builder()
                .referenceId(UUID.randomUUID())     // fresh UUID per notification; never reuse
                .receiverId(receiverId)
                .templateValues(objectMapper.writeValueAsString(templateValues))
                .isProcessed(false)                 // must be false so the reader picks it up
                .build();
        repository.save(request);
    }
}
```

**Key rules**:
*   Always generate a fresh `UUID.randomUUID()` for `referenceId`. Reusing one causes the processor to find an existing `MCNSRequestStatusDetails` record and increment its attempt count instead of registering a new send.
*   `isProcessed` must be `false` on creation.
*   Records must be persisted before the next cron window fires — there is no mechanism to inject records into a running job.

---

## Recipe 13: Full Configuration

**Goal**: Configure cron schedules and batch retry settings for multiple tag IDs.

```yaml
spring:
  security:
    eds:
      mcc:
        mcns:
          url: https://prod.mcns-ecs.defcloud.gov.sg/
          senderId: notifications@yourapp.gov.sg
          requestTimeout: 2
          maxRetries: 3
          retryWaitDuration: 10
          limitRefreshPeriod: 30
          rateLimitPerPeriod: 3000

  eds:
    mcc:
      mcns:
        retry:
          enabled: true
          maxAttempts: 5
          chunkSize: 500
          tagId:
            EMAIL_ALERTS:
              cronExpression: "0 */10 * * * *"    # every 10 minutes
              readerBean: emailAlertReader
            SMS_OTP:
              cronExpression: "0 */2 * * * *"     # every 2 minutes
              readerBean: smsOtpReader
```

Each tag ID maps to one scheduled job with its own reader bean and cron schedule.

---

## Recipe 13a: Static System Identity Bean

**Goal**: Provide a stable service account identifier that the batch pipeline uses as the sender identity on all MCNS calls and audit entries. There is no user context at job execution time, so a static configured value is used instead.

Add `systemIdentity` to your batch retry configuration:

```yaml
spring:
  eds:
    mcc:
      mcns:
        retry:
          systemIdentity: "system:mcns-batch-processor"
```

Expose it as an injectable bean:

```java
@Configuration
public class SystemIdentityConfiguration {

    @Bean
    public String mcnsBatchSystemIdentity(MCNSRetryProperties retryProperties) {
        return retryProperties.getSystemIdentity();
    }
}
```

Inject into the per-item processor:

```java
@Component
@RequiredArgsConstructor
public class MCNSItemProcessor
        implements ItemProcessor<NotificationRequestItem, CompletableFuture<NotificationResult>> {

    private final MCNSRequestStatusRepository statusRepository;
    private final MCNSSendService sendService;
    private final Executor mcnsBatchExecutor;
    private final int maxAttempts;
    private final String mcnsBatchSystemIdentity;   // injected by bean name

    // ... process() uses mcnsBatchSystemIdentity as senderId when constructing the request
    //     and passes it through to audit log entries
}
```

**Key rules**:
*   Must be non-null and non-blank at startup. Fail fast — do not allow the job to run with an unconfigured identity.
*   Use a stable, recognisable value such as `system:mcns-batch-processor` or `system:{app-name}-batch`. It will appear as `sender.id` in every audit log entry the pipeline emits.
*   Do not derive this value from user context or request scope — the batch job has neither.

---

## Recipe 14: Registering Scheduled Batch Jobs from Configuration

**Goal**: At application startup, read the `tagId` map from configuration and register one ShedLock-backed scheduled job per tag ID using its cron expression and reader bean name.

First, bind the configuration into a properties class:

```java
@Configuration
@ConfigurationProperties(prefix = "spring.eds.mcc.mcns.retry")
@Data
public class MCNSRetryProperties {

    private boolean enabled;
    private int maxAttempts;
    private int chunkSize;
    private Map<String, TagIdConfig> tagId = new HashMap<>();
    private ExecutorConfig executor = new ExecutorConfig();

    @Data
    public static class TagIdConfig {
        private String cronExpression;
        private String readerBean;
    }

    @Data
    public static class ExecutorConfig {
        private int corePoolSize = 10;   // default: 10 warm threads between chunks
        private int maxPoolSize = 20;    // default: 20 concurrent sends; must be <= chunkSize
        private int queueCapacity = 100; // default: 100 queued tasks before rejection
    }
}
```

Then register a scheduled job per tag ID at startup using `TaskScheduler` and ShedLock:

```java
@Component
@RequiredArgsConstructor
public class ScheduledBatchJobRegistrar implements ApplicationRunner {

    private final MCNSRetryProperties retryProperties;
    private final JobLauncher jobLauncher;
    private final Job retryJob;
    private final LockProvider lockProvider;
    private final TaskScheduler taskScheduler;

    @Override
    public void run(ApplicationArguments args) {
        if (!retryProperties.isEnabled()) {
            return;
        }

        retryProperties.getTagId().forEach((tagId, config) -> {
            CronTrigger trigger = new CronTrigger(config.getCronExpression());

            taskScheduler.schedule(() -> launchWithLock(tagId, config.getReaderBean()), trigger);
        });
    }

    private void launchWithLock(String tagId, String readerBean) {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                "mcns-batch-" + tagId,
                Duration.ofMinutes(10),   // lockAtMostFor: max lock duration if node crashes
                Duration.ofMinutes(1)     // lockAtLeastFor: prevents immediate re-run on fast node
        );

        SimpleLock lock = lockProvider.lock(lockConfig).orElse(null);

        if (lock == null) {
            // another node holds the lock — skip this run
            return;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("tagId", tagId)
                    .addString("readerBean", readerBean)
                    .addDate("jobDateTime", new Date())   // unique per execution
                    .toJobParameters();

            jobLauncher.run(retryJob, params);
        } catch (Exception e) {
            // log error: tagId, cronExpression, exception
        } finally {
            lock.unlock();  // lock is guaranteed non-null here; early return above handles null case
        }
    }
}
```

**Key rules**:
*   One scheduled task is registered per tag ID at startup. Adding a new tag ID to configuration requires a restart to take effect.
*   `lockAtMostFor` must be longer than the job's maximum expected runtime but shorter than the cron interval. This ensures a crashed node's lock expires before the next window fires, allowing another node to take over without skipping a run.
*   `lockAtLeastFor` must be shorter than the cron interval. It holds the lock for a minimum duration even if the job finishes early, protecting against clock skew between nodes causing an unintended re-run. It does not prevent the next scheduled window from running.
*   `jobDateTime` is added as a job parameter to ensure every scheduled execution is a distinct job instance in the Spring Batch metadata tables. Without it, Spring Batch rejects a re-run of a previously completed job.
*   If `enabled = false`, no jobs are registered and no threads are consumed.

---

## Recipe 15: Integration Test Setup

**Goal**: Write integration tests for the batch retry pipeline using in-memory H2.

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class NotificationBatchIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private MCNSRequestStatusRepository statusRepository;

    @Autowired
    private EmailNotificationRepository notificationRepository;

    @MockBean
    private MCNSSendService sendService;   // mock the core send service

    @Test
    void batchRetry_successfulSend_marksStatusSuccess() throws Exception {
        UUID refId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        notificationRepository.save(EmailNotificationRequest.builder()
                .referenceId(refId)
                .receiverId("test@example.gov.sg")
                .isProcessed(false)
                .build());

        when(sendService.send(any(), any(), any(), any()))
                .thenReturn(new MCNSResponse(/* ... */));

        JobParameters params = new JobParametersBuilder()
                .addString("tagId", "EMAIL_ALERTS")
                .addString("readerBean", "emailAlertReader")
                .addDate("jobDateTime", new Date())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        MCNSRequestStatusDetails status =
                statusRepository.findByReferenceId(refId).orElseThrow();
        assertThat(status.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
    }

    @Test
    void batchRetry_maxAttemptsExceeded_marksAttemptsExceeded() throws Exception {
        UUID refId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        statusRepository.save(MCNSRequestStatusDetails.builder()
                .referenceId(refId)
                .tagId("EMAIL_ALERTS")
                .status(NotificationStatus.FAILED)
                .attemptCount(5)   // equal to maxAttempts
                .build());

        notificationRepository.save(EmailNotificationRequest.builder()
                .referenceId(refId)
                .receiverId("test2@example.gov.sg")
                .isProcessed(false)
                .build());

        JobParameters params = new JobParametersBuilder()
                .addString("tagId", "EMAIL_ALERTS")
                .addString("readerBean", "emailAlertReader")
                .addDate("jobDateTime", new Date())
                .toJobParameters();

        jobLauncherTestUtils.launchJob(params);

        MCNSRequestStatusDetails status =
                statusRepository.findByReferenceId(refId).orElseThrow();
        assertThat(status.getStatus()).isEqualTo(NotificationStatus.ATTEMPTS_EXCEEDED);
    }
}
```

**Test data guidelines**:
*   Use deterministic UUIDs for reproducible assertions across test runs.
*   Use fixed cron expressions that do not fire automatically during tests (e.g., `"0 0 0 1 1 ? 2099"`).
*   Use H2 in-memory for all persistence; enable auto-initialisation for Spring Batch and ShedLock schemas.
*   Mock `MCNSSendService` to control MCNS responses without network calls.
