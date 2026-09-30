package local.builderday.account.registration.service;

import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import local.builderday.common.ratelimit.RateLimitBuckets;
import local.builderday.common.ratelimit.RateLimitProperties;
import local.builderday.account.registration.config.RegistrationRateLimitProperties;
import org.springframework.stereotype.Service;

/**
 * Registration lockout: a long lock per source IP after too many rejected registrations, on the shared durable
 * {@link RateLimitBuckets} store. {@link #attempt} owns the whole protocol, so callers supply only their own
 * validation, preparation and decision.
 *
 * <p>Two buckets per source: a rejection counter (capacity = threshold, greedily refilled over the window) and a
 * one-token lock bucket (refilled after the lock duration). Consuming the single lock token is idempotent, so
 * concurrent threshold hits cannot stack or extend a lock.
 *
 * <p>ponytail: the greedy refill makes the "rolling window" a token-bucket approximation (rejections spaced evenly
 * slower than threshold/window never lock). Switch to a sliding-log table if an exact rolling count is required.
 */
@Service
public class RegistrationLockout {
  private static final String COUNTER_PREFIX = "registration-rejections:";
  private static final String LOCK_PREFIX = "registration-lock:";
  private static final String GATE_PREFIX = "registration-gate:";

  private final RateLimitBuckets buckets;
  private final Duration gateRetention;
  private final BucketConfiguration counterConfiguration;
  private final BucketConfiguration lockConfiguration;

  public RegistrationLockout(RateLimitBuckets buckets, RegistrationRateLimitProperties properties,
      RateLimitProperties storage) {
    this.buckets = buckets;
    this.gateRetention = properties.lockDuration().plus(storage.retention());
    this.counterConfiguration = BucketConfiguration.builder()
        .addLimit(limit -> limit.capacity(properties.rejectedAttemptThreshold())
            .refillGreedy(properties.rejectedAttemptThreshold(), properties.window()))
        .build();
    this.lockConfiguration = BucketConfiguration.builder()
        .addLimit(limit -> limit.capacity(1).refillGreedy(1, properties.lockDuration()))
        .build();
  }

  /**
   * One registration attempt from {@code sourceIp}:
   * <ol>
   *   <li>a locked source gets {@link RegistrationResult.Locked} without any further work;</li>
   *   <li>{@code validate} and {@code prepare} run outside the gate, so slow work (password hashing) never holds it;
   *       a rejection from {@code validate} (null when valid) is counted and returned;</li>
   *   <li>{@code decide} runs inside the source's gate, serialised against every other decision for that source on
   *       every instance, after the lock is checked again. So no request can create an account after a concurrent
   *       rejection has locked the source. A {@link RegistrationResult.Rejected} decision is counted and its
   *       transaction rolled back.</li>
   * </ol>
   * Rejections are counted in their own transactions, so they persist even when the gate rolls back.
   */
  public <P> RegistrationResult attempt(String sourceIp, Supplier<RegistrationResult.Rejected> validate,
      Supplier<P> prepare, Function<P, RegistrationResult> decide) {
    if (isLocked(sourceIp)) return new RegistrationResult.Locked();
    var rejected = validate.get();
    if (rejected != null) {
      // Counted inside the gate, so it is ordered against every other decision for this source.
      return serialised(sourceIp, () -> {
        recordRejection(sourceIp);
        return rejected;
      });
    }
    P prepared = prepare.get();
    try {
      return serialised(sourceIp, () -> {
        if (isLocked(sourceIp)) return new RegistrationResult.Locked();
        var result = decide.apply(prepared);
        if (!(result instanceof RegistrationResult.Rejected rejection)) return result;
        recordRejection(sourceIp);
        throw new RolledBack(rejection);
      });
    } catch (RolledBack rolledBack) {
      return rolledBack.rejection;
    }
  }

  private <T> T serialised(String sourceIp, Supplier<T> work) {
    return buckets.serialised(GATE_PREFIX + sourceIp, gateRetention, work);
  }

  /** True when the source IP is currently locked. Never creates state for unknown sources. */
  boolean isLocked(String sourceIp) {
    String key = LOCK_PREFIX + sourceIp;
    return buckets.exists(key) && buckets.bucket(key, lockConfiguration).getAvailableTokens() == 0;
  }

  /** Counts one rejected server-processed submission, locking the source when the threshold is reached. */
  void recordRejection(String sourceIp) {
    var probe = buckets.bucket(COUNTER_PREFIX + sourceIp, counterConfiguration).tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed() || probe.getRemainingTokens() == 0) {
      buckets.bucket(LOCK_PREFIX + sourceIp, lockConfiguration).tryConsume(1);
    }
  }

  /** Carries a rejected decision out of the gate so its transaction rolls back. */
  private static final class RolledBack extends RuntimeException {
    private final transient RegistrationResult.Rejected rejection;

    RolledBack(RegistrationResult.Rejected rejection) {
      super(null, null, false, false);
      this.rejection = rejection;
    }
  }
}
