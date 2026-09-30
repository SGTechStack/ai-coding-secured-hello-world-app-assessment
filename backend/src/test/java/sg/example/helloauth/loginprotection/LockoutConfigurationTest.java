package sg.example.helloauth.loginprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

@TestPropertySource(properties = {
        "app.login-protection.lockout.threshold=2",
        "app.login-protection.lockout.duration=3m"})
class LockoutConfigurationTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @Test
    void thresholdAndDurationAreConfigurable() {
        Browser browser = newBrowser().registered(ALICE);
        browser.login(ALICE.username(), "wrong-password-1");
        browser.login(ALICE.username(), "wrong-password-2");

        assertProblem(browser.login(ALICE), 401, "invalid credentials");
        clock.setInstant(START.plus(Duration.ofMinutes(3)));
        assertThat(browser.login(ALICE)).hasStatusOk();
    }
}
