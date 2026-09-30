package sg.example.helloauth.loginprotection;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

/**
 * A failed login takes as long as its one BCrypt check, whatever the reason it fails, so timing
 * can't tell an unknown, Locked or Disabled Account from a wrong password. Spring Security does
 * this itself; these tests hold it to that. Watching for the check is steadier than timing it.
 */
class LoginTimingTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private void assertOnePasswordCheckFor(Browser browser, String username, String password) {
        clearInvocations(passwordEncoder);

        assertProblem(browser.login(username, password), 401, "invalid credentials");

        verify(passwordEncoder, times(1)).matches(eq(password), anyString());
    }

    @Test
    void unknownUsernameRunsADummyPasswordCheck() {
        Browser browser = newBrowser().registered(ALICE);

        assertOnePasswordCheckFor(browser, "nobody", "some-password-1");
    }

    @Test
    void wrongPasswordRunsOnePasswordCheck() {
        Browser browser = newBrowser().registered(ALICE);

        assertOnePasswordCheckFor(browser, ALICE.username(), "some-password-2");
    }

    @Test
    void lockedAccountStillRunsThePasswordCheck() {
        Browser browser = newBrowser().registered(ALICE);
        for (int i = 0; i < 5; i++) {
            browser.login(ALICE.username(), "wrong-password");
        }

        assertOnePasswordCheckFor(browser, ALICE.username(), ALICE.password());
    }

    @Test
    void disabledAccountStillRunsThePasswordCheck() {
        Browser browser = newBrowser().registered(ALICE);
        jdbc.sql("UPDATE users SET enabled = FALSE").update();

        assertOnePasswordCheckFor(browser, ALICE.username(), ALICE.password());
    }
}
