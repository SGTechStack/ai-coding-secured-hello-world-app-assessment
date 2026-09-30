package local.builderday.account.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import local.builderday.common.ratelimit.RateLimitBuckets;
import local.builderday.common.ratelimit.RateLimitProperties;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.account.registration.config.RegistrationRateLimitProperties;
import local.builderday.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** The Registration lockout through its interface, against the durable Bucket4j JDBC store on H2. */
@SpringBootTest
@Import(TestClocks.class)
class RegistrationLockoutTest {
  private static final String IP = "198.51.100.23";
  private static final String LOCK_KEY = "registration-lock:" + IP;
  private static final RegistrationResult.Rejected REJECTED = new RegistrationResult.Rejected(List.of());
  private static final RegistrationResult CREATED = new RegistrationResult.Created(UUID.randomUUID());

  @Autowired RegistrationLockout lockout;
  @Autowired RegistrationRateLimitProperties properties;
  @Autowired RateLimitProperties storage;
  @Autowired TestClocks clocks;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactions;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void reset() {
    clocks.reset();
    rateLimitBucketRepository.deleteAllInBatch();
  }

  @Test
  void should_countScreeningRejections_andReturnLockedWithoutScreening_onceLocked() {
    for (int attempt = 0; attempt < 10; attempt++) {
      assertThat(lockout.attempt(IP, () -> REJECTED, () -> "x", prepared -> CREATED)).isEqualTo(REJECTED);
    }

    assertThat(lockout.attempt(IP, () -> { throw new AssertionError("screened while locked"); }, () -> "x",
        prepared -> CREATED)).isInstanceOf(RegistrationResult.Locked.class);
    assertThat(lockout.isLocked("198.51.100.24")).isFalse();
  }

  @Test
  void should_countRejectedVerdicts() {
    for (int attempt = 0; attempt < 9; attempt++) {
      lockout.attempt(IP, () -> null, () -> "x", prepared -> REJECTED);
    }
    assertThat(lockout.isLocked(IP)).isFalse();

    lockout.attempt(IP, () -> null, () -> "x", prepared -> REJECTED);
    assertThat(lockout.isLocked(IP)).isTrue();
  }

  @Test
  void should_refuseADecisionThatRacesTheTenthRejection() throws Exception {
    rejections(9);
    var tenthDeciding = new CountDownLatch(1);
    var releaseTenth = new CountDownLatch(1);
    var racingDecided = new AtomicBoolean();

    // The tenth rejection pauses inside the gate, before it is counted.
    var tenth = CompletableFuture.supplyAsync(() -> lockout.attempt(IP, () -> null, () -> "x", prepared -> {
      tenthDeciding.countDown();
      await(releaseTenth);
      return REJECTED;
    }));
    assertThat(tenthDeciding.await(10, TimeUnit.SECONDS)).isTrue();
    // Passes the (not yet locked) fast-path check, then must wait at the gate.
    var racing = CompletableFuture.supplyAsync(() -> lockout.attempt(IP, () -> null, () -> "x",
        prepared -> { racingDecided.set(true); return CREATED; }));

    Thread.sleep(1_500);
    assertThat(racing).isNotDone();
    releaseTenth.countDown();

    assertThat(tenth.get(20, TimeUnit.SECONDS)).isEqualTo(REJECTED);
    assertThat(racing.get(20, TimeUnit.SECONDS)).isInstanceOf(RegistrationResult.Locked.class);
    assertThat(racingDecided).isFalse();
  }

  @Test
  void should_notCreateStateForUnknownSources_whenCheckingTheLock() {
    assertThat(lockout.isLocked(IP)).isFalse();

    assertThat(rateLimitBucketRepository.count()).isZero();
  }

  @Test
  void should_unlockAutomatically_onlyAfterTheLockDuration() {
    rejections(10);

    clocks.advance(Duration.ofHours(23).plusMinutes(59));
    assertThat(lockout.isLocked(IP)).isTrue();
    clocks.advance(Duration.ofMinutes(2));
    assertThat(lockout.isLocked(IP)).isFalse();
  }

  @Test
  void should_notExtendTheLock_whenRejectionsArriveWhileLocked() {
    rejections(10);
    clocks.advance(Duration.ofHours(12));
    rejections(5);

    clocks.advance(Duration.ofHours(12).plusMinutes(1));
    assertThat(lockout.isLocked(IP)).isFalse();
  }

  @Test
  void should_countConcurrentRejectionsAtomically() throws Exception {
    concurrentRejections(9);
    assertThat(lockout.isLocked(IP)).as("nine concurrent rejections stay under the threshold").isFalse();

    concurrentRejections(1);
    assertThat(lockout.isLocked(IP)).isTrue();
  }

  @Test
  void should_retainLockStateUntil48HoursAfterTheLockEnds() {
    long lockedAtMillis = System.currentTimeMillis();
    rejections(10);

    long expiresAt = jdbcTemplate.queryForObject("select expires_at from rate_limit_buckets where id = ?",
        Long.class, LOCK_KEY);
    long expectedMillis = lockedAtMillis + Duration.ofHours(24 + 48).toMillis();
    assertThat(expiresAt).isBetween(expectedMillis - 5_000, expectedMillis + 5_000);
  }

  @Test
  void should_deleteLockState_onlyOnceRetentionHasPassed() throws Exception {
    // Bucket4j stamps expires_at with system time, so use millisecond durations rather than a shifted clock.
    var shortLived = new RegistrationRateLimitProperties(10, properties.window(), Duration.ofMillis(50));
    var shortStorage = new RateLimitProperties(Duration.ofSeconds(2));
    var store = buckets(shortStorage);
    var fastLockout = new RegistrationLockout(store, shortLived, shortStorage);
    for (int i = 0; i < 10; i++) fastLockout.recordRejection(IP);

    store.removeExpired();
    assertThat(rateLimitBucketRepository.existsById(LOCK_KEY)).as("within retention: kept").isTrue();

    Thread.sleep(2_500);
    store.removeExpired();
    assertThat(rateLimitBucketRepository.existsById(LOCK_KEY)).as("retention passed: removed").isFalse();
  }

  @Test
  void should_runTheScheduledCleanupThroughItsSchedulerLock(@Autowired RateLimitBuckets scheduled) {
    // The Spring bean, so the call goes through the ShedLock proxy exactly as the scheduler's does.
    scheduled.removeExpired();
  }

  @Test
  void should_shareLockStateWithAnotherInstance_becauseItLivesInTheDatabase() {
    rejections(10);

    var otherInstance = new RegistrationLockout(buckets(storage), properties, storage);

    assertThat(otherInstance.isLocked(IP)).isTrue();
  }

  private RateLimitBuckets buckets(RateLimitProperties settings) {
    return new RateLimitBuckets(dataSource, rateLimitBucketRepository, settings, TimeMeter.SYSTEM_MILLISECONDS,
        transactions);
  }

  private void rejections(int count) {
    for (int i = 0; i < count; i++) lockout.recordRejection(IP);
  }

  private void concurrentRejections(int count) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(count)) {
      for (int i = 0; i < count; i++) {
        pool.submit(() -> { start.await(); lockout.recordRejection(IP); return null; });
      }
      start.countDown();
      pool.shutdown();
      assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
