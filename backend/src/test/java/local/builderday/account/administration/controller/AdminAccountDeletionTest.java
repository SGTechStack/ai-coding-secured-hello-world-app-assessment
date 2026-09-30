package local.builderday.account.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.notification.service.EmailService;
import local.builderday.support.Accounts;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin Account deletion at the HTTP boundary (Story 11), through the real security chain, Session store and H2:
 * {@code DELETE /api/admin/users/{id}}. External behaviour only: status codes, bodies, audit events and the stored
 * state as the API shows it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class AdminAccountDeletionTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final String LINK_PREFIX = "https://app.test.example/reset-password#token=";

  @Autowired MockMvc mvc;
  @MockitoSpyBean UserRepository userRepository;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;
  @MockitoSpyBean EmailService emailService;
  @TestBean(name = "applicationTaskExecutor", methodName = "callerRuns") TaskExecutor applicationTaskExecutor;

  private String passwordHash;
  private UUID adminId;
  private UUID userId;

  static TaskExecutor callerRuns() { return new SyncTaskExecutor(); }

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    rateLimitBucketRepository.deleteAllInBatch();
    if (passwordHash == null) passwordHash = passwordEncoder.encode(PASSWORD);
    adminId = save("janeadmin", "ADMIN");
    userId = save("johndoe", "USER");
  }

  @Test
  void should_tombstoneTheAccountEndItsSessionAndBlockLogin_when_anAdminDeletesIt() throws Exception {
    var victim = login("johndoe");
    mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isOk());

    mvc.perform(deleteAs("janeadmin", userId)).andExpect(status().isNoContent()).andExpect(content().string(""));

    // The User list still shows it, as deleted.
    mvc.perform(get("/api/admin/users").cookie(login("janeadmin"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.username == 'johndoe')].deleted").value(true))
        .andExpect(jsonPath("$.content[?(@.username == 'johndoe')].enabled").value(false));
    // Its Session is gone and it can never log in again (the generic failure).
    mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isUnauthorized());
    loginAttempt("johndoe").andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    // Its username and email stay reserved.
    mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"johndoe\",\"email\":\"johndoe@test.example.com\","
                + "\"password\":\"N3w!Different#Pw\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGISTRATION_REJECTED"));
  }

  @Test
  void should_deleteItLeavingLockoutFieldsAsTheyWere_when_theTargetIsAnAdminDisabledOrLocked() throws Exception {
    var otherAdmin = save("otheradmin", "ADMIN");
    var disabled = save("disabled01", "USER");
    userRepository.save(Accounts.disable(userRepository.findById(disabled).orElseThrow(), clocks.now()));
    var locked = save("locked0001", "USER");
    for (int attempt = 0; attempt < 5; attempt++) {
      mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
          .content("{\"username\":\"locked0001\",\"password\":\"Wr0ng!Password\"}"));
    }
    var lockedUntil = userRepository.findById(locked).orElseThrow().getLockedUntil();
    assertThat(lockedUntil).isAfter(clocks.now());

    for (UUID target : List.of(otherAdmin, disabled, locked)) {
      mvc.perform(deleteAs("janeadmin", target)).andExpect(status().isNoContent());
      assertThat(userRepository.findById(target).orElseThrow().getDeletedAt()).isNotNull();
    }
    assertThat(userRepository.findById(locked).orElseThrow().getLockedUntil()).isEqualTo(lockedUntil);
  }

  @Test
  void should_refuseWithoutAuthorizationEvent_when_anAdminTargetsTheirOwnAccount() throws Exception {
    var admin = login("janeadmin");
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(delete("/api/admin/users/" + adminId).with(csrf()).cookie(admin)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_TARGET_SELF"))
          .andExpect(jsonPath("$.detail").value("You cannot perform this action on your own account."));

      var events = audit.events();
      assertThat(deletionEvents(events)).singleElement().satisfies(event -> {
        var fields = AuditLogCapture.fields(event);
        assertThat(fields).containsEntry("event.outcome", "failure").containsEntry("event.reason", "self_target")
            .containsEntry("user.id", adminId.toString()).containsEntry("source.user.id", adminId.toString());
        assertThat(event.getFormattedMessage() + fields).doesNotContain("janeadmin", "@test.example.com");
      });
      assertThat(events).noneMatch(event ->
          List.of("authorization").equals(AuditLogCapture.fields(event).get("event.category")));
    }
    var self = userRepository.findById(adminId).orElseThrow();
    assertThat(self.getDeletedAt()).isNull();
    assertThat(self.isEnabled()).isTrue();
    mvc.perform(get("/api/profile").cookie(admin)).andExpect(status().isOk());
  }

  @Test
  void should_refuseATombstone_when_theTargetIsAlreadyDeleted() throws Exception {
    mvc.perform(deleteAs("janeadmin", userId)).andExpect(status().isNoContent());

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(deleteAs("janeadmin", userId)).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("ACCOUNT_DELETED"))
          .andExpect(jsonPath("$.detail").value("This account is deleted and cannot be changed."));

      assertThat(deletionEvents(audit.events())).singleElement().satisfies(event ->
          assertThat(AuditLogCapture.fields(event)).containsEntry("event.outcome", "failure")
              .containsEntry("event.reason", "account_deleted").containsEntry("user.id", userId.toString())
              .containsEntry("source.user.id", adminId.toString()));
      assertThat(audit.events()).noneMatch(event ->
          List.of("authorization").equals(AuditLogCapture.fields(event).get("event.category")));
    }
  }

  @Test
  void should_answerCleanlyOrDeny_when_theIdIsUnknownOrMalformedOrTheCallerIsNotAnAdmin() throws Exception {
    var admin = login("janeadmin");
    mvc.perform(delete("/api/admin/users/" + UUID.randomUUID()).with(csrf()).cookie(admin))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    mvc.perform(delete("/api/admin/users/not-a-uuid").with(csrf()).cookie(admin))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(put("/api/admin/users/" + userId).with(csrf()).cookie(admin)
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());

    // Visitor: no Session.
    mvc.perform(delete("/api/admin/users/" + userId).with(csrf())).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    // A User is refused and keeps their Session.
    var user = login("johndoe");
    mvc.perform(delete("/api/admin/users/" + adminId).with(csrf()).cookie(user))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mvc.perform(get("/api/profile").cookie(user)).andExpect(status().isOk());
    assertThat(userRepository.findById(adminId).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void should_writeOneCleanEvent_when_anAccountIsDeleted() throws Exception {
    var admin = login("janeadmin");
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(delete("/api/admin/users/" + userId).with(csrf()).cookie(admin))
          .andExpect(status().isNoContent());

      assertThat(deletionEvents(audit.events())).singleElement().satisfies(event -> {
        var fields = AuditLogCapture.fields(event);
        assertThat(fields).containsEntry("event.category", List.of("iam"))
            .containsEntry("event.type", List.of("deletion")).containsEntry("event.outcome", "success")
            .containsEntry("event.reason", "admin_action").containsEntry("user.id", userId.toString())
            .containsEntry("source.user.id", adminId.toString());
        assertThat(event.getFormattedMessage() + fields).doesNotContain("johndoe", "janeadmin", "@test.example.com");
      });
    }
  }

  @Test
  void should_refuseAndChangeNothing_when_theAccountChangesWhileTheDeletionRuns() throws Exception {
    var victim = login("johndoe");
    var admin = login("janeadmin");
    doAnswer(invocation -> {
      var read = Optional.ofNullable(entityManager.find(UserEntity.class, userId));
      var separate = new TransactionTemplate(transactions);
      separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      separate.executeWithoutResult(status ->
          jdbcTemplate.update("update users set version = version + 1 where id = ?", userId));
      return read;
    }).when(userRepository).findById(userId);

    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(delete("/api/admin/users/" + userId).with(csrf()).cookie(admin))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
      assertThat(deletionEvents(audit.events())).isEmpty();
    }
    Mockito.reset(userRepository);
    assertThat(userRepository.findById(userId).orElseThrow().getDeletedAt()).isNull();
    mvc.perform(get("/api/profile").cookie(victim)).andExpect(status().isOk());
  }

  @Test
  void should_keepResetTokensButRefuseTheirLink_when_theAccountIsDeleted() throws Exception {
    clearInvocations(emailService);
    mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"johndoe@test.example.com\"}")).andExpect(status().isOk());
    var link = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetEmail(eq("johndoe@test.example.com"), eq(userId), link.capture());
    String token = link.getValue().substring(LINK_PREFIX.length());

    mvc.perform(deleteAs("janeadmin", userId)).andExpect(status().isNoContent());

    assertThat(jdbcTemplate.queryForObject("select count(*) from password_reset_tokens where user_id = ?",
        Long.class, userId)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select count(*) from password_history where user_id = ?",
        Long.class, userId)).isPositive();
    clocks.advance(Duration.ofMinutes(1));
    mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"newPassword\":\"N3w!Different#Pw\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
  }

  private org.springframework.test.web.servlet.RequestBuilder deleteAs(String admin, UUID id) throws Exception {
    return delete("/api/admin/users/" + id).with(csrf()).cookie(login(admin));
  }

  private UUID save(String username, String role) {
    var id = UUID.randomUUID();
    userRepository.save(new UserEntity(id, username, username + "@test.example.com", passwordHash, role, true));
    jdbcTemplate.update("insert into password_history (id, user_id, password_hash, created_at, updated_at)"
        + " values (?, ?, ?, ?, ?)",
        UUID.randomUUID(), id, passwordHash, Timestamp.from(clocks.now()), Timestamp.from(clocks.now()));
    return id;
  }

  private Cookie login(String username) throws Exception {
    return loginAttempt(username).andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }

  private ResultActions loginAttempt(String username) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"));
  }

  private static List<ILoggingEvent> deletionEvents(List<ILoggingEvent> events) {
    return events.stream()
        .filter(event -> "account-deleted".equals(AuditLogCapture.fields(event).get("event.action"))).toList();
  }
}
