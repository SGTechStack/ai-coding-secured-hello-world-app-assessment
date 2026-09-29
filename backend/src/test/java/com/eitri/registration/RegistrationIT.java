package com.eitri.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.MutableClock;
import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registration;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(MutableClock.Config.class)
class RegistrationIT {

    private static final String STRONG_PASSWORD = "correct-horse-battery";
    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00.123456Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private TestAccounts accounts;

    @BeforeEach
    void reset() {
        clock.set(NOW);
        accounts = new TestAccounts(jdbc);
        jdbc.update("DELETE FROM users WHERE username <> 'johndoe'");
        accounts.resetJohndoe();
    }

    @Test
    @DisplayName("[assessment/story1-ac1] a unique username, unique email and strong password create a USER account")
    void registrationCreatesAnEnabledUserAccount() throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);

        MvcTestResult result = register(session, json("janedoe", "jane@example.com", STRONG_PASSWORD));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = result.getResponse().getContentAsString();
        Map<String, Object> row = accounts.row("janedoe");
        assertThat(row)
                .containsEntry("EMAIL", "jane@example.com")
                .containsEntry("ROLE", "USER")
                .containsEntry("ENABLED", true)
                .containsEntry("FAILED_LOGIN_ATTEMPTS", 0)
                .containsEntry("LOCKED_UNTIL", null);
        assertThat(((java.time.OffsetDateTime) row.get("CREATED_AT")).toInstant()).isEqualTo(NOW);
        String hash = (String) row.get("PASSWORD_HASH");
        assertThat(hash).startsWith("{bcrypt}$2");
        assertThat(TestAccounts.bcryptMatches(STRONG_PASSWORD, hash)).isTrue();

        assertThat(JsonPath.<String>read(body, "$.id")).isEqualTo(row.get("ID").toString());
        assertThat(JsonPath.<Map<String, Object>>read(body, "$"))
                .containsOnlyKeys("id", "username", "email", "role", "enabled", "createdAt")
                .containsEntry("username", "janedoe")
                .containsEntry("email", "jane@example.com")
                .containsEntry("role", "USER")
                .containsEntry("enabled", true)
                .containsEntry("createdAt", "2026-09-28T08:00:00.123456Z");
        assertThat(body).doesNotContain(STRONG_PASSWORD, hash.substring("{bcrypt}".length()));

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(session.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
        "johndoe, new@example.com, Username is already taken",
        "JohnDoe, new@example.com, Username is already taken",
        "newuser, john@example.com, Email is already registered",
        "newuser, JOHN@Example.com, Email is already registered",
        "johndoe, john@example.com, Username is already taken"
    })
    @DisplayName("[assessment/story1-ac2] an already-registered username or email is rejected")
    void duplicatesAreRejected(String username, String email, String message) throws Exception {
        int before = accounts.count();

        MvcTestResult result = register(SessionClient.fetchCsrf(mvc), json(username, email, STRONG_PASSWORD));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"message\":\"" + message + "\"}");
        assertThat(accounts.count()).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "'', Password is required",
        "abcdefghijk, Password must be at least 12 characters",
        "a73, Password must be at most 72 bytes",
        "missing, Password is required"
    })
    @DisplayName("[assessment/story1-ac3] a password failing the strength policy is rejected")
    void weakPasswordsAreRejected(String password, String message) throws Exception {
        String body = switch (password) {
            case "a73" -> json("janedoe", "jane@example.com", "a".repeat(73));
            case "missing" -> "{\"username\":\"janedoe\",\"email\":\"jane@example.com\"}";
            default -> json("janedoe", "jane@example.com", password);
        };

        assertRejected(body, message);
    }

    @ParameterizedTest(name = "{0} characters")
    @CsvSource({"12", "72"})
    @DisplayName("[assessment/story1-ac4] passwords at the policy boundaries are accepted")
    void boundaryPasswordsAreAccepted(int length) throws Exception {
        assertThat(register(SessionClient.fetchCsrf(mvc), json("janedoe", "jane@example.com", "p".repeat(length))))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void aSeventyTwoByteMultiByteBoundaryIsAcceptedAndOneMoreByteIsNot() throws Exception {
        assertThat(register(SessionClient.fetchCsrf(mvc), json("janedoe", "jane@example.com", "é".repeat(36))))
                .hasStatus(HttpStatus.CREATED);
        assertRejected(json("jane2", "jane2@example.com", "é".repeat(36) + "a"),
                "Password must be at most 72 bytes");
    }

    @ParameterizedTest(name = "[{0}] / [{1}]")
    @CsvSource({
        "'', jane@example.com, Username is required",
        "jane doe, jane@example.com, Username is invalid",
        "<script>, jane@example.com, Username is invalid",
        "janedoe, '', Email is required",
        "janedoe, not-an-email, Email is invalid",
        "'', not-an-email, Username is required"
    })
    @DisplayName("[assessment/story1-ac5] a malformed username or email is rejected")
    void malformedUsernameOrEmailIsRejected(String username, String email, String message) throws Exception {
        assertRejected(json(username, email, STRONG_PASSWORD), message);
    }

    @Test
    void missingFieldsAndOverlongValuesAreRejected() throws Exception {
        assertRejected("{}", "Username is required");
        assertRejected("{\"username\":\"janedoe\",\"password\":\"" + STRONG_PASSWORD + "\"}",
                "Email is required");
        assertRejected(json("j".repeat(65), "jane@example.com", STRONG_PASSWORD),
                "Username is invalid");
        assertRejected(json("janedoe", "j".repeat(243) + "@example.com", STRONG_PASSWORD),
                "Email is invalid");
        assertThat(register(SessionClient.fetchCsrf(mvc), "not json")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"message\":\"Bad Request\"}");
    }

    @Test
    @DisplayName("[assessment/story1-ac6] client-supplied role or account state is ignored")
    void clientSuppliedStateIsIgnored() throws Exception {
        String body = """
                {"username":"janedoe","email":"jane@example.com","password":"correct-horse-battery",
                 "role":"ADMIN","enabled":false,"failed_login_attempts":-99,"id":"00000000-0000-0000-0000-000000000001"}
                """;

        assertThat(register(SessionClient.fetchCsrf(mvc), body)).hasStatus(HttpStatus.CREATED);

        assertThat(accounts.row("janedoe"))
                .containsEntry("ROLE", "USER")
                .containsEntry("ENABLED", true)
                .containsEntry("FAILED_LOGIN_ATTEMPTS", 0);
        assertThat(accounts.idOf("janedoe").toString()).isNotEqualTo("00000000-0000-0000-0000-000000000001");
    }

    @Test
    @DisplayName("[assessment/story1-ac7] the plaintext password is never logged or stored")
    void plaintextPasswordIsNeverLoggedOrStored(CapturedOutput output) throws Exception {
        String canary = "plaintext-canary-password";
        int logStart = output.getAll().length();

        assertThat(register(SessionClient.fetchCsrf(mvc), json("janedoe", "jane@example.com", canary)))
                .hasStatus(HttpStatus.CREATED);
        assertRejected(json("jane doe", "x@example.com", canary),
                "Username is invalid");

        assertThat(output.getAll().substring(logStart)).doesNotContain(canary);
        for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM users")) {
            assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains(canary));
        }
    }

    @Test
    void theNewAccountCanLogIn() throws Exception {
        assertThat(register(SessionClient.fetchCsrf(mvc), json("JaneDoe", "Jane@Example.com", STRONG_PASSWORD)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.username").isEqualTo("janedoe");

        assertThat(SessionClient.fetchCsrf(mvc)
                        .login(mvc, "{\"username\":\"janedoe\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .hasStatusOk();
        assertThat(accounts.row("janedoe")).containsEntry("EMAIL", "jane@example.com");
    }

    @Test
    void registrationRequiresCsrf() {
        int before = accounts.count();

        assertThat(mvc.post()
                        .uri("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("janedoe", "jane@example.com", STRONG_PASSWORD)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(accounts.count()).isEqualTo(before);
    }

    private void assertRejected(String body, String message) throws Exception {
        int before = accounts.count();
        MvcTestResult result = register(SessionClient.fetchCsrf(mvc), body);
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"message\":\"" + message + "\"}");
        assertThat(accounts.count()).isEqualTo(before);
    }

    private MvcTestResult register(Session session, String body) {
        return mvc.post()
                .uri("/api/v1/auth/register")
                .cookie(session.cookie())
                .header(session.headerName(), session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String json(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

}
