package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import sg.securedhello.error.ProblemDetailWriter;
import sg.securedhello.security.ratelimit.RateLimitProperties.Budget;
import sg.securedhello.testsupport.CtxNondevTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.time.ClockConfig;

import tools.jackson.databind.json.JsonMapper;

/**
 * The budget table and the body cap as production binds them (ctx-nondev): each row's burst and refill period equal
 * the spec's table, and the limiter's buckets are built from those properties, so changing one changes the behaviour.
 *
 * <p>T-RL-010 is parameterised over every route in the spec's table; the rows here are the ones built so far, and each
 * route ticket adds its own. The row stays on the pending ledger until the table is complete.
 */
class RateLimitBindingTest extends CtxNondevTest {

    /** The spec's budget table, for the routes built so far. */
    private static final Map<RateLimit, Budget> TABLE = Map.ofEntries(
            Map.entry(RateLimit.LOGIN_SOURCE, new Budget(60, Duration.ofSeconds(1))),
            Map.entry(RateLimit.LOGIN_USERNAME, new Budget(10, Duration.ofSeconds(6))),
            Map.entry(RateLimit.CSRF_SOURCE, new Budget(30, Duration.ofSeconds(2))),
            Map.entry(RateLimit.PROFILE_PASSWORD_SOURCE, new Budget(10, Duration.ofSeconds(6))),
            Map.entry(RateLimit.REGISTER_SOURCE, new Budget(5, Duration.ofSeconds(12))),
            Map.entry(RateLimit.REGISTER_ACTIVATE_SOURCE, new Budget(10, Duration.ofSeconds(6))),
            Map.entry(RateLimit.PASSWORD_RESET_REQUEST_SOURCE, new Budget(5, Duration.ofSeconds(12))),
            Map.entry(RateLimit.PASSWORD_RESET_REQUEST_IDENTIFIER, new Budget(3, Duration.ofMinutes(20))),
            Map.entry(RateLimit.PASSWORD_RESET_CONFIRM_SOURCE, new Budget(10, Duration.ofSeconds(6))),
            Map.entry(RateLimit.MFA_TOTP_ENROLMENT_SOURCE, new Budget(10, Duration.ofSeconds(6))),
            Map.entry(RateLimit.MFA_TOTP_ENROLMENT_CONFIRMATION_SOURCE, new Budget(20, Duration.ofSeconds(3))));

    static Stream<Arguments> rows() {
        return Stream.of(RateLimit.values()).map(row -> Arguments.of(row, TABLE.get(row)));
    }

    @ParameterizedTest
    @MethodSource("rows")
    void everyBudgetRowBindsTheSpecsValues(RateLimit row, Budget tabled) {
        assertThat(tabled).as("%s is in the spec's table", row).isNotNull();
        assertThat(productionProperty(row.property(), Budget.class)).isEqualTo(tabled);
    }

    @Test
    void theSessionMissBudgetBindsThreeHundredPerFifteenMinutes() {
        assertThat(productionProperty("app.security.rate-limit.session-miss.capacity", Long.class)).isEqualTo(300);
        assertThat(productionProperty("app.security.rate-limit.session-miss.window", Duration.class))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void theBucketsAreBuiltFromTheBoundPropertiesSoChangingOneChangesTheBehaviour() {
        productionContextRunner().withUserConfiguration(RateLimitConfig.class, ClockConfig.class).run(context -> {
            assertThat(admitted(context.getBean(AuthRateLimiter.class))).isEqualTo(60);
        });
        productionContextRunner().withUserConfiguration(RateLimitConfig.class, ClockConfig.class)
                .withPropertyValues("app.security.rate-limit.login.source.burst=7").run(context -> {
                    assertThat(admitted(context.getBean(AuthRateLimiter.class))).isEqualTo(7);
                });
    }

    @Test
    @Proves("T-RL-011")
    void theBodyCapIs16384BytesAndIsTheLimitTheFilterEnforces() throws Exception {
        long cap = productionProperty("app.security.request.max-body-bytes", Long.class);
        assertThat(cap).isEqualTo(16_384);
        RequestBodyCapFilter filter = new RequestBodyCapFilter(cap,
                new ProblemDetailWriter(JsonMapper.builder().build(), Tracer.NOOP));

        assertThat(status(filter, (int) cap)).isEqualTo(HttpStatus.OK.value());
        assertThat(status(filter, (int) cap + 1)).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private static int admitted(AuthRateLimiter limiter) {
        int admitted = 0;
        while (limiter.tryConsume(RateLimit.LOGIN_SOURCE, "4:c0000201").isEmpty()) {
            admitted++;
        }
        return admitted;
    }

    /** The status after {@code filter} and a chain that reads the whole body, for a body of {@code bytes}. */
    private static int status(RequestBodyCapFilter filter, int bytes) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContent(new byte[bytes]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());
        return response.getStatus();
    }
}
