package org.eds.demo.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Sign-in, greeting and sign-out through the real security chains, CSRF and JDBC sessions. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:sign-in-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.admin.username=" + SignInIT.ADMIN_USERNAME,
      "app.admin.password=" + SignInIT.ADMIN_PASSWORD
    })
class SignInIT {

  static final String ADMIN_USERNAME = "sign-in-admin";
  static final String ADMIN_PASSWORD = "test-only-sign-in-secret";

  private static final String SESSION_COOKIE = "SESSION";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";

  private static final String DISABLED_USERNAME = "suspended-holder";
  private static final String DISABLED_PASSWORD = "test-only-suspended-secret";
  private static final String FLAKY_USERNAME = "flaky-holder";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private FindByIndexNameSessionRepository<? extends Session> sessions;

  @Test
  void correctCredentialsSignInAndGreetTheAccountByUsername() throws Exception {
    var signIn =
        mockMvc
            .perform(signInRequest(ADMIN_USERNAME, ADMIN_PASSWORD))
            .andExpect(status().isOk())
            .andReturn();
    var session = signIn.getResponse().getCookie(SESSION_COOKIE);

    assertThat(session).isNotNull();
    mockMvc
        .perform(get("/api/hello").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Hello, " + ADMIN_USERNAME));
  }

  @Test
  void greetingRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/hello")).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownUsernameWrongPasswordAndDisabledAccountAreIndistinguishable() throws Exception {
    appUserRepository.save(disabledAccount());

    var unknown = failedSignIn("nobody-by-that-name", ADMIN_PASSWORD);
    var wrongPassword = failedSignIn(ADMIN_USERNAME, "not-the-password");
    var disabled = failedSignIn(DISABLED_USERNAME, DISABLED_PASSWORD);

    assertThat(wrongPassword).isEqualTo(unknown);
    assertThat(disabled).isEqualTo(unknown);
    assertThat(unknown).contains("Invalid username or password");
  }

  @Test
  void signInWithoutCsrfTokenIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsJson(ADMIN_USERNAME, ADMIN_PASSWORD)))
        .andExpect(status().isForbidden());
  }

  @Test
  void signOutWithoutCsrfTokenIsForbidden() throws Exception {
    var session = signIn();

    mockMvc.perform(post("/logout").cookie(session)).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isOk());
  }

  @Test
  void sessionCookieReplayedAfterSignOutIsUnauthenticated() throws Exception {
    var session = signIn();

    mockMvc.perform(withCsrf(post("/logout").cookie(session))).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void signInIssuesAFreshSessionIdInsteadOfTheOneTheClientPresented() throws Exception {
    var plantedId = plantAnonymousSession(sessions);
    var planted = new Cookie(SESSION_COOKIE, encodeSessionId(plantedId));

    var issued =
        mockMvc
            .perform(signInRequest(ADMIN_USERNAME, ADMIN_PASSWORD).cookie(planted))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(SESSION_COOKIE);

    assertThat(issued).isNotNull();
    assertThat(issued.getValue()).isNotEqualTo(planted.getValue());
    mockMvc.perform(get("/api/hello").cookie(planted)).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/hello").cookie(issued)).andExpect(status().isOk());
  }

  @Test
  void successfulSignInResetsTheFailedAttemptCounter() throws Exception {
    appUserRepository.save(
        AppUser.builder()
            .username(FLAKY_USERNAME)
            .passwordHash(passwordEncoder.encode(DISABLED_PASSWORD))
            .failedLoginAttempts(2)
            .build());

    mockMvc.perform(signInRequest(FLAKY_USERNAME, DISABLED_PASSWORD)).andExpect(status().isOk());

    assertThat(
            appUserRepository.findByUsername(FLAKY_USERNAME).orElseThrow().getFailedLoginAttempts())
        .isZero();
  }

  @Test
  void signInOutcomesAreAuditedWithoutLoggingThePassword(CapturedOutput output) throws Exception {
    mockMvc.perform(signInRequest(ADMIN_USERNAME, ADMIN_PASSWORD)).andExpect(status().isOk());
    mockMvc
        .perform(signInRequest("audited-stranger", "stranger-secret-value"))
        .andExpect(status().isUnauthorized());

    assertThat(output.getAll())
        .contains("Sign-in succeeded: username=" + ADMIN_USERNAME)
        .contains("Sign-in failed: username=audited-stranger")
        .doesNotContain(ADMIN_PASSWORD)
        .doesNotContain("stranger-secret-value");
  }

  private Cookie signIn() throws Exception {
    return mockMvc
        .perform(signInRequest(ADMIN_USERNAME, ADMIN_PASSWORD))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie(SESSION_COOKIE);
  }

  private String failedSignIn(String username, String password) throws Exception {
    var response =
        mockMvc
            .perform(signInRequest(username, password))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse();
    assertThat(response.getCookie(SESSION_COOKIE)).isNull();
    return response.getContentAsString();
  }

  private AppUser disabledAccount() {
    return AppUser.builder()
        .username(DISABLED_USERNAME)
        .passwordHash(passwordEncoder.encode(DISABLED_PASSWORD))
        .enabled(false)
        .build();
  }

  private static <S extends Session> String plantAnonymousSession(
      FindByIndexNameSessionRepository<S> repository) {
    S session = repository.createSession();
    repository.save(session);
    return session.getId();
  }

  private static String encodeSessionId(String id) {
    return Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
  }

  private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
    return request.cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN)).header(CSRF_HEADER, CSRF_TOKEN);
  }

  private static String credentialsJson(String username, String password) {
    return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
  }

  private static MockHttpServletRequestBuilder signInRequest(String username, String password) {
    return withCsrf(
        post("/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentialsJson(username, password)));
  }
}
