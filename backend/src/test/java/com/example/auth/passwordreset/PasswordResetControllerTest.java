package com.example.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.auth.LoginRequest;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seam 1 (backend HTTP seam), same real-filter-chain style as {@link
 * com.example.auth.auth.AuthControllerTest}. {@code @Transactional} so the
 * fixed {@code reset-flow-user} row -- created fresh every {@code
 * @BeforeEach} -- rolls back at the end of each test rather than colliding
 * with the next test's insert of the same username/email.
 *
 * <p>{@link EmailService} is replaced with a {@code @MockitoBean} rather than
 * asserting against {@link LoggingEmailService}'s log output -- it's the one
 * true external-I/O boundary {@code PasswordResetService} has, and mocking it
 * is also how the raw, never-persisted reset token is recovered for the
 * confirm-side tests below (only its SHA-256 hash is ever written to the
 * database). {@code @MockitoBean} resets the mock after every test method by
 * default ({@code MockReset.AFTER}), so no manual reset is needed here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("dev")
class PasswordResetControllerTest {

    private static final String REQUEST_URL = "/api/password-reset/request";
    private static final String CONFIRM_URL = "/api/password-reset/confirm";
    private static final String HELLO_URL = "/api/hello";
    /** Spring Session's cookie: the session travels as a real cookie, exactly like a browser. */
    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String RESET_USERNAME = "reset-flow-user";
    private static final String RESET_EMAIL = "reset-flow-user@example.com";
    private static final String RESET_PASSWORD = "OriginalPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private User resetUser;

    @BeforeEach
    void seedUser() {
        resetUser = userRepository.save(
                new User(RESET_USERNAME, RESET_EMAIL, passwordEncoder.encode(RESET_PASSWORD), "Reset"));
    }

    @Test
    void requestForRegisteredEmailCreatesTokenRowAndInvokesEmailService() throws Exception {
        List<String> loggedMessages = captureLogs(() -> requestReset(RESET_EMAIL).andExpect(status().isOk()));

        assertThat(tokenRepository.findAll()).hasSize(1);
        assertThat(tokenRepository.findAll().get(0).getUserId()).isEqualTo(resetUser.getId());
        verify(emailService).sendPasswordResetEmail(eq(RESET_EMAIL), any());

        assertThat(loggedMessages)
                .anyMatch(message -> message.contains("event=password_reset_requested") && message.contains(RESET_USERNAME));
    }

    @Test
    void requestForUnregisteredEmailReturnsIdenticalGenericResponseAndCreatesNoTokenRow() throws Exception {
        requestReset("no-such-account@example.com").andExpect(status().isOk());

        assertThat(tokenRepository.findAll()).isEmpty();
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void confirmWithValidTokenUpdatesPasswordAndMarksTokenUsed() throws Exception {
        String token = requestResetAndCaptureToken();

        List<String> loggedMessages =
                captureLogs(() -> confirmReset(token, "BrandNewPassword123!").andExpect(status().isOk()));

        User updated = userRepository.findByUsername(RESET_USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewPassword123!", updated.getPassword())).isTrue();

        PasswordResetToken tokenRow = tokenRepository.findAll().get(0);
        assertThat(tokenRow.getUsedAt()).isNotNull();

        assertThat(loggedMessages)
                .anyMatch(message -> message.contains("event=password_reset_completed") && message.contains(RESET_USERNAME));
        assertThat(loggedMessages).noneMatch(
                message -> message.contains(token) || message.contains("BrandNewPassword123!"));
    }

    @Test
    void confirmWithMissingTokenIsRejectedWithoutError() throws Exception {
        confirmReset(null, "BrandNewPassword123!").andExpect(status().isBadRequest());
    }

    @Test
    void confirmWithExpiredTokenIsRejectedAndPasswordUnchanged() throws Exception {
        String rawToken = "expired-token-value";
        tokenRepository.save(new PasswordResetToken(resetUser.getId(), hash(rawToken), Instant.now().minusSeconds(60)));

        confirmReset(rawToken, "BrandNewPassword123!").andExpect(status().isBadRequest());

        User unchanged = userRepository.findByUsername(RESET_USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches(RESET_PASSWORD, unchanged.getPassword())).isTrue();
    }

    @Test
    void reusedTokenIsRejectedOnTheSecondConfirmAttempt() throws Exception {
        String token = requestResetAndCaptureToken();

        confirmReset(token, "FirstNewPassword123!").andExpect(status().isOk());
        confirmReset(token, "SecondNewPassword123!").andExpect(status().isBadRequest());

        User updated = userRepository.findByUsername(RESET_USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches("FirstNewPassword123!", updated.getPassword())).isTrue();
    }

    @Test
    void resettingThePasswordInvalidatesTheOldSessionSoHelloReturnsUnauthorizedAfterwards() throws Exception {
        Cookie session = login(RESET_USERNAME, RESET_PASSWORD);
        mockMvc.perform(get(HELLO_URL).cookie(session)).andExpect(status().isOk());

        String token = requestResetAndCaptureToken();
        confirmReset(token, "BrandNewPassword123!").andExpect(status().isOk());

        mockMvc.perform(get(HELLO_URL).cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void resettingThePasswordInvalidatesEveryExistingSessionNotJustOne() throws Exception {
        Cookie firstSession = login(RESET_USERNAME, RESET_PASSWORD);
        Cookie secondSession = login(RESET_USERNAME, RESET_PASSWORD);
        mockMvc.perform(get(HELLO_URL).cookie(firstSession)).andExpect(status().isOk());
        mockMvc.perform(get(HELLO_URL).cookie(secondSession)).andExpect(status().isOk());

        String token = requestResetAndCaptureToken();
        confirmReset(token, "BrandNewPassword123!").andExpect(status().isOk());

        mockMvc.perform(get(HELLO_URL).cookie(firstSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(HELLO_URL).cookie(secondSession)).andExpect(status().isUnauthorized());
    }

    /** Requests a reset for {@link #RESET_EMAIL}, then recovers the raw token from the captured reset link. */
    private String requestResetAndCaptureToken() throws Exception {
        requestReset(RESET_EMAIL).andExpect(status().isOk());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(RESET_EMAIL), linkCaptor.capture());
        String resetLink = linkCaptor.getValue();
        return resetLink.substring(resetLink.indexOf("token=") + "token=".length());
    }

    /** Same SHA-256 + Base64 scheme as {@code PasswordResetService#hash} -- needed to plant a token row directly. */
    private String hash(String rawToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hashed);
    }

    private ResultActions requestReset(String email) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        return mockMvc.perform(post(REQUEST_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetRequest(email)))
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue()));
    }

    private ResultActions confirmReset(String token, String newPassword) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        return mockMvc.perform(post(CONFIRM_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(token, newPassword)))
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue()));
    }

    private Cookie mintCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private Cookie login(String username, String password) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password)))
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    /**
     * Same {@code ListAppender}-on-root pattern as {@code
     * com.example.auth.auth.RegistrationTest}, reused here to assert on
     * {@code AuditLogger}'s password-reset events.
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
