package org.eds.demo.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Set;
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

/** Forced password change for a Temporary Password, through the real chains and JDBC sessions. */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(MutableClockConfiguration.class)
@SpringBootTest(
    properties = {
      "app.email.inbound.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:password-change-it;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.admin.password=test-only-password-change-admin-secret"
    })
class PasswordChangeIT {

  private static final String SESSION_COOKIE = "SESSION";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final String CSRF_TOKEN = "test-csrf-token";
  private static final String CHANGE_PATH = "/api/v1/me/password";
  private static final String REQUIRED_TITLE = "Password change required";

  private static final String TEMP_PASSWORD = "test-only-temporary-secret";
  private static final String NEW_PASSWORD = "test-only-brand-new-secret";
  private static final String SHORT_PASSWORD = "short-pw-1";

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableClock clock;
  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void signInReportsThatAPasswordChangeIsRequiredAndEverythingElseIsRefused() throws Exception {
    account("forced-user", Role.USER);
    var signIn =
        mockMvc
            .perform(signInRequest("forced-user", TEMP_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn();
    var session = signIn.getResponse().getCookie(SESSION_COOKIE);

    mockMvc
        .perform(get("/api/hello").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(REQUIRED_TITLE));
    mockMvc
        .perform(get("/api/v1/me").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(REQUIRED_TITLE));
  }

  @Test
  void adminApiIsAlsoRefusedWhileAPasswordChangeIsRequired() throws Exception {
    account("forced-admin", Role.ADMIN);
    var session = signIn("forced-admin", TEMP_PASSWORD);

    mockMvc
        .perform(get("/admin/api/users").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(REQUIRED_TITLE));
  }

  @Test
  void pageRoutesOtherThanTheSpaShellAreRefusedWhileAPasswordChangeIsRequired() throws Exception {
    account("forced-pager", Role.USER);
    var session = signIn("forced-pager", TEMP_PASSWORD);

    mockMvc.perform(get("/app/change-password").cookie(session)).andExpect(status().isOk());
    mockMvc
        .perform(get("/some/other/page").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value(REQUIRED_TITLE));
  }

  @Test
  void signOutStillWorksWhileAPasswordChangeIsRequired() throws Exception {
    account("forced-leaver", Role.USER);
    var session = signIn("forced-leaver", TEMP_PASSWORD);

    mockMvc.perform(withCsrf(post("/logout").cookie(session))).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void mismatchedConfirmationIsRejectedAndChangesNothing() throws Exception {
    account("mismatcher", Role.USER);
    var session = signIn("mismatcher", TEMP_PASSWORD);

    mockMvc
        .perform(changePassword(session, NEW_PASSWORD, NEW_PASSWORD + "-typo"))
        .andExpect(status().isBadRequest());

    assertUnchanged("mismatcher", session);
  }

  @Test
  void newPasswordIdenticalToTheCurrentOneIsRejectedAndChangesNothing() throws Exception {
    account("recycler", Role.USER);
    var session = signIn("recycler", TEMP_PASSWORD);

    mockMvc
        .perform(changePassword(session, TEMP_PASSWORD, TEMP_PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("New password must differ from the current one"));

    assertUnchanged("recycler", session);
  }

  @Test
  void weakPasswordIsRejectedAndChangesNothing() throws Exception {
    account("weakling", Role.USER);
    var session = signIn("weakling", TEMP_PASSWORD);

    mockMvc
        .perform(changePassword(session, SHORT_PASSWORD, SHORT_PASSWORD))
        .andExpect(status().isUnprocessableContent());

    assertUnchanged("weakling", session);
  }

  @Test
  void passwordOfExactlyTheMinimumLengthIsAccepted() throws Exception {
    account("minimum", Role.USER);
    var session = signIn("minimum", TEMP_PASSWORD);
    var twelve = "a".repeat(12);

    mockMvc.perform(changePassword(session, twelve, twelve)).andExpect(status().isNoContent());
  }

  @Test
  void changeSucceedsClearsTheFlagAndKeepsTheCurrentSessionButEndsOthers() throws Exception {
    account("changer", Role.USER);
    var current = signIn("changer", TEMP_PASSWORD);
    var other = signIn("changer", TEMP_PASSWORD);
    var bystander = signInBystander();

    mockMvc
        .perform(changePassword(current, NEW_PASSWORD, NEW_PASSWORD))
        .andExpect(status().isNoContent());

    var stored = appUserRepository.findByUsername("changer").orElseThrow();
    assertThat(stored.isMustChangePassword()).isFalse();
    assertThat(stored.getTempPasswordExpiresAt()).isNull();
    assertThat(passwordEncoder.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
    mockMvc.perform(get("/api/hello").cookie(current)).andExpect(status().isOk());
    mockMvc.perform(get("/api/hello").cookie(other)).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/hello").cookie(bystander)).andExpect(status().isOk());
    mockMvc.perform(signInRequest("changer", TEMP_PASSWORD)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(signInRequest("changer", NEW_PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  @Test
  void temporaryPasswordPastItsExpiryFailsSignInWithTheGenericError() throws Exception {
    account("expirer", Role.USER);
    var unknown =
        mockMvc
            .perform(signInRequest("nobody-by-that-name", TEMP_PASSWORD))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    clock.advance(Duration.ofHours(24).plusSeconds(1));

    var expired =
        mockMvc
            .perform(signInRequest("expirer", TEMP_PASSWORD))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse();

    assertThat(expired.getContentAsString()).isEqualTo(unknown);
    assertThat(expired.getCookie(SESSION_COOKIE)).isNull();
  }

  @Test
  void passwordChangeIsAuditedWithoutLoggingAnyPassword(CapturedOutput output) throws Exception {
    account("audited", Role.USER);
    var session = signIn("audited", TEMP_PASSWORD);

    mockMvc
        .perform(changePassword(session, NEW_PASSWORD, NEW_PASSWORD))
        .andExpect(status().isNoContent());

    assertThat(output.getAll())
        .contains("Password changed: username=audited")
        .doesNotContain(NEW_PASSWORD)
        .doesNotContain(TEMP_PASSWORD);
  }

  private void assertUnchanged(String username, Cookie session) throws Exception {
    var stored = appUserRepository.findByUsername(username).orElseThrow();
    assertThat(stored.isMustChangePassword()).isTrue();
    assertThat(stored.getTempPasswordExpiresAt()).isNotNull();
    assertThat(passwordEncoder.matches(TEMP_PASSWORD, stored.getPasswordHash())).isTrue();
    mockMvc.perform(get("/api/hello").cookie(session)).andExpect(status().isForbidden());
  }

  private void account(String username, Role role) {
    var account = AppUser.create(username, Set.of(role));
    account.issueTemporaryPassword(
        passwordEncoder.encode(TEMP_PASSWORD), clock.instant().plus(Duration.ofHours(24)));
    appUserRepository.save(account);
  }

  private Cookie signInBystander() throws Exception {
    var bystander = AppUser.create("bystander", Set.of(Role.USER));
    bystander.updatePasswordHash(passwordEncoder.encode(NEW_PASSWORD));
    appUserRepository.save(bystander);
    return signIn("bystander", NEW_PASSWORD);
  }

  private Cookie signIn(String username, String password) throws Exception {
    return mockMvc
        .perform(signInRequest(username, password))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie(SESSION_COOKIE);
  }

  private static MockHttpServletRequestBuilder changePassword(
      Cookie session, String newPassword, String confirmPassword) {
    return withCsrf(
        post(CHANGE_PATH)
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                    .formatted(newPassword, confirmPassword)));
  }

  private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
    return request.cookie(new Cookie(CSRF_COOKIE, CSRF_TOKEN)).header(CSRF_HEADER, CSRF_TOKEN);
  }

  private static MockHttpServletRequestBuilder signInRequest(String username, String password) {
    return withCsrf(
        post("/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
  }
}
