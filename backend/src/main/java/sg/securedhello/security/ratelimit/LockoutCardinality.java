package sg.securedhello.security.ratelimit;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import com.github.benmanes.caffeine.cache.Policy.FixedExpiration;
import com.github.benmanes.caffeine.cache.Ticker;

import sg.securedhello.security.ratelimit.AuthRateLimiter.Refusal;
import sg.securedhello.security.source.SourceKey;

/**
 * The lockout-cardinality axis (ADR-015): each source key keeps the set of accounts it has driven into a timed
 * lockout, and once the set holds {@code k}, sign-ins from that source for any other username are refused with 429.
 * It is the only width bound on the mass-disable the NIST cap makes possible (ADR-013): one source can carry about
 * {@code k} disable chains at once, rather than 36 an hour.
 *
 * <ul>
 *   <li><b>Recorded at the lockout transition</b>, by the lockout listener, never at the attempt: a hundred people
 *       behind one NAT attempt a hundred accounts on an ordinary morning, but lock very few (T-RL-007).</li>
 *   <li><b>Checked in the login converter</b>, beside the per-username bucket and before anything looks the account
 *       up. A full set refuses non-members only; sign-ins for its members proceed (T-RL-030).</li>
 *   <li><b>Expiry runs from first insertion.</b> A member is added with {@code putIfAbsent}, so re-locking it writes
 *       nothing and does not extend its hour; with {@code expireAfterWrite} reset on every write, an attacker re-locking
 *       its own members would keep the set full for the whole of each chain (T-RL-031).</li>
 * </ul>
 *
 * <p>State is in memory, per instance, and times come from the {@code Clock} through {@link Ticker}. A source's set is
 * dropped once it has been idle for a window, by which time every member in it has expired too. Evicting a member
 * early would bypass the control rather than save memory, so a set's size bound is far above anything admission lets
 * it reach.
 */
public final class LockoutCardinality implements MeterBinder {

    /** The per-source sets' cache name on {@code cache.*} meters. */
    public static final String CACHE = "lockout-cardinality";

    /** Members held per source: admission stops non-members at {@code k}, so only races go past it. */
    static final long MEMBERS_PER_SOURCE = 1_000;

    private final int k;
    private final Duration window;
    private final Ticker ticker;
    private final Cache<String, Cache<String, Boolean>> sources;

    public LockoutCardinality(LockoutCardinalityProperties properties, Ticker ticker) {
        this.k = properties.k();
        this.window = properties.window();
        this.ticker = ticker;
        this.sources = Caffeine.newBuilder().ticker(ticker).expireAfterAccess(window)
                .maximumSize(AuthRateLimiter.MAXIMUM_KEYS).recordStats().build();
    }

    /** Publishes the per-source set map's statistics as {@code cache.*} meters (IM8 lm-16; T-OBS-001). */
    @Override
    public void bindTo(MeterRegistry registry) {
        CaffeineCacheMetrics.monitor(registry, sources, CACHE);
    }

    /**
     * Whether a sign-in for {@code username} from {@code source} is refused: the source's set is full and
     * {@code username} is not in it. Takes nothing and writes nothing.
     *
     * @return empty if the sign-in may proceed, otherwise the refusal, whose {@code Retry-After} is when the set's
     *         oldest member expires
     */
    public Optional<Refusal> refusal(SourceKey source, String username) {
        Cache<String, Boolean> members = sources.getIfPresent(source.value());
        if (members == null) {
            return Optional.empty();
        }
        members.cleanUp();
        if (members.getIfPresent(username) != null || members.estimatedSize() < k) {
            return Optional.empty();
        }
        return Optional.of(new Refusal(retryAfterSeconds(members), false));
    }

    /** Records that {@code source} drove {@code username} into lockout; a member already in the set is not touched. */
    public void recordLockout(SourceKey source, String username) {
        sources.get(source.value(), unused -> Caffeine.newBuilder().ticker(ticker).expireAfterWrite(window)
                .maximumSize(MEMBERS_PER_SOURCE).<String, Boolean>build())
                .asMap().putIfAbsent(username, Boolean.TRUE);
    }

    /** Whole seconds until the oldest member leaves the set and makes room. */
    private long retryAfterSeconds(Cache<String, Boolean> members) {
        FixedExpiration<String, Boolean> expiry = members.policy().expireAfterWrite().orElseThrow();
        Duration oldest = members.asMap().keySet().stream()
                .map(member -> expiry.ageOf(member).orElse(Duration.ZERO))
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
        return AuthRateLimiter.seconds(window.minus(oldest).toNanos());
    }
}
