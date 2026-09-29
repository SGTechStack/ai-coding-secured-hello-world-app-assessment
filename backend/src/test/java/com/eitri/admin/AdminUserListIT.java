package com.eitri.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.LoggedIn;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Background: admin (bootstrapped), johndoe, and a disabled janedoe. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-user-list;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdminUserListIT {

    private static final String USERS = "/api/v1/admin/users";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID janedoeId;

    @BeforeEach
    void seed() {
        TestAccounts accounts = new TestAccounts(jdbc);
        accounts.restoreSeed();
        janedoeId = accounts.create("janedoe", "jane@example.com", "jane-password-123", "USER");
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", janedoeId);
    }

    @Test
    @DisplayName("[assessment/story8-ac1] an admin lists every user with their account details")
    void adminListsEveryUser() throws Exception {
        MvcTestResult result = admin().get(mvc, USERS);

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        List<Map<String, Object>> users = JsonPath.read(body, "$");
        assertThat(users).hasSize(3);
        assertThat(users).allSatisfy(user ->
                assertThat(user).containsOnlyKeys("id", "username", "email", "role", "enabled", "createdAt"));
        assertThat(users).extracting(user -> user.get("username")).containsExactlyInAnyOrder("admin", "johndoe", "janedoe");
        Map<String, Object> jane = users.stream().filter(user -> "janedoe".equals(user.get("username"))).findFirst().orElseThrow();
        assertThat(jane)
                .containsEntry("id", janedoeId.toString())
                .containsEntry("email", "jane@example.com")
                .containsEntry("role", "USER")
                .containsEntry("enabled", false);
        assertThat((String) jane.get("createdAt")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    }

    @Test
    void theListIsOrderedByCreationThenUsername() throws Exception {
        jdbc.update("UPDATE users SET created_at = TIMESTAMP WITH TIME ZONE '2020-01-01 00:00:00+00'");

        List<String> usernames = JsonPath.read(admin().get(mvc, USERS).getResponse().getContentAsString(), "$[*].username");

        assertThat(usernames).containsExactly("admin", "janedoe", "johndoe");
    }

    @Test
    @DisplayName("[assessment/story8-ac2] the user list never exposes credentials or lockout internals")
    void theListExposesNoCredentialsOrLockoutState() throws Exception {
        jdbc.update("UPDATE users SET failed_login_attempts = 3, locked_until = CURRENT_TIMESTAMP WHERE id = ?", janedoeId);

        String body = admin().get(mvc, USERS).getResponse().getContentAsString();

        assertThat(body).doesNotContain("$2", "bcrypt", "password", "failed_login_attempts", "failedLoginAttempts",
                "locked_until", "lockedUntil");
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api/v1/admin/users, ",
        "PATCH, /api/v1/admin/users/{janedoe}/status, '{\"enabled\":true}'",
        "PATCH, /api/v1/admin/users/{janedoe}/role, '{\"role\":\"ADMIN\"}'",
        "DELETE, /api/v1/admin/users/{janedoe}, "
    })
    @DisplayName("[assessment/story8-ac3] a USER calling any admin endpoint is forbidden")
    void userIsForbiddenFromEveryAdminEndpoint(String method, String path, String body) throws Exception {
        LoggedIn johndoe = SessionClient.loggedIn(mvc, "johndoe", TestAccounts.JOHNDOE_PASSWORD);
        List<Map<String, Object>> before = snapshot();

        MvcTestResult result = johndoe.send(
                mvc, HttpMethod.valueOf(method), path.replace("{janedoe}", janedoeId.toString()), body);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("[assessment/story8-ac4] an unauthenticated request to the admin API is unauthorized")
    void anonymousRequestIsUnauthorized() {
        assertThat(mvc.get().uri(USERS)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.delete().uri(USERS + "/" + janedoeId)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private LoggedIn admin() throws Exception {
        return SessionClient.loggedIn(mvc, "admin", TestAccounts.ADMIN_PASSWORD);
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT id, username, email, role, enabled, password_hash FROM users ORDER BY id");
    }
}
