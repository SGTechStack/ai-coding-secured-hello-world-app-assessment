package com.eitri.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in for mail delivery (PRD: no real SMTP): logs the reset link instead of sending it. The link
 * holds a live reset token, so it is written on this class's own logger, never on {@code AUDIT}, under a
 * key the log masker leaves readable. Replace it with a real {@link EmailService} before any deployment.
 */
@Component
class LoggingEmailService implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String email, String resetLink) {
        // The recipient's address is left out: logs never carry email addresses, and the link alone is
        // enough to follow a reset locally.
        LOGGER.atInfo()
                .addKeyValue("email.reset_link", resetLink)
                .setMessage("Password reset email (stub, not sent)")
                .log();
    }
}
