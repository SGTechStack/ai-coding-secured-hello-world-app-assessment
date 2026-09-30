package sg.example.helloauth.loginprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.fields;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.time.Duration;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.RecordingEmailService.Kind;
import sg.example.helloauth.support.RecordingEmailService.SentEmail;
import sg.example.helloauth.support.TestAccount;

class AccountLockoutTest extends IntegrationTest {

    private static final TestAccount ALICE = TestAccount.testUser(1);

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    private Browser alice;

    private Browser registerAlice() {
        alice = newBrowser().registered(ALICE);
        return alice;
    }

    private void failLogins(int times) {
        for (int i = 0; i < times; i++) {
            assertProblem(alice.login(ALICE.username(), "wrong-password-" + i), 401, "invalid credentials");
        }
    }

    private MvcTestResult loginWithTheCorrectPassword() {
        return alice.login(ALICE);
    }

    private void advance(Duration duration) {
        clock.setInstant(clock.instant().plus(duration));
    }

    private String aliceId() {
        return jdbc.sql("SELECT id FROM users WHERE username_key = 'testuser1'").query(String.class).single();
    }

    @Test
    void fourFailuresDoNotLockTheAccount() {
        registerAlice();
        failLogins(4);

        assertThat(loginWithTheCorrectPassword()).hasStatusOk();
    }

    @Test
    void fiveConsecutiveFailuresLockTheAccountEvenAgainstTheCorrectPassword() {
        registerAlice();
        failLogins(5);

        assertProblem(loginWithTheCorrectPassword(), 401, "invalid credentials");
    }

    @Test
    void failuresCountAgainstTheAccountWhateverTheCaseOfTheUsername() {
        registerAlice();
        for (String typed : new String[] {"testuser1", "TestUser1", "TESTUSER1", "testUSER1", "Testuser1"}) {
            alice.login(typed, "wrong-password");
        }

        assertProblem(loginWithTheCorrectPassword(), 401, "invalid credentials");
    }

    @Test
    void lockExpiresByItselfAfter20Minutes() {
        registerAlice();
        failLogins(5);

        advance(Duration.ofMinutes(19));
        assertProblem(loginWithTheCorrectPassword(), 401, "invalid credentials");

        advance(Duration.ofMinutes(1));
        assertThat(loginWithTheCorrectPassword()).hasStatusOk();
    }

    @Test
    void failuresWhileLockedDoNotExtendTheLock() {
        registerAlice();
        failLogins(5);
        advance(Duration.ofMinutes(10));
        failLogins(5);

        advance(Duration.ofMinutes(10));

        assertThat(loginWithTheCorrectPassword()).hasStatusOk();
    }

    @Test
    void successfulLoginResetsTheFailedLoginCounter() {
        registerAlice();
        failLogins(4);
        loginWithTheCorrectPassword().assertThat().hasStatusOk();
        alice.fetchCsrf();

        failLogins(4);

        assertThat(loginWithTheCorrectPassword()).hasStatusOk();
    }

    @Test
    void lockAndCounterAreStoredOnTheAccountAndClearedByTheLoginAfterExpiry() {
        registerAlice();
        failLogins(5);

        Map<String, Object> locked = jdbc.sql("SELECT failed_login_attempts, locked_until FROM users")
                .query().singleRow();
        assertThat(locked.get("FAILED_LOGIN_ATTEMPTS")).isEqualTo(5);
        assertThat(locked.get("LOCKED_UNTIL")).isNotNull();

        advance(Duration.ofMinutes(20));
        loginWithTheCorrectPassword().assertThat().hasStatusOk();

        Map<String, Object> cleared = jdbc.sql("SELECT failed_login_attempts, locked_until FROM users")
                .query().singleRow();
        assertThat(cleared.get("FAILED_LOGIN_ATTEMPTS")).isEqualTo(0);
        assertThat(cleared.get("LOCKED_UNTIL")).isNull();
    }

    @Test
    void everyKindOfFailureGetsTheSameResponse() {
        registerAlice();
        MvcTestResult wrongPassword = alice.login(ALICE.username(), "wrong-password");
        MvcTestResult unknownUsername = alice.login("nobody", "wrong-password");
        failLogins(4);
        MvcTestResult locked = loginWithTheCorrectPassword();
        TestAccount bob = TestAccount.testUser(2);
        Browser bobs = newBrowser().registered(bob);
        jdbc.sql("UPDATE users SET enabled = FALSE WHERE username_key = 'testuser2'").update();
        MvcTestResult disabled = bobs.login(bob);

        for (MvcTestResult failure : new MvcTestResult[] {wrongPassword, unknownUsername, locked, disabled}) {
            assertProblem(failure, 401, "invalid credentials");
            assertThat(failure.getResponse().getContentAsByteArray())
                    .isEqualTo(wrongPassword.getResponse().getContentAsByteArray());
        }
    }

    @Test
    void ownerIsNotifiedOnceWhenTheAccountBecomesLocked() {
        registerAlice();
        failLogins(4);
        assertThat(emails.sent()).isEmpty();

        failLogins(3);

        assertThat(emails.sent()).singleElement().satisfies((SentEmail email) -> {
            assertThat(email.kind()).isEqualTo(Kind.LOCKOUT);
            assertThat(email.to().address()).isEqualTo(ALICE.email());
            assertThat(email.to().accountId()).hasToString(aliceId());
        });
    }

    @Test
    void lockoutIsAuditedAtWarnByAccountIdOnly() {
        registerAlice();
        failLogins(5);

        ILoggingEvent event = audit.single("ATTEMPTS_EXCEEDED");
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(event))
                .containsEntry("event.outcome", "failure")
                .containsEntry("error_code", "423")
                .containsEntry("error_follow_up_action", "true")
                .containsEntry("user.id", aliceId());
        assertThat(LogCapture.everything(event)).doesNotContain(ALICE.username(), ALICE.email());
    }

    /** Disabled and Locked are independent: re-enabling must not reveal a lock earned while Disabled. */
    @Test
    void failuresAgainstADisabledAccountDoNotLockIt() {
        registerAlice();
        jdbc.sql("UPDATE users SET enabled = FALSE").update();
        failLogins(5);

        jdbc.sql("UPDATE users SET enabled = TRUE").update();

        assertThat(emails.sent()).isEmpty();
        assertThat(loginWithTheCorrectPassword()).hasStatusOk();
    }

    @Test
    void unknownUsernameNeverLocksAnything() {
        registerAlice();
        for (int i = 0; i < 6; i++) {
            alice.login("nobody", "wrong-password");
        }

        assertThat(audit.withAction("ATTEMPTS_EXCEEDED")).isEmpty();
        assertThat(loginWithTheCorrectPassword()).hasStatusOk();
    }
}
