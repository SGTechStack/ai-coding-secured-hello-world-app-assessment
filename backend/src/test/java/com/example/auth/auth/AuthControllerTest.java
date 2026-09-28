package com.example.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.security.IpLoginThrottleService;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Seam 1 (backend HTTP seam): drives the real auth API through its HTTP
 * layer with MockMvc, Spring Security's filter chain fully engaged, against
 * the real seeded H2 database (the repository is not mocked).
 *
 * <p>State-changing requests carry a real {@code XSRF-TOKEN} cookie + {@code
 * X-XSRF-TOKEN} header pair, minted via a real prior GET (exactly like a
 * browser SPA, and exactly what the manual end-to-end smoke test drives
 * against the packaged jar) -- deliberately <b>not</b> {@code
 * SecurityMockMvcRequestPostProcessors.csrf()}. That postprocessor saves its
 * generated token onto the outgoing mock *response* via {@code
 * CookieCsrfTokenRepository}, but never onto the *incoming* request's cookie
 * jar that {@code CsrfFilter} actually reads on the next call; the filter
 * then has no token to load, mints a fresh one for comparison, and the
 * postprocessor's value can never match it. Minting through a real GET avoids
 * that mismatch entirely and is a closer simulation of the real client flow.
 *
 * <p>Session propagation across requests uses the {@link MockHttpSession}
 * object returned from the login request rather than a literal session
 * {@code Set-Cookie} header: under MockMvc's mock web environment (no real
 * servlet container), the container-managed session cookie is never actually
 * written to the mock response -- that write only happens on a real connector
 * (e.g. embedded Tomcat). This is exactly why the pre-migration hand-rolled
 * implementation set its session cookie explicitly rather than relying on a
 * container session. The real cookie (name, HttpOnly flag) is verified for
 * real against the packaged jar as part of this issue's end-to-end smoke
 * test, not here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerTest {

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String VALID_USERNAME = "johndoe";
    private static final String VALID_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IpLoginThrottleService ipLoginThrottleService;

    /**
     * The H2 database stays alive for the whole test JVM, and all tests in
     * this class share one Spring context, so a lockout left over from one
     * test -- or IP-throttle state left over against MockMvc's fixed
     * 127.0.0.1 remote address -- would otherwise leak into whichever test
     * happens to run next. Also seeds {@code VALID_USERNAME} on first use,
     * since no data.sql exists to do it anymore (see AdminBootstrapRunner,
     * which only seeds an ADMIN account, not this ordinary test user).
     */
    @BeforeEach
    void resetLockoutState() {
        User user = userRepository
                .findByUsername(VALID_USERNAME)
                .orElseGet(() -> new User(
                        VALID_USERNAME,
                        "johndoe@example.com",
                        passwordEncoder.encode(VALID_PASSWORD),
                        "John"));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        ipLoginThrottleService.reset();
    }

    @Test
    void passwordIsStoredAsABcryptHashNotPlaintext() {
        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();

        assertThat(user.getPassword()).isNotEqualTo(VALID_PASSWORD);
        assertThat(user.getPassword()).startsWith("$2");
    }

    @Test
    void loginWithValidCredentialsReturnsOkAndCreatesAnAuthenticatedSession() throws Exception {
        MockHttpSession[] capturedSession = new MockHttpSession[1];
        List<String> loggedMessages = captureLogs(() -> {
            MvcResult result = performLogin(VALID_USERNAME, VALID_PASSWORD)
                    .andExpect(status().isOk())
                    .andReturn();
            capturedSession[0] = (MockHttpSession) result.getRequest().getSession(false);
        });

        MockHttpSession session = capturedSession[0];
        assertThat(session).isNotNull();

        SecurityContext securityContext = (SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext).isNotNull();
        assertThat(securityContext.getAuthentication()).isNotNull();
        assertThat(securityContext.getAuthentication().isAuthenticated()).isTrue();
        assertThat(securityContext.getAuthentication().getName()).isEqualTo(VALID_USERNAME);

        assertThat(loggedMessages)
                .anyMatch(message -> message.contains("event=login_success") && message.contains(VALID_USERNAME));
        assertThat(loggedMessages).noneMatch(message -> message.contains(VALID_PASSWORD));
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorized() throws Exception {
        List<String> loggedMessages = captureLogs(() -> performLogin(VALID_USERNAME, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"message\": \"Invalid username or password\"}")));

        assertThat(loggedMessages)
                .anyMatch(message -> message.contains("event=login_failure") && message.contains(VALID_USERNAME));
        assertThat(loggedMessages).noneMatch(message -> message.contains("wrong-password"));
    }

    @Test
    void loginWithUnknownUsernameReturnsIdenticalGenericUnauthorized() throws Exception {
        performLogin("no-such-user", VALID_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"message\": \"Invalid username or password\"}"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(VALID_USERNAME, VALID_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void meReturnsUnauthorizedWithoutSessionCookie() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsUnauthorizedWithAnUnauthenticatedSession() throws Exception {
        // A session that exists but was never authenticated into (no
        // SecurityContext attribute) must not grant access.
        mockMvc.perform(get("/api/auth/me").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUserWhenSessionIsAuthenticated() throws Exception {
        MockHttpSession session = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(VALID_USERNAME))
                .andExpect(jsonPath("$.email").value("johndoe@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logoutInvalidatesSessionSoMeIsUnauthorizedAfterwards() throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        MockHttpSession session = login(VALID_USERNAME, VALID_PASSWORD, csrfCookie);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheSessionCookie() throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        MockHttpSession session = login(VALID_USERNAME, VALID_PASSWORD, csrfCookie);

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cleared = result.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void loginWithDisabledAccountReturnsGenericUnauthorized() throws Exception {
        User disabledUser = new User(
                "disableduser", "disableduser@example.com", passwordEncoder.encode(VALID_PASSWORD), "Disabled");
        disabledUser.setEnabled(false);
        userRepository.save(disabledUser);

        performLogin("disableduser", VALID_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"message\": \"Invalid username or password\"}"));
    }

    @Test
    void adminRoleLoginGrantsRoleAdminAuthorityInTheSession() throws Exception {
        User admin = new User("adminuser", "adminuser@example.com", passwordEncoder.encode(VALID_PASSWORD), "Admin");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        MvcResult result = performLogin("adminuser", VALID_PASSWORD).andExpect(status().isOk()).andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        SecurityContext securityContext = (SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext.getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void fifthConsecutiveFailedLoginLocksTheAccountEvenWithTheCorrectPasswordAfterwards() throws Exception {
        List<String> loggedMessages = captureLogs(() -> {
            for (int attempt = 1; attempt <= 5; attempt++) {
                performLogin(VALID_USERNAME, "wrong-password")
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().json("{\"message\": \"Invalid username or password\"}"));
            }

            // Locked out now -- even the correct password is rejected, with the
            // exact same generic body (no enumeration signal that lockout, vs.
            // wrong credentials, is why this attempt failed).
            performLogin(VALID_USERNAME, VALID_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"message\": \"Invalid username or password\"}"));
        });

        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        assertThat(user.isLocked(java.time.Instant.now())).isTrue();

        assertThat(loggedMessages)
                .anyMatch(message -> message.contains("event=account_locked") && message.contains(VALID_USERNAME));
        assertThat(loggedMessages).noneMatch(message -> message.contains(VALID_PASSWORD));
    }

    @Test
    void successfulLoginResetsThePriorFailedAttemptCounter() throws Exception {
        performLogin(VALID_USERNAME, "wrong-password").andExpect(status().isUnauthorized());
        performLogin(VALID_USERNAME, "wrong-password").andExpect(status().isUnauthorized());

        login(VALID_USERNAME, VALID_PASSWORD);

        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void cooldownElapsedRetryWithCorrectPasswordSucceedsAndResetsTheCounter() throws Exception {
        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        userRepository.save(user);

        login(VALID_USERNAME, VALID_PASSWORD);

        User updated = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isZero();
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    void ipThrottleAcrossDifferentUsernamesBlocksAFreshNeverFailedUsernameFromTheSameIp() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            performLogin("ip-throttle-user-" + attempt, "wrong-password").andExpect(status().isUnauthorized());
        }

        // Same IP, but a username that has never failed before -- still
        // rejected, because the throttle is keyed by IP, not by account.
        performLogin("ip-throttle-user-fresh", VALID_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void aSecondLoginDoesNotEvictTheFirstSessionMultipleConcurrentSessionsAreAllowed() throws Exception {
        MockHttpSession firstSession = login(VALID_USERNAME, VALID_PASSWORD);
        mockMvc.perform(get("/api/auth/me").session(firstSession)).andExpect(status().isOk());

        MockHttpSession secondSession = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(secondSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(firstSession)).andExpect(status().isOk());
    }

    /** Mints a real {@code XSRF-TOKEN} cookie via {@code CsrfCookieFilter}, exactly like a browser's first request. */
    private Cookie mintCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return login(username, password, mintCsrfCookie());
    }

    private MockHttpSession login(String username, String password, Cookie csrfCookie) throws Exception {
        MvcResult result = performLogin(username, password, csrfCookie)
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions performLogin(String username, String password) throws Exception {
        return performLogin(username, password, mintCsrfCookie());
    }

    private ResultActions performLogin(String username, String password, Cookie csrfCookie) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password)))
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue()));
    }

    /**
     * Runs {@code action} while capturing every log line (root logger, so
     * this also catches the dedicated {@code AUDIT} logger since it's
     * additive into root by default) -- same {@code ListAppender} pattern as
     * {@code RegistrationTest}, reused here to assert on audit events instead
     * of duplicating a separate audit-focused test class.
     */
    private List<String> captureLogs(Action action) throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        try {
            action.run();
        } finally {
            rootLogger.detachAppender(appender);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private interface Action {
        void run() throws Exception;
    }
}
