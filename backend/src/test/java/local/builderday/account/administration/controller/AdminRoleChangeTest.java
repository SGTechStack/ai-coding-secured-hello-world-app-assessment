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
import local.builderday.support.Accounts;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The admin Role change at the HTTP boundary (Story 10), through the real security chain, Session store and H2:
 * {@code PUT /api/admin/users/{id}/role} with body {@code {"role": "<ROLE>"}}. External behaviour only: status codes,
 * response body, Sessions and audit events.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class AdminRoleChangeTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @MockitoSpyBean UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;

  private UUID adminId;
  private UUID otherAdminId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    String passwordHash = passwordEncoder.encode(PASSWORD);
    adminId = save("janeadmin", "ADMIN", passwordHash);
    otherAdminId = save("maryadmin", "ADMIN", passwordHash);
    userId = save("johndoe", "USER", passwordHash);
  }

  @Test
  void should_promoteAUser_endTheirSessions_andGrantAdminAtTheirNextLogin() throws Exception {
    var target = login("johndoe");

    var body = mvc.perform(changeRole(userId, "ADMIN")).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andReturn().getResponse().getContentAsString();
    Map<String, Object> row = JsonPath.read(body, "$");
    assertThat(row).containsOnlyKeys("id", "username", "email", "role", "enabled", "deleted", "locked",
        "failedLoginAttempts", "lockedUntil", "disabledAt", "deletedAt", "lastLoginAt", "createdAt", "updatedAt");

    mvc.perform(get("/api/profile").cookie(target)).andExpect(status().isUnauthorized());
    var again = login("johndoe");
    mvc.perform(get("/api/profile").cookie(again)).andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    mvc.perform(get("/api/admin/users").cookie(again)).andExpect(status().isOk());
  }

  @Test
  void should_demoteAnAdmin_endTheirSessions_andRefuseAdminPagesAfterTheirNextLogin() throws Exception {
    var target = login("maryadmin");
    mvc.perform(get("/api/admin/users").cookie(target)).andExpect(status().isOk());

    mvc.perform(changeRole(otherAdminId, "USER")).andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));

    mvc.perform(get("/api/admin/users").cookie(target)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/users").cookie(login("maryadmin"))).andExpect(status().isForbidden());
  }

  @Test
  void should_changeTheRoleOfADisabledAccount() throws Exception {
    var user = userRepository.findById(userId).orElseThrow();
    userRepository.save(Accounts.disable(user, clocks.now()));

    mvc.perform(changeRole(userId, "ADMIN")).andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void should_returnUnchanged_withNoAuditAndSessionsKept_when_theRoleIsAlreadyHeld() throws Exception {
    var target = login("johndoe");
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(changeRole(userId, "USER")).andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("USER"));
      assertThat(roleEvents(audit)).isEmpty();
    }
    mvc.perform(get("/api/profile").cookie(target)).andExpect(status().isOk());
  }

  @Test
  void should_refuseTheCallersOwnAccount_whateverRoleIsAsked() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      for (String role : List.of("USER", "ADMIN")) {
        mvc.perform(changeRole(adminId, role)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_TARGET_SELF"));
      }
      var events = roleEvents(audit);
      assertThat(events).hasSize(2);
      assertThat(AuditLogCapture.fields(events.getFirst())).containsEntry("event.outcome", "failure")
          .containsEntry("event.reason", "self_target").containsEntry("user.id", adminId.toString())
          .containsEntry("source.user.id", adminId.toString()).containsEntry("user.changes.roles", List.of("USER"));
      assertThat(audit.events()).noneMatch(event ->
          List.of("authorization").equals(AuditLogCapture.fields(event).get("event.category")));
    }
    assertThat(userRepository.findById(adminId).orElseThrow().getRole()).isEqualTo("ADMIN");
  }

  @Test
  void should_refuseADeletedAccount_andAnswerNotFoundForAnUnknownId() throws Exception {
    var user = userRepository.findById(userId).orElseThrow();
    userRepository.save(Accounts.markDeleted(user, clocks.now()));

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(changeRole(userId, "ADMIN")).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ACCOUNT_DELETED"))
          .andExpect(jsonPath("$.detail").value("This account is deleted and cannot be changed."));
      var events = roleEvents(audit);
      assertThat(events).hasSize(1);
      assertThat(AuditLogCapture.fields(events.getFirst())).containsEntry("event.outcome", "failure")
          .containsEntry("event.reason", "account_deleted").containsEntry("user.id", userId.toString());
    }
    assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo("USER");

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(changeRole(UUID.randomUUID(), "ADMIN")).andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
      assertThat(roleEvents(audit)).isEmpty();
    }
  }

  @Test
  void should_answerACleanBadRequest_when_theRoleOrBodyOrIdIsInvalid() throws Exception {
    var admin = login("janeadmin");
    for (String badBody : List.of("{}", "{\"role\":null}", "{\"role\":\"\"}", "{\"role\":\"  \"}",
        "{\"role\":\"AUDITOR\"}", "{\"role\":\"admin\"}", "{\"role\":1}", "{\"role\":[\"ADMIN\"]}",
        "{\"role\":{}}", "not json")) {
      var body = mvc.perform(put("/api/admin/users/" + userId + "/role").with(csrf()).cookie(admin)
              .contentType(MediaType.APPLICATION_JSON).content(badBody))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
          .andReturn().getResponse().getContentAsString();
      assertThat(body).as(badBody).doesNotContain("Exception", "at local.", "Role", "Jackson");
    }
    mvc.perform(put("/api/admin/users/not-a-uuid/role").with(csrf()).cookie(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo("USER");
  }

  @Test
  void should_denyUsersAndVisitors_andRejectUnsupportedMethods() throws Exception {
    mvc.perform(put("/api/admin/users/" + userId + "/role").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    var user = login("johndoe");
    mvc.perform(put("/api/admin/users/" + userId + "/role").with(csrf()).cookie(user)
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo("USER");

    var admin = login("janeadmin");
    mvc.perform(patch("/api/admin/users/" + userId + "/role").with(csrf()).cookie(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(get("/api/admin/users/" + userId + "/role").cookie(admin))
        .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void should_writeOneCleanEvent_when_theRoleReallyChanges() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(changeRole(userId, "ADMIN")).andExpect(status().isOk());

      var events = roleEvents(audit);
      assertThat(events).hasSize(1);
      var fields = AuditLogCapture.fields(events.getFirst());
      assertThat(fields).containsEntry("event.action", "role-changed")
          .containsEntry("event.category", List.of("iam")).containsEntry("event.type", List.of("change"))
          .containsEntry("event.outcome", "success").containsEntry("event.reason", "admin_action")
          .containsEntry("user.id", userId.toString()).containsEntry("source.user.id", adminId.toString())
          .containsEntry("user.changes.roles", List.of("ADMIN"));
      assertThat(events.getFirst().getFormattedMessage() + fields)
          .doesNotContain("johndoe", "janeadmin", "@test.example.com");
    }
  }

  @Test
  void should_refuseAndChangeNothing_when_theAccountChangesWhileTheRoleChangeRuns() throws Exception {
    var target = login("johndoe");
    var admin = login("janeadmin");
    doAnswer(invocation -> {
      var read = Optional.ofNullable(entityManager.find(UserEntity.class, userId));
      concurrentWrite(userId);
      return read;
    }).when(userRepository).findById(userId);

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(put("/api/admin/users/" + userId + "/role").with(csrf()).cookie(admin)
              .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
      assertThat(roleEvents(audit)).isEmpty();
    }
    Mockito.reset(userRepository);
    assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo("USER");
    mvc.perform(get("/api/profile").cookie(target)).andExpect(status().isOk());
  }

  /** Bumps the row's version in its own committed transaction, as another writer would. */
  private void concurrentWrite(UUID id) {
    var separate = new TransactionTemplate(transactions);
    separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    separate.executeWithoutResult(status ->
        jdbcTemplate.update("update users set version = version + 1 where id = ?", id));
  }

  private RequestBuilder changeRole(UUID id, String role) throws Exception {
    return put("/api/admin/users/" + id + "/role").with(csrf()).cookie(login("janeadmin"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}");
  }

  private UUID save(String username, String role, String passwordHash) {
    var id = UUID.randomUUID();
    userRepository.save(new UserEntity(id, username, username + "@test.example.com", passwordHash, role, true));
    return id;
  }

  private Cookie login(String username) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }

  private static List<ILoggingEvent> roleEvents(AuditLogCapture audit) {
    return audit.events().stream()
        .filter(event -> "role-changed".equals(AuditLogCapture.fields(event).get("event.action"))).toList();
  }
}
