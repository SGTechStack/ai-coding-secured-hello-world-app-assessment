package com.assessment.securedhelloworld.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub {@link EmailService}: logs the reset link instead of sending real
 * mail, per the PRD's explicit out-of-scope note on SMTP delivery. The
 * plaintext token appears here (in the link) since this is the one place
 * it is legitimately handed to the user — never elsewhere in the logs.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toAddress, String resetLink) {
        log.info("Password reset requested for {} - reset link: {}", toAddress, resetLink);
    }
}
