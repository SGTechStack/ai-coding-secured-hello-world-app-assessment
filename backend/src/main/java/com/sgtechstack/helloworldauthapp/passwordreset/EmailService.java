package com.sgtechstack.helloworldauthapp.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub implementation: logs the reset link instead of sending real mail.
 * Real SMTP integration is out of scope for this build (see spec).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Password reset email (stub) to={} link={}", toEmail, resetLink);
    }
}
