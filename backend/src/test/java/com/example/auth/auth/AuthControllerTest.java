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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
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
 * <p>Session propagation across requests uses the real {@code SESSION}
 * cookie from the login response, replayed on later requests exactly like a
 * browser would: Spring Session's {@code SessionRepositoryFilter} runs inside
 * MockMvc's filter chain, writes that cookie itself, and resolves the session
 * from the JDBC store on the way back in. A {@code MockHttpSession} handed to
 * the request builder would be ignored -- the filter replaces the request's
 * session handling entirely. The cookie's attributes (HttpOnly, SameSite)
 * are not asserted here: Spring Boot only applies {@code
 * server.servlet.session.cookie.*} when it runs its own web server, so they
 * are checked against a real one in {@code SessionCookieAttributesTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerTest {

    private static final String SESSION_COOKIE_NAME = "SESSION";
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

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

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
        user.setFailedLoginWindowStart(null);
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
        Cookie[] capturedSession = new Cookie[1];
        List<String> loggedMessages = captureLogs(() -> {
            MvcResult result = performLogin(VALID_USERNAME, VALID_PASSWORD)
                    .andExpect(status().isOk())
                    .andReturn();
            capturedSession[0] = result.getResponse().getCookie(SESSION_COOKIE_NAME);
        });

        Cookie session = capturedSession[0];
        assertThat(session).isNotNull();

        SecurityContext securityContext = storedSecurityContext(session);
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
    void meReturnsUnauthorizedWithAnUnknownSessionCookie() throws Exception {
        // A session cookie the store has no row for (forged, or long since
        // expired) must not grant access.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(SESSION_COOKIE_NAME, "bm8tc3VjaC1zZXNzaW9u")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUserWhenSessionIsAuthenticated() throws Exception {
        Cookie session = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(VALID_USERNAME))
                .andExpect(jsonPath("$.email").value("johndoe@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logoutInvalidatesSessionSoMeIsUnauthorizedAfterwards() throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        Cookie session = login(VALID_USERNAME, VALID_PASSWORD, csrfCookie);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheSessionCookie() throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        Cookie session = login(VALID_USERNAME, VALID_PASSWORD, csrfCookie);

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(session)
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
        Cookie session = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isForbidden());
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

        SecurityContext securityContext = storedSecurityContext(result.getResponse().getCookie(SESSION_COOKIE_NAME));
        assertThat(securityContext.getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void fifthConsecutiveFailedLoginLocksTheAccountEvenWithTheCorrectPasswordAfterwards() throws Exception {
        List<String> loggedMessages = captureLogs(() -> {
            // Spread over two sources: a single IP is throttled before it can
            // supply all five failures by itself (see the next test).
            for (int attempt = 1; attempt <= 5; attempt++) {
                performLoginFrom(attempt <= 3 ? "203.0.113.1" : "203.0.113.2", VALID_USERNAME, "wrong-password")
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().json("{\"message\": \"Invalid username or password\"}"));
            }

            // Locked out now -- even the correct password is rejected, with the
            // exact same generic body (no enumeration signal that lockout, vs.
            // wrong credentials, is why this attempt failed).
            performLoginFrom("203.0.113.3", VALID_USERNAME, VALID_PASSWORD)
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
    void aSingleSourceIsThrottledBeforeItCanLockTheAccountSoTheOwnerCanStillLogIn() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            performLoginFrom("203.0.113.9", VALID_USERNAME, "wrong-password").andExpect(status().isUnauthorized());
        }

        // The would-be fifth failure never reaches authentication: the source
        // is throttled one attempt short of the lockout threshold.
        performLoginFrom("203.0.113.9", VALID_USERNAME, "wrong-password").andExpect(status().isTooManyRequests());

        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(user.isLocked(Instant.now())).isFalse();

        // The legitimate owner, coming from anywhere else, is unaffected.
        performLoginFrom("198.51.100.7", VALID_USERNAME, VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void attemptsWhileLockedAreRejectedButDoNotExtendTheLockout() throws Exception {
        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        userRepository.save(user);
        Instant lockedUntil =
                userRepository.findByUsername(VALID_USERNAME).orElseThrow().getLockedUntil();

        performLogin(VALID_USERNAME, "wrong-password").andExpect(status().isUnauthorized());
        performLogin(VALID_USERNAME, VALID_PASSWORD).andExpect(status().isUnauthorized());

        User updated = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        assertThat(updated.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(updated.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void failuresOlderThanTheLockoutWindowDoNotCountTowardALockout() throws Exception {
        User user = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        user.setFailedLoginAttempts(4);
        user.setFailedLoginWindowStart(Instant.now().minus(java.time.Duration.ofMinutes(16)));
        userRepository.save(user);

        performLogin(VALID_USERNAME, "wrong-password").andExpect(status().isUnauthorized());

        User updated = userRepository.findByUsername(VALID_USERNAME).orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(updated.isLocked(Instant.now())).isFalse();
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
        Cookie firstSession = login(VALID_USERNAME, VALID_PASSWORD);
        mockMvc.perform(get("/api/auth/me").cookie(firstSession)).andExpect(status().isOk());

        Cookie secondSession = login(VALID_USERNAME, VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/me").cookie(secondSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(firstSession)).andExpect(status().isOk());
    }

    /** Mints a real {@code XSRF-TOKEN} cookie via {@code CsrfCookieFilter}, exactly like a browser's first request. */
    private Cookie mintCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    /**
     * Reads the {@code SecurityContext} a session cookie points at straight out of the Spring
     * Session store. The cookie value is the Base64-encoded session id ({@code
     * DefaultCookieSerializer}'s default).
     */
    private SecurityContext storedSecurityContext(Cookie sessionCookie) {
        String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
        Session session = sessionRepository.findById(sessionId);
        assertThat(session).isNotNull();
        return session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    private Cookie login(String username, String password) throws Exception {
        return login(username, password, mintCsrfCookie());
    }

    private Cookie login(String username, String password, Cookie csrfCookie) throws Exception {
        MvcResult result = performLogin(username, password, csrfCookie)
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private ResultActions performLogin(String username, String password) throws Exception {
        return performLogin(username, password, mintCsrfCookie());
    }

    /** Same as {@link #performLogin(String, String)}, but arriving from {@code remoteAddr} instead of 127.0.0.1. */
    private ResultActions performLoginFrom(String remoteAddr, String username, String password) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password)))
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue())
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                }));
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
