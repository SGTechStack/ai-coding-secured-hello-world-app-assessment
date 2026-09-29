package com.eitri.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.CapturingEmailService;
import com.eitri.testsupport.MutableClock;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password-reset-request;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import({MutableClock.Config.class, CapturingEmailService.Config.class})
class PasswordResetRequestIT {

    static final String ACCEPTED =
            "{\"message\":\"If that email is registered, a password reset link has been sent.\"}";
    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00.123456Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private CapturingEmailService email;

    @BeforeEach
    void reset() {
        clock.set(NOW);
        email.clear();
        jdbc.update("DELETE FROM password_reset_tokens");
        new TestAccounts(jdbc).resetJohndoe();
    }

    @ParameterizedTest
    @ValueSource(strings = {"john@example.com", "JOHN@example.com", "nobody@example.com"})
    @DisplayName("[assessment/story6-ac1] the response is identical whether or not the email is registered")
    void responseIsIdenticalForAnyEmail(String address) throws Exception {
        MvcTestResult result = requestReset("{\"email\":\"" + address + "\"}");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(ACCEPTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"email\":\"\"}", "{\"email\":\"not-an-email\"}", "{\"email\":null}", "{\"email\":{}}",
        "{\"email\":42}", "[]", "not json", ""
    })
    void missingOrMalformedEmailsGetTheSameResponse(String body) throws Exception {
        MvcTestResult result = requestReset(body);

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(ACCEPTED);
        assertThat(tokenCount()).isZero();
        assertThat(email.sent()).isEmpty();
    }

    @Test
    @DisplayName("[assessment/story6-ac2] a registered email gets a single-use hashed token and a reset email")
    void registeredEmailGetsAHashedTokenAndAnEmail() throws Exception {
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            assertThat(requestReset("{\"email\":\"john@example.com\"}")).hasStatus(HttpStatus.ACCEPTED);

            assertThat(email.sent()).hasSize(1);
            CapturingEmailService.SentEmail sent = email.last();
            assertThat(sent.email()).isEqualTo("john@example.com");
            assertThat(sent.link()).startsWith("http://localhost:5173/reset-password?token=");
            String token = sent.token();
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);

            List<Map<String, Object>> rows =
                    jdbc.queryForList("SELECT * FROM password_reset_tokens WHERE user_id = ?", TestAccounts.JOHNDOE_ID);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.getFirst();
            assertThat(row.get("TOKEN_HASH")).isEqualTo(PasswordResetService.hash(token)).isNotEqualTo(token);
            assertThat(((OffsetDateTime) row.get("EXPIRES_AT")).toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
            assertThat(row.get("USED_AT")).isNull();
            assertPlaintextStoredNowhere(token);

            String event = audit.line("Password reset requested");
            assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("password-reset-request");
            assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("success");
            assertThat(JsonPath.<String>read(event, "$.user.id")).isEqualTo(TestAccounts.JOHNDOE_ID.toString());
            assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
            assertThat(event).doesNotContain(token, PasswordResetService.hash(token), "john@example.com");
        }
    }

    @Test
    void aNewRequestReplacesTheAccountsUnusedTokens() throws Exception {
        requestReset("{\"email\":\"john@example.com\"}");
        String first = email.last().token();
        requestReset("{\"email\":\"John@Example.com\"}");
        String second = email.last().token();

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForList("SELECT token_hash FROM password_reset_tokens", String.class))
                .containsExactly(PasswordResetService.hash(second));
    }

    @Test
    @DisplayName("[assessment/story6-ac3] an unregistered email creates no token and sends no email")
    void unregisteredEmailStoresAndSendsNothing() throws Exception {
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            requestReset("{\"email\":\"nobody@example.com\"}");

            assertThat(tokenCount()).isZero();
            assertThat(email.sent()).isEmpty();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> audit.line("Password reset requested"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void theEndpointIsPublicButRequiresCsrf() {
        assertThat(mvc.post()
                        .uri("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john@example.com\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(tokenCount()).isZero();
    }

    private void assertPlaintextStoredNowhere(String token) {
        for (String table : List.of("users", "password_reset_tokens")) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table)) {
                assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains(token));
            }
        }
    }

    private int tokenCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
    }

    private MvcTestResult requestReset(String body) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        return mvc.post()
                .uri("/api/v1/auth/password-reset/request")
                .cookie(session.cookie())
                .header(session.headerName(), session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
