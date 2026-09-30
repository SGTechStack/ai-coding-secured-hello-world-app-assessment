package sg.securedhello.security.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;

import sg.securedhello.security.ratelimit.RateLimit.Axis;
import sg.securedhello.security.ratelimit.RateLimitProperties.Budget;
import sg.securedhello.security.ratelimit.RateLimitProperties.SessionMiss;
import sg.securedhello.security.source.SourceKey;

/**
 * The one component for every per-source and per-submitted-value budget (ADR-010), and for the per-source
 * session-miss budget (ADR-017). Each {@link RateLimit} row gets its own family of Bucket4j buckets, one per key, held
 * in a bounded Caffeine cache. Refill reads the injected {@code Clock} through a {@link TimeMeter}, and expiry through
 * a {@link Ticker} (ADR-066; T-RL-018).
 *
 * <p>A refusal is a {@link Refusal}: the caller answers 429 {@code TOO_MANY_REQUESTS} with its integer
 * {@code Retry-After}, and nothing else happens. No refusal touches an account, a success refunds nothing, and nothing
 * here waits (ADR-010; ADR-014).
 *
 * <p>State is in memory in one instance and is lost on restart. Distributed limiting is not supported (REJ-018;
 * R-RL-005; R-RL-006). Each family holds at most {@value #MAXIMUM_KEYS} keys. Evicting a bucket hands its key a full
 * one, which is not a bypass of the audit bound: that key must spend a whole budget again before it is refused.
 *
 * <h2>Adding a budget row</h2>
 * A route ticket adds its row from the spec's budget table in three small steps:
 * <ol>
 *   <li>bind it: a {@link RateLimitProperties.Route} component named after the route, and its
 *       {@code app.security.rate-limit.<route>.<axis>.burst} and {@code .refill-period} values in
 *       {@code application.yml};</li>
 *   <li>register it: one {@link RateLimit} constant naming the route, method, path, axis and that component. Startup
 *       fails if the constant's budget is not configured;</li>
 *   <li>a {@link Axis#SOURCE} row needs nothing more: {@code SourceRateLimitFilter} meters every request its route
 *       matches. A submitted-value row is checked at its call site with {@link #tryConsume}, straight after the value
 *       is read and before any lookup, and refused with {@link TooManyRequests}.</li>
 * </ol>
 * Then add the row to the {@code RateLimitBindingTest} table (T-RL-010).
 */
public final class AuthRateLimiter implements MeterBinder {

    /** The bucket maps' cache names on {@code cache.*} meters; a budget row's family is tagged with its row. */
    public static final String BUCKETS_CACHE = "rate-limit-buckets";
    public static final String MISSES_CACHE = "rate-limit-session-misses";

    /** Keys held per row, and per-source miss buckets held; a bound on memory, not on any client. */
    static final long MAXIMUM_KEYS = 10_000;

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final Map<RateLimit, Family> families = new EnumMap<>(RateLimit.class);
    private final SessionMiss missBudget;
    private final Cache<String, Bucket> misses;
    private final TimeMeter timeMeter;

    /**
     * @throws IllegalStateException if a {@link RateLimit} row has no budget in {@code properties}
     */
    public AuthRateLimiter(RateLimitProperties properties, TimeMeter timeMeter, Ticker ticker) {
        this.timeMeter = timeMeter;
        for (RateLimit limit : RateLimit.values()) {
            Budget budget = limit.budget(properties);
            if (budget == null) {
                throw new IllegalStateException(limit.property() + ".burst and .refill-period are not set");
            }
            families.put(limit, new Family(budget, cache(ticker, budget.refillPeriod().multipliedBy(budget.burst()))));
        }
        this.missBudget = properties.sessionMiss();
        this.misses = cache(ticker, missBudget.window());
    }

    /**
     * Takes one token from {@code value}'s bucket in {@code limit}'s family.
     *
     * @param value the source key's text for a {@link Axis#SOURCE} row, otherwise the submitted value as sent
     * @return empty if the request may proceed, otherwise the refusal
     */
    public Optional<Refusal> tryConsume(RateLimit limit, String value) {
        Family family = families.get(limit);
        String key = limit.axis() == Axis.SOURCE ? value : digest(value);
        Metered metered = family.buckets().get(key, unused -> new Metered(greedy(family.budget())));
        ConsumptionProbe probe = metered.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            metered.refusing().set(false);
            return Optional.empty();
        }
        return Optional.of(new Refusal(seconds(probe.getNanosToWaitForRefill()),
                !metered.refusing().getAndSet(true)));
    }

    /**
     * Whether {@code source} has spent its session-miss budget, checked before the request can cost a lookup. Takes
     * no token: a miss is paid for by {@link #recordMiss} once it has happened.
     */
    public Optional<Refusal> missRefusal(SourceKey source) {
        Bucket bucket = misses.getIfPresent(source.value());
        if (bucket == null) {
            return Optional.empty();
        }
        EstimationProbe probe = bucket.estimateAbilityToConsume(1);
        return probe.canBeConsumed() ? Optional.empty()
                : Optional.of(new Refusal(seconds(probe.getNanosToWaitForRefill()), false));
    }

    /** Charges {@code source} one session-store lookup that did not resolve. */
    public void recordMiss(SourceKey source) {
        misses.get(source.value(), unused -> intervally(missBudget)).tryConsume(1);
    }

    /** {@code budget.burst()} at once, then one token back per refill period, greedily. */
    private Bucket greedy(Budget budget) {
        return Bucket.builder().withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(budget.burst()).refillGreedy(1, budget.refillPeriod()))
                .build();
    }

    /** The whole capacity back once per window, counted from the bucket's creation (T-RL-022). */
    private Bucket intervally(SessionMiss budget) {
        return Bucket.builder().withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(budget.capacity()).refillIntervally(budget.capacity(),
                        budget.window()))
                .build();
    }

    /**
     * A cache whose entries expire {@code idle} after their last use, the time an untouched bucket takes to refill
     * completely, so an expired entry and a fresh one are the same bucket.
     */
    private static <V> Cache<String, V> cache(Ticker ticker, Duration idle) {
        return Caffeine.newBuilder().ticker(ticker).expireAfterAccess(idle).maximumSize(MAXIMUM_KEYS).recordStats()
                .build();
    }

    /** Publishes each bucket map's statistics as {@code cache.*} meters (IM8 lm-16; T-OBS-001). */
    @Override
    public void bindTo(MeterRegistry registry) {
        families.forEach((limit, family) -> CaffeineCacheMetrics.monitor(registry, family.buckets(), BUCKETS_CACHE,
                "rate_limit", limit.name()));
        CaffeineCacheMetrics.monitor(registry, misses, MISSES_CACHE);
    }

    /** {@code Retry-After}: whole seconds, rounded up, never 0. */
    static long seconds(long nanos) {
        return Math.max(1, Math.ceilDiv(nanos, NANOS_PER_SECOND));
    }

    /** A fixed-size key for a submitted value, which can be as long as the body cap allows. */
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }

    /**
     * A refused request.
     *
     * @param retryAfterSeconds the integer {@code Retry-After}: when the next token is due, at least 1
     * @param breach            whether this is the first refusal since the key was last admitted, the transition a
     *                          transition-keyed audit row is written on
     */
    public record Refusal(long retryAfterSeconds, boolean breach) {
    }

    private record Family(Budget budget, Cache<String, Metered> buckets) {
    }

    /** A bucket, and whether its key's last attempt was refused. */
    private record Metered(Bucket bucket, AtomicBoolean refusing) {

        Metered(Bucket bucket) {
            this(bucket, new AtomicBoolean());
        }
    }
}
