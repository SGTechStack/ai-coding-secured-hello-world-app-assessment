package sg.example.helloauth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.fields;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.RecordingEmailService.Kind;
import sg.example.helloauth.support.TestAccount;

class AdminRoleChangeAndDeleteTest extends IntegrationTest {

    private static final TestAccount ADMIN = TestAccount.BOOTSTRAP_ADMIN;
    private static final TestAccount BOB = TestAccount.testUser(2);

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    private Browser admin;
    private String bobId;

    /** An Admin logged in, and Bob, a Regular user, logged in too. */
    private Browser bobLoggedIn() {
        admin = loggedInAdmin(ADMIN);
        Browser bob = newBrowser().registerAndLogin(BOB);
        bobId = idOf(BOB);
        return bob;
    }

    private Browser visitor() {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        return browser;
    }

    private MvcTestResult bobLogsIn() {
        return visitor().login(BOB);
    }

    @Test
    void adminPromotesARegularUserWhoGainsAdminAccessAtTheirNextLogin() {
        bobLoggedIn();

        assertThat(admin.changeRole(bobId, "ADMIN")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.role", role -> assertThat(role).isEqualTo("ADMIN"));

        Browser bob = visitor();
        bob.login(BOB).assertThat().hasStatusOk();
        assertThat(bob.listAccounts()).hasStatusOk();
    }

    @Test
    void roleChangeEndsTheTargetsSessions() {
        Browser bob = bobLoggedIn();

        admin.changeRole(bobId, "ADMIN").assertThat().hasStatusOk();

        assertProblem(bob.get("/hello"), 401, "unauthenticated");
    }

    @Test
    void demotedAdminsOldSessionNoLongerReachesTheAdminEndpoints() {
        Browser bob = bobLoggedIn();
        admin.changeRole(bobId, "ADMIN").assertThat().hasStatusOk();
        bob = visitor();
        bob.login(BOB).assertThat().hasStatusOk();
        bob.fetchCsrf();
        assertThat(bob.listAccounts()).hasStatusOk();

        admin.changeRole(bobId, "USER").assertThat().hasStatusOk();

        assertProblem(bob.listAccounts(), 401, "unauthenticated");
        Browser again = visitor();
        again.login(BOB).assertThat().hasStatusOk();
        assertProblem(again.listAccounts(), 403, "forbidden");
    }

    @Test
    void unknownRoleIsAValidationError() {
        bobLoggedIn();

        assertProblem(admin.changeRole(bobId, "SUPERUSER"), 400, "validation failed");
    }

    @Test
    void deleteHidesTheAccountFromTheListAndEndsItsSessions() {
        Browser bob = bobLoggedIn();

        assertThat(admin.delete(bobId)).hasStatus(204);

        assertProblem(bob.get("/hello"), 401, "unauthenticated");
        assertThat(admin.listAccounts()).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.content[*].username", usernames -> assertThat(usernames)
                        .asInstanceOf(InstanceOfAssertFactories.LIST).containsExactly(ADMIN.username()));
    }

    @Test
    void deleteKeepsTheRowAsATombstone() {
        bobLoggedIn();

        admin.delete(bobId).assertThat().hasStatus(204);

        assertThat(jdbc.sql("SELECT deleted_at FROM users WHERE id = ?").param(bobId).query().singleValue())
                .isNotNull();
    }

    @Test
    void tombstoneCannotLogInAndGetsTheGenericError() {
        bobLoggedIn();
        admin.delete(bobId).assertThat().hasStatus(204);

        MvcTestResult tombstone = bobLogsIn();

        assertProblem(tombstone, 401, "invalid credentials");
        assertThat(tombstone.getResponse().getContentAsByteArray())
                .isEqualTo(visitor().login("nobody", "wrong-password").getResponse().getContentAsByteArray());
    }

    @Test
    void everyLaterAdminActionOnATombstoneIsNotFound() {
        bobLoggedIn();
        admin.delete(bobId).assertThat().hasStatus(204);

        for (MvcTestResult result : List.of(admin.setEnabled(bobId, true), admin.unlock(bobId),
                admin.changeRole(bobId, "ADMIN"), admin.delete(bobId))) {
            assertProblem(result, 404, "not found");
        }
    }

    @Test
    void tombstonesUsernameInAnyCaseAndEmailStayReserved() {
        bobLoggedIn();
        admin.delete(bobId).assertThat().hasStatus(204);
        Browser visitor = visitor();

        assertProblem(visitor.register("TestUser2", "someone-else@test.example.com", BOB.password()), 400,
                "user exist");
        assertProblem(visitor.register("someone-else", BOB.email(), BOB.password()), 400, "user exist");
    }

    @Test
    void deleteRemovesTheAccountsPendingPasswordResetTokens() {
        bobLoggedIn();
        visitor().requestPasswordReset(BOB.email()).assertThat().hasStatus(202);
        backgroundTasks.runAll();
        String link = emails.sent(Kind.PASSWORD_RESET).getFirst().resetLink();
        String token = link.substring(link.indexOf("token=") + "token=".length());

        admin.delete(bobId).assertThat().hasStatus(204);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM password_reset_tokens").query(Integer.class).single()).isZero();
        assertProblem(visitor().confirmPasswordReset(token, "a-brand-new-password-1"), 400,
                "password reset token expired or invalid");
    }

    @Test
    void passwordResetRequestForATombstonesEmailIssuesNoTokenButGetsTheSameAnswer() {
        bobLoggedIn();
        admin.delete(bobId).assertThat().hasStatus(204);

        MvcTestResult tombstone = visitor().requestPasswordReset(BOB.email());
        backgroundTasks.runAll();

        assertThat(tombstone).hasStatus(202);
        assertThat(tombstone.getResponse().getContentAsByteArray()).isEqualTo(
                visitor().requestPasswordReset("nobody@test.example.com").getResponse().getContentAsByteArray());
        assertThat(emails.sent(Kind.PASSWORD_RESET)).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM password_reset_tokens").query(Integer.class).single()).isZero();
    }

    @Test
    void adminCannotDemoteOrDeleteTheirOwnAccount() {
        bobLoggedIn();
        String adminId = idOf(ADMIN);

        assertProblem(admin.changeRole(adminId, "USER"), 403, "self action not allowed");
        assertProblem(admin.delete(adminId), 403, "self action not allowed");

        assertThat(admin.listAccounts()).hasStatusOk();
    }

    @Test
    void unknownAccountIsNotFound() {
        bobLoggedIn();
        UUID unknown = UUID.randomUUID();

        assertProblem(admin.changeRole(unknown, "ADMIN"), 404, "not found");
        assertProblem(admin.delete(unknown), 404, "not found");
    }

    @Test
    void bothEndpointsRejectAMissingOrInvalidCsrfToken() {
        Browser bob = bobLoggedIn();

        MvcTestResult missing = admin.send(mvc.patch().uri(basePath + "/admin/users/" + bobId + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"));
        MvcTestResult invalid = admin.send(mvc.delete().uri(basePath + "/admin/users/" + bobId)
                .header("X-CSRF-TOKEN", "not-the-token"));

        assertProblem(missing, 403, "forbidden");
        assertProblem(invalid, 403, "forbidden");
        assertThat(bob.get("/hello")).hasStatusOk();
    }

    @Test
    void roleChangesAndDeletesAreAuditedWithTheActorAndTarget() {
        bobLoggedIn();
        String adminId = idOf(ADMIN);

        admin.changeRole(bobId, "ADMIN").assertThat().hasStatusOk();
        admin.delete(bobId).assertThat().hasStatus(204);

        List<ILoggingEvent> events = audit.withAction("user-administration");
        assertThat(events).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Account role changed.", "Account deleted.");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(fields(event))
                    .containsEntry("event.outcome", "success")
                    .containsEntry("user.id", adminId)
                    .containsEntry("user.target.id", bobId);
        });
        assertThat(fields(events.get(0))).containsEntry("user.target.roles", "[ADMIN]");
        assertThat(fields(events.get(1))).containsEntry("event.type", "[deletion]");
    }
}
