package local.builderday.account.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** The User list at the HTTP boundary, through the real security chain, Session store and H2. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class UserListControllerTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final String USERS = "/api/admin/users";
  private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired TestClocks clocks;

  private String passwordHash;
  private UUID adminId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    passwordHash = passwordEncoder.encode(PASSWORD);
    adminId = account("janeadmin", "janeadmin@test.example.com", "ADMIN", 0);
    userId = account("johndoe", "johndoe@test.example.com", "USER", 1);
  }

  @Test
  void should_listEveryFieldButThePasswordHash_when_anAdminReadsThePage() throws Exception {
    var body = mvc.perform(get(USERS).cookie(login("janeadmin"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(20)).andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(1))
        .andExpect(jsonPath("$.content[1].id").value(adminId.toString()))
        .andExpect(jsonPath("$.content[1].username").value("janeadmin"))
        .andExpect(jsonPath("$.content[1].email").value("janeadmin@test.example.com"))
        .andExpect(jsonPath("$.content[1].role").value("ADMIN"))
        .andExpect(jsonPath("$.content[1].enabled").value(true))
        .andExpect(jsonPath("$.content[1].deleted").value(false))
        .andExpect(jsonPath("$.content[1].locked").value(false))
        .andExpect(jsonPath("$.content[1].failedLoginAttempts").value(0))
        .andExpect(jsonPath("$.content[1].createdAt").value("2026-01-01T00:00:00Z"))
        .andReturn().getResponse().getContentAsString();

    Map<String, Object> entry = JsonPath.read(body, "$.content[1]");
    assertThat(entry).containsOnlyKeys("id", "username", "email", "role", "enabled", "deleted", "locked",
        "failedLoginAttempts", "lockedUntil", "disabledAt", "deletedAt", "lastLoginAt", "createdAt", "updatedAt");
    assertThat(entry.get("lastLoginAt")).isNotNull();
    assertThat(entry.get("updatedAt")).isNotNull();
    assertThat(body).doesNotContainIgnoringCase("password").doesNotContain(passwordHash);
  }

  @Test
  void should_reportEachAccountsState_when_accountsAreTombstonedDisabledOrLocked() throws Exception {
    var deleted = account("gone00001", "gone00001@test.example.com", "USER", 2);
    var disabled = account("idle00001", null, "USER", 3);
    var locked = account("locked001", "locked001@test.example.com", "USER", 4);
    var unlocked = account("unlocked1", "unlocked1@test.example.com", "USER", 5);
    var now = clocks.now();
    jdbcTemplate.update("update users set deleted_at = ?, disabled_at = ?, enabled = false where id = ?",
        Timestamp.from(now), Timestamp.from(now), deleted);
    jdbcTemplate.update("update users set disabled_at = ?, enabled = false where id = ?",
        Timestamp.from(now), disabled);
    jdbcTemplate.update("update users set locked_until = ?, failed_login_attempts = 2 where id = ?",
        Timestamp.from(now.plus(Duration.ofMinutes(20))), locked);
    jdbcTemplate.update("update users set locked_until = ? where id = ?",
        Timestamp.from(now.minusSeconds(1)), unlocked);

    var body = mvc.perform(get(USERS).cookie(login("janeadmin"))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(state(body, deleted)).containsEntry("enabled", false).containsEntry("deleted", true)
        .containsEntry("locked", false).containsEntry("deletedAt", iso(now)).containsEntry("disabledAt", iso(now));
    assertThat(state(body, disabled)).containsEntry("enabled", false).containsEntry("deleted", false)
        .containsEntry("email", null).containsEntry("deletedAt", null);
    assertThat(state(body, locked)).containsEntry("locked", true).containsEntry("failedLoginAttempts", 2)
        .containsEntry("lockedUntil", iso(now.plus(Duration.ofMinutes(20))));
    assertThat(state(body, unlocked)).containsEntry("locked", false).containsEntry("enabled", true);

    // The lock ends with the application clock, not the wall clock.
    clocks.advance(Duration.ofMinutes(21));
    var later = mvc.perform(get(USERS).cookie(login("janeadmin"))).andReturn().getResponse().getContentAsString();
    assertThat(state(later, locked)).containsEntry("locked", false);
  }

  @Test
  void should_pageNewestFirst_when_pageAndSizeAreGiven() throws Exception {
    for (int i = 2; i < 7; i++) account("member000" + i, null, "USER", i);
    var session = login("janeadmin");

    var second = mvc.perform(get(USERS).param("page", "1").param("size", "3").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(3)).andExpect(jsonPath("$.page.number").value(1))
        .andExpect(jsonPath("$.page.totalElements").value(7)).andExpect(jsonPath("$.page.totalPages").value(3))
        .andReturn().getResponse().getContentAsString();
    assertThat(usernames(second)).containsExactly("member0003", "member0002", "johndoe");
    var last = mvc.perform(get(USERS).param("page", "2").param("size", "3").cookie(session))
        .andReturn().getResponse().getContentAsString();
    assertThat(usernames(last)).containsExactly("janeadmin");
    mvc.perform(get(USERS).param("size", "100").cookie(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].username").value("member0006"));
    mvc.perform(get(USERS).param("page", "9").cookie(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page.totalElements").value(7));
  }

  @Test
  void should_rejectTheRequest_when_pageOrSizeIsOutOfRangeOrNotANumber() throws Exception {
    var session = login("janeadmin");
    for (String[] query : new String[][] {
        {"size", "0"}, {"size", "101"}, {"page", "-1"}, {"page", "two"}, {"size", "x"}}) {
      mvc.perform(get(USERS).param(query[0], query[1]).cookie(session)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
  }

  @Test
  void should_denyUsersAndVisitorsAndRejectOtherMethods() throws Exception {
    mvc.perform(get(USERS)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    var user = login("johndoe");
    mvc.perform(get(USERS).cookie(user)).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mvc.perform(get("/api/profile").cookie(user)).andExpect(status().isOk());

    var admin = login("janeadmin");
    mvc.perform(post(USERS).with(csrf()).cookie(admin)).andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(put(USERS).with(csrf()).cookie(admin)).andExpect(status().isMethodNotAllowed());
    mvc.perform(delete(USERS).with(csrf()).cookie(admin)).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void should_auditEachPageWithoutAccountData_when_anAdminReadsIt() throws Exception {
    var session = login("janeadmin");
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(get(USERS).param("size", "1").cookie(session)).andExpect(status().isOk());

      var events = audit.events().stream()
          .filter(event -> "user-list".equals(AuditLogCapture.fields(event).get("event.action"))).toList();
      assertThat(events).hasSize(1);
      var fields = AuditLogCapture.fields(events.getFirst());
      assertThat(fields).containsEntry("event.category", List.of("iam")).containsEntry("event.type", List.of("info"))
          .containsEntry("event.outcome", "success").containsEntry("user.id", adminId.toString())
          .containsEntry("user_list.page", 0).containsEntry("user_list.size", 1).containsEntry("user_list.count", 1);
      assertThat(events.getFirst().getFormattedMessage() + fields)
          .doesNotContain("johndoe", "janeadmin", "@test.example.com", userId.toString());
    }
  }

  private UUID account(String username, String email, String role, int minutesAfterBase) {
    var id = UUID.randomUUID();
    userRepository.save(new UserEntity(id, username, email, passwordHash, role, true));
    jdbcTemplate.update("update users set created_at = ? where id = ?",
        Timestamp.from(BASE.plus(Duration.ofMinutes(minutesAfterBase))), id);
    return id;
  }

  private Cookie login(String username) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }

  private static List<String> usernames(String body) {
    return JsonPath.read(body, "$.content[*].username");
  }

  private static Map<String, Object> state(String body, UUID id) {
    List<Map<String, Object>> matches = JsonPath.read(body, "$.content[?(@.id == '" + id + "')]");
    assertThat(matches).hasSize(1);
    return matches.getFirst();
  }

  private static String iso(Instant instant) {
    return instant.toString();
  }
}
