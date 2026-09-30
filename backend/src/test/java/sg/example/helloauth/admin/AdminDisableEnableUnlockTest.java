package sg.example.helloauth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.fields;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.TestAccount;

class AdminDisableEnableUnlockTest extends IntegrationTest {

    private static final TestAccount ADMIN = TestAccount.BOOTSTRAP_ADMIN;
    private static final TestAccount BOB = TestAccount.testUser(2);

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    private Browser admin;
    private String bobId;

    /** An Admin logged in, and Bob, a Regular user, registered but not logged in. */
    private Browser bob() {
        admin = loggedInAdmin(ADMIN);
        Browser bob = newBrowser().registered(BOB);
        bobId = idOf(BOB);
        return bob;
    }

    private MvcTestResult bobLogsIn() {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        return browser.login(BOB);
    }

    private static void failLogins(Browser browser, int times) {
        for (int i = 0; i < times; i++) {
            assertProblem(browser.login(BOB.username(), "wrong-password-" + i), 401, "invalid credentials");
        }
    }

    @Test
    void adminDisablesAnotherAccountWhichThenGetsTheGenericLoginError() {
        bob();

        assertThat(admin.setEnabled(bobId, false)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.enabled", enabled -> assertThat(enabled).isEqualTo(false))
                .hasPathSatisfying("$.id", id -> assertThat(id).isEqualTo(bobId));

        assertProblem(bobLogsIn(), 401, "invalid credentials");
    }

    @Test
    void disablingEndsTheTargetsSessionsAtOnce() {
        bob();
        Browser bobsSession = newBrowser();
        bobsSession.fetchCsrf();
        bobsSession.login(BOB).assertThat().hasStatusOk();

        admin.setEnabled(bobId, false).assertThat().hasStatusOk();

        assertProblem(bobsSession.get("/hello"), 401, "unauthenticated");
    }

    @Test
    void adminReEnablesADisabledAccount() {
        bob();
        admin.setEnabled(bobId, false).assertThat().hasStatusOk();

        assertThat(admin.setEnabled(bobId, true)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.enabled", enabled -> assertThat(enabled).isEqualTo(true));

        assertThat(bobLogsIn()).hasStatusOk();
    }

    @Test
    void reEnablingLeavesALockInPlace() {
        failLogins(bob(), 5);
        admin.setEnabled(bobId, false).assertThat().hasStatusOk();

        assertThat(admin.setEnabled(bobId, true)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.locked", locked -> assertThat(locked).isEqualTo(true));

        assertProblem(bobLogsIn(), 401, "invalid credentials");
        clock.setInstant(START.plus(Duration.ofMinutes(20)));
        assertThat(bobLogsIn()).hasStatusOk();
    }

    @Test
    void adminUnlocksALockedAccountSoItCanLogInStraightAway() {
        failLogins(bob(), 5);

        assertThat(admin.unlock(bobId)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.locked", locked -> assertThat(locked).isEqualTo(false));

        assertThat(bobLogsIn()).hasStatusOk();
    }

    @Test
    void unlockResetsTheFailedLoginCounter() {
        Browser bob = bob();
        failLogins(bob, 4);
        admin.unlock(bobId).assertThat().hasStatusOk();

        failLogins(bob, 1);

        assertThat(bobLogsIn()).hasStatusOk();
    }

    @Test
    void unlockLeavesADisabledAccountDisabled() {
        failLogins(bob(), 5);
        admin.setEnabled(bobId, false).assertThat().hasStatusOk();

        admin.unlock(bobId).assertThat().hasStatusOk();

        assertProblem(bobLogsIn(), 401, "invalid credentials");
    }

    @Test
    void adminCannotDisableOrUnlockTheirOwnAccount() {
        bob();
        String adminId = idOf(ADMIN);

        assertProblem(admin.setEnabled(adminId, false), 403, "self action not allowed");
        assertProblem(admin.unlock(adminId), 403, "self action not allowed");

        assertThat(admin.listAccounts()).hasStatusOk();
    }

    @Test
    void unknownAccountIsNotFound() {
        bob();
        UUID unknown = UUID.randomUUID();

        assertProblem(admin.setEnabled(unknown, false), 404, "not found");
        assertProblem(admin.unlock(unknown), 404, "not found");
    }

    @Test
    void statusChangeWithoutAnEnabledFlagIsAValidationError() {
        bob();

        MvcTestResult result = admin.send(admin.withCsrf(mvc.patch().uri(basePath + "/admin/users/" + bobId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")));

        assertProblem(result, 400, "validation failed");
    }

    @Test
    void bothEndpointsRejectAMissingOrInvalidCsrfToken() {
        bob();

        MvcTestResult missing = admin.send(mvc.patch().uri(basePath + "/admin/users/" + bobId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"));
        MvcTestResult invalid = admin.send(mvc.post().uri(basePath + "/admin/users/" + bobId + "/unlock")
                .header("X-CSRF-TOKEN", "not-the-token"));

        assertProblem(missing, 403, "forbidden");
        assertProblem(invalid, 403, "forbidden");
        assertThat(bobLogsIn()).hasStatusOk();
    }

    @Test
    void enableDisableAndUnlockAreAuditedWithTheActorAndTarget() {
        bob();
        String adminId = idOf(ADMIN);

        admin.setEnabled(bobId, false).assertThat().hasStatusOk();
        admin.setEnabled(bobId, true).assertThat().hasStatusOk();
        admin.unlock(bobId).assertThat().hasStatusOk();

        List<ILoggingEvent> events = audit.withAction("user-administration");
        assertThat(events).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Account disabled.", "Account enabled.", "Account unlocked.");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(fields(event))
                    .containsEntry("event.outcome", "success")
                    .containsEntry("user.id", adminId)
                    .containsEntry("user.target.id", bobId);
        });
    }

    @Test
    void failedAdminActionsAreAuditedAtWarn() {
        bob();
        String adminId = idOf(ADMIN);
        String unknown = UUID.randomUUID().toString();

        admin.setEnabled(adminId, false);
        admin.unlock(unknown);

        List<ILoggingEvent> events = audit.withAction("user-administration");
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fields(event)).containsEntry("event.outcome", "failure").containsEntry("user.id", adminId);
        });
        assertThat(fields(events.get(0))).containsEntry("error_code", "403").containsEntry("user.target.id", adminId);
        assertThat(fields(events.get(1))).containsEntry("error_code", "404").containsEntry("user.target.id", unknown);
    }
}
