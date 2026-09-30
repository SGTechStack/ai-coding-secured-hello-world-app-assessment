package com.example.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.passwordreset.PasswordResetToken;
import com.example.auth.passwordreset.PasswordResetTokenRepository;
import com.example.auth.security.ratelimit.RateLimiters;
import com.example.auth.support.ApiSession;
import com.example.auth.support.LogCapture;
import com.example.auth.support.TestUsers;
import com.example.auth.user.Role;
import com.example.auth.user.User;
import com.example.auth.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Seam 1 (backend HTTP seam): admin user management through the real filter chain.
 *
 * <p>Not {@code @Transactional}: session termination runs after commit, and a test transaction
 * would hide it. Each test creates its own uniquely named actor and target instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminUserControllerTest {

    private static final String USERS_URL = "/api/admin/users";
    private static final String HELLO_URL = "/api/hello";
    private static final String ADMIN_PASSWORD = "AdminUnderTest123!";
    private static final String TARGET_PASSWORD = "TargetUserPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private RateLimiters rateLimiters;

    private User actor;
    private User target;
    private ApiSession admin;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiters.resetAll();
        actor = TestUsers.create(userRepository, passwordEncoder, TestUsers.unique("admin"), ADMIN_PASSWORD, Role.ADMIN);
        target = TestUsers.create(userRepository, passwordEncoder, TestUsers.unique("target"), TARGET_PASSWORD, Role.USER);
        admin = loggedIn(actor.getUsername(), ADMIN_PASSWORD);
    }

    private ApiSession client() {
        return new ApiSession(mockMvc, objectMapper);
    }

    private ApiSession loggedIn(String username, String password) throws Exception {
        ApiSession session = client();
        session.login(username, password).andExpect(status().isOk());
        return session;
    }

    private String statusUrl(User user) {
        return USERS_URL + "/" + user.getId() + "/status";
    }

    private String roleUrl(User user) {
        return USERS_URL + "/" + user.getId() + "/role";
    }

    private String userUrl(User user) {
        return USERS_URL + "/" + user.getId();
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    // --- list --------------------------------------------------------------------------------

    @Test
    void listUsersAsAdminReturnsFullShapeWithNoPasswordField() throws Exception {
        String body = admin.get(USERS_URL).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode users = objectMapper.readTree(body);
        assertThat(users.isArray()).isTrue();
        JsonNode row = null;
        for (JsonNode u : users) {
            if (u.get("username").asString().equals(target.getUsername())) {
                row = u;
            }
        }
        assertThat(row).as("target listed").isNotNull();
        assertThat(row.get("id").asLong()).isEqualTo(target.getId());
        assertThat(row.get("email").asString()).isEqualTo(target.getEmail());
        assertThat(row.get("role").asString()).isEqualTo("USER");
        assertThat(row.get("enabled").asBoolean()).isTrue();
        assertThat(row.has("createdAt")).isTrue();
        assertThat(row.has("password")).isFalse();
        assertThat(body).doesNotContain("$2a$").doesNotContain("publicId");
    }

    // --- authorisation -----------------------------------------------------------------------

    @Test
    void regularUserIsForbiddenFromEveryAdminEndpoint() throws Exception {
        ApiSession user = loggedIn(target.getUsername(), TARGET_PASSWORD);

        user.get(USERS_URL).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        user.patch(statusUrl(actor), Map.of("enabled", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        user.patch(roleUrl(target), Map.of("role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        user.delete(userUrl(actor)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reload(actor).isEnabled()).isTrue();
        assertThat(reload(target).getRole()).isEqualTo(Role.USER);
    }

    @Test
    void anonymousCallerIsUnauthenticated() throws Exception {
        client().get(USERS_URL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        client().patch(statusUrl(target), Map.of("enabled", false)).andExpect(status().isUnauthorized());
        assertThat(reload(target).isEnabled()).isTrue();
    }

    // --- status ------------------------------------------------------------------------------

    @Test
    void disablingAnotherUserBlocksLoginEndsTheirSessionAndIsAudited() throws Exception {
        ApiSession targetSession = loggedIn(target.getUsername(), TARGET_PASSWORD);
        targetSession.get(HELLO_URL).andExpect(status().isOk());

        try (LogCapture logs = LogCapture.start()) {
            admin.patch(statusUrl(target), Map.of("enabled", false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(target.getId()))
                    .andExpect(jsonPath("$.enabled").value(false));

            LogCapture.Event change = logs.audit("User administration change").getFirst();
            assertThat(change.mdc()).containsEntry("user.id", actor.getPublicId().toString());
            assertThat(change.kv("user.target.id")).isEqualTo(target.getPublicId().toString());
            assertThat(change.kv("labels.change")).isEqualTo("account_disabled");
            assertThat(change.kv("labels.new_value")).isNull();
            assertThat(logs.audit("User sessions terminated").getFirst().kv("event.reason"))
                    .isEqualTo("account_disabled");
        }

        assertThat(reload(target).isEnabled()).isFalse();
        targetSession.get(HELLO_URL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        client().login(target.getUsername(), TARGET_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void reEnablingAUserLetsThemLogInAgain() throws Exception {
        admin.patch(statusUrl(target), Map.of("enabled", false)).andExpect(status().isOk());

        try (LogCapture logs = LogCapture.start()) {
            admin.patch(statusUrl(target), Map.of("enabled", true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));
            assertThat(logs.audit("User administration change").getFirst().kv("labels.change"))
                    .isEqualTo("account_enabled");
            assertThat(logs.audit("User sessions terminated")).isEmpty();
        }

        client().login(target.getUsername(), TARGET_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void statusWithoutEnabledFieldIsAValidationErrorNotASilentDisable() throws Exception {
        admin.patch(statusUrl(target), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("enabled is required"));
        assertThat(reload(target).isEnabled()).isTrue();
    }

    @Test
    void disablingSelfIsRejectedAndAudited() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            admin.patch(statusUrl(actor), Map.of("enabled", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SELF_ACTION"))
                    .andExpect(jsonPath("$.message").value("Cannot change your own account status"));

            LogCapture.Event rejected = logs.audit("User administration change rejected").getFirst();
            assertThat(rejected.kv("event.reason")).isEqualTo("self_action");
            assertThat(rejected.kv("labels.change")).isEqualTo("account_disabled");
        }
        assertThat(reload(actor).isEnabled()).isTrue();
        admin.get(USERS_URL).andExpect(status().isOk());
    }

    // --- role --------------------------------------------------------------------------------

    @Test
    void promotingAnotherUserSucceedsEndsTheirSessionAndGrantsAdminOnNextLogin() throws Exception {
        ApiSession targetSession = loggedIn(target.getUsername(), TARGET_PASSWORD);

        try (LogCapture logs = LogCapture.start()) {
            admin.patch(roleUrl(target), Map.of("role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            LogCapture.Event change = logs.audit("User administration change").getFirst();
            assertThat(change.kv("labels.change")).isEqualTo("role_changed");
            assertThat(change.kv("labels.new_value")).isEqualTo("ADMIN");
        }

        assertThat(reload(target).getRole()).isEqualTo(Role.ADMIN);
        targetSession.get(HELLO_URL).andExpect(status().isUnauthorized());
        loggedIn(target.getUsername(), TARGET_PASSWORD).get(USERS_URL).andExpect(status().isOk());
    }

    @Test
    void demotingAnotherAdminEndsTheirSessionAndRevokesAdminAccess() throws Exception {
        User otherAdmin = TestUsers.create(
                userRepository, passwordEncoder, TestUsers.unique("admin2"), ADMIN_PASSWORD, Role.ADMIN);
        ApiSession otherSession = loggedIn(otherAdmin.getUsername(), ADMIN_PASSWORD);
        otherSession.get(USERS_URL).andExpect(status().isOk());

        admin.patch(roleUrl(otherAdmin), Map.of("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        otherSession.get(USERS_URL).andExpect(status().isUnauthorized());
        loggedIn(otherAdmin.getUsername(), ADMIN_PASSWORD).get(USERS_URL).andExpect(status().isForbidden());
    }

    @Test
    void roleChangeWithNullRoleIsRejectedRatherThanCorruptingTheRow() throws Exception {
        admin.patchRaw(roleUrl(target), "{\"role\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Role must be provided"));
        assertThat(reload(target).getRole()).isEqualTo(Role.USER);
    }

    @Test
    void roleChangeWithUnknownRoleIsAMalformedBody() throws Exception {
        admin.patchRaw(roleUrl(target), "{\"role\":\"SUPERUSER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
        assertThat(reload(target).getRole()).isEqualTo(Role.USER);
    }

    @Test
    void demotingSelfIsRejectedAndLeavesRoleUnchanged() throws Exception {
        admin.patch(roleUrl(actor), Map.of("role", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ACTION"))
                .andExpect(jsonPath("$.message").value("Cannot change your own role"));
        assertThat(reload(actor).getRole()).isEqualTo(Role.ADMIN);
    }

    // --- delete ------------------------------------------------------------------------------

    @Test
    void deletingAnotherUserRemovesThemAndEndsTheirSession() throws Exception {
        ApiSession targetSession = loggedIn(target.getUsername(), TARGET_PASSWORD);

        try (LogCapture logs = LogCapture.start()) {
            admin.delete(userUrl(target)).andExpect(status().isNoContent());
            assertThat(logs.audit("User administration change").getFirst().kv("labels.change"))
                    .isEqualTo("account_deleted");
            assertThat(logs.audit("User sessions terminated").getFirst().kv("event.reason"))
                    .isEqualTo("account_deleted");
        }

        assertThat(userRepository.findById(target.getId())).isEmpty();
        targetSession.get(HELLO_URL).andExpect(status().isUnauthorized());
        client().login(target.getUsername(), TARGET_PASSWORD).andExpect(status().isUnauthorized());
        admin.delete(userUrl(target))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deletingAUserWithOutstandingResetTokensCascades() throws Exception {
        PasswordResetToken token = tokenRepository.save(new PasswordResetToken(
                target, "planted-hash-" + target.getUsername(), Instant.now().plusSeconds(600)));

        admin.delete(userUrl(target)).andExpect(status().isNoContent());

        assertThat(userRepository.findById(target.getId())).isEmpty();
        assertThat(tokenRepository.findById(token.getId())).isEmpty();
    }

    @Test
    void deletingSelfIsRejectedAndLeavesAccountUnchanged() throws Exception {
        admin.delete(userUrl(actor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ACTION"))
                .andExpect(jsonPath("$.message").value("Cannot delete your own account"));
        assertThat(userRepository.findById(actor.getId())).isPresent();
    }

    // --- ids ---------------------------------------------------------------------------------

    @Test
    void unknownIdIsNotFoundOnEveryEndpoint() throws Exception {
        String missing = USERS_URL + "/" + Long.MAX_VALUE;
        admin.patch(missing + "/status", Map.of("enabled", false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found"));
        admin.patch(missing + "/role", Map.of("role", "ADMIN")).andExpect(status().isNotFound());
        admin.delete(missing).andExpect(status().isNotFound());
    }

    @Test
    void nonNumericIdIsAValidationError() throws Exception {
        admin.patch(USERS_URL + "/abc/status", Map.of("enabled", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
        admin.delete(USERS_URL + "/abc").andExpect(status().isBadRequest());
    }

    // --- last-admin guard --------------------------------------------------------------------

    @Test
    void disablingOneOfSeveralAdminsSucceeds() throws Exception {
        User otherAdmin = TestUsers.create(
                userRepository, passwordEncoder, TestUsers.unique("admin2"), ADMIN_PASSWORD, Role.ADMIN);

        admin.patch(statusUrl(otherAdmin), Map.of("enabled", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    /**
     * Because the self-action check runs first and the actor must be an admin, LAST_ADMIN is only
     * reachable when the actor is no longer an enabled admin in the DB while still holding an admin
     * session (a stale session / concurrent change). Reproduce that: leave exactly one enabled admin
     * (the target) in the DB while the actor's ADMIN session stays live.
     */
    @Test
    void theLastEnabledAdminCannotBeDisabledDemotedOrDeleted() throws Exception {
        User lastAdmin = TestUsers.create(
                userRepository, passwordEncoder, TestUsers.unique("lastadmin"), ADMIN_PASSWORD, Role.ADMIN);
        List<User> parked = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isEnabled() && !u.getId().equals(lastAdmin.getId()))
                .toList();
        try {
            parked.forEach(u -> {
                u.setEnabled(false);
                userRepository.save(u);
            });

            try (LogCapture logs = LogCapture.start()) {
                admin.patch(statusUrl(lastAdmin), Map.of("enabled", false))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("LAST_ADMIN"))
                        .andExpect(jsonPath("$.message").value("Cannot remove the last active administrator"));
                assertThat(logs.audit("User administration change rejected").getFirst().kv("event.reason"))
                        .isEqualTo("last_admin");
            }
            admin.patch(roleUrl(lastAdmin), Map.of("role", "USER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
            admin.delete(userUrl(lastAdmin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
            // Promoting/enabling is never blocked by the guard.
            admin.patch(roleUrl(lastAdmin), Map.of("role", "ADMIN")).andExpect(status().isOk());

            User still = reload(lastAdmin);
            assertThat(still.isEnabled()).isTrue();
            assertThat(still.getRole()).isEqualTo(Role.ADMIN);
        } finally {
            parked.forEach(u -> {
                User fresh = reload(u);
                fresh.setEnabled(true);
                userRepository.save(fresh);
            });
        }
    }
}
