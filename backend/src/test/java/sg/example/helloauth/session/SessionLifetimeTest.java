package sg.example.helloauth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class SessionLifetimeTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    private void advance(Duration duration) {
        clock.setInstant(clock.instant().plus(duration));
    }

    @Test
    void sessionUsedWithinTheIdleTimeoutStaysValid() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        advance(Duration.ofMinutes(15));

        assertThat(browser.get("/hello")).hasStatusOk();
    }

    @Test
    void sessionIdleForMoreThan15MinutesIsRejected() {
        Browser browser = newBrowser().registerAndLogin(ALICE);

        advance(Duration.ofMinutes(15).plusSeconds(1));

        assertProblem(browser.get("/hello"), 401, "unauthenticated");
    }

    @Test
    void activityResetsTheIdleTimeout() {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        advance(Duration.ofMinutes(10));
        browser.get("/hello").assertThat().hasStatusOk();

        advance(Duration.ofMinutes(10));

        assertThat(browser.get("/hello")).hasStatusOk();
    }

    @Test
    void sessionOlderThan8HoursIsRejectedEvenWhenActiveThroughout() {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        Duration elapsed = Duration.ZERO;
        while (elapsed.compareTo(Duration.ofHours(8)) < 0) {
            advance(Duration.ofMinutes(10));
            elapsed = elapsed.plusMinutes(10);
            browser.get("/hello").assertThat().hasStatusOk();
        }

        advance(Duration.ofSeconds(1));

        assertProblem(browser.get("/hello"), 401, "unauthenticated");
    }

    @Test
    void visitorWithAnExpiredSessionCanStillFetchACsrfTokenAndLogIn() {
        Browser browser = newBrowser().registerAndLogin(ALICE);
        advance(Duration.ofMinutes(16));

        assertThat(browser.fetchCsrf()).hasStatusOk();

        assertThat(browser.login(ALICE)).hasStatusOk();
        assertThat(browser.get("/hello")).hasStatusOk();
    }
}
