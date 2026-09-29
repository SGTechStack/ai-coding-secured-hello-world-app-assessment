package com.eitri.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.passwordreset.PasswordResetTestHashes;
import com.eitri.testsupport.CapturingEmailService;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.LoggedIn;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Every security-relevant action writes one structured AUDIT line that names accounts by username and id. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audit-contract;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(CapturingEmailService.Config.class)
class AuditContractIT {

    private static final String NEW_PASSWORD = "a-brand-new-passphrase";
    private static final String WRONG_PASSWORD = "WrongPassword!";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingEmailService email;

    /** Values that must never appear in an audit line, collected while each action runs. */
    private final List<String> secrets = new ArrayList<>();

    private UUID adminId;

    enum Row {
        LOGIN_SUCCESS("User authentication succeeded", "user-authentication", "success", false),
        LOGIN_FAILURE("User authentication failed", "user-authentication", "failure", false),
        LOCKOUT("Account locked after failed authentication attempts", "account-lockout", "failure", false),
        RESET_REQUEST("Password reset requested", "password-reset-request", "success", false),
        RESET_COMPLETE("Password reset completed", "password-reset-complete", "success", false),
        ROLE_CHANGE("User role changed", "user-role-change", "success", true),
        ENABLE("User account enabled", "user-enable", "success", true),
        DISABLE("User account disabled", "user-disable", "success", true),
        DELETE("User account deleted", "user-delete", "success", true);

        final String message;
        final String action;
        final String outcome;
        final boolean adminAction;

        Row(String message, String action, String outcome, boolean adminAction) {
            this.message = message;
            this.action = action;
            this.outcome = outcome;
            this.adminAction = adminAction;
        }
    }

    @BeforeEach
    void seed() {
        TestAccounts accounts = new TestAccounts(jdbc);
        accounts.restoreSeed();
        adminId = accounts.idOf("admin");
        email.clear();
        secrets.clear();
        secrets.addAll(List.of(TestAccounts.JOHNDOE_PASSWORD, TestAccounts.ADMIN_PASSWORD, WRONG_PASSWORD, NEW_PASSWORD));
        for (Map<String, Object> row : jdbc.queryForList("SELECT password_hash FROM users")) {
            secrets.add(((String) row.get("PASSWORD_HASH")).substring("{bcrypt}".length()));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Row.class)
    @DisplayName("[assessment/story13-ac6] security-relevant actions emit structured audit lines without secrets")
    void actionWritesASafeStructuredAuditLine(Row row) throws Exception {
        LoggedIn admin = row.adminAction ? remember(SessionClient.loggedIn(mvc, "admin", TestAccounts.ADMIN_PASSWORD)) : null;
        if (row == Row.RESET_COMPLETE) {
            requestReset();
        }

        String line;
        try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
            perform(row, admin);
            line = audit.line(row.message);
        }

        assertThat(JsonPath.<String>read(line, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(line, "$.event.action")).isEqualTo(row.action);
        assertThat(JsonPath.<String>read(line, "$.event.outcome")).isEqualTo(row.outcome);
        // "identifies the user johndoe" / "actor admin, target johndoe": by username, with the id alongside.
        if (row.adminAction) {
            assertThat(JsonPath.<String>read(line, "$.actor.name")).isEqualTo("admin");
            assertThat(JsonPath.<String>read(line, "$.actor.id")).isEqualTo(adminId.toString());
            assertThat(JsonPath.<String>read(line, "$.target.name")).isEqualTo("johndoe");
            assertThat(JsonPath.<String>read(line, "$.target.id")).isEqualTo(TestAccounts.JOHNDOE_ID.toString());
        } else {
            assertThat(JsonPath.<String>read(line, "$.user.name")).isEqualTo("johndoe");
            assertThat(JsonPath.<String>read(line, "$.user.id")).isEqualTo(TestAccounts.JOHNDOE_ID.toString());
        }
        assertThat(line).doesNotContain(secrets).doesNotContain("\"password\"", "\"token\"", "$2a$");
    }

    private void perform(Row row, LoggedIn admin) throws Exception {
        String johndoe = TestAccounts.JOHNDOE_ID.toString();
        switch (row) {
            case LOGIN_SUCCESS -> login(TestAccounts.JOHNDOE_PASSWORD);
            case LOGIN_FAILURE -> login(WRONG_PASSWORD);
            case LOCKOUT -> {
                jdbc.update("UPDATE users SET failed_login_attempts = 4 WHERE id = ?", TestAccounts.JOHNDOE_ID);
                login(WRONG_PASSWORD);
            }
            case RESET_REQUEST -> {
                requestReset();
                remember(email.last().token());
                remember(PasswordResetTestHashes.hash(email.last().token()));
            }
            case RESET_COMPLETE -> post("/api/v1/auth/password-reset/confirm",
                    "{\"token\":\"" + email.last().token() + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}");
            case ROLE_CHANGE -> admin.send(mvc, HttpMethod.PATCH, "/api/v1/admin/users/" + johndoe + "/role",
                    "{\"role\":\"ADMIN\"}");
            case ENABLE -> admin.send(mvc, HttpMethod.PATCH, "/api/v1/admin/users/" + johndoe + "/status",
                    "{\"enabled\":true}");
            case DISABLE -> admin.send(mvc, HttpMethod.PATCH, "/api/v1/admin/users/" + johndoe + "/status",
                    "{\"enabled\":false}");
            case DELETE -> admin.send(mvc, HttpMethod.DELETE, "/api/v1/admin/users/" + johndoe, null);
        }
    }

    private void login(String password) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        remember(session);
        session.login(mvc, "{\"username\":\"johndoe\",\"password\":\"" + password + "\"}");
        secrets.add(session.sessionId());
    }

    private void requestReset() throws Exception {
        post("/api/v1/auth/password-reset/request", "{\"email\":\"john@example.com\"}");
        remember(email.last().token());
        remember(PasswordResetTestHashes.hash(email.last().token()));
    }

    private void post(String path, String body) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        remember(session);
        mvc.post()
                .uri(path)
                .cookie(session.cookie())
                .header(session.headerName(), session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private void remember(Session session) {
        secrets.add(session.sessionId());
        secrets.add(session.token());
    }

    private LoggedIn remember(LoggedIn loggedIn) {
        secrets.add(SessionClient.sessionId(loggedIn.cookie()));
        secrets.add(loggedIn.token());
        return loggedIn;
    }

    private void remember(String secret) {
        secrets.add(secret);
    }
}
