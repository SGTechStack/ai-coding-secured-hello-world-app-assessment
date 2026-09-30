package local.builderday.account.registration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import local.builderday.account.registration.service.RegistrationLockout;
import java.util.UUID;
import local.builderday.notification.service.EmailService;
import local.builderday.notification.service.EmailService.NotificationType;
import local.builderday.notification.service.LoggingEmailService;
import local.builderday.support.AuditLogCapture;
import local.builderday.common.ratelimit.repository.RateLimitBucketRepository;
import local.builderday.account.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RegistrationNotificationTest {
  private static final String EMAIL = "testuser123@test.example.com";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired RateLimitBucketRepository rateLimitBucketRepository;
  @MockitoSpyBean EmailService emailService;
  @MockitoSpyBean RegistrationLockout lockout;

  @BeforeEach
  void reset() {
    userRepository.deleteAll();
    rateLimitBucketRepository.deleteAllInBatch();
  }

  @Test
  void should_sendAccountCreatedNotificationAfterCommit_withoutLoggingTheAddress() throws Exception {
    try (var stubLog = new AuditLogCapture(LoggingEmailService.class.getName())) {
      register().andExpect(status().isCreated());

      UUID id = userRepository.findByUsername("testuser123").orElseThrow().getId();
      verify(emailService).send(EMAIL, NotificationType.ACCOUNT_CREATED, id);
      assertThat(stubLog.events()).singleElement().satisfies(event -> {
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.action", "notification_stubbed")
            .containsEntry("notification.type", "ACCOUNT_CREATED").containsEntry("user.id", id.toString())
            .containsKey("trace.id");
        assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain("testuser123@");
      });
    }
  }

  @Test
  void should_stillReturnCreated_andKeepTheAccount_whenTheEmailServiceFails() throws Exception {
    doThrow(new IllegalStateException("mail provider down")).when(emailService)
        .send(eq(EMAIL), eq(NotificationType.ACCOUNT_CREATED), any());

    register().andExpect(status().isCreated());

    assertThat(userRepository.findByUsername("testuser123")).isPresent();
  }

  @Test
  void should_auditSystemFailureAtError_withoutSubmittedValuesOrAUserId() throws Exception {
    doThrow(new IllegalStateException("database unavailable")).when(lockout).attempt(any(), any(), any(), any());

    try (var audit = new AuditLogCapture("audit")) {
      register().andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
          .andExpect(content().string(not(containsString("database unavailable"))));

      assertThat(audit.events()).singleElement().satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(AuditLogCapture.fields(event)).containsEntry("event.action", "user-registration")
            .containsEntry("event.outcome", "failure").containsEntry("event.reason", "system_error")
            .containsKeys("trace.id", "source.ip").doesNotContainKey("user.id");
        assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event))
            .doesNotContain("testuser123", "Str0ng", "database unavailable");
      });
    }
    assertThat(userRepository.count()).isZero();
  }

  private ResultActions register() throws Exception {
    return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"testuser123\",\"email\":\"" + EMAIL + "\",\"password\":\"Str0ng!Passw0rd\"}"));
  }
}
