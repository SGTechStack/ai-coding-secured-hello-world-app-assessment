package com.example.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.auth.LoginRequest;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seam 1 (backend HTTP seam), same real-filter-chain style as {@link
 * com.example.auth.auth.AuthControllerTest}. {@code @Transactional} so each
 * test's fixed {@code admin-under-test}/{@code other-user} rows -- created
 * fresh every {@code @BeforeEach} -- roll back at the end of the test rather
 * than colliding with the next test's insert of the same usernames.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("dev")
class AdminUserControllerTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String ADMIN_PASSWORD = "AdminPass123!";
    private static final String OTHER_PASSWORD = "OtherPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User other;

    @BeforeEach
    void seedUsers() {
        admin = new User(
                "admin-under-test", "admin-under-test@example.com", passwordEncoder.encode(ADMIN_PASSWORD), "Admin");
        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        other = userRepository.save(
                new User("other-user", "other-user@example.com", passwordEncoder.encode(OTHER_PASSWORD), "Other"));
    }

    @Test
    void listUsersAsAdminReturnsFullShapeWithNoPasswordField() throws Exception {
        MockHttpSession session = login(admin.getUsername(), ADMIN_PASSWORD);
        String filter = "$[?(@.username=='" + other.getUsername() + "')]";

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filter + ".id").exists())
                .andExpect(jsonPath(filter + ".email").value(other.getEmail()))
                .andExpect(jsonPath(filter + ".role").value("USER"))
                .andExpect(jsonPath(filter + ".enabled").value(true))
                .andExpect(jsonPath(filter + ".createdAt").exists())
                .andExpect(jsonPath(filter + ".password").doesNotExist());
    }

    @Test
    void listUsersAsRegularUserReturns403() throws Exception {
        MockHttpSession session = login(other.getUsername(), OTHER_PASSWORD);

        mockMvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void updatingStatusAsRegularUserReturns403() throws Exception {
        MockHttpSession session = login(other.getUsername(), OTHER_PASSWORD);

        patchWithCsrf("/api/admin/users/" + admin.getId() + "/status", session, "{\"enabled\": false}")
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingRoleAsRegularUserReturns403() throws Exception {
        MockHttpSession session = login(other.getUsername(), OTHER_PASSWORD);

        patchWithCsrf("/api/admin/users/" + admin.getId() + "/role", session, "{\"role\": \"ADMIN\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingUserAsRegularUserReturns403() throws Exception {
        MockHttpSession session = login(other.getUsername(), OTHER_PASSWORD);
        Cookie csrfCookie = mintCsrfCookie();

        mockMvc.perform(delete("/api/admin/users/{id}", admin.getId())
                        .session(session)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void disablingAnotherUserPreventsThemFromLoggingIn() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);

        List<String> loggedMessages = captureLogs(() -> patchWithCsrf(
                        "/api/admin/users/" + other.getId() + "/status", adminSession, "{\"enabled\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)));

        performLogin(other.getUsername(), OTHER_PASSWORD).andExpect(status().isUnauthorized());

        assertThat(loggedMessages).anyMatch(message -> message.contains("event=account_disabled")
                && message.contains("actor=" + admin.getUsername())
                && message.contains("target=" + other.getUsername()));
        assertThat(loggedMessages).noneMatch(message -> message.contains(ADMIN_PASSWORD) || message.contains(OTHER_PASSWORD));
    }

    @Test
    void disablingSelfIsRejectedAndLeavesAccountUnchanged() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);

        patchWithCsrf("/api/admin/users/" + admin.getId() + "/status", adminSession, "{\"enabled\": false}")
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void disablingAnotherUserExpiresTheirExistingSessionImmediately() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);
        MockHttpSession otherSession = login(other.getUsername(), OTHER_PASSWORD);
        mockMvc.perform(get("/api/hello").session(otherSession)).andExpect(status().isOk());

        patchWithCsrf("/api/admin/users/" + other.getId() + "/status", adminSession, "{\"enabled\": false}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").session(otherSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAnotherUserExpiresTheirExistingSessionImmediately() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);
        MockHttpSession otherSession = login(other.getUsername(), OTHER_PASSWORD);
        mockMvc.perform(get("/api/hello").session(otherSession)).andExpect(status().isOk());
        Cookie csrfCookie = mintCsrfCookie();

        mockMvc.perform(delete("/api/admin/users/{id}", other.getId())
                        .session(adminSession)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hello").session(otherSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingAnotherUsersRoleExpiresTheirExistingSessionImmediately() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);
        MockHttpSession otherSession = login(other.getUsername(), OTHER_PASSWORD);
        mockMvc.perform(get("/api/hello").session(otherSession)).andExpect(status().isOk());

        patchWithCsrf("/api/admin/users/" + other.getId() + "/role", adminSession, "{\"role\": \"ADMIN\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").session(otherSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void promotingAnotherUserToAdminSucceeds() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);

        List<String> loggedMessages = captureLogs(() -> patchWithCsrf(
                        "/api/admin/users/" + other.getId() + "/role", adminSession, "{\"role\": \"ADMIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN")));

        assertThat(loggedMessages).anyMatch(message -> message.contains("event=role_changed")
                && message.contains("actor=" + admin.getUsername())
                && message.contains("target=" + other.getUsername())
                && message.contains("newRole=ADMIN"));
        assertThat(loggedMessages).noneMatch(message -> message.contains(ADMIN_PASSWORD) || message.contains(OTHER_PASSWORD));
    }

    @Test
    void roleChangeWithNullRoleIsRejectedRatherThanCorruptingTheRow() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);

        patchWithCsrf("/api/admin/users/" + other.getId() + "/role", adminSession, "{\"role\": null}")
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(other.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void demotingSelfIsRejectedAndLeavesRoleUnchanged() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);

        patchWithCsrf("/api/admin/users/" + admin.getId() + "/role", adminSession, "{\"role\": \"USER\"}")
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void deletingAnotherUserRemovesThem() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);
        Cookie csrfCookie = mintCsrfCookie();

        List<String> loggedMessages = captureLogs(() -> mockMvc.perform(delete(
                                "/api/admin/users/{id}", other.getId())
                        .session(adminSession)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isNoContent()));

        assertThat(userRepository.existsById(other.getId())).isFalse();

        assertThat(loggedMessages).anyMatch(message -> message.contains("event=account_deleted")
                && message.contains("actor=" + admin.getUsername())
                && message.contains("target=" + other.getUsername()));
        assertThat(loggedMessages).noneMatch(message -> message.contains(ADMIN_PASSWORD) || message.contains(OTHER_PASSWORD));
    }

    @Test
    void deletingSelfIsRejectedAndLeavesAccountUnchanged() throws Exception {
        MockHttpSession adminSession = login(admin.getUsername(), ADMIN_PASSWORD);
        Cookie csrfCookie = mintCsrfCookie();

        mockMvc.perform(delete("/api/admin/users/{id}", admin.getId())
                        .session(adminSession)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    private ResultActions patchWithCsrf(String url, MockHttpSession session, String jsonBody) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        return mockMvc.perform(patch(url)
                .session(session)
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    private Cookie mintCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = performLogin(username, password).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions performLogin(String username, String password) throws Exception {
        Cookie csrfCookie = mintCsrfCookie();
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password)))
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, csrfCookie.getValue()));
    }

    /**
     * Same {@code ListAppender}-on-root pattern as {@code
     * com.example.auth.auth.RegistrationTest} and {@code AuthControllerTest},
     * reused here to assert on {@code AuditLogger}'s admin-action events.
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
