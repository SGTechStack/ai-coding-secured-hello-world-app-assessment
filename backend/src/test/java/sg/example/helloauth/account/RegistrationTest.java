package sg.example.helloauth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.AbstractJsonContentAssert;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class RegistrationTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);
    private static final TestAccount BOB = TestAccount.testUser(2);

    private Browser visitor() {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        return browser;
    }

    /** The list at this JSON path holds exactly these values, in any order. */
    private static void assertListAt(AbstractJsonContentAssert<?> problem, String path, Object... expected) {
        problem.hasPathSatisfying(path, actual -> assertThat(actual)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder(expected));
    }

    private static void assertFailingFields(AbstractJsonContentAssert<?> problem, String... fields) {
        assertListAt(problem, "$.errors[*].field", (Object[]) fields);
    }

    private static Map<String, String> error(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    /** The problem lists exactly these field errors, in any order. */
    @SafeVarargs
    private static void assertErrors(AbstractJsonContentAssert<?> problem, Map<String, String>... errors) {
        assertListAt(problem, "$.errors", (Object[]) errors);
    }

    private int accountCount() {
        return jdbc.sql("SELECT COUNT(*) FROM users").query(Integer.class).single();
    }

    @Test
    void visitorRegistersAnEnabledUnlockedRegularUserWithABcryptHash() {
        assertThat(visitor().register(ALICE)).hasStatus(201);

        Map<String, Object> account = jdbc.sql("SELECT * FROM users").query().singleRow();
        assertThat(account).containsEntry("USERNAME", "testuser1")
                .containsEntry("EMAIL", "testuser1@test.example.com")
                .containsEntry("ROLE", "USER")
                .containsEntry("ENABLED", true)
                .containsEntry("FAILED_LOGIN_ATTEMPTS", 0)
                .containsEntry("LOCKED_UNTIL", null)
                .containsEntry("DELETED_AT", null);
        // Timestamps are stored as UTC wall-clock time.
        assertThat(((Timestamp) account.get("CREATED_AT")).toLocalDateTime())
                .isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        assertThat((String) account.get("PASSWORD_HASH")).startsWith("{bcrypt}$2a$12$")
                .doesNotContain(ALICE.password());
    }

    @Test
    void registeringDoesNotLogTheVisitorIn() {
        Browser browser = visitor();
        browser.register(ALICE);

        assertThat(browser.get("/hello")).hasStatus(401);
    }

    @Test
    void takenUsernameOrEmailGetsTheSameUserExistError() {
        Browser browser = visitor();
        browser.register(ALICE);

        MvcTestResult usernameClash = browser.register(ALICE.username(), BOB.email(), BOB.password());
        MvcTestResult emailClash = browser.register(BOB.username(), ALICE.email(), BOB.password());

        assertProblem(usernameClash, 400, "user exist");
        assertProblem(emailClash, 400, "user exist");
        assertThat(usernameClash.getResponse().getContentAsByteArray())
                .isEqualTo(emailClash.getResponse().getContentAsByteArray());
        assertThat(accountCount()).isEqualTo(1);
    }

    @Test
    void validationErrorListsEachFailingField() {
        MvcTestResult result = visitor().register("", "not-an-email", "");

        assertFailingFields(assertProblem(result, 400, "validation failed"), "username", "email", "password");
        assertThat(accountCount()).isZero();
    }

    @Test
    void tooLongUsernameAndEmailAreValidationErrorsNotAClash() {
        String username = "u".repeat(33);
        // Well-formed, but 255 characters: one more than the column holds.
        String email = "t".repeat(64) + "@" + "d".repeat(63) + "." + "e".repeat(63) + "." + "f".repeat(50) + ".example.com";

        MvcTestResult result = visitor().register(username, email, ALICE.password());

        assertFailingFields(assertProblem(result, 400, "validation failed"), "username", "email");
    }

    @Test
    void passwordLongerThan72BytesIsAValidationError() {
        // 25 characters, but 75 bytes of UTF-8: BCrypt only uses the first 72 bytes.
        String password = "€".repeat(25);

        MvcTestResult result = visitor().register(ALICE.username(), ALICE.email(), password);

        assertErrors(assertProblem(result, 400, "validation failed"),
                error("password", "must be at most 72 bytes of UTF-8"));
        assertThat(accountCount()).isZero();
    }

    @Test
    void passwordShorterThan12CharactersIsAValidationError() {
        MvcTestResult result = visitor().register(ALICE.username(), ALICE.email(), "11-chars-xy");

        assertErrors(assertProblem(result, 400, "validation failed"),
                error("password", "must be at least 12 characters"));
        assertThat(accountCount()).isZero();
    }

    @Test
    void passwordAtBothLengthLimitsIsAccepted() {
        assertThat(visitor().register(ALICE.username(), ALICE.email(), "12-chars-xyz")).hasStatus(201);
        // 24 characters and exactly 72 bytes of UTF-8.
        assertThat(visitor().register(BOB.username(), BOB.email(), "€".repeat(24))).hasStatus(201);
    }

    @Test
    void compromisedPasswordIsAValidationError() {
        MvcTestResult result = visitor().register(ALICE.username(), ALICE.email(), "passwordpassword");

        assertErrors(assertProblem(result, 400, "validation failed"), error("password", "must not be a password known from data breaches"));
        assertThat(accountCount()).isZero();
    }

    @Test
    void everyFailingPasswordRuleIsListed() {
        MvcTestResult result = visitor().register(ALICE.username(), ALICE.email(), "password");

        assertErrors(assertProblem(result, 400, "validation failed"),
                error("password", "must be at least 12 characters"),
                error("password", "must not be a password known from data breaches"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "semi;colon", "ünïcode"})
    void usernameOutsideTheAllowedPatternIsAValidationError(String username) {
        MvcTestResult result = visitor().register(username, ALICE.email(), ALICE.password());

        assertFailingFields(assertProblem(result, 400, "validation failed"), "username");
        assertThat(accountCount()).isZero();
    }

    @Test
    void usernameAtBothLengthLimitsWithEveryAllowedSymbolIsAccepted() {
        assertThat(visitor().register("a.b", ALICE.email(), ALICE.password())).hasStatus(201);
        assertThat(visitor().register("Aa0._-" + "x".repeat(26), BOB.email(), BOB.password())).hasStatus(201);
    }

    @Test
    void usernameDifferingOnlyInCaseGetsTheSameUserExistError() {
        Browser browser = visitor();
        browser.register(ALICE);

        MvcTestResult caseClash = browser.register("TestUser1", BOB.email(), BOB.password());
        MvcTestResult emailClash = browser.register(BOB.username(), ALICE.email(), BOB.password());

        assertProblem(caseClash, 400, "user exist");
        assertThat(caseClash.getResponse().getContentAsByteArray())
                .isEqualTo(emailClash.getResponse().getContentAsByteArray());
        assertThat(accountCount()).isEqualTo(1);
    }

    @Test
    void usernameIsStoredAsTyped() {
        visitor().register("TestUser1", ALICE.email(), ALICE.password());

        assertThat(jdbc.sql("SELECT username FROM users").query(String.class).single()).isEqualTo("TestUser1");
    }

    @Test
    void emailIsStoredLowercasedAndComparedWhateverItsCase() {
        Browser browser = visitor();
        browser.register(ALICE.username(), "TestUser1@Test.Example.COM", ALICE.password());

        MvcTestResult emailClash = browser.register(BOB.username(), "TESTUSER1@test.example.com", BOB.password());

        assertThat(jdbc.sql("SELECT email FROM users").query(String.class).single())
                .isEqualTo("testuser1@test.example.com");
        assertProblem(emailClash, 400, "user exist");
    }

    @Test
    void oversizedRequestBodyIsAValidationError() {
        Browser browser = visitor();
        String body = """
                {"username":"testuser1","email":"testuser1@test.example.com","password":"correct-horse-battery-1",\
                "padding":"%s"}""".formatted("x".repeat(20_000));

        MvcTestResult result = browser.send(browser.withCsrf(mvc.post().uri(basePath + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));

        assertProblem(result, 400, "validation failed")
                .hasPathSatisfying("$.detail", detail -> assertThat(detail).isEqualTo("The request body is too large."));
        assertThat(accountCount()).isZero();
    }

    /** A chunked body declares no size, so it could be any size at all. */
    @Test
    void chunkedRequestBodyIsAValidationError() {
        Browser browser = visitor();

        MvcTestResult result = browser.send(browser.withCsrf(mvc.post().uri(basePath + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Transfer-Encoding", "chunked")
                .content("""
                        {"username":"testuser1","email":"testuser1@test.example.com","password":"correct-horse-battery-1"}""")));

        assertProblem(result, 400, "validation failed")
                .hasPathSatisfying("$.detail", detail -> assertThat(detail).isEqualTo("The request body must declare its length."));
        assertThat(accountCount()).isZero();
    }

    @Test
    void malformedJsonIsAValidationErrorWithoutInternals() {
        Browser browser = visitor();

        MvcTestResult result = browser.send(browser.withCsrf(mvc.post().uri(basePath + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":")));

        assertProblem(result, 400, "validation failed");
        assertThat(result.getResponse().getContentAsByteArray()).asString()
                .doesNotContain("Exception", "jackson", "at sg.", "at org.");
    }
}
