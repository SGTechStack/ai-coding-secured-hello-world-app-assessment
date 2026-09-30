package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SignedIn;

/**
 * The limiter's state is observable: both bucket maps and the per-source lockout-cardinality sets record statistics
 * and publish them as {@code cache.*} meters (IM8 lm-16). Counts are global to the shared context, so the test asserts
 * a delta across requests that exercise each map.
 */
class LimiterMetricsTest extends CtxDefaultTest {

    @Autowired
    private MeterRegistry registry;

    /** The summed {@code cache.gets} of every meter for {@code cache}, hits and misses. */
    private double gets(String cache) {
        return registry.find("cache.gets").tag("cache", cache).functionCounters().stream()
                .mapToDouble(FunctionCounter::count).sum();
    }

    @Test
    @Proves("T-OBS-001")
    void bothBucketMapsAndTheCardinalitySetsRecordTheirGets() throws Exception {
        assertThat(registry.find("cache.gets").tag("cache", AuthRateLimiter.BUCKETS_CACHE).tag("rate_limit",
                RateLimit.LOGIN_SOURCE.name()).functionCounters()).as("a family's meters").isNotEmpty();
        double buckets = gets(AuthRateLimiter.BUCKETS_CACHE);
        double misses = gets(AuthRateLimiter.MISSES_CACHE);
        double cardinality = gets(LockoutCardinality.CACHE);

        // A sign-in draws on the login buckets and asks the cardinality set whether its source is full.
        SignedIn.loginFrom(mockMvc, "203.0.113.61", Accounts.unknownUsername(), Accounts.WRONG_PASSWORD);
        // A session cookie the store does not know is a miss, charged to the source's miss bucket.
        mockMvc.perform(get("/api/hello").cookie(new Cookie("SESSION", Base64.getEncoder().encodeToString(
                UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)))).with(request -> {
                    request.setRemoteAddr("203.0.113.61");
                    return request;
                }));

        assertThat(gets(AuthRateLimiter.BUCKETS_CACHE)).as("bucket gets").isGreaterThan(buckets);
        assertThat(gets(AuthRateLimiter.MISSES_CACHE)).as("session-miss bucket gets").isGreaterThan(misses);
        assertThat(gets(LockoutCardinality.CACHE)).as("cardinality set gets").isGreaterThan(cardinality);
    }
}
