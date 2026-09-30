package local.builderday.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import local.builderday.support.AuditLogCapture;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** The link-logging deviation of ADR 0004 §5: logged in every environment, never the recipient address. */
class LoggingEmailServiceTest {
  private static final String LINK = "https://app.example/reset-password#token=abc";

  @Test
  void should_logTheLinkButNotTheRecipient_when_aResetEmailIsSent() {
    try (var log = new AuditLogCapture(LoggingEmailService.class.getName());
        var trace = MDC.putCloseable("trace.id", "test-trace")) {
      new LoggingEmailService().sendPasswordResetEmail("owner@test.example.com", UUID.randomUUID(), LINK);

      assertThat(log.events()).hasSize(2).last().satisfies(event ->
          assertThat(AuditLogCapture.fields(event)).containsEntry("url.full", LINK));
      assertThat(log.events()).allSatisfy(event ->
          assertThat(event.getFormattedMessage() + AuditLogCapture.fields(event)).doesNotContain("owner@"));
    }
  }
}
