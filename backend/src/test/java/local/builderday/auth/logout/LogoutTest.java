package local.builderday.auth.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.builderday.common.audit.SecurityAudit;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Logout of a live Session, observed at the HTTP boundary: status, cookies, replay and the audit trail. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class LogoutTest {
  private static final String PASSWORD = "Str0ng!Passw0rd";
  private static final String LOGOUT = "/api/auth/logout";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  private UUID userId;

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    userId = UUID.randomUUID();
    userRepository.save(new UserEntity(userId, "testuser123", "testuser123@test.example.com",
        passwordEncoder.encode(PASSWORD), "USER", true));
  }

  @Test
  void should_return204AndExpireTheSessionCookie_when_anAuthenticatedUserLogsOut() throws Exception {
    Cookie session = login();

    var logout = mvc.perform(post(LOGOUT).with(csrf()).cookie(session))
        .andExpect(status().isNoContent()).andExpect(content().string("")).andReturn();

    String expired = sessionCookieHeader(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
    assertThat(expired).startsWith("id=;").contains("Max-Age=0", "Path=/", "Secure", "HttpOnly", "SameSite=Strict");
  }

  @Test
  void should_rejectTheReplayedSessionCookie_when_itIsReplayedAfterLogout() throws Exception {
    Cookie session = login();
    mvc.perform(post(LOGOUT).with(csrf()).cookie(session)).andExpect(status().isNoContent());

    mvc.perform(get("/api/anything").cookie(session)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    mvc.perform(get("/api/profile").cookie(session)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  /** Enforces the IM8 ac-1 deviation: logout is outside the authorization matrix yet still authenticated-only. */
  @Test
  void should_refuseAsAuthenticationRequiredWithoutAudit_when_thereIsNoSession() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      mvc.perform(post(LOGOUT).with(csrf())).andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

      assertThat(logoutEvents(audit)).isEmpty();
    }
  }

  @Test
  void should_refuseLogoutAndKeepTheSession_when_theCsrfTokenIsMissing() throws Exception {
    Cookie session = login();

    mvc.perform(post(LOGOUT).cookie(session)).andExpect(status().isForbidden());

    mvc.perform(get("/api/profile").cookie(session)).andExpect(status().isOk());
  }

  @Test
  void should_auditExactlyOneLogoutForTheEndedSession_when_anAuthenticatedUserLogsOut() throws Exception {
    try (var audit = new AuditLogCapture(SecurityAudit.LOGGER)) {
      Cookie session = login();
      Object loginSessionHash = fields(audit, "user-login").get("session.hash");

      mvc.perform(post(LOGOUT).with(csrf()).cookie(session)).andExpect(status().isNoContent());

      var logouts = logoutEvents(audit);
      assertThat(logouts).hasSize(1);
      var logout = AuditLogCapture.fields(logouts.getFirst());
      assertThat(logout).containsEntry("event.category", List.of("authentication"))
          .containsEntry("event.type", List.of("end")).containsEntry("event.outcome", "success")
          .containsEntry("event.reason", "success").containsEntry("user.id", userId.toString());
      assertThat(loginSessionHash).isNotNull();
      assertThat(logout).containsEntry("session.hash", loginSessionHash);
    }
  }

  /**
   * Pins the filter order the SPA's Logout retry relies on: an expired Session holds no CSRF token, so Logout fails the
   * CSRF check (403) before any session check. Only the retry, with a fresh anonymous token, reaches the 401.
   */
  @Test
  void should_refuseCsrfFirst_thenReportNoSession_when_logoutIsRetriedAfterTheSessionExpired() throws Exception {
    Cookie session = login();
    var token = csrfToken(session);

    clocks.advance(Duration.ofHours(8).plusMinutes(1));
    mvc.perform(post(LOGOUT).cookie(session).header(token.header(), token.value())).andExpect(status().isForbidden());
    // Only the lifetime filter reads the test clock (Spring Session stamps creation with the real one), so a Session
    // created on an advanced clock would look 8 hours old at once. The expired Session is already gone.
    clocks.reset();

    // The response may also expire the dead cookie first; the browser keeps the last one set, the new Session's.
    var anonymous = mvc.perform(get("/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
    Cookie anonymousSession = Arrays.stream(anonymous.getCookies())
        .filter(cookie -> cookie.getName().equals("id") && !cookie.getValue().isEmpty()).reduce((first, last) -> last)
        .orElseThrow();
    var anonymousToken = csrfToken(anonymousSession);
    mvc.perform(post(LOGOUT).cookie(anonymousSession).header(anonymousToken.header(), anonymousToken.value()))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void should_keepTheSession_when_theFrameworkDefaultLogoutUrlIsCalled() throws Exception {
    Cookie session = login();

    mvc.perform(post("/logout").with(csrf()).cookie(session));

    mvc.perform(get("/api/profile").cookie(session)).andExpect(status().isOk());
  }

  private Cookie login() throws Exception {
    var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser123\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk()).andReturn();
    return login.getResponse().getCookie("id");
  }

  private record CsrfToken(String header, String value) {}

  /** The real token of the Session named by {@code session}, as the SPA gets it from {@code GET /csrf}. */
  private CsrfToken csrfToken(Cookie session) throws Exception {
    var body = mvc.perform(get("/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
        .getContentAsString();
    return new CsrfToken(JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
  }

  private static String sessionCookieHeader(List<String> setCookies) {
    return setCookies.stream().filter(header -> header.startsWith("id=")).findFirst().orElseThrow();
  }

  private static List<ILoggingEvent> logoutEvents(AuditLogCapture audit) {
    return audit.events().stream()
        .filter(event -> "user-logout".equals(AuditLogCapture.fields(event).get("event.action")))
        .toList();
  }

  private static Map<String, Object> fields(AuditLogCapture audit, String action) {
    return audit.events().stream().map(AuditLogCapture::fields)
        .filter(fields -> action.equals(fields.get("event.action")))
        .findFirst().orElseThrow();
  }
}
