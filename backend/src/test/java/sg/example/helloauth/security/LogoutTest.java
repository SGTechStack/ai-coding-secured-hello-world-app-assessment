package sg.example.helloauth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class LogoutTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @Test
    void regularUserLogsOutAndTheSessionCookieIsCleared() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        MvcTestResult result = browser.logout();

        assertThat(result).hasStatusOk();
        Cookie cleared = result.getResponse().getCookie(Browser.SESSION_COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(browser.get("/hello")).hasStatus(401);
    }

    @Test
    void sessionCookieCapturedBeforeLogoutIsRejectedAfterIt() {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        Cookie captured = browser.sessionCookie();

        browser.logout();

        MvcTestResult replayed = mvc.get().uri(basePath + "/hello").cookie(captured).exchange();
        assertProblem(replayed, 401, "unauthenticated");
    }

    @Test
    void logoutOverHttpsClearsSiteData() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        MvcTestResult result = browser.send(browser.withCsrf(mvc.post().uri(basePath + "/logout").secure(true)));

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader("Clear-Site-Data"))
                .isEqualTo("\"cache\", \"cookies\", \"storage\"");
    }

    @Test
    void logoutOverPlainHttpSendsNoClearSiteData() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        assertThat(browser.logout()).hasStatusOk().doesNotContainHeader("Clear-Site-Data");
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        MvcTestResult result = browser.send(mvc.post().uri(basePath + "/logout"));

        assertProblem(result, 403, "forbidden");
        assertThat(browser.get("/hello")).hasStatusOk();
    }

    @Test
    void csrfTokenFromBeforeLogoutIsRejectedAfterIt() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        browser.logout();

        // Still holding the old session's token, as a stale SPA would.
        assertProblem(browser.login(ALICE), 403, "forbidden");
        browser.fetchCsrf();
        assertThat(browser.login(ALICE)).hasStatusOk();
    }
}
