package com.eitri.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.CapturingEmailService;
import com.eitri.testsupport.MutableClock;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password-reset-confirm;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import({MutableClock.Config.class, CapturingEmailService.Config.class})
class PasswordResetConfirmIT {

    private static final String TOKEN = "valid-reset-token";
    private static final String OLD_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "a-brand-new-passphrase";
    private static final String INVALID_TOKEN = "{\"message\":\"Invalid or expired reset token\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private CapturingEmailService email;

    private Instant issuedAt;

    /** Background: johndoe was issued "valid-reset-token" 10 minutes ago with a 30 minute lifetime. */
    @BeforeEach
    void issueToken() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        clock.set(now);
        issuedAt = now.minus(Duration.ofMinutes(10));
        email.clear();
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM SPRING_SESSION");
        new TestAccounts(jdbc).resetJohndoe();
        jdbc.update(
                "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                TestAccounts.JOHNDOE_ID,
                PasswordResetService.hash(TOKEN),
                Timestamp.from(issuedAt.plus(Duration.ofMinutes(30))));
    }

    @Test
    @DisplayName("[assessment/story7-ac1] a valid token sets the new password, is consumed, and ends all sessions")
    void validTokenResetsThePasswordAndEndsAllSessions() throws Exception {
        Session otherDevice = login(OLD_PASSWORD);
        assertThat(otherDevice).isNotNull();

        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            assertThat(confirm(TOKEN, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

            assertThat(TestAccounts.bcryptMatches(NEW_PASSWORD, passwordHash())).isTrue();
            assertThat(usedAt()).isEqualTo(clock.instant());
            assertThat(mvc.get().uri("/api/v1/hello").cookie(otherDevice.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(tryLogin(OLD_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(tryLogin(NEW_PASSWORD)).hasStatusOk();

            String event = audit.line("Password reset completed");
            assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo("password-reset-complete");
            assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("success");
            assertThat(JsonPath.<String>read(event, "$.user.id")).isEqualTo(TestAccounts.JOHNDOE_ID.toString());
            assertThat(JsonPath.<String>read(event, "$.user.name")).isEqualTo("johndoe");
            assertThat(event).doesNotContain(TOKEN, PasswordResetService.hash(TOKEN), NEW_PASSWORD);
        }
    }

    @Test
    @DisplayName("[assessment/story7-ac2] an expired token is rejected and the password is unchanged")
    void expiredTokenIsRejected() throws Exception {
        clock.set(issuedAt.plus(Duration.ofMinutes(31)));

        assertInvalidToken(confirm(TOKEN, NEW_PASSWORD));
        assertThat(TestAccounts.bcryptMatches(OLD_PASSWORD, passwordHash())).isTrue();
    }

    @Test
    void aTokenIsExpiredExactlyAtItsExpiryTime() throws Exception {
        clock.set(issuedAt.plus(Duration.ofMinutes(30)));

        assertInvalidToken(confirm(TOKEN, NEW_PASSWORD));
    }

    @Test
    @DisplayName("[assessment/story7-ac3] a token that has already been used is rejected")
    void usedTokenIsRejected() throws Exception {
        assertThat(confirm(TOKEN, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertInvalidToken(confirm(TOKEN, "yet-another-passphrase"));
        assertThat(TestAccounts.bcryptMatches(NEW_PASSWORD, passwordHash())).isTrue();
    }

    @Test
    @DisplayName("[assessment/story7-ac4] an unknown token is rejected")
    void unknownTokenIsRejected() throws Exception {
        assertInvalidToken(confirm("not-a-real-token", NEW_PASSWORD));
        assertInvalidToken(confirm("", NEW_PASSWORD));
        assertThat(TestAccounts.bcryptMatches(OLD_PASSWORD, passwordHash())).isTrue();
    }

    @Test
    @DisplayName("[assessment/story7-ac5] a new password failing the policy is rejected without consuming the token")
    void weakPasswordDoesNotConsumeTheToken() throws Exception {
        MvcTestResult result = confirm(TOKEN, "short");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"message\":\"Password must be at least 12 characters\"}");
        assertThat(usedAt()).isNull();
        assertThat(TestAccounts.bcryptMatches(OLD_PASSWORD, passwordHash())).isTrue();
        assertThat(confirm(TOKEN, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void theEmailedTokenWorksEndToEndAndLeavesLockoutStateAlone() throws Exception {
        jdbc.update("UPDATE users SET failed_login_attempts = 3 WHERE id = ?", TestAccounts.JOHNDOE_ID);
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(mvc.post()
                        .uri("/api/v1/auth/password-reset/request")
                        .cookie(session.cookie())
                        .header(session.headerName(), session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john@example.com\"}"))
                .hasStatus(HttpStatus.ACCEPTED);

        assertThat(confirm(email.last().token(), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(TestAccounts.bcryptMatches(NEW_PASSWORD, passwordHash())).isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, TestAccounts.JOHNDOE_ID))
                .isEqualTo(3);
        // The request replaced the older unused token.
        assertInvalidToken(confirm(TOKEN, "yet-another-passphrase"));
    }

    @Test
    void confirmationRequiresCsrf() {
        assertThat(mvc.post()
                        .uri("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(usedAt()).isNull();
    }

    private void assertInvalidToken(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponse().getContentAsString()).isEqualTo(INVALID_TOKEN);
    }

    private MvcTestResult confirm(String token, String newPassword) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        return mvc.post()
                .uri("/api/v1/auth/password-reset/confirm")
                .cookie(session.cookie())
                .header(session.headerName(), session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}")
                .exchange();
    }

    private Session login(String password) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, "{\"username\":\"johndoe\",\"password\":\"" + password + "\"}"))
                .hasStatusOk();
        return session;
    }

    private MvcTestResult tryLogin(String password) throws Exception {
        return SessionClient.fetchCsrf(mvc)
                .login(mvc, "{\"username\":\"johndoe\",\"password\":\"" + password + "\"}");
    }

    private String passwordHash() {
        return jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, TestAccounts.JOHNDOE_ID);
    }

    private Instant usedAt() {
        Timestamp value = jdbc.queryForObject(
                "SELECT MAX(used_at) FROM password_reset_tokens WHERE token_hash = ?",
                Timestamp.class,
                PasswordResetService.hash(TOKEN));
        return value == null ? null : value.toInstant();
    }
}
