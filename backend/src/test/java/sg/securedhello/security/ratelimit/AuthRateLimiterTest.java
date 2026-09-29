package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.securedhello.security.ratelimit.AuthRateLimiter.Refusal;
import sg.securedhello.security.ratelimit.RateLimitProperties.Budget;
import sg.securedhello.security.ratelimit.RateLimitProperties.Route;
import sg.securedhello.security.ratelimit.RateLimitProperties.SessionMiss;
import sg.securedhello.security.source.SourceKey;
import sg.securedhello.testsupport.MutableClock;
import sg.securedhello.time.ClockTimes;

/**
 * The limiter's buckets on a clock the test moves (ADR-010; ADR-017; ADR-066): burst, refusal with an integer
 * {@code Retry-After}, refill on the clock, the transition flag, and that each row is built from its own property.
 */
class AuthRateLimiterTest {

    private static final String SOURCE = "4:c6120001";

    private final MutableClock clock = MutableClock.startingNow();

    private AuthRateLimiter limiter(long loginSourceBurst) {
        RateLimitProperties properties = new RateLimitProperties(
                new Route(new Budget(loginSourceBurst, Duration.ofSeconds(1)), new Budget(10, Duration.ofSeconds(6)),
                        null),
                new Route(new Budget(30, Duration.ofSeconds(2)), null, null),
                new SessionMiss(300, Duration.ofMinutes(15)),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(5, Duration.ofSeconds(12)), null, null),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(5, Duration.ofSeconds(12)), null, new Budget(3, Duration.ofMinutes(20))),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(20, Duration.ofSeconds(3)), null, null),
                new Route(new Budget(20, Duration.ofSeconds(3)), null, null));
        return new AuthRateLimiter(properties, ClockTimes.timeMeter(clock), ClockTimes.ticker(clock));
    }

    private static long admitted(AuthRateLimiter limiter, RateLimit limit, String key, int attempts) {
        long admitted = 0;
        for (int i = 0; i < attempts; i++) {
            if (limiter.tryConsume(limit, key).isEmpty()) {
                admitted++;
            }
        }
        return admitted;
    }

    @Test
    void theBurstIsAdmittedAndTheNextIsRefusedWithAnIntegerRetryAfterThenRefillFollowsTheClock() {
        AuthRateLimiter limiter = limiter(60);

        assertThat(admitted(limiter, RateLimit.LOGIN_SOURCE, SOURCE, 60)).isEqualTo(60);
        assertThat(limiter.tryConsume(RateLimit.LOGIN_SOURCE, SOURCE)).contains(new Refusal(1, true));

        clock.advance(Duration.ofMillis(999));
        assertThat(limiter.tryConsume(RateLimit.LOGIN_SOURCE, SOURCE)).isPresent();
        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryConsume(RateLimit.LOGIN_SOURCE, SOURCE)).isEmpty();
        assertThat(limiter.tryConsume(RateLimit.LOGIN_SOURCE, SOURCE)).isPresent();
    }

    @Test
    void changingTheBoundBurstChangesWhereRefusalStarts() {
        AuthRateLimiter limiter = limiter(3);

        assertThat(admitted(limiter, RateLimit.LOGIN_SOURCE, SOURCE, 10)).isEqualTo(3);
    }

    @Test
    void retryAfterIsWholeSecondsRoundedUpAndNeverZero() {
        AuthRateLimiter limiter = limiter(60);
        admitted(limiter, RateLimit.LOGIN_USERNAME, "alice", 10);

        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "alice")).map(Refusal::retryAfterSeconds).contains(6L);
        clock.advance(Duration.ofMillis(5_001));
        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "alice")).map(Refusal::retryAfterSeconds).contains(1L);
        assertThat(AuthRateLimiter.seconds(0)).isEqualTo(1);
        assertThat(AuthRateLimiter.seconds(1_000_000_001)).isEqualTo(2);
    }

    @Test
    void onlyTheFirstRefusalAfterAnAdmissionIsABreach() {
        AuthRateLimiter limiter = limiter(60);
        admitted(limiter, RateLimit.LOGIN_USERNAME, "bob", 10);

        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "bob")).map(Refusal::breach).contains(true);
        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "bob")).map(Refusal::breach).contains(false);
        clock.advance(Duration.ofSeconds(6));
        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "bob")).isEmpty();
        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "bob")).map(Refusal::breach).contains(true);
    }

    @Test
    void rowsAndKeysHaveTheirOwnBuckets() {
        AuthRateLimiter limiter = limiter(60);
        admitted(limiter, RateLimit.LOGIN_USERNAME, "carol", 10);

        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "carol")).isPresent();
        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, "Carol")).isEmpty();
        assertThat(limiter.tryConsume(RateLimit.LOGIN_SOURCE, "carol")).isEmpty();
        assertThat(admitted(limiter, RateLimit.CSRF_SOURCE, SOURCE, 31)).isEqualTo(30);
    }

    @Test
    void aSubmittedValueOfAnyLengthIsKeyedByItsDigest() {
        AuthRateLimiter limiter = limiter(60);
        String long1 = "x".repeat(16_000) + "1";
        String long2 = "x".repeat(16_000) + "2";

        assertThat(admitted(limiter, RateLimit.LOGIN_USERNAME, long1, 11)).isEqualTo(10);
        assertThat(limiter.tryConsume(RateLimit.LOGIN_USERNAME, long2)).isEmpty();
    }

    @Test
    void theMissBudgetIsCheckedWithoutSpendingAndRestoredWholeOneWindowAfterTheFirstMiss() {
        AuthRateLimiter limiter = limiter(60);
        SourceKey source = new SourceKey(SOURCE);

        assertThat(limiter.missRefusal(source)).isEmpty();
        for (int i = 0; i < 300; i++) {
            assertThat(limiter.missRefusal(source)).as("miss %d", i).isEmpty();
            limiter.recordMiss(source);
        }
        Optional<Refusal> refused = limiter.missRefusal(source);
        assertThat(refused).map(Refusal::retryAfterSeconds).contains(Duration.ofMinutes(15).toSeconds());
        assertThat(limiter.missRefusal(new SourceKey("4:c6120002"))).isEmpty();

        clock.advance(Duration.ofMinutes(15).minusMillis(1));
        assertThat(limiter.missRefusal(source)).isPresent();
        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.missRefusal(source)).isEmpty();
    }

    @Test
    void aRowWithoutABudgetStopsStartupNamingItsProperty() {
        RateLimitProperties missingCsrf = new RateLimitProperties(
                new Route(new Budget(60, Duration.ofSeconds(1)), new Budget(10, Duration.ofSeconds(6)), null),
                null, new SessionMiss(300, Duration.ofMinutes(15)),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(5, Duration.ofSeconds(12)), null, null),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(5, Duration.ofSeconds(12)), null, new Budget(3, Duration.ofMinutes(20))),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(10, Duration.ofSeconds(6)), null, null),
                new Route(new Budget(20, Duration.ofSeconds(3)), null, null),
                new Route(new Budget(20, Duration.ofSeconds(3)), null, null));

        assertThatIllegalStateException()
                .isThrownBy(() -> new AuthRateLimiter(missingCsrf, ClockTimes.timeMeter(clock),
                        ClockTimes.ticker(clock)))
                .withMessage("app.security.rate-limit.csrf.source.burst and .refill-period are not set");
    }
}
