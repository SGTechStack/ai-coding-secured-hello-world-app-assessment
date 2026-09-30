package local.builderday.account.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The admin account status toggle at the HTTP boundary (Story 9), through the real security chain, Session store and
 * H2: {@code PATCH /api/admin/users/{id}} with body {@code {"enabled": <boolean>}}. External behaviour only —
 * status codes, response body and audit events.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class AdminStatusToggleTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @MockitoSpyBean UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;

  private UUID adminId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    String passwordHash = passwordEncoder.encode(PASSWORD);
    adminId = save("janeadmin", "janeadmin@test.example.com", "ADMIN", passwordHash);
    userId = save("johndoe", "johndoe@test.example.com", "USER", passwordHash);
  }

  @Test
  void should_disableAnAccountEndItsSessionsAndBlockLogin_when_anAdminDisablesIt() throws Exception {
    var victim = login("johndoe");
    mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isOk());

    var body = mvc.perform(toggle(userId, false)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.username").value("johndoe"))
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.deleted").value(false))
        .andReturn().getResponse().getContentAsString();
    // The response is the User-list row shape, no password hash.
    Map<String, Object> row = JsonPath.read(body, "$");
    assertThat(row).containsOnlyKeys("id", "username", "email", "role", "enabled", "deleted", "locked",
        "failedLoginAttempts", "lockedUntil", "disabledAt", "deletedAt", "lastLoginAt", "createdAt", "updatedAt");
    assertThat(row.get("disabledAt")).isNotNull();

    // Existing Sessions are gone, and the disabled account can no longer log in.
    mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(credentials("johndoe"))).andExpect(status().isUnauthorized());
  }

  @Test
  void should_enableADisabledAccountAndRestoreLogin_when_anAdminEnablesIt() throws Exception {
    mvc.perform(toggle(userId, false)).andExpect(status().isOk());

    mvc.perform(toggle(userId, true)).andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.disabledAt").doesNotExist());

    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(credentials("johndoe"))).andExpect(status().isOk());
  }

  @Test
  void should_returnUnchangedAndWriteNoAudit_when_theRequestMatchesTheCurrentState() throws Exception {
    var victim = login("johndoe");
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      // Enable an already-enabled account: no-op.
      mvc.perform(toggle(userId, true)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
      // Its Session survives a no-op.
      mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isOk());
      assertThat(toggleEvents(audit)).isEmpty();
    }

    mvc.perform(toggle(userId, false)).andExpect(status().isOk());
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      // Disable an already-disabled account: no-op.
      mvc.perform(toggle(userId, false)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
      assertThat(toggleEvents(audit)).isEmpty();
    }
  }

  @Test
  void should_rejectWithoutAuthorizationEvent_when_anAdminTargetsTheirOwnAccount() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(toggle(adminId, false)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_TARGET_SELF"))
          .andExpect(jsonPath("$.detail").value("You cannot perform this action on your own account."));
      mvc.perform(toggle(adminId, true)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_TARGET_SELF"));

      assertThat(userRepository.findById(adminId).orElseThrow().isEnabled()).isTrue();
      // A failed privileged-attempt event, not an access-denied (authorization) event.
      var events = toggleEvents(audit);
      assertThat(events).hasSize(2);
      var fields = AuditLogCapture.fields(events.getFirst());
      assertThat(fields).containsEntry("event.action", "account-disabled")
          .containsEntry("event.outcome", "failure").containsEntry("event.reason", "self_target")
          .containsEntry("user.id", adminId.toString()).containsEntry("source.user.id", adminId.toString());
      assertThat(events).noneMatch(event ->
          List.of("authorization").equals(AuditLogCapture.fields(event).get("event.category")));
      assertThat(events.getFirst().getFormattedMessage() + fields).doesNotContain("janeadmin", "@test.example.com");
    }
  }

  @Test
  void should_rejectATombstone_when_theTargetIsDeleted() throws Exception {
    mvc.perform(toggle(userId, false)).andExpect(status().isOk());
    // Tombstone it directly through the store (deletion is a later story's action, not this endpoint's).
    var deleted = userRepository.findById(userId).orElseThrow();
    deleted.setDeletedAt(clocks.now());
    userRepository.save(deleted);

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(toggle(userId, true)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ACCOUNT_DELETED"))
          .andExpect(jsonPath("$.detail").value("This account is deleted and cannot be changed."));

      var events = toggleEvents(audit);
      assertThat(events).hasSize(1);
      assertThat(AuditLogCapture.fields(events.getFirst())).containsEntry("event.action", "account-enabled")
          .containsEntry("event.outcome", "failure").containsEntry("event.reason", "account_deleted")
          .containsEntry("user.id", userId.toString()).containsEntry("source.user.id", adminId.toString());
    }
    assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isFalse();
  }

  @Test
  void should_answerCleanly_when_theIdIsUnknownOrTheBodyIsMalformed() throws Exception {
    var admin = login("janeadmin");
    mvc.perform(patch("/api/admin/users/" + UUID.randomUUID()).with(csrf()).cookie(admin)
        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

    for (String badBody : List.of("{}", "{\"enabled\":\"nope\"}", "{\"enabled\":null}", "not json")) {
      mvc.perform(patch("/api/admin/users/" + userId).with(csrf()).cookie(admin)
          .contentType(MediaType.APPLICATION_JSON).content(badBody))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    // A path id that is not a UUID is a clean 400, never a 500.
    mvc.perform(patch("/api/admin/users/not-a-uuid").with(csrf()).cookie(admin)
        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void should_denyUsersAndVisitors_andRejectUnsupportedMethods() throws Exception {
    // Visitor: no Session.
    mvc.perform(patch("/api/admin/users/" + userId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":false}")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    // A User is refused and keeps their Session.
    var user = login("johndoe");
    mvc.perform(patch("/api/admin/users/" + adminId).with(csrf()).cookie(user)
        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mvc.perform(get("/api/profile").cookie(user)).andExpect(status().isOk());
    assertThat(userRepository.findById(adminId).orElseThrow().isEnabled()).isTrue();

    // The id path supports PATCH and DELETE only; another method is a 405 (ADR 0007), not a 404 or 500.
    var admin = login("janeadmin");
    mvc.perform(put("/api/admin/users/" + userId).with(csrf()).cookie(admin)
        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
        .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(post("/api/admin/users/" + userId).with(csrf()).cookie(admin))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void should_writeOneCleanEvent_when_aRealTransitionHappens() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(toggle(userId, false)).andExpect(status().isOk());
      var disable = toggleEvents(audit);
      assertThat(disable).hasSize(1);
      var fields = AuditLogCapture.fields(disable.getFirst());
      assertThat(fields).containsEntry("event.action", "account-disabled")
          .containsEntry("event.category", List.of("iam")).containsEntry("event.type", List.of("change"))
          .containsEntry("event.outcome", "success").containsEntry("event.reason", "admin_action")
          .containsEntry("user.id", userId.toString()).containsEntry("source.user.id", adminId.toString());
      assertThat(disable.getFirst().getFormattedMessage() + fields)
          .doesNotContain("johndoe", "janeadmin", "@test.example.com");
    }
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(toggle(userId, true)).andExpect(status().isOk());
      var enable = toggleEvents(audit);
      assertThat(enable).hasSize(1);
      assertThat(AuditLogCapture.fields(enable.getFirst())).containsEntry("event.action", "account-enabled")
          .containsEntry("event.outcome", "success").containsEntry("event.reason", "admin_action")
          .containsEntry("user.id", userId.toString()).containsEntry("source.user.id", adminId.toString());
    }
  }

  @Test
  void should_refuseAndChangeNothing_when_theAccountChangesWhileTheToggleRuns() throws Exception {
    var victim = login("johndoe");
    var admin = login("janeadmin");
    // The one test double at this seam: the toggle's own read is followed by a concurrent committed write.
    doAnswer(invocation -> {
      var read = Optional.ofNullable(entityManager.find(UserEntity.class, userId));
      concurrentWrite(userId);
      return read;
    }).when(userRepository).findById(userId);

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      var body = mvc.perform(patch("/api/admin/users/" + userId).with(csrf()).cookie(admin)
              .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
          .andExpect(jsonPath("$.detail").value("This account changed while you were acting on it. Try again."))
          .andReturn().getResponse().getContentAsString();
      assertThat(body).doesNotContain("UserEntity", "johndoe", "version");
      assertThat(toggleEvents(audit)).isEmpty();
    }
    Mockito.reset(userRepository);
    assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isTrue();
    mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isOk());
  }

  /** Bumps the row's version in its own committed transaction, as another writer would. */
  private void concurrentWrite(UUID id) {
    var separate = new TransactionTemplate(transactions);
    separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    separate.executeWithoutResult(status ->
        jdbcTemplate.update("update users set version = version + 1 where id = ?", id));
  }

  private org.springframework.test.web.servlet.RequestBuilder toggle(UUID id, boolean enabled) throws Exception {
    return patch("/api/admin/users/" + id).with(csrf()).cookie(login("janeadmin"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}");
  }

  private UUID save(String username, String email, String role, String passwordHash) {
    var id = UUID.randomUUID();
    userRepository.save(new UserEntity(id, username, email, passwordHash, role, true));
    return id;
  }

  private Cookie login(String username) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(credentials(username))).andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }

  private static String credentials(String username) {
    return "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
  }

  private static List<ILoggingEvent> toggleEvents(AuditLogCapture audit) {
    return audit.events().stream().filter(event -> {
      Object action = AuditLogCapture.fields(event).get("event.action");
      return "account-disabled".equals(action) || "account-enabled".equals(action);
    }).toList();
  }
}
