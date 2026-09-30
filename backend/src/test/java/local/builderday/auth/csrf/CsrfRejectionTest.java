package local.builderday.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** A CSRF rejection is told apart from an authorization denial, and audited, for a logged-in Session. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CsrfRejectionTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final String BAD_TOKEN = "submitted-bogus-csrf-token";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    userId = UUID.randomUUID();
    userRepository.save(new UserEntity(userId, "testuser123", "testuser123@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
  }

  @Test
  void should_returnCsrfTokenRejected_when_aMutatingCallCarriesABadToken() throws Exception {
    Cookie session = login();

    mvc.perform(post("/api/auth/logout").cookie(session).header("X-CSRF-TOKEN", BAD_TOKEN))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_REJECTED"))
        .andExpect(jsonPath("$.detail").value("Request could not be verified."));
  }

  @Test
  void should_auditTheRejectionWithoutTheToken_when_theCsrfCheckFails() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      Cookie session = login();
      Object sessionHash = fields(audit, "user-login").get("session.hash");

      mvc.perform(post("/api/auth/logout").cookie(session).header("X-CSRF-TOKEN", BAD_TOKEN))
          .andExpect(status().isForbidden());

      var rejections = audit.events().stream()
          .filter(event -> "csrf-check".equals(AuditLogCapture.fields(event).get("event.action"))).toList();
      assertThat(rejections).hasSize(1);
      var event = rejections.getFirst();
      assertThat(AuditLogCapture.fields(event)).containsEntry("event.category", List.of("web"))
          .containsEntry("event.type", List.of("access")).containsEntry("event.outcome", "failure")
          .containsEntry("event.reason", "csrf_rejected").containsEntry("user.id", userId.toString())
          .containsEntry("session.hash", sessionHash);
      assertThat(sessionHash).isNotNull();
      assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain(BAD_TOKEN);
      assertThat(audit.events())
          .noneMatch(each -> "authorization".equals(AuditLogCapture.fields(each).get("event.action")));
    }
  }

  @Test
  void should_notAuditCsrf_when_aUserWithAValidTokenIsRefusedForAnotherReason() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      Cookie session = login();
      var token = JsonPath.<String>read(mvc.perform(get("/csrf").cookie(session)).andReturn().getResponse()
          .getContentAsString(), "$.token");

      // The GET is an authorization denial (audited as such), the POST a wrong method; neither is a CSRF failure.
      mvc.perform(get("/api/admin/users").cookie(session))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
      mvc.perform(post("/api/admin/users").cookie(session).header("X-CSRF-TOKEN", token))
          .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

      assertThat(audit.events())
          .noneMatch(event -> "csrf-check".equals(AuditLogCapture.fields(event).get("event.action")));
      assertThat(audit.events())
          .filteredOn(event -> "authorization".equals(AuditLogCapture.fields(event).get("event.action")))
          .singleElement().satisfies(event -> assertThat(AuditLogCapture.fields(event))
              .containsEntry("http.request.method", "GET").containsEntry("user.id", userId.toString()));
    }
  }

  private Cookie login() throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser123\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
  }

  private static Map<String, Object> fields(AuditLogCapture audit, String action) {
    return audit.events().stream().map(AuditLogCapture::fields)
        .filter(fields -> action.equals(fields.get("event.action")))
        .findFirst().orElseThrow();
  }
}
