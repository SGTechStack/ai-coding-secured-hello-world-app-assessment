package com.example.demo_app.admin;

import static com.example.demo_app.auth.SpaAuthFlow.credentialsJson;
import static com.example.demo_app.auth.SpaAuthFlow.fromIp;
import static com.example.demo_app.auth.SpaAuthFlow.logIn;
import static com.example.demo_app.auth.SpaAuthFlow.logInAsAdmin;
import static com.example.demo_app.auth.SpaAuthFlow.loginRequest;
import static com.example.demo_app.auth.SpaAuthFlow.register;
import static com.example.demo_app.auth.SpaAuthFlow.uniqueIp;
import static com.example.demo_app.auth.SpaAuthFlow.userId;
import static com.example.demo_app.auth.SpaAuthFlow.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The admin's account actions (disable/enable, role change, delete) over the real filter chain.
 * Every test registers its own target user; the bootstrap {@code admin} and {@code johndoe} are
 * never changed, because the test database is shared with every other test class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AdminUserActionsApiTest {

  private static final String PASSWORD = "correct horse battery";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private MockHttpSession admin;
  private String target;
  private long targetId;

  @BeforeEach
  void freshTarget() throws Exception {
    admin = logInAsAdmin(mvc);
    target = "target-" + UUID.randomUUID().toString().substring(0, 8);
    register(mvc, target, PASSWORD);
    targetId = userId(mvc, admin, target);
  }

  // --- status ---

  @Test
  void disablingEndsTheTargetsSessionsAndBlocksLoginUntilReEnabled() throws Exception {
    MockHttpSession targetSession = logIn(mvc, target, PASSWORD);

    setStatus(targetId, false, admin)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(targetId))
        .andExpect(jsonPath("$.username").value(target))
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    mvc.perform(get("/api/v1/auth/me").session(targetSession))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    login(target).andExpect(status().isUnauthorized());

    setStatus(targetId, true, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
    login(target).andExpect(status().isOk());
  }

  @Test
  void aMissingEnabledFlagIsAValidationFailure() throws Exception {
    send(patch("/api/v1/admin/users/{id}/status", targetId), "{}", admin)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("enabled"));
  }

  // --- role ---

  @Test
  void promotingEndsTheTargetsSessionsAndAppliesAfterTheNextLogin() throws Exception {
    MockHttpSession oldSession = logIn(mvc, target, PASSWORD);
    mvc.perform(get("/api/v1/admin/users").session(oldSession)).andExpect(status().isForbidden());

    setRole(targetId, "ADMIN", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(target))
        .andExpect(jsonPath("$.role").value("ADMIN"));

    mvc.perform(get("/api/v1/auth/me").session(oldSession)).andExpect(status().isUnauthorized());
    MockHttpSession newSession = logIn(mvc, target, PASSWORD);
    mvc.perform(get("/api/v1/auth/me").session(newSession))
        .andExpect(jsonPath("$.role").value("ADMIN"));
    mvc.perform(get("/api/v1/admin/users").session(newSession)).andExpect(status().isOk());
  }

  @Test
  void demotingTakesAdminRightsAwayAfterTheNextLogin() throws Exception {
    setRole(targetId, "ADMIN", admin).andExpect(status().isOk());
    MockHttpSession asAdmin = logIn(mvc, target, PASSWORD);

    setRole(targetId, "USER", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));

    mvc.perform(get("/api/v1/admin/users").session(asAdmin)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/users").session(logIn(mvc, target, PASSWORD)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnknownOrMissingRoleIsAValidationFailure() throws Exception {
    for (String body : List.of("{\"role\": \"SUPERUSER\"}", "{\"role\": \"admin\"}", "{}")) {
      send(patch("/api/v1/admin/users/{id}/role", targetId), body, admin)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors[0].field").value("role"));
    }
    assertUnchanged();
  }

  // --- delete ---

  @Test
  void deletingEndsTheTargetsSessionsRemovesTheirResetTokensAndBlocksLogin() throws Exception {
    MockHttpSession targetSession = logIn(mvc, target, PASSWORD);
    mvc.perform(
            withCsrf(mvc, post("/api/v1/auth/password-reset/request"))
                .with(fromIp(uniqueIp()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + target + "@example.com\"}"))
        .andExpect(status().isAccepted());
    assertThat(resetTokensOf(targetId)).isEqualTo(1);

    deleteUser(targetId, admin).andExpect(status().isNoContent()).andExpect(content().string(""));

    assertThat(resetTokensOf(targetId)).isZero();
    mvc.perform(get("/api/v1/auth/me").session(targetSession)).andExpect(status().isUnauthorized());
    login(target).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/users").session(admin))
        .andExpect(jsonPath("$[?(@.username == '" + target + "')]").isEmpty());
  }

  // --- refusals ---

  @Test
  void anAdminCannotDisableDemoteOrDeleteThemselves(CapturedOutput output) throws Exception {
    long adminId = userId(mvc, admin, "admin");

    for (ResultActions refused :
        List.of(
            setStatus(adminId, false, admin),
            setRole(adminId, "USER", admin),
            deleteUser(adminId, admin))) {
      refused
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.code").value("SELF_ACTION_NOT_ALLOWED"))
          .andExpect(jsonPath("$.message").value("You can't change your own account."));
    }

    mvc.perform(get("/api/v1/admin/users").session(admin))
        .andExpect(jsonPath("$[?(@.username == 'admin')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.username == 'admin')].role").value("ADMIN"));
    assertThat(auditLines(output, "ADMIN_SELF_ACTION_REJECTED"))
        .hasSize(3)
        .allMatch(
            line ->
                line.contains("event=ADMIN_SELF_ACTION_REJECTED actor=admin ip=127.0.0.1")
                    && line.contains("outcome=failure target=admin action="));
  }

  @Test
  void anUnknownIdIsNotFound() throws Exception {
    long unknown = Long.MAX_VALUE;
    for (ResultActions missing :
        List.of(
            setStatus(unknown, false, admin),
            setRole(unknown, "ADMIN", admin),
            deleteUser(unknown, admin))) {
      missing
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
          .andExpect(jsonPath("$.message").value("User not found"));
    }
  }

  @Test
  void aUserIsForbiddenAndNothingChanges() throws Exception {
    MockHttpSession john = logIn(mvc);
    for (ResultActions forbidden : actionsOn(targetId, john)) {
      forbidden.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertUnchanged();
  }

  @Test
  void anonymousIsUnauthorizedAndNothingChanges() throws Exception {
    for (ResultActions unauthorized : actionsOn(targetId, null)) {
      unauthorized
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
    assertUnchanged();
  }

  @Test
  void everyActionNeedsTheCsrfToken() throws Exception {
    List<MockHttpServletRequestBuilder> withoutToken =
        List.of(
            patch("/api/v1/admin/users/{id}/status", targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"),
            patch("/api/v1/admin/users/{id}/role", targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"ADMIN\"}"),
            delete("/api/v1/admin/users/{id}", targetId));
    for (MockHttpServletRequestBuilder request : withoutToken) {
      mvc.perform(request.session(admin))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    assertUnchanged();
  }

  // --- audit ---

  @Test
  void everyChangeIsAuditedWithActorTargetAndIp(CapturedOutput output) throws Exception {
    setStatus(targetId, false, admin).andExpect(status().isOk());
    setStatus(targetId, true, admin).andExpect(status().isOk());
    setRole(targetId, "ADMIN", admin).andExpect(status().isOk());
    deleteUser(targetId, admin).andExpect(status().isNoContent());

    String who = "actor=admin ip=127.0.0.1 outcome=success target=" + target;
    assertThat(auditLines(output, "USER_DISABLED")).singleElement().asString().contains(who);
    assertThat(auditLines(output, "USER_ENABLED")).singleElement().asString().contains(who);
    assertThat(auditLines(output, "USER_ROLE_CHANGED"))
        .singleElement()
        .asString()
        .contains(who + " oldRole=USER newRole=ADMIN");
    assertThat(auditLines(output, "USER_DELETED")).singleElement().asString().contains(who);
  }

  @Test
  void settingTheCurrentValueChangesNothingAndIsNotAudited(CapturedOutput output)
      throws Exception {
    MockHttpSession targetSession = logIn(mvc, target, PASSWORD);

    setStatus(targetId, true, admin).andExpect(status().isOk());
    setRole(targetId, "USER", admin).andExpect(status().isOk());

    mvc.perform(get("/api/v1/auth/me").session(targetSession)).andExpect(status().isOk());
    assertThat(auditLines(output, "USER_ENABLED")).isEmpty();
    assertThat(auditLines(output, "USER_ROLE_CHANGED")).isEmpty();
  }

  /** Disable, promote and delete account {@code id} with {@code session} ({@code null}: none). */
  private List<ResultActions> actionsOn(long id, MockHttpSession session) throws Exception {
    return List.of(
        setStatus(id, false, session), setRole(id, "ADMIN", session), deleteUser(id, session));
  }

  private void assertUnchanged() throws Exception {
    mvc.perform(get("/api/v1/admin/users").session(admin))
        .andExpect(jsonPath("$[?(@.username == '" + target + "')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.username == '" + target + "')].role").value("USER"));
  }

  private ResultActions setStatus(long id, boolean enabled, MockHttpSession session)
      throws Exception {
    return send(
        patch("/api/v1/admin/users/{id}/status", id), "{\"enabled\": " + enabled + "}", session);
  }

  private ResultActions setRole(long id, String role, MockHttpSession session) throws Exception {
    return send(
        patch("/api/v1/admin/users/{id}/role", id), "{\"role\": \"" + role + "\"}", session);
  }

  private ResultActions deleteUser(long id, MockHttpSession session) throws Exception {
    MockHttpServletRequestBuilder request = withCsrf(mvc, delete("/api/v1/admin/users/{id}", id));
    return mvc.perform(session == null ? request : request.session(session));
  }

  private ResultActions send(
      MockHttpServletRequestBuilder request, String body, MockHttpSession session)
      throws Exception {
    MockHttpServletRequestBuilder json =
        withCsrf(mvc, request).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(session == null ? json : json.session(session));
  }

  /** Logs in from a fresh address, so a refused login never feeds another test's throttle. */
  private ResultActions login(String username) throws Exception {
    return mvc.perform(
        loginRequest(mvc, credentialsJson(username, PASSWORD)).with(fromIp(uniqueIp())));
  }

  /** The reset tokens stored for {@code userId}: no API exposes them, so this reads the table. */
  private int resetTokensOf(long userId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM password_reset_token WHERE user_id = ?", Integer.class, userId);
  }

  /** The {@code AUDIT} lines for {@code event} in {@code output}. */
  private static List<String> auditLines(CapturedOutput output, String event) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.contains("\"logger\":\"AUDIT\"") || line.contains(" AUDIT "))
        .filter(line -> line.contains("event=" + event + " "))
        .toList();
  }
}
