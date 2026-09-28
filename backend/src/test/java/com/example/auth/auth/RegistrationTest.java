package com.example.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Seam 1 (backend HTTP seam): same real-filter-chain, real-database style as
 * {@link AuthControllerTest}, exercising the new registration endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RegistrationTest {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String RAW_PASSWORD = "SuperSecret123!";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void validRegistrationCreatesAUserAndReturns201() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser", "NewUser@Example.com", RAW_PASSWORD, "New");

        register(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").exists());

        User saved = userRepository.findByUsername("newuser").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("newuser@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        register(new RegisterRequest("duplicateuser", "first@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());

        register(new RegisterRequest("duplicateuser", "second@example.com", RAW_PASSWORD, "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void usernameDifferingOnlyByCaseReturns409() throws Exception {
        register(new RegisterRequest("CaseUser", "casefirst@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());

        register(new RegisterRequest("caseuser", "casesecond@example.com", RAW_PASSWORD, "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        register(new RegisterRequest("emailfirst", "shared@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isCreated());

        register(new RegisterRequest("emailsecond", "shared@example.com", RAW_PASSWORD, "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void missingUsernameReturns400InsteadOf500() throws Exception {
        register(new RegisterRequest(null, "missingusername@example.com", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedEmailReturns400InsteadOf500() throws Exception {
        register(new RegisterRequest("malformedemailuser", "not-an-email", RAW_PASSWORD, "First"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFirstNameReturns400InsteadOf500() throws Exception {
        register(new RegisterRequest("missingfirstnameuser", "missingfirstname@example.com", RAW_PASSWORD, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordShorterThanTwelveCharactersReturns400() throws Exception {
        register(new RegisterRequest("shortpassworduser", "shortpw@example.com", "short11chr", "Short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must be at least 12 characters long"));

        assertThat(userRepository.existsByUsername("shortpassworduser")).isFalse();
    }

    @Test
    void registrationWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("nocsrfuser", "nocsrf@example.com", RAW_PASSWORD, "NoCsrf"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void neitherTheResponseBodyNorTheLogsEverContainTheRawPassword() throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);

        String responseBody;
        try {
            responseBody = register(new RegisterRequest(
                            "logcheckuser", "logcheck@example.com", "LogCheckPassword123!", "LogCheck"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        } finally {
            rootLogger.detachAppender(appender);
        }

        assertThat(responseBody).doesNotContain("LogCheckPassword123!");

        List<String> loggedMessages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(loggedMessages).noneMatch(message -> message.contains("LogCheckPassword123!"));
    }

    private ResultActions register(RegisterRequest request) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        return mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue()));
    }

    /** Mints a real {@code XSRF-TOKEN} cookie via {@code CsrfCookieFilter}, exactly like a browser's first request. */
    private Cookie mintCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }
}
