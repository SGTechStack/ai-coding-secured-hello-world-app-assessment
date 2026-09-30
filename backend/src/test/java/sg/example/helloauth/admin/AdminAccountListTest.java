package sg.example.helloauth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static sg.example.helloauth.support.LogCapture.fields;
import static sg.example.helloauth.support.ProblemAssertions.assertProblem;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sg.example.helloauth.support.Browser;
import sg.example.helloauth.support.IntegrationTest;
import sg.example.helloauth.support.LogCapture;
import sg.example.helloauth.support.TestAccount;

class AdminAccountListTest extends IntegrationTest {

    private static final TestAccount ADMIN = TestAccount.BOOTSTRAP_ADMIN;
    private static final TestAccount ALICE = TestAccount.testUser(1);
    private static final TestAccount BOB = TestAccount.testUser(2);

    @RegisterExtension
    final LogCapture audit = LogCapture.audit();

    @Test
    void adminListsEveryLiveAccountWithItsDetailsOldestFirst() {
        Browser admin = loggedInAdmin(ADMIN);
        clock.setInstant(START.plusSeconds(60));
        newBrowser().registered(ALICE);
        clock.setInstant(START.plusSeconds(120));
        newBrowser().registered(BOB);

        MvcTestResult list = admin.listAccounts();

        assertThat(list).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.content[*].username",
                        usernames -> assertThat(usernames).asInstanceOf(InstanceOfAssertFactories.LIST)
                                .containsExactly(ADMIN.username(), ALICE.username(), BOB.username()))
                .hasPathSatisfying("$.content[1]", alice -> assertThat(alice).isEqualTo(Map.of(
                        "id", idOf(ALICE),
                        "username", ALICE.username(),
                        "email", ALICE.email(),
                        "role", "USER",
                        "enabled", true,
                        "locked", false,
                        "createdAt", "2026-01-05T09:01:00Z")))
                .hasPathSatisfying("$.content[0].role", role -> assertThat(role).isEqualTo("ADMIN"));
    }

    @Test
    void listShowsWhichAccountsAreLockedAndWhichDisabled() {
        Browser admin = loggedInAdmin(ADMIN);
        Browser alice = newBrowser().registered(ALICE);
        for (int i = 0; i < 5; i++) {
            alice.login(ALICE.username(), "wrong-password-" + i);
        }
        newBrowser().registered(BOB);
        jdbc.sql("UPDATE users SET enabled = FALSE WHERE username_key = 'testuser2'").update();

        assertThat(admin.listAccounts()).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.content[1].locked", locked -> assertThat(locked).isEqualTo(true))
                .hasPathSatisfying("$.content[1].enabled", enabled -> assertThat(enabled).isEqualTo(true))
                .hasPathSatisfying("$.content[2].locked", locked -> assertThat(locked).isEqualTo(false))
                .hasPathSatisfying("$.content[2].enabled", enabled -> assertThat(enabled).isEqualTo(false));
    }

    @Test
    void listNeverContainsPasswordHashes() throws Exception {
        Browser admin = loggedInAdmin(ADMIN);
        newBrowser().registered(ALICE);

        String body = admin.listAccounts().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("password").doesNotContain("{bcrypt}", "$2a$");
    }

    @Test
    void listIsPaginatedFiftyToAPageByDefault() {
        Browser admin = loggedInAdmin(ADMIN);

        assertThat(admin.listAccounts()).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(50))
                .hasPathSatisfying("$.page.number", number -> assertThat(number).isEqualTo(0));
    }

    @Test
    void adminPagesThroughTheList() {
        Browser admin = loggedInAdmin(ADMIN);
        clock.setInstant(START.plusSeconds(60));
        newBrowser().registered(ALICE);
        clock.setInstant(START.plusSeconds(120));
        newBrowser().registered(BOB);

        assertThat(admin.get("/admin/users?page=1&size=2")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.content[*].username", usernames -> assertThat(usernames)
                        .asInstanceOf(InstanceOfAssertFactories.LIST).containsExactly(BOB.username()))
                .hasPathSatisfying("$.page.totalElements", total -> assertThat(total).isEqualTo(3))
                .hasPathSatisfying("$.page.totalPages", pages -> assertThat(pages).isEqualTo(2));
    }

    @Test
    void pageOutsideTheAllowedRangeIsAValidationError() {
        Browser admin = loggedInAdmin(ADMIN);

        for (String query : List.of("page=-1", "size=0", "size=101", "page=first")) {
            assertProblem(admin.get("/admin/users?" + query), 400, "validation failed");
        }
    }

    @Test
    void regularUserGetsForbiddenOnEveryAdminEndpoint() {
        loggedInAdmin(ADMIN);
        Browser alice = newBrowser().registerAndLogin(ALICE);
        String adminId = idOf(ADMIN);

        for (MvcTestResult result : List.of(alice.listAccounts(), alice.setEnabled(adminId, false),
                alice.unlock(adminId), alice.changeRole(adminId, "USER"), alice.delete(adminId))) {
            assertProblem(result, 403, "forbidden");
        }
        assertThat(admin().get("/me")).hasStatusOk();
    }

    /** Logs the Admin in again, in a fresh browser, to check its Account is untouched. */
    private Browser admin() {
        Browser browser = newBrowser();
        browser.fetchCsrf();
        browser.login(ADMIN).assertThat().hasStatusOk();
        return browser;
    }

    @Test
    void forbiddenRequestIsAuditedAtWarnWithTheCallersId() {
        Browser alice = newBrowser().registerAndLogin(ALICE);

        assertProblem(alice.listAccounts(), 403, "forbidden");

        ILoggingEvent denied = audit.single("access-control");
        assertThat(denied.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(denied))
                .containsEntry("event.outcome", "failure")
                .containsEntry("error_code", "403")
                .containsEntry("url.path", basePath + "/admin/users")
                .containsEntry("user.id", idOf(ALICE));
    }

    @Test
    void visitorIsUnauthenticatedOnAdminEndpoints() {
        assertProblem(newBrowser().listAccounts(), 401, "unauthenticated");
    }
}
