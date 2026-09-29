package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.eitri.testsupport.TestAccounts;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Account changes made behind a live session take effect on that session's very next request. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:account-refresh;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountRefreshIT {

    private static final String PASSWORD = "refresh-test-password";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountService accountService;

    private TestAccounts accounts;
    private UUID targetId;

    @BeforeEach
    void createAccounts() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        accounts = new TestAccounts(jdbc);
        targetId = accounts.create("refresh-target", "refresh-target@example.com", PASSWORD, "USER");
        accounts.create("refresh-bystander", "refresh-bystander@example.com", PASSWORD, "USER");
    }

    @Test
    void aDisabledAccountsSessionIsRejectedAndEnded() throws Exception {
        Session target = login("refresh-target");
        Session bystander = login("refresh-bystander");

        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", targetId);

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(target.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionCount("refresh-target")).isZero();
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(bystander.cookie())).hasStatusOk();

        jdbc.update("UPDATE users SET enabled = TRUE WHERE id = ?", targetId);
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(target.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aDeletedAccountsSessionIsRejected() throws Exception {
        Session target = login("refresh-target");
        Session bystander = login("refresh-bystander");

        accounts.delete("refresh-target");

        assertThat(mvc.get().uri("/api/v1/hello").cookie(target.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/hello").cookie(bystander.cookie())).hasStatusOk();
    }

    @Test
    void aChangedRoleAppliesOnTheNextRequest() throws Exception {
        Session target = login("refresh-target");

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", targetId);

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(target.cookie()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"username\":\"refresh-target\",\"role\":\"ADMIN\"}");

        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", targetId);

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(target.cookie()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"username\":\"refresh-target\",\"role\":\"USER\"}");
    }

    @Test
    void endingAllSessionsRejectsEverySessionOfThatAccountOnly() throws Exception {
        Session target = login("refresh-target");
        Session bystander = login("refresh-bystander");

        accountService.endAllSessions(targetId);

        assertThat(sessionCount("refresh-target")).isZero();
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(target.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(bystander.cookie())).hasStatusOk();
    }

    @Test
    void endingSessionsOfAnUnknownAccountDoesNothing() throws Exception {
        Session bystander = login("refresh-bystander");

        accountService.endAllSessions(UUID.randomUUID());

        assertThat(mvc.get().uri("/api/v1/auth/me").cookie(bystander.cookie())).hasStatusOk();
    }

    private Session login(String username) throws Exception {
        Session session = SessionClient.fetchCsrf(mvc);
        assertThat(session.login(mvc, "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .hasStatusOk();
        return session;
    }

    private int sessionCount(String username) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, username);
    }
}
