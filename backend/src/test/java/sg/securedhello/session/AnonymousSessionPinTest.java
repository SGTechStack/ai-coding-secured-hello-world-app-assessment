package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.session.SessionRepository;
import org.springframework.web.filter.CorsFilter;

import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;

/**
 * An anonymous session expires at its creation plus the idle window W, however often it is used (REJ-091;
 * R-SES-009). Spring Session stamps its own times, so the tests age the stored creation time rather than advancing
 * the shared clock (ADR-066).
 */
class AnonymousSessionPinTest extends CtxDefaultTest {

    private static final Duration W = Duration.ofMinutes(15);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private SessionRepository<?> sessionRepository;

    private SessionRows sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionRows(jdbc);
    }

    private Cookie bootstrap() throws Exception {
        return CsrfSession.bootstrap(mockMvc).cookie();
    }

    private void ping(Cookie cookie) throws Exception {
        mockMvc.perform(get("/actuator/health").cookie(cookie));
    }

    private long creationPlusW(String id) {
        return ((Number) sessions.row(id).get("CREATION_TIME")).longValue() + W.toMillis();
    }

    @Test
    @Proves("T-SES-032")
    void pingsNeverMoveAnAnonymousSessionsExpiryPastCreationPlusW() throws Exception {
        Cookie cookie = bootstrap();
        String id = SessionRows.idOf(cookie.getValue());

        for (int i = 1; i <= 3; i++) {
            sessions.ageCreation(id, Duration.ofMinutes(4).toMillis());
            ping(cookie);
            assertThat(sessions.exists(id)).as("still live after ping %d", i).isTrue();
            assertThat(((Number) sessions.row(id).get("EXPIRY_TIME")).longValue())
                    .as("expiry after ping %d", i).isLessThanOrEqualTo(creationPlusW(id));
        }

        sessions.ageCreation(id, Duration.ofMinutes(4).toMillis());
        ping(cookie);
        assertThat(sessions.exists(id)).as("a ping after W ends the session").isFalse();
    }

    @Test
    @Proves("T-SES-024")
    void noStoredSessionEverCarriesANonPositiveInterval() throws Exception {
        Cookie cookie = bootstrap();
        String id = SessionRows.idOf(cookie.getValue());
        sessions.ageCreation(id, W.toMillis() + 1);

        ping(cookie);

        assertThat(sessions.exists(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE MAX_INACTIVE_INTERVAL <= 0",
                Integer.class)).isZero();
    }

    @Test
    @Proves("T-SES-033")
    void thePinsWindowIsTheRepositorysResolvedInterval() {
        AbsoluteLifetimeFilter filter = (AbsoluteLifetimeFilter) filters().stream()
                .filter(AbsoluteLifetimeFilter.class::isInstance).findFirst().orElseThrow();

        assertThat(filter.window()).isEqualTo(sessionRepository.createSession().getMaxInactiveInterval())
                .isEqualTo(W);
    }

    /** ADR-038's order; the source rate limiter (ticket 11) is to go ahead of all three. */
    @Test
    void theLifetimeFilterSitsBetweenTheSecurityContextAndCsrfFilters() {
        List<Class<?>> order = filters().stream().<Class<?>>map(Object::getClass).toList();

        assertThat(order.indexOf(SecurityContextHolderFilter.class)).isNotNegative()
                .isLessThan(order.indexOf(AbsoluteLifetimeFilter.class));
        assertThat(order.indexOf(AbsoluteLifetimeFilter.class)).isLessThan(order.indexOf(CsrfFilter.class));
    }

    /** Its 401 is written after the CORS and security-header filters have wrapped the response (T-SES-037). */
    @Test
    @Proves("T-SES-037")
    void theLifetimeFilterSitsAfterTheCorsAndHeaderWriterFilters() {
        List<Filter> filters = filters();
        int lifetime = filters.indexOf(filters.stream().filter(AbsoluteLifetimeFilter.class::isInstance).findFirst()
                .orElseThrow());

        for (Class<?> earlier : List.of(CorsFilter.class, HeaderWriterFilter.class)) {
            assertThat(filters.stream().filter(earlier::isInstance).findFirst().map(filters::indexOf).orElseThrow())
                    .as(earlier.getSimpleName()).isLessThan(lifetime);
        }
    }

    private List<Filter> filters() {
        return filterChainProxy.getFilterChains().getFirst().getFilters();
    }
}
