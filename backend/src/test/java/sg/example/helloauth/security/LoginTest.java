package sg.example.helloauth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class LoginTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @Test
    void regularUserLogsInAndSeesTheGreeting() {
        Browser browser = newBrowser().registered(ALICE);

        assertThat(browser.login(ALICE)).hasStatusOk();

        assertThat(browser.get("/hello")).hasStatusOk().hasBodyTextEqualTo("Hello, testuser1");
    }

    @Test
    void usernameMatchesTheSameAccountWhateverItsCase() {
        newBrowser().registered(new TestAccount("TestUser1", ALICE.email(), ALICE.password()));
        String id = jdbc.sql("SELECT id FROM users").query(String.class).single();

        for (String typed : new String[] {"TestUser1", "testuser1", "TESTUSER1"}) {
            Browser browser = newBrowser();
            browser.fetchCsrf();
            assertThat(browser.login(typed, ALICE.password())).hasStatusOk();
            assertThat(browser.get("/me")).bodyJson()
                    .hasPathSatisfying("$.id", actual -> assertThat(actual).isEqualTo(id))
                    .hasPathSatisfying("$.username", actual -> assertThat(actual).isEqualTo("TestUser1"));
        }
    }

    @Test
    void loginIssuesANewSessionId() {
        Browser browser = newBrowser().registered(ALICE);
        Cookie beforeLogin = browser.sessionCookie();

        browser.login(ALICE);

        assertThat(browser.sessionCookie().getValue()).isNotEqualTo(beforeLogin.getValue());
        MvcTestResult withPlantedSession = mvc.get().uri(basePath + "/hello").cookie(beforeLogin).exchange();
        assertThat(withPlantedSession).hasStatus(401);
    }

    @Test
    void secondLoginEndsTheFirstSession() {
        Browser first = newBrowser().registerAndLogin(ALICE);
        Browser second = newBrowser();
        second.fetchCsrf();

        assertThat(second.login(ALICE)).hasStatusOk();

        assertProblem(first.get("/hello"), 401, "unauthenticated");
        assertThat(second.get("/hello")).hasStatusOk();
    }

    @Test
    void sessionIsStoredInTheDatabase() {
        newBrowser().registered(ALICE).login(ALICE);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = 'testuser1'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndSameSiteLax() {
        Browser browser = newBrowser().registered(ALICE);

        browser.login(ALICE);

        Cookie session = browser.sessionCookie();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isTrue();
        assertThat(session.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void wrongPasswordAndUnknownUsernameGetTheSameInvalidCredentialsError() {
        Browser browser = newBrowser().registered(ALICE);

        MvcTestResult wrongPassword = browser.login(ALICE.username(), "not-the-password");
        MvcTestResult unknownUsername = browser.login("nobody", ALICE.password());

        assertProblem(wrongPassword, 401, "invalid credentials");
        assertProblem(unknownUsername, 401, "invalid credentials");
        assertThat(wrongPassword.getResponse().getContentAsByteArray())
                .isEqualTo(unknownUsername.getResponse().getContentAsByteArray());
        assertThat(browser.get("/hello")).hasStatus(401);
    }

    @Test
    void helloWithoutASessionIsUnauthenticated() {
        assertProblem(newBrowser().get("/hello"), 401, "unauthenticated");
    }

    @Test
    void meReturnsOnlyTheCallersOwnDetails() {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        String id = jdbc.sql("SELECT id FROM users WHERE username = 'testuser1'").query(String.class).single();

        MvcTestResult result = browser.get("/me");

        assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"id":"%s","username":"testuser1","email":"testuser1@test.example.com","role":"USER"}"""
                .formatted(id));
    }

    @Test
    void meWithoutASessionIsUnauthenticated() {
        assertProblem(newBrowser().get("/me"), 401, "unauthenticated");
    }
}
