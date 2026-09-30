package com.example.hello.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PRD stub: logs the reset link instead of sending mail. Replace with a real
 * {@link EmailService} implementation (and stop logging the link) before any real deployment.
 */
@Service
public class LoggingEmailService implements EmailService {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailService.class);

  @Override
  public void sendPasswordResetEmail(String toEmail, String resetLink) {
    LOG.info("[EMAIL STUB] To: {} | Password reset link: {}", toEmail, resetLink);
  }
}
