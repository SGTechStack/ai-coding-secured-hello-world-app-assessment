package local.builderday.account.passwordreset.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.account.passwordreset.repository.PasswordResetTokenRepository;
import local.builderday.notification.service.EmailService;
import local.builderday.support.AuditLogCapture;
import local.builderday.support.TestClocks;
import local.builderday.account.core.repository.UserRepository;
import local.builderday.account.core.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Password reset limits at their App Standard defaults: 5 requests and 10 confirms per IP, 3 emails per
 * account.
 */
@SpringBootTest(properties = {
    "app.security.password-reset.request-rate-limit.attempts=5",
    "app.security.password-reset.confirm-rate-limit.attempts=10",
    "app.security.password-reset.email-cap.attempts=3"})
@AutoConfigureMockMvc
@Import(TestClocks.class)
class PasswordResetRateLimitTest {
  private static final String EMAIL = "resetuser1@test.example.com";

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
  void should_answer429_toTheSixthRequestFromOneIp_whateverTheEmails_andQueueNothing() throws Exception {
    for (int attempt = 0; attempt < 5; attempt++) {
      request("unknown" + attempt + "@test.example.com", "198.51.100.7").andExpect(status().isOk());
    }
    UUID id = account();

    try (var audit = new AuditLogCapture("audit")) {
      request(EMAIL, "198.51.100.7").andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"))
          .andExpect(header().doesNotExist("Retry-After"));
      assertThat(fields(audit)).containsEntry("event.reason", "rate_limited").doesNotContainKey("user.id");
    }
    request("malformed", "198.51.100.7").andExpect(status().isTooManyRequests()); // Malformed ones count too.
    assertThat(passwordResetTokenRepository.findAll()).noneMatch(row -> row.getUserId().equals(id));

    request(EMAIL, "198.51.100.8").andExpect(status().isOk()); // Another source is unaffected.
    assertThat(passwordResetTokenRepository.findAll()).filteredOn(row -> row.getUserId().equals(id)).hasSize(1);
  }

  @Test
  void should_capEmailsPerAccountSilently_evenFromManySources() throws Exception {
    UUID id = account();
    String body = null;

    try (var audit = new AuditLogCapture("audit")) {
      for (int attempt = 0; attempt < 4; attempt++) {
        String answer = request(EMAIL, "198.51.100." + (20 + attempt)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        if (body != null) assertThat(answer).isEqualTo(body);
        body = answer;
      }
      assertThat(fields(audit)).containsEntry("event.reason", "capped").containsEntry("user.id", id.toString());
    }
    verify(emailService, times(3)).sendPasswordResetEmail(eq(EMAIL), eq(id), anyString());
  }

  @Test
  void should_answer429_toTheEleventhConfirmFromOneIp_withoutLookingTheTokenUp() throws Exception {
    for (int attempt = 0; attempt < 10; attempt++) {
      confirm("198.51.100.30").andExpect(status().isBadRequest());
    }

    try (var audit = new AuditLogCapture("audit")) {
      confirm("198.51.100.30").andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"));
      assertThat(fields(audit)).containsEntry("event.action", "password-reset")
          .containsEntry("event.reason", "rate_limited");
    }
    confirm("198.51.100.31").andExpect(status().isBadRequest());
  }

  private UUID account() {
    return userRepository.save(new UserEntity(UUID.randomUUID(), "resetuser1", EMAIL, "{noop}unused", "USER", true))
        .getId();
  }

  private ResultActions request(String email, String sourceIp) throws Exception {
    return mvc.perform(post("/api/auth/password-reset").with(csrf()).with(from(sourceIp))
        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
  }

  private ResultActions confirm(String sourceIp) throws Exception {
    return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).with(from(sourceIp))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + "A".repeat(43) + "\",\"newPassword\":\"x\"}"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String sourceIp) {
    return request -> { request.setRemoteAddr(sourceIp); return request; };
  }

  private static Map<String, Object> fields(AuditLogCapture audit) {
    return audit.events().stream().map(AuditLogCapture::fields).reduce((first, second) -> second).orElseThrow();
  }
}
