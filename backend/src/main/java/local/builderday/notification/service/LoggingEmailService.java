package local.builderday.notification.service;

import java.util.Map;
import java.util.UUID;
import local.builderday.common.logging.LogFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The stub {@link EmailService}: records that an email would be sent, and sends nothing. It never logs the recipient
 * address or message content, with the one exception of the reset link (ADR 0004).
 */
@Service
public class LoggingEmailService implements EmailService {
  private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

  @Override
  public void send(String recipient, NotificationType type, UUID userId) {
    LogFields.log(log.atInfo(), Map.of("event.action", "notification_stubbed", "event.outcome", "success",
        "notification.type", type.name(), "user.id", userId.toString()), "Email notification stubbed");
  }

  @Override
  public void sendPasswordResetEmail(String recipient, UUID userId, String link) {
    send(recipient, NotificationType.PASSWORD_RESET, userId);
    // ponytail: IM8 lm-19 deviation required by the PRD, logged in every environment (ADR 0004 §5). The logged link is
    // a live credential for 30 minutes; delete this line when a real mail sender replaces the stub.
    LogFields.log(log.atInfo(), Map.of("event.action", "reset_link_stubbed", "user.id", userId.toString(),
        "url.full", link), "Password reset link: " + link);
  }
}
