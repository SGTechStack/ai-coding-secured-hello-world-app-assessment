package com.example.demo_app.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub {@link EmailService}: logs the message at {@code INFO} on its own {@code EMAIL} logger
 * instead of sending it. It exists only because real email is out of scope, and it is the
 * <strong>only</strong> place a reset token is ever logged (never on the {@code AUDIT} logger).
 * The link is in the message text, so it is readable in the plain dev console as well as in ECS
 * JSON; the e2e suite reads it from the backend's log file.
 */
@Component
class LoggingEmailService implements EmailService {

  private static final Logger LOG = LoggerFactory.getLogger("EMAIL");

  @Override
  public void sendPasswordResetEmail(String email, String link) {
    LOG.info("Password reset email to {}: {}", email, link);
  }
}
