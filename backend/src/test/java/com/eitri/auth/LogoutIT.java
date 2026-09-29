package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:logout;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogoutIT {

    private static final String LOGIN_JSON = """
            {"username":"johndoe","password":"Password123!"}
            """;
    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_HASH_KEY = "test-only-session-hash-placeholder-not-for-production";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[assessment/story4-ac1] logout invalidates the session, expires the cookie and is audited")
    void logoutInvalidatesTheSessionDeletesItsCookieAndWritesASafeAuditEvent() throws Exception {
        Session session = authenticatedSession();
        String sessionId = session.sessionId();
        Csrf csrf = refreshCsrf(session);

        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            MvcTestResult result = logout(session, csrf);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result.getResponse().getHeader("Clear-Site-Data"))
                .isEqualTo("\"cache\",\"cookies\",\"storage\"");
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(header -> assertThat(header)
                        .contains("SESSION=")
                        .contains("Max-Age=0"));
        assertThat(sessionRowCount(sessionId)).isZero();
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie()))
                .hasStatus(HttpStatus.UNAUTHORIZED);

            String event = audit.line("User logged out");
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("INFO");
        assertThat(JsonPath.<String>read(event, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(event, "$.event.kind")).isEqualTo("event");
        assertThat(JsonPath.<String>read(event, "$.event.category")).isEqualTo("authentication");
        assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("user-logout");
        assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("success");
        assertThat(JsonPath.<String>read(event, "$.user.id")).isEqualTo(ACCOUNT_ID);
        assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
        assertThat(JsonPath.<String>read(event, "$.session.hash")).isEqualTo(hmac(sessionId));
            assertThat(event)
                    .doesNotContain("Password123!", csrf.token(), sessionId)
                    .doesNotContain("\"username\"", "\"password\"");
        }
    }

    @Test
    void logoutWithoutCsrfIsForbiddenAndLeavesTheSessionUsable() throws Exception {
        Session session = authenticatedSession();

        assertThat(mvc.post().uri("/api/v1/auth/logout").cookie(session.cookie()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie()))
                .hasStatusOk();
    }

    @Test
    void logoutForAnExpiredSessionIsUnauthorized() throws Exception {
        Session session = authenticatedSession();
        Csrf csrf = refreshCsrf(session);
        jdbc.update("DELETE FROM SPRING_SESSION WHERE SESSION_ID = ?", session.sessionId());

        assertThat(logout(session, csrf)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"GET, /api/v1/hello", "GET, /api/v1/auth/me", "POST, /api/v1/auth/logout"})
    @DisplayName("[assessment/story4-ac2] a session cookie captured before logout is rejected when replayed")
    void capturedCookieIsRejectedAfterLogout(String method, String path) throws Exception {
        Session session = authenticatedSession();
        Csrf csrf = refreshCsrf(session);
        jakarta.servlet.http.Cookie captured = session.cookie();
        assertThat(logout(session, csrf)).hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult replay = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(method), path)
                        .cookie(captured)
                        .header(csrf.headerName(), csrf.token()));

        assertThat(replay).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[assessment/story4-ac3] logout without an authenticated session is rejected")
    void logoutWithoutASessionIsUnauthorized() {
        assertThat(mvc.post().uri("/api/v1/auth/logout")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousRequestWithAValidCsrfTokenDoesNotReachTheLogoutHandler() throws Exception {
        Session anonymous = SessionClient.fetchCsrf(mvc);

        assertThat(logout(anonymous, new Csrf(anonymous.headerName(), anonymous.token())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private Session authenticatedSession() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, LOGIN_JSON)).hasStatusOk();
        return session;
    }

    private Csrf refreshCsrf(Session session) throws Exception {
        MvcTestResult result = mvc.get()
                .uri("/api/v1/csrf")
                .cookie(session.cookie())
                .exchange();
        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        return new Csrf(JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    private MvcTestResult logout(Session session, Csrf csrf) {
        return mvc.post()
                .uri("/api/v1/auth/logout")
                .cookie(session.cookie())
                .header(csrf.headerName(), csrf.token())
                .exchange();
    }

    private int sessionRowCount(String sessionId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, sessionId);
    }

    private static String hmac(String sessionId) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(TEST_HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8)));
    }

    private record Csrf(String headerName, String token) {}
}
