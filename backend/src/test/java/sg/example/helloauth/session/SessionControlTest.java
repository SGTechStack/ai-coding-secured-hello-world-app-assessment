package sg.example.helloauth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.TestAccount;

class SessionControlTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);
    private static final TestAccount BOB = TestAccount.testUser(2);

    @Autowired
    private SessionControl sessionControl;

    @Test
    void endingAllSessionsOfAnAccountLeavesOtherAccountsLoggedIn() {
        Browser alice = newBrowser().registerAndLogin(ALICE);
        Browser bob = newBrowser().registerAndLogin(BOB);

        sessionControl.endAllSessions(ALICE.username());

        assertProblem(alice.get("/hello"), 401, "unauthenticated");
        assertThat(bob.get("/hello")).hasStatusOk();
    }

    @Test
    void endingAllSessionsOfAnAccountWithoutAnyIsHarmless() {
        newBrowser().registered(ALICE);

        sessionControl.endAllSessions(ALICE.username());

        assertThat(newBrowser().registerAndLogin(BOB).get("/hello")).hasStatusOk();
    }
}
