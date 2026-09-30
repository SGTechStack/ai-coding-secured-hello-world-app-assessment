package local.builderday.account.passwordreset.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.notification.service.EmailService;
import local.builderday.notification.service.EmailService.NotificationType;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.Accounts;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Completing a Password reset (ADR 0004) at the HTTP boundary, from a real emailed link to the next login. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestClocks.class)
class PasswordResetConfirmTest {
  private static final String USERNAME = "resetuser1";
  private static final String EMAIL = "resetuser1@test.example.com";
  private static final String OLD_PASSWORD = "Str0ng!Passw0rd";
  private static final String NEW_PASSWORD = "N3w!Different#Pw";
  private static final String LINK_PREFIX = "https://app.test.example/reset-password#token=";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired TestClocks clocks;
  @MockitoSpyBean EmailService emailService;
  @TestBean(name = "applicationTaskExecutor", methodName = "callerRuns") TaskExecutor applicationTaskExecutor;
  private UUID userId;

  static TaskExecutor callerRuns() { return new SyncTaskExecutor(); }

  @BeforeEach
  void setUp() {
    clocks.reset();
    userRepository.deleteAll();
    rateLimitBucketRepository.deleteAllInBatch();
    userId = userRepository.save(new UserEntity(UUID.randomUUID(), USERNAME, EMAIL,
        passwordEncoder.encode(OLD_PASSWORD), "USER", true)).getId();
  }

  @Test
  void should_changeThePassword_endEverySession_andNotify_withoutLoggingIn() throws Exception {
    Cookie earlierSession = login(OLD_PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("id");
    mvc.perform(get("/api/hello").cookie(earlierSession)).andExpect(status().isOk());
    String token = emailedToken();

    try (var audit = new AuditLogCapture("audit")) {
      confirm(token, NEW_PASSWORD).andExpect(status().isNoContent()).andExpect(content().string(""))
          .andExpect(header().doesNotExist("Set-Cookie"));

      assertThat(fields(audit, "password-reset")).containsEntry("event.outcome", "success")
          .containsEntry("event.reason", "success").containsEntry("user.id", userId.toString());
      assertThat(audit.events()).allSatisfy(event ->
          assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain(token, NEW_PASSWORD));
    }
    mvc.perform(get("/api/hello").cookie(earlierSession)).andExpect(status().isUnauthorized());
    login(OLD_PASSWORD).andExpect(status().isUnauthorized());
    login(NEW_PASSWORD).andExpect(status().isOk());
    verify(emailService).send(EMAIL, NotificationType.PASSWORD_CHANGED, userId);
  }

  @Test
  void should_notCountAsUseOfTheAccount() throws Exception {
    confirm(emailedToken(), NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(userRepository.findById(userId).orElseThrow().getLastLoginAt()).isNull();
  }

  @Test
  void should_endALoginLockout() throws Exception {
    for (int attempt = 0; attempt < 5; attempt++) login("Wr0ng!Password").andExpect(status().isUnauthorized());
    login(OLD_PASSWORD).andExpect(status().isUnauthorized()); // Locked: even the right password is refused.

    confirm(emailedToken(), NEW_PASSWORD).andExpect(status().isNoContent());

    login(NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_rejectAUsedToken_andLeaveThePasswordAsTheFirstResetSetIt() throws Exception {
    String token = emailedToken();
    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

    try (var audit = new AuditLogCapture("audit")) {
      invalid(confirm(token, "An0ther!Password#"));
      assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "used_token");
    }
    login(NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_rejectAnExpiredToken_andKeepThePassword() throws Exception {
    String token = emailedToken();
    clocks.advance(Duration.ofMinutes(31));

    try (var audit = new AuditLogCapture("audit")) {
      invalid(confirm(token, NEW_PASSWORD));
      assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "expired_token")
          .containsEntry("user.id", userId.toString());
    }
    login(OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_rejectUnknownAndMalformedTokens_identically() throws Exception {
    emailedToken();
    for (String token : List.of("A".repeat(43), "short", "", "A".repeat(42) + "!")) {
      try (var audit = new AuditLogCapture("audit")) {
        invalid(confirm(token, NEW_PASSWORD));
        assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "invalid_token")
            .doesNotContainKey("user.id");
      }
    }
    login(OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_refuseAWeakPassword_withoutSpendingTheToken() throws Exception {
    String token = emailedToken();

    try (var audit = new AuditLogCapture("audit")) {
      confirm(token, "resetuser1Aa!x").andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("PASSWORD_RESET_REJECTED"))
          .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
          .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_CONTAINS_IDENTITY"));
      assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "weak_password");
    }
    confirm(token, "password").andExpect(jsonPath("$.errors[?(@.code == 'PASSWORD_TOO_SHORT')]").exists());

    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void should_refuseTheCurrentPassword_withoutSpendingTheToken() throws Exception {
    String token = emailedToken();

    try (var audit = new AuditLogCapture("audit")) {
      reused(confirm(token, OLD_PASSWORD));
      assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "password_reused")
          .containsEntry("user.id", userId.toString());
    }
    confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  /** History length 3: the current password and the two before it, starting from the one registration recorded. */
  @Test
  void should_refuseRecentPasswords_andAcceptOnesOlderThanTheHistory() throws Exception {
    userRepository.deleteAll();
    mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + USERNAME + "\",\"email\":\"" + EMAIL
            + "\",\"password\":\"" + OLD_PASSWORD + "\"}"))
        .andExpect(status().isCreated());
    userId = userRepository.findByUsername(USERNAME).orElseThrow().getId();
    String first = "Fir5t!Password#";
    String second = "Sec0nd!Password#";

    confirm(emailedToken(), first).andExpect(status().isNoContent());
    reused(confirm(emailedToken(), OLD_PASSWORD)); // Only registration's history entry knows it.
    confirm(emailedToken(), second).andExpect(status().isNoContent());
    reused(confirm(emailedToken(), OLD_PASSWORD)); // Current, and two before it: second, first, the original.
    confirm(emailedToken(), "Th1rd!Password#").andExpect(status().isNoContent());

    confirm(emailedToken(), OLD_PASSWORD).andExpect(status().isNoContent()); // Now older than the history.
    login(OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_refuseTheReset_when_theAccountWasDisabledAfterTheLinkWasSent() throws Exception {
    String token = emailedToken();
    var user = userRepository.findById(userId).orElseThrow();
    Accounts.disable(user, clocks.now());
    userRepository.save(user);

    try (var audit = new AuditLogCapture("audit")) {
      invalid(confirm(token, NEW_PASSWORD));
      assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "not_eligible");
    }
  }

  @Test
  void should_refuseTheReset_when_theAccountWasPromotedAfterTheLinkWasSent() throws Exception {
    String token = emailedToken();
    var user = userRepository.findById(userId).orElseThrow();
    user.setRole("ADMIN");
    userRepository.save(user);

    try (var audit = new AuditLogCapture("audit")) {
      invalid(confirm(token, NEW_PASSWORD));
      assertThat(fields(audit, "password-reset")).containsEntry("event.reason", "not_eligible");
    }
    login(OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_resetOnlyOnce_when_theSameTokenIsConfirmedConcurrently() throws Exception {
    String token = emailedToken();
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var outcomes = new ArrayList<Future<Integer>>();
      for (String password : List.of(NEW_PASSWORD, "An0ther!Password#")) {
        outcomes.add(pool.submit(() -> {
          start.await();
          return confirm(token, password).andReturn().getResponse().getStatus();
        }));
      }
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var outcome : outcomes) statuses.add(outcome.get());

      assertThat(statuses).containsExactlyInAnyOrder(204, 400);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void should_requireTheCsrfToken() throws Exception {
    String token = emailedToken();

    mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
        .andExpect(status().isForbidden());

    login(OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void should_serveTheResetPasswordPage_toAVisitor() throws Exception {
    mvc.perform(get("/reset-password").accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
  }

  /** Requests a reset and returns the token from the link the stub "emailed". */
  private String emailedToken() throws Exception {
    clearInvocations(emailService);
    mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + EMAIL + "\"}")).andExpect(status().isOk());
    var link = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetEmail(eq(EMAIL), eq(userId), link.capture());
    clearInvocations(emailService);
    return link.getValue().substring(LINK_PREFIX.length());
  }

  private ResultActions confirm(String token, String newPassword) throws Exception {
    return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
  }

  private static void invalid(ResultActions confirm) throws Exception {
    confirm.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  private static void reused(ResultActions confirm) throws Exception {
    confirm.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_REJECTED"))
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
        .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_REUSED"));
  }

  private ResultActions login(String password) throws Exception {
    return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + password + "\"}"));
  }

  private static Map<String, Object> fields(AuditLogCapture audit, String action) {
    return audit.events().stream().map(AuditLogCapture::fields)
        .filter(fields -> action.equals(fields.get("event.action")))
        .reduce((first, second) -> second).orElseThrow();
  }
}
