package com.eitri.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.LoggedIn;
import com.eitri.testsupport.StructuredLogTestCapture;
import com.eitri.testsupport.TestAccounts;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Background: the bootstrapped admin is logged in; johndoe is an enabled USER with Password123!. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-user-actions;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdminUserActionsIT {

    private static final String USERS = "/api/v1/admin/users/";
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";
    private static final String GENERIC_LOGIN_FAILURE = "{\"message\":\"Invalid username or password\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestAccounts accounts;
    private LoggedIn admin;
    private UUID adminId;
    private final UUID johndoeId = TestAccounts.JOHNDOE_ID;

    @BeforeEach
    void seed() throws Exception {
        accounts = new TestAccounts(jdbc);
        accounts.restoreSeed();
        adminId = accounts.idOf("admin");
        admin = SessionClient.loggedIn(mvc, "admin", TestAccounts.ADMIN_PASSWORD);
    }

    @Nested
    class EnableAndDisable {

        @Test
        @DisplayName("[assessment/story9-ac1] an admin disables another user's account")
        void adminDisablesAnotherAccount() throws Exception {
            try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
                MvcTestResult result = status(johndoeId.toString(), "{\"enabled\": false}");

                assertThat(result).hasStatusOk();
                assertThat(JsonPath.<String>read(body(result), "$.username")).isEqualTo("johndoe");
                assertThat(JsonPath.<Boolean>read(body(result), "$.enabled")).isFalse();
                assertThat(JsonPath.<Map<String, Object>>read(body(result), "$"))
                        .containsOnlyKeys("id", "username", "email", "role", "enabled", "createdAt");
                assertThat(accounts.row("johndoe")).containsEntry("ENABLED", false);
                assertAdminEvent(audit.line("User account disabled"), "user-disable");
            }
        }

        @Test
        @DisplayName("[assessment/story9-ac2] a disabled user can no longer log in")
        void disabledUserCannotLogIn() throws Exception {
            assertThat(status(johndoeId.toString(), "{\"enabled\": false}")).hasStatusOk();

            MvcTestResult login = johndoeLogin();

            assertThat(login).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(body(login)).isEqualTo(GENERIC_LOGIN_FAILURE);
        }

        @Test
        @DisplayName("[assessment/story9-ac3] disabling a user ends their existing sessions")
        void disablingEndsExistingSessions() throws Exception {
            LoggedIn johndoe = SessionClient.loggedIn(mvc, "johndoe", TestAccounts.JOHNDOE_PASSWORD);

            assertThat(status(johndoeId.toString(), "{\"enabled\": false}")).hasStatusOk();

            assertThat(sessionCount("johndoe")).isZero();
            assertThat(johndoe.get(mvc, "/api/v1/hello")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("[assessment/story9-ac4] re-enabling a disabled account restores login")
        void reEnablingRestoresLogin() throws Exception {
            assertThat(status(johndoeId.toString(), "{\"enabled\": false}")).hasStatusOk();

            try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
                MvcTestResult result = status(johndoeId.toString(), "{\"enabled\": true}");

                assertThat(result).hasStatusOk();
                assertThat(JsonPath.<Boolean>read(body(result), "$.enabled")).isTrue();
                assertAdminEvent(audit.line("User account enabled"), "user-enable");
            }
            assertThat(johndoeLogin()).hasStatusOk();
        }

        @Test
        @DisplayName("[assessment/story9-ac5] an admin cannot change the status of their own account")
        void adminCannotChangeOwnStatus() throws Exception {
            MvcTestResult result = status(adminId.toString(), "{\"enabled\": false}");

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(body(result)).isEqualTo("{\"message\":\"You cannot change the status of your own account\"}");
            assertThat(accounts.row("admin")).containsEntry("ENABLED", true);
            assertThat(admin.get(mvc, "/api/v1/admin/users")).hasStatusOk();
        }

        @ParameterizedTest(name = "{0} {1} -> {2}")
        @CsvSource(delimiter = '|', value = {
            "johndoe | {} | 400",
            "johndoe | {\"enabled\": \"maybe\"} | 400",
            "johndoe | {\"enabled\": null} | 400",
            "johndoe | {\"enabled\": \"false\"} | 400",
            "johndoe | not json | 400",
            "unknown | {\"enabled\": false} | 404",
            "not-a-uuid | {\"enabled\": false} | 400"
        })
        @DisplayName("[assessment/story9-ac6] invalid status change requests are rejected")
        void invalidStatusChangesAreRejected(String target, String body, int expected) throws Exception {
            List<Map<String, Object>> before = snapshot();

            MvcTestResult result = status(target(target), body);

            assertThat(result).hasStatus(expected);
            if (expected == 404) {
                assertThat(body(result)).isEqualTo("{\"message\":\"User not found\"}");
            }
            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void bodyValidationComesBeforeTheSelfGuard() throws Exception {
            assertThat(status(adminId.toString(), "{}")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().isStrictlyEqualTo("{\"message\":\"Bad Request\"}");
        }
    }

    @Nested
    class Roles {

        @Test
        @DisplayName("[assessment/story10-ac1] an admin promotes a USER to ADMIN")
        void adminPromotesAUser() throws Exception {
            LoggedIn johndoe = SessionClient.loggedIn(mvc, "johndoe", TestAccounts.JOHNDOE_PASSWORD);

            try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
                MvcTestResult result = role(johndoeId.toString(), "{\"role\": \"ADMIN\"}");

                assertThat(result).hasStatusOk();
                assertThat(JsonPath.<String>read(body(result), "$.username")).isEqualTo("johndoe");
                assertThat(JsonPath.<String>read(body(result), "$.role")).isEqualTo("ADMIN");
                assertThat(accounts.row("johndoe")).containsEntry("ROLE", "ADMIN");
                String event = audit.line("User role changed");
                assertAdminEvent(event, "user-role-change");
                assertThat(JsonPath.<String>read(event, "$.role.from")).isEqualTo("USER");
                assertThat(JsonPath.<String>read(event, "$.role.to")).isEqualTo("ADMIN");
            }
            assertThat(johndoe.get(mvc, "/api/v1/admin/users")).hasStatusOk();
        }

        @Test
        @DisplayName("[assessment/story10-ac2] a demoted admin loses admin access on their next request")
        void demotedAdminLosesAccessImmediately() throws Exception {
            accounts.create("otheradmin", "otheradmin@example.com", "other-admin-password", "ADMIN");
            LoggedIn otheradmin = SessionClient.loggedIn(mvc, "otheradmin", "other-admin-password");
            assertThat(otheradmin.get(mvc, "/api/v1/admin/users")).hasStatusOk();

            assertThat(role(accounts.idOf("otheradmin").toString(), "{\"role\": \"USER\"}")).hasStatusOk();

            assertThat(accounts.row("otheradmin")).containsEntry("ROLE", "USER");
            assertThat(otheradmin.get(mvc, "/api/v1/admin/users")).hasStatus(HttpStatus.FORBIDDEN);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "{}", "{\"role\": null}", "{\"role\": \"SUPERUSER\"}", "{\"role\": \"USER_MANAGER\"}",
            "{\"role\": \"admin\"}", "{\"role\": 1}"
        })
        @DisplayName("[assessment/story10-ac3] an invalid role value is rejected")
        void invalidRolesAreRejected(String body) throws Exception {
            assertThat(role(johndoeId.toString(), body)).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(accounts.row("johndoe")).containsEntry("ROLE", "USER");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"USER", "ADMIN"})
        @DisplayName("[assessment/story10-ac4] an admin cannot change their own role")
        void adminCannotChangeOwnRole(String requested) throws Exception {
            MvcTestResult result = role(adminId.toString(), "{\"role\": \"" + requested + "\"}");

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(body(result)).isEqualTo("{\"message\":\"You cannot change your own role\"}");
            assertThat(accounts.row("admin")).containsEntry("ROLE", "ADMIN");
        }

        @Test
        @DisplayName("[assessment/story10-ac5] changing the role of an unknown user returns not found")
        void unknownUserIsNotFound() throws Exception {
            MvcTestResult result = role(UNKNOWN_ID, "{\"role\": \"ADMIN\"}");

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(body(result)).isEqualTo("{\"message\":\"User not found\"}");
        }

        @Test
        void anAdminWhoLostTheRoleMidSessionCannotAct() throws Exception {
            accounts.create("otheradmin", "otheradmin@example.com", "other-admin-password", "ADMIN");
            LoggedIn otheradmin = SessionClient.loggedIn(mvc, "otheradmin", "other-admin-password");

            assertThat(role(accounts.idOf("otheradmin").toString(), "{\"role\": \"USER\"}")).hasStatusOk();

            assertThat(otheradmin.send(mvc, HttpMethod.PATCH, USERS + adminId + "/role", "{\"role\": \"USER\"}"))
                    .hasStatus(HttpStatus.FORBIDDEN);
            assertThat(accounts.row("admin")).containsEntry("ROLE", "ADMIN");
        }
    }

    @Nested
    class Deletion {

        @Test
        @DisplayName("[assessment/story11-ac1] an admin deletes another user's account")
        void adminDeletesAnotherAccount() throws Exception {
            jdbc.update(
                    "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    johndoeId,
                    "0".repeat(64),
                    Timestamp.from(Instant.now().plus(Duration.ofMinutes(30))));

            try (StructuredLogTestCapture audit = StructuredLogTestCapture.audit()) {
                assertThat(delete(johndoeId.toString())).hasStatus(HttpStatus.NO_CONTENT);

                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'johndoe'", Integer.class))
                        .isZero();
                assertThat(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, johndoeId))
                        .isZero();
                List<String> listed = JsonPath.read(body(admin.get(mvc, "/api/v1/admin/users")), "$[*].username");
                assertThat(listed).doesNotContain("johndoe");
                assertAdminEvent(audit.line("User account deleted"), "user-delete");
            }
        }

        @Test
        @DisplayName("[assessment/story11-ac2] a deleted user can no longer log in or use an existing session")
        void deletedUserIsLockedOut() throws Exception {
            LoggedIn johndoe = SessionClient.loggedIn(mvc, "johndoe", TestAccounts.JOHNDOE_PASSWORD);

            assertThat(delete(johndoeId.toString())).hasStatus(HttpStatus.NO_CONTENT);

            assertThat(sessionCount("johndoe")).isZero();
            assertThat(johndoe.get(mvc, "/api/v1/hello")).hasStatus(HttpStatus.UNAUTHORIZED);
            MvcTestResult login = johndoeLogin();
            assertThat(login).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(body(login)).isEqualTo(GENERIC_LOGIN_FAILURE);
        }

        @Test
        @DisplayName("[assessment/story11-ac3] an admin cannot delete their own account")
        void adminCannotDeleteThemselves() throws Exception {
            MvcTestResult result = delete(adminId.toString());

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(body(result)).isEqualTo("{\"message\":\"You cannot delete your own account\"}");
            assertThat(accounts.row("admin")).containsEntry("ROLE", "ADMIN");
        }

        @Test
        @DisplayName("[assessment/story11-ac4] deleting an unknown user returns not found")
        void unknownUserIsNotFound() throws Exception {
            List<Map<String, Object>> before = snapshot();

            MvcTestResult result = delete(UNKNOWN_ID);

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(body(result)).isEqualTo("{\"message\":\"User not found\"}");
            assertThat(snapshot()).isEqualTo(before);
            assertThat(delete("not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    private void assertAdminEvent(String event, String action) {
        assertThat(JsonPath.<String>read(event, "$.log.logger")).isEqualTo("AUDIT");
        assertThat(JsonPath.<String>read(event, "$.event.category")).isEqualTo("iam");
        assertThat(JsonPath.<String>read(event, "$.event.action")).isEqualTo(action);
        assertThat(JsonPath.<String>read(event, "$.event.outcome")).isEqualTo("success");
        assertThat(JsonPath.<String>read(event, "$.actor.id")).isEqualTo(adminId.toString());
        assertThat(JsonPath.<String>read(event, "$.actor.name")).isEqualTo("admin");
        assertThat(JsonPath.<String>read(event, "$.target.id")).isEqualTo(johndoeId.toString());
        assertThat(JsonPath.<String>read(event, "$.target.name")).isEqualTo("johndoe");
        assertThat(event).doesNotContain("john@example.com", "$2", admin.token());
    }

    private String target(String name) {
        return switch (name) {
            case "johndoe" -> johndoeId.toString();
            case "unknown" -> UNKNOWN_ID;
            default -> name;
        };
    }

    private MvcTestResult status(String id, String body) {
        return admin.send(mvc, HttpMethod.PATCH, USERS + id + "/status", body);
    }

    private MvcTestResult role(String id, String body) {
        return admin.send(mvc, HttpMethod.PATCH, USERS + id + "/role", body);
    }

    private MvcTestResult delete(String id) {
        return admin.send(mvc, HttpMethod.DELETE, USERS + id, null);
    }

    private MvcTestResult johndoeLogin() {
        return SessionClient.fetchCsrfUnchecked(mvc)
                .login(mvc, "{\"username\":\"johndoe\",\"password\":\"" + TestAccounts.JOHNDOE_PASSWORD + "\"}");
    }

    private int sessionCount(String username) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, username);
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT id, username, email, role, enabled, password_hash FROM users ORDER BY id");
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
