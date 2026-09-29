package com.example.hello.reset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Assessment stub only. Replace with a mail provider before real deployment. */
@Service
public class LoggingEmailService implements EmailService {
  private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailService.class);

  @Override
  public void sendPasswordResetEmail(String email, String resetLink) {
    LOG.atInfo()
        .addKeyValue("event", "development_reset_email")
        .addKeyValue("reset_link", resetLink)
        .log("Development email stub; treat this link as a credential.");
  }
}
