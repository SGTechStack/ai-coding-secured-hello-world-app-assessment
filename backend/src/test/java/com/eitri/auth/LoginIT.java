package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.TestAccounts;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:login;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LoginIT {

    private static final String GENERIC_FAILURE = "{\"message\":\"Invalid username or password\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestAccounts accounts;

    @BeforeEach
    void resetJohndoe() {
        accounts = new TestAccounts(jdbc);
        accounts.resetJohndoe();
        jdbc.update("DELETE FROM SPRING_SESSION");
    }

    @Test
    @DisplayName("[assessment/story2-ac1] correct credentials create a server-side session and reset the failure counter")
    void correctCredentialsCreateASessionAndResetTheCounter() throws Exception {
        accounts.setState("johndoe", true, 3, null);
        Session session = SessionClient.fetchCsrf(mvc);

        MvcTestResult result = login(session, "johndoe", "Password123!");

        assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"username":"johndoe","role":"USER"}
                """);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SESSION=", "HttpOnly");
        assertThat(sessionPrincipal(session)).isEqualTo("johndoe");
        assertThat(accounts.failedLoginAttempts("johndoe")).isZero();
    }

    @Test
    @DisplayName("[assessment/story2-ac2] a wrong password gets the generic 401 and increments the failure counter")
    void wrongPasswordIsRejectedGenericallyAndCounted() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        MvcTestResult result = login(session, "johndoe", "WrongPassword!");

        assertGenericFailure(result);
        assertNoAuthenticatedSession(session);
        assertThat(accounts.failedLoginAttempts("johndoe")).isEqualTo(1);
    }

    @Test
    @DisplayName("[assessment/story2-ac3] an unknown username gets a response identical to a wrong password")
    void unknownUsernameMatchesAWrongPasswordResponse() throws Exception {
        MvcTestResult wrongPassword = login(SessionClient.fetchCsrf(mvc), "johndoe", "Password123!x");
        accounts.resetJohndoe();
        int attemptsBefore = totalFailedAttempts();

        MvcTestResult unknown = login(SessionClient.fetchCsrf(mvc), "nosuchuser", "Password123!");

        assertGenericFailure(unknown);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(wrongPassword.getResponse().getStatus());
        assertThat(headers(unknown.getResponse())).isEqualTo(headers(wrongPassword.getResponse()));
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
        assertThat(totalFailedAttempts()).isEqualTo(attemptsBefore);
    }

    @Test
    @DisplayName("[assessment/story2-ac4] a locked account is rejected even with correct credentials")
    void lockedAccountIsRejected() throws Exception {
        accounts.setState("johndoe", true, 5, Instant.now().plus(Duration.ofMinutes(10)));
        Session session = SessionClient.fetchCsrf(mvc);

        assertGenericFailure(login(session, "johndoe", "Password123!"));
        assertNoAuthenticatedSession(session);
    }

    @Test
    @DisplayName("[assessment/story2-ac5] a disabled account is rejected even with correct credentials")
    void disabledAccountIsRejected() throws Exception {
        accounts.setState("johndoe", false, 0, null);
        Session session = SessionClient.fetchCsrf(mvc);

        assertGenericFailure(login(session, "johndoe", "Password123!"));
        assertNoAuthenticatedSession(session);
    }

    @Test
    @DisplayName("[assessment/story2-ac6] username matching at login is case-insensitive")
    void usernameMatchingIsCaseInsensitive() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        assertThat(login(session, "JohnDoe", "Password123!")).hasStatusOk();
        assertThat(sessionPrincipal(session)).isEqualTo("johndoe");
    }

    @Test
    void aPasswordOverSeventyTwoBytesIsAnInvalidLoginRequest() throws Exception {
        MvcTestResult result = login(SessionClient.fetchCsrf(mvc), "johndoe", "é".repeat(37));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
                {"message":"Invalid login request"}
                """);
        assertThat(accounts.failedLoginAttempts("johndoe")).isZero();
    }

    private MvcTestResult login(Session session, String username, String password) {
        return session.login(
                mvc, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private String sessionPrincipal(Session session) {
        return jdbc.queryForObject(
                "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE SESSION_ID = ?", String.class, session.sessionId());
    }

    private void assertNoAuthenticatedSession(Session session) {
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, "johndoe"))
                .isZero();
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private int totalFailedAttempts() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(failed_login_attempts), 0) FROM users", Integer.class);
    }

    private static void assertGenericFailure(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(GENERIC_FAILURE);
    }

    /** Header names and values, with the random session ID in Set-Cookie masked. */
    private static Map<String, Object> headers(MockHttpServletResponse response) {
        Map<String, Object> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name).stream()
                    .map(value -> value.replaceAll("SESSION=[^;]*", "SESSION=<id>"))
                    .toList());
        }
        return headers;
    }
}
