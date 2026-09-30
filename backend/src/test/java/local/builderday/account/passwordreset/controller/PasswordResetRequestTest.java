package local.builderday.account.passwordreset.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import local.builderday.common.audit.SessionIds;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.account.passwordreset.repository.PasswordResetTokenRepository;
import local.builderday.notification.service.EmailService;
import local.builderday.notification.service.LoggingEmailService;
import local.builderday.account.passwordreset.repository.entity.PasswordResetTokenEntity;
import local.builderday.account.passwordreset.service.PasswordResetService;
import local.builderday.common.web.AuthenticatedUser;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.Accounts;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Requesting a Password reset link (ADR 0004), at the HTTP boundary, with the background work run on the caller. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClocks.class)
class PasswordResetRequestTest {
  private static final String EMAIL = "resetuser1@test.example.com";
  private static final String LINK_PREFIX = "https://app.test.example/reset-password#token=";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @Autowired TestClocks clocks;
  @MockitoSpyBean EmailService emailService;
  @TestBean(name = "applicationTaskExecutor", methodName = "callerRuns") TaskExecutor applicationTaskExecutor;

  static TaskExecutor callerRuns() { return new SyncTaskExecutor(); }

  @BeforeEach
  void reset() {
    clocks.reset();
    userRepository.deleteAll();
    rateLimitBucketRepository.deleteAllInBatch();
  }

  @Test
  void should_issueOneHashedTokenAndEmailTheLink_forAnEligibleAccount() throws Exception {
    UUID id = account("resetuser1", EMAIL, "USER", true);

    try (var audit = new AuditLogCapture("audit")) {
      request(EMAIL).andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value(PasswordResetController.REQUESTED_MESSAGE));

      String token = sentToken(EMAIL, id);
      assertThat(token).matches("[A-Za-z0-9_-]{43}");
      assertThat(passwordResetTokenRepository.findByTokenHash(token)).as("the raw token is never stored").isEmpty();
      var row = passwordResetTokenRepository.findByTokenHash(SessionIds.sha256Hex(token)).orElseThrow();
      assertThat(tokensOf(id)).singleElement().extracting(PasswordResetTokenEntity::getId).isEqualTo(row.getId());
      assertThat(Duration.between(clocks.now(), row.getExpiresAt()))
          .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
      assertThat(row.getUsedAt()).isNull();
      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.action", "password-reset-request")
            .containsEntry("event.outcome", "success").containsEntry("event.reason", "issued")
            .containsEntry("user.id", id.toString()).containsKeys("trace.id", "source.ip", "url.path");
        assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain(EMAIL, token);
      });
    }
  }

  @Test
  void should_normalizeTheEmail_beforeLookup() throws Exception {
    UUID id = account("resetuser1", EMAIL, "USER", true);

    request("  ResetUser1@Test.Example.COM ").andExpect(status().isOk());

    sentToken(EMAIL, id);
  }

  @Test
  void should_answerIdentically_andIssueNothing_forUnknownAndIneligibleAccounts() throws Exception {
    UUID disabled = account("disabled01", "disabled01@test.example.com", "USER", false);
    UUID deleted = account("deleted001", "deleted001@test.example.com", "USER", true);
    userRepository.findById(deleted).ifPresent(user -> {
      Accounts.markDeleted(user, clocks.now());
      userRepository.save(user);
    });
    UUID admin = account("adminuser1", "adminuser1@test.example.com", "ADMIN", true);
    account("noemail001", null, "USER", true);
    // The eligible account's answer is the same fixed message (asserted above), so any 200 is the baseline.
    String eligibleBody = responseBody("baseline01@test.example.com");

    try (var audit = new AuditLogCapture("audit")) {
      for (String email : List.of("unknown01@test.example.com", "disabled01@test.example.com",
          "deleted001@test.example.com", "adminuser1@test.example.com")) {
        assertThat(responseBody(email)).isEqualTo(eligibleBody);
      }

      verify(emailService, never()).sendPasswordResetEmail(anyString(), any(), anyString());
      assertThat(passwordResetTokenRepository.count()).isZero();
      assertThat(audit.events()).hasSize(4).allSatisfy(event -> assertThat(AuditLogCapture.fields(event))
          .containsEntry("event.reason", "not_eligible").containsEntry("event.outcome", "failure"));
      assertThat(audit.events()).extracting(event -> AuditLogCapture.fields(event).get("user.id"))
          .containsExactly(null, disabled.toString(), deleted.toString(), admin.toString());
    }
  }

  @Test
  void should_invalidateTheEarlierToken_whenAnotherIsRequested() throws Exception {
    UUID id = account("resetuser1", EMAIL, "USER", true);

    request(EMAIL).andExpect(status().isOk());
    request(EMAIL).andExpect(status().isOk());

    var links = ArgumentCaptor.forClass(String.class);
    verify(emailService, org.mockito.Mockito.times(2)).sendPasswordResetEmail(eq(EMAIL), eq(id), links.capture());
    String latest = links.getAllValues().get(1).substring(LINK_PREFIX.length());
    assertThat(tokensOf(id)).singleElement().extracting(PasswordResetTokenEntity::getId)
        .isEqualTo(passwordResetTokenRepository.findByTokenHash(SessionIds.sha256Hex(latest)).orElseThrow().getId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "a@b", ""})
  void should_rejectAMalformedEmail_withAFieldViolation_andNoAudit(String email) throws Exception {
    try (var audit = new AuditLogCapture("audit")) {
      request(email).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("PASSWORD_RESET_REJECTED"))
          .andExpect(jsonPath("$.errors[0].field").value("email"))
          .andExpect(jsonPath("$.errors[0].code").value(email.isEmpty() ? "FIELD_REQUIRED" : "EMAIL_INVALID"));

      assertThat(audit.events()).isEmpty();
    }
  }

  @Test
  void should_rejectAnOverLongEmail() throws Exception {
    request("a".repeat(250) + "@test.example.com").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'EMAIL_TOO_LONG')]").exists());
  }

  @Test
  void should_requireTheCsrfToken() throws Exception {
    account("resetuser1", EMAIL, "USER", true);

    mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + EMAIL + "\"}")).andExpect(status().isForbidden());

    assertThat(passwordResetTokenRepository.count()).isZero();
  }

  @Test
  void should_stillAnswer200_andAuditAFailure_whenTheEmailCannotBeSent() throws Exception {
    UUID id = account("resetuser1", EMAIL, "USER", true);
    doThrow(new IllegalStateException("mail provider down " + EMAIL)).when(emailService)
        .sendPasswordResetEmail(eq(EMAIL), eq(id), anyString());

    try (var audit = new AuditLogCapture("audit"); var log = new AuditLogCapture(
        "local.builderday.account.passwordreset.service.PasswordResetService")) {
      request(EMAIL).andExpect(status().isOk());

      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.reason", "failure")
            .containsEntry("user.id", id.toString());
      });
      assertThat(log.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain(EMAIL);
      });
    }
  }

  /**
   * A signed-in User may ask for another account's link: the request's MDC then holds the caller's {@code user.id},
   * while the failure line names the account. The line must still encode, with the account's id once.
   */
  @Test
  void should_logTheFailureWithTheAccountsIdOnce_when_aSignedInCallerRequestsTheReset() throws Exception {
    UUID id = account("resetuser1", EMAIL, "USER", true);
    UUID callerId = UUID.randomUUID();
    doThrow(new IllegalStateException("mail provider down")).when(emailService)
        .sendPasswordResetEmail(eq(EMAIL), eq(id), anyString());

    try (var log = new AuditLogCapture(PasswordResetService.class.getName())) {
      mvc.perform(post("/api/auth/password-reset").with(csrf())
          .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
              new Caller(callerId), null, AuthorityUtils.createAuthorityList("ROLE_USER"))))
          .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + EMAIL + "\"}"))
          .andExpect(status().isOk());

      assertThat(log.events()).singleElement().satisfies(event -> {
        assertThat(AuditLogCapture.fields(event)).containsEntry("user.id", id.toString());
        assertThat(AuditLogCapture.render(Logger.ROOT_LOGGER_NAME, "FILE", event))
            .containsOnlyOnce("\"user\":{\"id\":\"" + id + "\"}").doesNotContain(callerId.toString());
      });
    }
  }

  @Test
  void should_logTheLinkButNeverTheEmail_withoutAnyProfile() throws Exception {
    UUID id = account("resetuser1", EMAIL, "USER", true);

    try (var stubLog = new AuditLogCapture(LoggingEmailService.class.getName())) {
      request(EMAIL).andExpect(status().isOk());

      String token = sentToken(EMAIL, id);
      assertThat(stubLog.events()).hasSize(2).first().satisfies(event ->
          assertThat(AuditLogCapture.fields(event)).containsEntry("notification.type", "PASSWORD_RESET"));
      assertThat(stubLog.events().getLast().getFormattedMessage()).contains(token);
      assertThat(stubLog.events()).allSatisfy(event ->
          assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain(EMAIL));
    }
  }

  @Test
  void should_serveTheForgotPasswordPage_toAVisitor() throws Exception {
    mvc.perform(get("/forgot-password").accept(MediaType.TEXT_HTML)).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  /** A signed-in caller's principal; serializable with a short {@code toString}, as the JDBC Session stores it. */
  private record Caller(UUID userId) implements AuthenticatedUser, java.io.Serializable {}

  private UUID account(String username, String email, String role, boolean enabled) {
    return userRepository.save(new UserEntity(UUID.randomUUID(), username, email, "{noop}unused", role, enabled))
        .getId();
  }

  private ResultActions request(String email) throws Exception {
    return mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}"));
  }

  private String responseBody(String email) throws Exception {
    return request(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  /** The token from the one link emailed to {@code email}. */
  private String sentToken(String email, UUID id) {
    var link = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetEmail(eq(email), eq(id), link.capture());
    assertThat(link.getValue()).startsWith(LINK_PREFIX);
    return link.getValue().substring(LINK_PREFIX.length());
  }

  private List<PasswordResetTokenEntity> tokensOf(UUID userId) {
    return passwordResetTokenRepository.findAll().stream().filter(row -> row.getUserId().equals(userId)).toList();
  }
}
