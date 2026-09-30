package local.builderday.auth.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Every 403 ACCESS_DENIED is a Security audit event; the handler's other answers are not. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AccessDeniedAuditTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    userId = UUID.randomUUID();
    userRepository.save(new UserEntity(userId, "testuser123", null, passwordEncoder.encode(PASSWORD), "USER", true));
  }

  @Test
  void should_auditTheDenialWithoutNamingRoles_when_aUserIsRefusedAnAdminEndpoint() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      Cookie session = login();
      Object sessionHash = AuditLogCapture.fields(audit.events().getLast()).get("session.hash");

      mvc.perform(get("/api/admin/users").cookie(session)).andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

      var denials = authorizationEvents(audit);
      assertThat(denials).hasSize(1);
      var fields = AuditLogCapture.fields(denials.getFirst());
      assertThat(fields).containsEntry("event.category", List.of("web")).containsEntry("event.type", List.of("access"))
          .containsEntry("event.outcome", "failure").containsEntry("event.reason", "access_denied")
          .containsEntry("user.id", userId.toString()).containsEntry("session.hash", sessionHash)
          .containsEntry("url.path", "/api/admin/users");
      assertThat(sessionHash).isNotNull();
      assertThat(denials.getFirst().getFormattedMessage()).isEqualTo("authorization access_denied");
      assertThat(fields).doesNotContainKeys("user.roles", "user.role").doesNotContainValue("ADMIN")
          .doesNotContainValue("USER").doesNotContainValue(List.of("ADMIN"));
    }
  }

  @Test
  void should_notAudit_when_theAnswerIsNotFoundMethodNotAllowedOrAuthenticationRequired() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      Cookie session = login();

      mvc.perform(get("/api/nothing-here").cookie(session)).andExpect(status().isNotFound());
      mvc.perform(delete("/api/profile").with(csrf()).cookie(session)).andExpect(status().isMethodNotAllowed());
      mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());

      assertThat(authorizationEvents(audit)).isEmpty();
    }
  }

  private static List<ch.qos.logback.classic.spi.ILoggingEvent> authorizationEvents(AuditLogCapture audit) {
    return audit.events().stream()
        .filter(event -> "authorization".equals(AuditLogCapture.fields(event).get("event.action"))).toList();
  }

  private Cookie login() throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser123\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }
}
