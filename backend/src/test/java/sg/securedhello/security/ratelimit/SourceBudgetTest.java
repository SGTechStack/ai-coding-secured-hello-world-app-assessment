package sg.securedhello.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.security.login.SignIn;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxBudgetTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

/**
 * The per-source rows of the budget table on {@code application.yml}'s values (ADR-010), and the body cap that sits
 * between the source filter and the login converter (Std §5:494). Each test sends from its own source address.
 */
class SourceBudgetTest extends CtxBudgetTest {

    private static final String SPA = "http://localhost:5173";

    @Autowired
    private RateLimitProperties budgets;

    @Autowired
    private RequestBodyProperties requestBody;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    private Accounts accounts;
    private SessionRows sessions;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        sessions = new SessionRows(jdbc);
    }

    private static MockHttpServletRequestBuilder login(String source, CsrfSession session, String body) {
        return post(SignIn.LOGIN_PATH).with(session.inHeader()).with(request -> {
            request.setRemoteAddr(source);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int failedLoginAttempts(Accounts.Account account) {
        return jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
                account.id());
    }

    @Test
    @Proves("T-RL-001")
    void pastTheSourceBurstALoginIs429BeforeAuthenticationAndTheRefillFollowsTheClock() throws Exception {
        long burst = budgets.login().source().burst();
        String source = nextSource();
        Accounts.Account target = accounts.user();
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        String wrong = SignedIn.credentials(target.username(), "not-the-password-at-all");
        for (long i = 0; i < burst; i++) {
            // Each username is fresh, so only the source axis can refuse.
            mockMvc.perform(login(source, session, SignedIn.credentials(Accounts.unknownUsername(), "x")))
                    .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        }
        int attemptsBefore = failedLoginAttempts(target);
        clearInvocations(passwordEncoder, userAccounts);

        mockMvc.perform(login(source, session, wrong))
                .andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userAccounts, never()).findByUsername(anyString());
        assertThat(failedLoginAttempts(target)).isEqualTo(attemptsBefore);

        clock.advance(budgets.login().source().refillPeriod());
        mockMvc.perform(login(source, session, wrong)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(login(source, session, wrong)).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    void pastTheCsrfBurstATokenFetchIs429AndCreatesNoSession() throws Exception {
        long burst = budgets.csrf().source().burst();
        String source = nextSource();
        for (long i = 0; i < burst; i++) {
            CsrfSession.bootstrap(mockMvc, source);
        }
        Set<String> before = sessions.ids();

        MvcResult refused = mockMvc.perform(get("/api/csrf").with(request -> {
            request.setRemoteAddr(source);
            return request;
        })).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andReturn();

        assertThat(sessions.ids()).isEqualTo(before);
        assertThat(refused.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        clock.advance(Duration.ofSeconds(2));
        CsrfSession.bootstrap(mockMvc, source);
    }

    @Test
    void anEarlyRefusalCarriesTheSpasCorsHeadersSoTheSpaCanReadIt() throws Exception {
        String source = nextSource();
        for (long i = 0; i < budgets.csrf().source().burst(); i++) {
            CsrfSession.bootstrap(mockMvc, source);
        }

        mockMvc.perform(get("/api/csrf").header(HttpHeaders.ORIGIN, SPA).with(request -> {
            request.setRemoteAddr(source);
            return request;
        })).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mockMvc.perform(get("/api/csrf").header(HttpHeaders.ORIGIN, "https://evil.example").with(request -> {
            request.setRemoteAddr(source);
            return request;
        })).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @Proves("T-RL-019")
    void aBodyOverTheCapIsRefusedBeforeTheConverterReadsIt() throws Exception {
        long cap = requestBody.maxBodyBytes();
        String source = nextSource();
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        clearInvocations(passwordEncoder, userAccounts);

        mockMvc.perform(login(source, session, padded(cap)))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        verify(passwordEncoder).matches(any(), any());
        clearInvocations(passwordEncoder, userAccounts);

        mockMvc.perform(login(source, session, padded(cap + 1)))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
        verify(passwordEncoder, never()).matches(any(), any());
        verify(userAccounts, never()).findByUsername(anyString());
    }

    @Test
    @Proves("T-RL-015")
    void anOversizedRequestOnABudgetedRouteStillSpendsItsSourceToken() throws Exception {
        long burst = budgets.login().source().burst();
        String source = nextSource();
        CsrfSession session = CsrfSession.bootstrap(mockMvc, source);
        String oversized = padded(requestBody.maxBodyBytes() + 1);
        for (long i = 0; i < burst; i++) {
            mockMvc.perform(login(source, session, oversized)).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        }

        mockMvc.perform(login(source, session, oversized)).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    @Proves("T-RL-012")
    void theSourceFilterPrecedesTheBodyCapWhichPrecedesTheLoginConverter() {
        List<String> order = filterChainProxy.getFilterChains().getFirst().getFilters().stream()
                .map(Filter::getClass).map(Class::getSimpleName).toList();

        int source = order.indexOf(SourceRateLimitFilter.class.getSimpleName());
        int contextHolder = order.indexOf(SecurityContextHolderFilter.class.getSimpleName());
        int bodyCap = order.indexOf(RequestBodyCapFilter.class.getSimpleName());
        int login = order.indexOf("JsonLoginFilter");
        assertThat(source).isNotNegative().isLessThan(contextHolder).isLessThan(bodyCap);
        assertThat(bodyCap).isLessThan(login);
    }

    @Test
    void anUnbudgetedRouteIsNotThrottledOnRate() throws Exception {
        String source = nextSource();
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/actuator/health").with(request -> {
                request.setRemoteAddr(source);
                return request;
            })).andExpect(status().isOk());
        }
    }

    @Test
    void pastTheProfilePasswordBurstAChangeIs429BeforeTheCurrentPasswordIsChecked() throws Exception {
        long burst = budgets.profilePassword().source().burst();
        String source = nextSource();
        Accounts.Account account = accounts.user();
        MvcResult login = SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc, source), account.username(),
                account.password()).andExpect(status().isOk()).andReturn();
        CsrfSession session = SignedIn.refreshed(mockMvc, login.getResponse().getCookie("SESSION"));
        MockHttpServletRequestBuilder wrongCurrent = patch("/api/profile/password").with(session.inHeader())
                .with(request -> {
                    request.setRemoteAddr(source);
                    return request;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"not the password\",\"newPassword\":\"velvet harbour quietly hums\"}");
        for (long i = 0; i < burst; i++) {
            mockMvc.perform(wrongCurrent).andExpect(problem(ErrorCode.VALIDATION_FAILED));
        }
        clearInvocations(passwordEncoder);

        mockMvc.perform(wrongCurrent).andExpect(problem(ErrorCode.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "6"));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    /** A JSON login body of exactly {@code bytes} bytes, for an unknown username. */
    private static String padded(long bytes) {
        String prefix = "{\"username\":\"" + Accounts.unknownUsername() + "\",\"password\":\"x\",\"pad\":\"";
        String suffix = "\"}";
        return prefix + "a".repeat((int) (bytes - prefix.length() - suffix.length())) + suffix;
    }
}
