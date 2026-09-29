package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.TestAccounts;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIT {

    private static final String LOGIN_JSON =
            """
            {"username":"johndoe","password":"Password123!"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loginIsPublicButRequiresCsrf() {
        assertThat(mvc.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aLoginBodyOfExactlyFourKilobytesIsAccepted() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        String body = LOGIN_JSON.stripTrailing();
        body += " ".repeat(4096 - body.getBytes(StandardCharsets.UTF_8).length);

        assertThat(session.login(mvc, body)).hasStatusOk();
    }

    // API side of the login redirect in stories/assessment/story2.feature (ac7); that UI scenario is
    // claimed by the frontend test that asserts the redirect (routes.test.tsx).
    @Test
    void validCredentialsSignInAndRotateSessionAndCsrfToken() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        String originalSessionId = session.sessionId();

        MvcTestResult result = session.login(mvc, LOGIN_JSON);

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"username":"johndoe","role":"USER"}
                        """);
        assertThat(session.sessionId()).isNotEqualTo(originalSessionId);
        assertThat(session.login(mvc, LOGIN_JSON)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void persistedBcryptAdminAccountSignsInWithItsRole() throws Exception {
        TestAccounts accounts = new TestAccounts(jdbc);
        accounts.create("bcrypt-admin", "bcrypt-admin@example.com", "AdminPassword123!", "ADMIN");
        try {
            Session session = SessionClient.fetchCsrf(mvc);
            assertThat(session.login(
                            mvc, "{\"username\":\"bcrypt-admin\",\"password\":\"AdminPassword123!\"}"))
                    .hasStatusOk()
                    .bodyJson()
                    .isStrictlyEqualTo("""
                            {"username":"bcrypt-admin","role":"ADMIN"}
                            """);
        } finally {
            accounts.delete("bcrypt-admin");
        }
    }

    @Test
    void mixedCaseUsernameSignsInAsTheStoredLowerCaseAccount() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        assertThat(session.login(
                        mvc,
                        """
                        {"username":"JohnDoe","password":"Password123!"}
                        """))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"username":"johndoe","role":"USER"}
                        """);
    }

    @Test
    void authenticationFailureUsesTheSameGenericResponse() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        assertThat(session.login(
                        mvc,
                        """
                        {"username":"johndoe","password":"wrong"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"message":"Invalid username or password"}
                        """);
    }

    // The session cookie alone restores the current user: what the SPA's route guards rely on for the
    // stories/assessment/story5.feature reload and redirect scenarios, which the frontend tests claim
    // because they assert the pages.
    @Test
    void currentUserSurvivesSubsequentSessionRequests() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, LOGIN_JSON)).hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"username":"johndoe","role":"USER"}
                        """);
    }

    @Test
    void anonymousCurrentUserRequestIsRejected() {
        assertThat(mvc.get().uri("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
