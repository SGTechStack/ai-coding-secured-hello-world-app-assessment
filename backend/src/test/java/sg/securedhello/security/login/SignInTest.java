package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.session.SessionLifetimeProperties;
import sg.securedhello.testsupport.Accounts;
import sg.securedhello.testsupport.Accounts.Account;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.SessionRows;
import sg.securedhello.testsupport.SignedIn;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Sign-in, the signed-in session, the greeting and sign-out, over the full context (ADR-033; ADR-036; ADR-038). */
class SignInTest extends CtxDefaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired
    private SessionLifetimeProperties lifetime;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> indexedSessions;

    private Accounts accounts;
    private SessionRows sessions;

    @BeforeEach
    void setUp() {
        accounts = new Accounts(jdbc, passwordEncoder);
        sessions = new SessionRows(jdbc);
    }

    private static String idOf(Cookie cookie) {
        return SessionRows.idOf(cookie.getValue());
    }

    private MvcResult login(CsrfSession session, String username, String password) throws Exception {
        return SignedIn.login(mockMvc, session, username, password).andReturn();
    }

    @Test
    @Proves("T-SES-005")
    void aJsonLoginSucceedsWithAJsonBodyAndRotatesTheSessionId() throws Exception {
        Account alice = accounts.user();
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);

        MvcResult result = SignedIn.login(mockMvc, anonymous, alice.username(), alice.password())
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.username").value(alice.username()))
                .andExpect(jsonPath("$.id").value(alice.id().toString()))
                .andReturn();

        Cookie rotated = result.getResponse().getCookie("SESSION");
        assertThat(rotated).isNotNull();
        assertThat(idOf(rotated)).isNotEqualTo(idOf(anonymous.cookie()));
        assertThat(sessions.exists(idOf(anonymous.cookie()))).as("the pre-login id is gone").isFalse();
        assertThat(sessions.exists(idOf(rotated))).isTrue();
    }

    @Test
    void helloReturnsTheExactGreetingAndProfileTheSelfRead() throws Exception {
        Account alice = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, alice);

        mockMvc.perform(get("/api/hello").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, " + alice.username()));
        mockMvc.perform(get("/api/profile").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(alice.username()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.factors.held").value(false))
                .andExpect(jsonPath("$.factors.required").value(false))
                .andExpect(jsonPath("$.factors.enrolled").value(false))
                .andExpect(jsonPath("$.factors.rebindRequired").value(false));
    }

    @Test
    void helloAndProfileRefuseAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/hello")).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(get("/api/profile")).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    @Proves({"T-AUTH-010", "T-AUTH-007"})
    void aFailedJsonLoginGetsThe401EnvelopeWithNoChallenge() throws Exception {
        Account alice = accounts.user();

        SignedIn.login(mockMvc, CsrfSession.bootstrap(mockMvc), alice.username(), Accounts.WRONG_PASSWORD)
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(header().doesNotExist("Location"));
    }

    /** Every password-axis failure gets one body; only {@code traceId} varies (ADR-033). */
    @Test
    void everyFailureCauseReturnsTheSameBodyApartFromTheTraceId() throws Exception {
        Account known = accounts.user();
        Account disabled = accounts.disabled();
        Account pending = accounts.notActivated();
        Account locked = accounts.user();
        jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?",
                java.sql.Timestamp.from(clock.instant().plus(Duration.ofHours(1))), locked.id());
        Account capped = accounts.user();
        jdbc.update("UPDATE users SET password_disabled_at = ? WHERE id = ?",
                java.sql.Timestamp.from(clock.instant()), capped.id());
        List<String[]> attempts = List.of(
                new String[] {Accounts.unknownUsername(), Accounts.PASSWORD},
                new String[] {known.username(), Accounts.WRONG_PASSWORD},
                new String[] {disabled.username(), Accounts.PASSWORD},
                new String[] {pending.username(), Accounts.PASSWORD},
                new String[] {locked.username(), Accounts.PASSWORD},
                new String[] {capped.username(), Accounts.PASSWORD});

        List<String> bodies = new java.util.ArrayList<>();
        for (String[] attempt : attempts) {
            MvcResult result = login(CsrfSession.bootstrap(mockMvc), attempt[0], attempt[1]);
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            ObjectNode body = (ObjectNode) JSON.readTree(result.getResponse().getContentAsString(
                    StandardCharsets.UTF_8));
            assertThat(body.get("traceId").asString()).isNotBlank();
            body.remove("traceId");
            bodies.add(body.toString());
        }
        assertThat(bodies).as("bodies without traceId").containsOnly(bodies.getFirst());
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    @Proves("T-AUTH-004")
    void aMalformedLoginBodyGetsTheValidationEnvelopeNeverA500(String contentType, String body) throws Exception {
        CsrfSession session = CsrfSession.bootstrap(mockMvc);

        mockMvc.perform(post("/api/login").with(session.inHeader()).contentType(contentType).content(body))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> malformedBodies() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(MediaType.APPLICATION_JSON_VALUE, "not json"),
                org.junit.jupiter.params.provider.Arguments.of(MediaType.APPLICATION_JSON_VALUE, "{\"username\":"),
                org.junit.jupiter.params.provider.Arguments.of(MediaType.APPLICATION_JSON_VALUE, "{\"username\":\"a\"}"),
                org.junit.jupiter.params.provider.Arguments.of(MediaType.APPLICATION_JSON_VALUE, "[]"),
                org.junit.jupiter.params.provider.Arguments.of(MediaType.TEXT_PLAIN_VALUE,
                        "{\"username\":\"a\",\"password\":\"b\"}"),
                org.junit.jupiter.params.provider.Arguments.of(MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                        "username=a&password=b"));
    }

    @Test
    @Proves("T-AUTH-005")
    void theEffectiveProviderAlwaysPerformsTheAdditionalChecks() {
        DaoAuthenticationProvider provider = context.getBean(DaoAuthenticationProvider.class);

        assertThat(ReflectionTestUtils.getField(provider, "alwaysPerformAdditionalChecksOnUser")).isEqualTo(true);
    }

    @Test
    @Proves("T-AUTH-013")
    void noCompromisedPasswordCheckerIsABeanOrSetOnTheProvider() {
        assertThat(context.getBeanNamesForType(CompromisedPasswordChecker.class)).isEmpty();
        assertThat(ReflectionTestUtils.getField(context.getBean(DaoAuthenticationProvider.class),
                "compromisedPasswordChecker")).isNull();
    }

    @Test
    @Proves("T-CSRF-003")
    void loginWithoutOrWithAWrongCsrfTokenIsRefusedAndAuthenticatesNothing() throws Exception {
        Account alice = accounts.user();
        CsrfSession session = CsrfSession.bootstrap(mockMvc);
        String body = SignedIn.credentials(alice.username(), alice.password());

        mockMvc.perform(post("/api/login").cookie(session.cookie()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        mockMvc.perform(post("/api/login").cookie(session.cookie()).header(CsrfSession.HEADER, "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));

        mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    @Proves("T-CSRF-007")
    void aTokenFromBeforeLoginOrLogoutIsRejectedAfterIt() throws Exception {
        Account alice = accounts.user();
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);
        Cookie signedIn = login(anonymous, alice.username(), alice.password()).getResponse().getCookie("SESSION");

        // The login composite rotated the token: the pre-login one no longer works on the signed-in session.
        mockMvc.perform(post("/api/logout").cookie(signedIn).header(CsrfSession.HEADER, anonymous.token()))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));

        CsrfSession current = SignedIn.refreshed(mockMvc, signedIn);
        mockMvc.perform(post("/api/logout").with(current.inHeader())).andExpect(status().isNoContent());

        // CsrfLogoutHandler dropped the pre-logout token: it is refused on the next session's login.
        CsrfSession next = CsrfSession.bootstrap(mockMvc);
        String body = SignedIn.credentials(alice.username(), alice.password());
        mockMvc.perform(post("/api/login").cookie(next.cookie()).header(CsrfSession.HEADER, current.token())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        SignedIn.login(mockMvc, next, alice.username(), alice.password()).andExpect(status().isOk());
    }

    @Test
    @Proves("T-CSRF-005")
    void logoutOnADeadSessionWithAStaleTokenIsRefusedForCsrf() throws Exception {
        CsrfSession expired = SignedIn.as(mockMvc, accounts.user());
        sessions.expire(idOf(expired.cookie()));
        CsrfSession unknown = new CsrfSession(new Cookie("SESSION", java.util.Base64.getEncoder().encodeToString(
                java.util.UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))), expired.token());

        mockMvc.perform(post("/api/logout").with(expired.inHeader())).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        mockMvc.perform(post("/api/logout").with(unknown.inHeader())).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @Proves("T-CSRF-006")
    void everyUnsafeMethodNeedsTheTokenAndProceedsWithIt(String method) throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());
        HttpMethod unsafe = HttpMethod.valueOf(method);

        mockMvc.perform(request(unsafe, "/api/profile").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
        // Past CsrfFilter, the matrix answers: no row admits this method, so the signed-in caller is denied.
        mockMvc.perform(request(unsafe, "/api/profile").with(session.inHeader()))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
        mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(status().isOk());
        MvcResult head = mockMvc.perform(request(HttpMethod.HEAD, "/api/hello").cookie(session.cookie()))
                .andReturn();
        assertThat(head.getResponse().getContentAsString()).doesNotContain(ErrorCode.CSRF_TOKEN_INVALID.name());
    }

    @Test
    @Proves("T-AUTH-002")
    void aSignedInUserOnAnAdminPathIsDeniedAndAMissingTokenIsACsrfRefusal() throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());

        mockMvc.perform(get("/api/admin/users").cookie(session.cookie())).andExpect(problem(ErrorCode.ACCESS_DENIED));
        mockMvc.perform(post("/api/logout").cookie(session.cookie())).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
    }

    @Test
    @Proves("T-SES-001")
    void anIdleSessionPastTheWindowGets401NotARedirect() throws Exception {
        assertThat(sessionRepository.createSession().getMaxInactiveInterval()).isLessThanOrEqualTo(
                Duration.ofMinutes(15));
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());

        sessions.expire(idOf(session.cookie()));

        mockMvc.perform(get("/api/hello").cookie(session.cookie()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @Proves("T-SES-006")
    void theAbsoluteLifetimeRunsFromTheAuthInstant() throws Exception {
        Account alice = accounts.user();
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);
        // Time passes between the session's creation and sign-in; the eight hours must not count it.
        clock.advance(Duration.ofMinutes(10));
        Cookie signedIn = login(anonymous, alice.username(), alice.password()).getResponse().getCookie("SESSION");

        assertThat(lifetime.absolute()).isEqualTo(Duration.ofHours(8));
        clock.advance(lifetime.absolute().minusSeconds(1));
        mockMvc.perform(get("/api/hello").cookie(signedIn)).andExpect(status().isOk());

        clock.advance(Duration.ofSeconds(1));
        mockMvc.perform(get("/api/hello").cookie(signedIn)).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        assertThat(sessions.exists(idOf(signedIn))).as("the session is invalidated").isFalse();
    }

    @Test
    @Proves("T-SES-006")
    void anAbsolutelyExpiredSessionPostingAMutationGets401NotACsrfRefusal() throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());

        clock.advance(lifetime.absolute());

        mockMvc.perform(post("/api/logout").with(session.inHeader())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(post("/api/logout").cookie(session.cookie())).andExpect(problem(ErrorCode.CSRF_TOKEN_INVALID));
    }

    @Test
    @Proves("T-SES-037")
    void theAbsoluteLifetime401CarriesTheSpasCorsHeadersAndTheSecurityHeaders() throws Exception {
        String spa = "http://localhost:5173";
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());

        clock.advance(lifetime.absolute());

        mockMvc.perform(post("/api/logout").header(HttpHeaders.ORIGIN, spa).with(session.inHeader()))
                .andExpect(problem(ErrorCode.AUTHENTICATION_FAILED))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, spa))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @Proves({"T-SES-034", "T-SES-035"})
    void loginResetsTheIntervalToWWhileAnAnonymousSessionStaysPinnedToItsCreation() throws Exception {
        long w = sessionRepository.createSession().getMaxInactiveInterval().toSeconds();
        Account alice = accounts.user();
        CsrfSession anonymous = CsrfSession.bootstrap(mockMvc);
        sessions.ageCreation(idOf(anonymous.cookie()), Duration.ofMinutes(10).toMillis());
        mockMvc.perform(get("/api/csrf").cookie(anonymous.cookie())).andExpect(status().isOk());
        assertThat(interval(anonymous.cookie())).as("anonymous: pinned to creation + W").isLessThanOrEqualTo(w - 590);

        Cookie signedIn = login(anonymous, alice.username(), alice.password()).getResponse().getCookie("SESSION");
        assertThat(interval(signedIn)).as("the login composite reset the interval").isEqualTo(w);

        sessions.ageCreation(idOf(signedIn), Duration.ofMinutes(10).toMillis());
        mockMvc.perform(get("/api/hello").cookie(signedIn)).andExpect(status().isOk());
        assertThat(interval(signedIn)).as("authenticated: not pinned to creation").isEqualTo(w);
    }

    private long interval(Cookie cookie) {
        return ((Number) sessions.row(idOf(cookie)).get("MAX_INACTIVE_INTERVAL")).longValue();
    }

    @Test
    @Proves("T-SES-009")
    void loginIndexesTheSessionByPrincipalName() throws Exception {
        Account alice = accounts.user();
        CsrfSession session = SignedIn.as(mockMvc, alice);

        assertThat(sessions.row(idOf(session.cookie())).get("PRINCIPAL_NAME")).isEqualTo(alice.username());
        assertThat(indexedSessions.findByPrincipalName(alice.username())).containsOnlyKeys(idOf(session.cookie()));
    }

    @Test
    @Proves("T-SES-010")
    void aSecondLoginEndsTheFirstSession() throws Exception {
        Account alice = accounts.user();
        CsrfSession first = SignedIn.as(mockMvc, alice);
        CsrfSession second = SignedIn.as(mockMvc, alice);

        mockMvc.perform(get("/api/hello").cookie(first.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
        mockMvc.perform(get("/api/hello").cookie(second.cookie())).andExpect(status().isOk());
        assertThat(sessions.exists(idOf(first.cookie()))).isFalse();
    }

    @Test
    @Proves("T-SES-010")
    void signingInAgainOnADisplacedSessionLeavesTheNewSessionLiveAndDisplacesTheOther() throws Exception {
        Account alice = accounts.user();
        CsrfSession displaced = SignedIn.as(mockMvc, alice);
        CsrfSession newer = SignedIn.as(mockMvc, alice);

        MvcResult again = SignedIn.login(mockMvc, displaced, alice.username(), alice.password()).andReturn();
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        Cookie rotated = again.getResponse().getCookie("SESSION");
        assertThat(rotated).isNotNull();

        mockMvc.perform(get("/api/hello").cookie(rotated)).andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").cookie(newer.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void logoutEndsTheSessionAndSendsClearSiteData() throws Exception {
        CsrfSession session = SignedIn.as(mockMvc, accounts.user());

        mockMvc.perform(post("/api/logout").with(session.inHeader()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\""));

        assertThat(sessions.exists(idOf(session.cookie()))).isFalse();
        mockMvc.perform(get("/api/hello").cookie(session.cookie())).andExpect(problem(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void aFailedLoginLeavesTheAccountsLiveSessionAlone() throws Exception {
        Account alice = accounts.user();
        CsrfSession live = SignedIn.as(mockMvc, alice);

        login(CsrfSession.bootstrap(mockMvc), alice.username(), Accounts.WRONG_PASSWORD);

        mockMvc.perform(get("/api/hello").cookie(live.cookie())).andExpect(status().isOk());
    }
}
