package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;

/**
 * A request the limiter short-circuits with 429 is still an {@code http.server.requests} observation, with
 * {@code uri=UNKNOWN} (no handler ran) and {@code status=429}, so throttling shows per status; the per-route
 * {@code uri} tag limit is not reached, so no route's series is being dropped (IM8 lm-16; R-OBS-004). On the real
 * budgets ({@code ctx-budget}): the token bootstrap's source budget is the one spent.
 */
class ThrottleMeterShapeTest extends CtxBudgetTest {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private RateLimitProperties budgets;

    @Autowired
    private Environment environment;

    private double throttled() {
        return registry.find("http.server.requests").tag("uri", "UNKNOWN").tag("status", "429").timers().stream()
                .mapToLong(Timer::count).sum();
    }

    @Test
    @Proves("T-OBS-014")
    void aLimiterRefusalIsRecordedWithUriUnknownAndStatus429AndTheUriTagLimitIsUnreached() throws Exception {
        String source = nextSource();
        double before = throttled();

        int status = 0;
        for (int i = 0; i <= budgets.csrf().source().burst() && status != 429; i++) {
            status = mockMvc.perform(get("/api/csrf").with(request -> {
                request.setRemoteAddr(source);
                return request;
            })).andReturn().getResponse().getStatus();
        }

        assertThat(status).as("the source budget ran out").isEqualTo(429);
        assertThat(throttled()).isEqualTo(before + 1);
        int maxUriTags = environment.getProperty("management.metrics.web.server.max-uri-tags", Integer.class, 100);
        Set<String> uris = registry.find("http.server.requests").meters().stream()
                .map(meter -> meter.getId().getTag("uri")).collect(Collectors.toSet());
        assertThat(maxUriTags).isEqualTo(100);
        assertThat(uris).hasSizeLessThan(maxUriTags);
        assertThat(registry.find("http.server.requests").meters()).extracting(Meter::getId).isNotEmpty();
    }
}
