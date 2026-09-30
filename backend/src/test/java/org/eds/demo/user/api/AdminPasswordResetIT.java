package org.eds.demo.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.eds.demo.support.MutableClock;
import org.eds.demo.support.MutableClockConfiguration;
import org.eds.demo.user.domain.AppUser;
import org.eds.demo.user.domain.Role;
import org.eds.demo.user.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Admin resets an Account's password through the real admin chain and JDBC sessions. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:admin-password-reset-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class AdminPasswordResetIT {

  private static final String ADMIN_ACTOR = "root";
  private static final String SESSION_COOKIE = "SESSION";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";
  private static final String OLD_PASSWORD = "test-only-old-secret-pw";

  /** Default backoff threshold: the third consecutive failure starts a delay. */
  private static final int FAILURES_TO_LOCK = 3;

  private static final String NEW_PASSWORD = "test-only-brand-new-secret";
  private static final Duration DEFAULT_TEMPORARY_PASSWORD_TTL = Duration.ofHours(24);

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MutableClock clock;

  @Test
  void resetReturnsANewTemporaryPasswordOnceAndStoresOnlyItsHash() throws Exception {
    clock.set(Instant.parse("2030-01-01T00:00:00Z"));
    var target = account("forgetful");

    var body =
        mockMvc
            .perform(resetPassword(target))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("forgetful"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String temporaryPassword = JsonPath.read(body, "$.temporaryPassword");

    var stored = appUserRepository.findByUsername("forgetful").orElseThrow();
    assertThat(temporaryPassword).isNotBlank().isNotEqualTo(OLD_PASSWORD);
    assertThat(passwordEncoder.matches(temporaryPassword, stored.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches(OLD_PASSWORD, stored.getPasswordHash())).isFalse();
    assertThat(stored.isMustChangePassword()).isTrue();
    assertThat(stored.getTempPasswordExpiresAt())
        .isEqualTo(clock.instant().plus(DEFAULT_TEMPORARY_PASSWORD_TTL));
  }

  @Test
  void resetClearsBackoffSoTheHolderCanSignInAtOnceAndMustChangeThePassword() throws Exception {
    var target = account("locked-out");
    for (int i = 0; i < FAILURES_TO_LOCK; i++) {
      mockMvc
          .perform(signInRequest("locked-out", "not-the-password"))
          .andExpect(status().isUnauthorized());
    }
    assertThat(appUserRepository.findByUsername("locked-out").orElseThrow().getLockedUntil())
        .isNotNull();
    String temporaryPassword = temporaryPasswordFrom(resetPassword(target));

    var stored = appUserRepository.findByUsername("locked-out").orElseThrow();
    assertThat(stored.getFailedLoginAttempts()).isZero();
    assertThat(stored.getLockedUntil()).isNull();
    mockMvc
        .perform(signInRequest("locked-out", temporaryPassword))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void resetEndsTheTargetsSessionsButNotAnyoneElses() throws Exception {
    var target = account("signed-in");
    var targetSession = signIn("signed-in", OLD_PASSWORD);
    var otherTargetSession = signIn("signed-in", OLD_PASSWORD);
    account("bystander");
    var bystanderSession = signIn("bystander", OLD_PASSWORD);

    mockMvc.perform(resetPassword(target)).andExpect(status().isOk());

    mockMvc.perform(get("/api/hello").cookie(targetSession)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/hello").cookie(otherTargetSession))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/hello").cookie(bystanderSession)).andExpect(status().isOk());
  }

  @Test
  void adminCannotResetTheirOwnPassword() throws Exception {
    var self = account("self-resetter");
    var hashBefore =
        appUserRepository.findByUsername("self-resetter").orElseThrow().getPasswordHash();

    mockMvc.perform(resetPasswordAs("self-resetter", self)).andExpect(status().isForbidden());

    var stored = appUserRepository.findByUsername("self-resetter").orElseThrow();
    assertThat(stored.getPasswordHash()).isEqualTo(hashBefore);
    assertThat(stored.isMustChangePassword()).isFalse();
  }

  @Test
  void unknownAccountIsNotFound() throws Exception {
    mockMvc.perform(resetPassword(UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void nonAdminsAreForbiddenAndAnonymousCallersUnauthorizedAndNothingChanges() throws Exception {
    var target = account("untouched");
    var hashBefore = appUserRepository.findByUsername("untouched").orElseThrow().getPasswordHash();

    mockMvc
        .perform(
            withCsrf(
                post("/admin/api/users/{id}/reset-password", target)
                    .with(user("ada").roles("USER"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(withCsrf(post("/admin/api/users/{id}/reset-password", target)))
        .andExpect(status().isUnauthorized());

    var stored = appUserRepository.findByUsername("untouched").orElseThrow();
    assertThat(stored.getPasswordHash()).isEqualTo(hashBefore);
    assertThat(stored.isMustChangePassword()).isFalse();
  }

  @Test
  void resetIsAuditedWithActorAndTargetButNeverThePassword(CapturedOutput output) throws Exception {
    var target = account("audited-target");

    String temporaryPassword = temporaryPasswordFrom(resetPassword(target));

    assertThat(output.getAll())
        .contains("Password reset: actor=" + ADMIN_ACTOR + ", target=audited-target")
        .doesNotContain(temporaryPassword)
        .doesNotContain(OLD_PASSWORD);
  }

  @Test
  void resetFlowsIntoTheForcedChangeAndTheOldPasswordStopsWorking() throws Exception {
    var target = account("recovering");
    String temporaryPassword = temporaryPasswordFrom(resetPassword(target));
    mockMvc.perform(signInRequest("recovering", OLD_PASSWORD)).andExpect(status().isUnauthorized());
    var session = signIn("recovering", temporaryPassword);
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isForbidden());

    mockMvc
        .perform(
            withCsrf(
                post("/api/v1/me/password")
                    .cookie(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                            .formatted(NEW_PASSWORD, NEW_PASSWORD))))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isOk());
    mockMvc
        .perform(signInRequest("recovering", NEW_PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  @Test
  void listExposesEachAccountsIdSoTheAdminCanTargetIt() throws Exception {
    var target = account("listed-target");

    mockMvc
        .perform(get("/admin/api/users").with(user(ADMIN_ACTOR).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.username == 'listed-target')].id")
                .value(org.hamcrest.Matchers.contains(target.toString())));
  }

  private Cookie signIn(String username, String password) throws Exception {
    return mockMvc
        .perform(signInRequest(username, password))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie(SESSION_COOKIE);
  }

  private String temporaryPasswordFrom(MockHttpServletRequestBuilder reset) throws Exception {
    var body =
        mockMvc
            .perform(reset)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.temporaryPassword");
  }

  private static MockHttpServletRequestBuilder signInRequest(String username, String password) {
    return withCsrf(
        post("/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
  }

  private UUID account(String username) {
    var account = AppUser.create(username, Set.of(Role.USER));
    account.updatePasswordHash(passwordEncoder.encode(OLD_PASSWORD));
    return appUserRepository.save(account).getId().value();
  }

  private static MockHttpServletRequestBuilder resetPassword(UUID id) {
    return resetPasswordAs(ADMIN_ACTOR, id);
  }

  private static MockHttpServletRequestBuilder resetPasswordAs(String actor, UUID id) {
    return withCsrf(
        post("/admin/api/users/{id}/reset-password", id).with(user(actor).roles("ADMIN")));
  }

  private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
    return request.cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN)).header(CSRF_HEADER, CSRF_TOKEN);
  }
}
