package local.builderday.notification.service;

import java.util.UUID;

/**
 * Sends a security notification to an account owner. Callers depend on this contract only, so a real mail sender can
 * replace {@link LoggingEmailService} without touching Registration or Password reset. An implementation never logs the
 * recipient address or message content.
 */
public interface EmailService {

  enum NotificationType { ACCOUNT_CREATED, PASSWORD_RESET, PASSWORD_CHANGED }

  void send(String recipient, NotificationType type, UUID userId);

  /** @param link the reset link carrying the plaintext Password reset token */
  void sendPasswordResetEmail(String recipient, UUID userId, String link);
}
