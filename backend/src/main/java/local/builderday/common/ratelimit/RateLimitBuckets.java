package local.builderday.common.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ExpiredEntriesCleaner;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import java.time.Duration;
import java.util.function.Supplier;
import javax.sql.DataSource;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.common.ratelimit.repository.entity.RateLimitBucketEntity;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable Bucket4j JDBC buckets shared by every instance, one store for every rate limit in the application. Features
 * define only their policy (key prefix, capacity, refill); this owns the store, its H2 workaround, per-key
 * serialisation and expired-state cleanup.
 *
 * <p>Each bucket operation is an atomic {@code SELECT ... FOR UPDATE} transaction on its own connection, so
 * concurrent consumers cannot overdraw a bucket, and it commits independently of any caller transaction.
 */
@Component
public class RateLimitBuckets {
  private static final int CLEANUP_BATCH_SIZE = 1_000;

  private final ProxyManager<String> buckets;
  private final RateLimitBucketRepository rateLimitBucketRepository;
  private final TransactionTemplate serialisedTransaction;
  private final TransactionTemplate independentTransaction;

  /** The rate-limit clock; tests replace it with a controllable {@code @Primary} one. */
  @Bean
  static TimeMeter rateLimitTimeMeter() {
    return TimeMeter.SYSTEM_MILLISECONDS;
  }

  /** Public so tests can model another instance or a short retention. */
  public RateLimitBuckets(DataSource dataSource, RateLimitBucketRepository rateLimitBucketRepository,
      RateLimitProperties properties,
      TimeMeter clock, PlatformTransactionManager transactions) {
    this.buckets = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
        .primaryKeyMapper(PrimaryKeyMapper.STRING)
        .table(RateLimitBucketEntity.TABLE)
        .idColumn("id")
        .stateColumn("state")
        .expiresAtColumn("expires_at")
        .clientClock(clock)
        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(properties.retention()))
        .build();
    this.rateLimitBucketRepository = rateLimitBucketRepository;
    this.serialisedTransaction = new TransactionTemplate(transactions);
    this.independentTransaction = new TransactionTemplate(transactions);
    this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** The bucket for {@code key}, created with {@code configuration} on first use. */
  public BucketProxy bucket(String key, BucketConfiguration configuration) {
    insertIfAbsent(new RateLimitBucketEntity(key));
    return buckets.getProxy(key, () -> configuration);
  }

  /**
   * True when state exists for {@code key}. Check this before reading a bucket that may not exist: every Bucket4j
   * command, even a read, creates a row for an unknown key, which would create state for every visitor.
   */
  public boolean exists(String key) {
    return rateLimitBucketRepository.existsById(key);
  }

  /**
   * Consumes one of {@code attempts} allowed per fixed {@code window} for {@code key}; false when the current window's
   * allowance is used up.
   */
  public boolean tryFixedWindow(String key, int attempts, Duration window) {
    var configuration = BucketConfiguration.builder()
        .addLimit(limit -> limit.capacity(attempts).refillIntervally(attempts, window))
        .build();
    return bucket(key, configuration).tryConsume(1);
  }

  /**
   * Runs {@code work} while holding the row lock for {@code key}, in one transaction, serialising it against every
   * other {@code serialised} call for the same key across all instances. Bucket operations inside still commit on
   * their own, even when {@code work} throws and the transaction rolls back.
   *
   * @param retainFor how long after this use the serialisation row is kept before cleanup removes it
   */
  public <T> T serialised(String key, Duration retainFor, Supplier<T> work) {
    long retainUntil = System.currentTimeMillis() + retainFor.toMillis();
    insertIfAbsent(new RateLimitBucketEntity(key, retainUntil));
    return serialisedTransaction.execute(status -> {
      rateLimitBucketRepository.findLockedById(key).ifPresent(row -> row.setExpiresAt(retainUntil));
      return work.get();
    });
  }

  /**
   * Pre-creates the row so Bucket4j never issues its {@code INSERT ... ON CONFLICT(id) DO NOTHING}, which H2
   * (development and tests) cannot parse even in PostgreSQL mode. Bucket4j treats a row with null state as a new
   * bucket on both databases. Commits on its own so Bucket4j's connections see it; a concurrent insert of the same
   * key loses the race harmlessly on the primary key.
   */
  private void insertIfAbsent(RateLimitBucketEntity row) {
    if (rateLimitBucketRepository.existsById(row.getId())) return;
    try {
      independentTransaction.executeWithoutResult(status -> rateLimitBucketRepository.saveAndFlush(row));
    } catch (DataIntegrityViolationException concurrentInsert) {
      // Another request created the row first.
    }
  }

  /**
   * Deletes rows whose retention has passed. Bucket4j sets {@code expires_at} to the time the bucket would be full
   * again plus the retention; serialisation rows carry their own {@code expires_at}. Void: ShedLock cannot lock a
   * method returning a primitive.
   */
  @Scheduled(cron = "${app.security.rate-limit.cleanup-cron}")
  @SchedulerLock(name = "rate-limit-cleanup")
  public void removeExpired() {
    if (!(buckets instanceof ExpiredEntriesCleaner cleaner)) return;
    while (cleaner.removeExpired(CLEANUP_BATCH_SIZE) == CLEANUP_BATCH_SIZE) {
      // Another full batch may remain.
    }
  }
}
