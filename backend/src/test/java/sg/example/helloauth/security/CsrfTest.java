package sg.example.helloauth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class CsrfTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @Test
    void visitorCanFetchTheCsrfTokenAndItsHeaderName() {
        MvcTestResult result = newBrowser().fetchCsrf();

        assertThat(result).hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.token", token -> assertThat(token).asString().isNotBlank())
                .hasPathSatisfying("$.headerName", name -> assertThat(name).isEqualTo("X-CSRF-TOKEN"))
                .hasPathSatisfying("$.parameterName", name -> assertThat(name).isEqualTo("_csrf"));
    }

    @Test
    void csrfTokenIsHeldInTheSessionAndNeverInACookie() {
        MvcTestResult result = newBrowser().fetchCsrf();

        assertThat(result.getResponse().getCookies())
                .extracting(cookie -> cookie.getName())
                .containsExactly(Browser.SESSION_COOKIE);
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() {
        Browser browser = newBrowser();

        assertProblem(browser.register(ALICE), 403, "forbidden");
        assertProblem(browser.login(ALICE), 403, "forbidden");
        assertProblem(browser.requestPasswordReset(ALICE.email()), 403, "forbidden");
        assertProblem(browser.confirmPasswordReset("some-token", "a-brand-new-password-1"), 403, "forbidden");
        assertThat(backgroundTasks.isIdle()).isTrue();
    }

    @Test
    void stateChangingRequestWithInvalidCsrfTokenIsRejected() {
        Browser browser = newBrowser();
        browser.fetchCsrf();

        MvcTestResult result = browser.send(mvc.post().uri(basePath + "/login")
                .header("X-CSRF-TOKEN", "not-the-token")
                .formField("username", ALICE.username())
                .formField("password", ALICE.password()));

        assertProblem(result, 403, "forbidden");
    }

    @Test
    void csrfTokenFromBeforeLoginIsRejectedAfterIt() {
        Browser browser = newBrowser().registered(ALICE);
        browser.login(ALICE).assertThat().hasStatusOk();

        // Still holding the pre-login token, as a stale SPA would.
        assertProblem(browser.logout(), 403, "forbidden");
        browser.fetchCsrf();
        assertThat(browser.logout()).hasStatusOk();
    }

    @Test
    void getRequestsNeedNoCsrfToken() {
        assertThat(newBrowser().get("/hello")).hasStatus(401);
    }
}
