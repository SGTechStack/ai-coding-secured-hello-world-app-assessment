package com.example.auth.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the reset email after the token row has committed, on the async executor. Two reasons:
 * no mail I/O while a DB transaction is open, and timing parity -- a request for a registered
 * email returns as fast as one for an unknown email, so response time doesn't reveal which
 * addresses have accounts.
 */
@Component
class PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

    private final EmailService emailService;

    PasswordResetEmailSender(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener
    public void send(PasswordResetEmailRequested event) {
        try {
            emailService.sendPasswordResetEmail(event.toEmail(), event.resetLink());
        } catch (RuntimeException ex) {
            // Never the address or link: the link is a live credential.
            log.atError()
                    .setMessage("Password reset email could not be sent")
                    .addKeyValue("error_type", ex.getClass().getName())
                    .log();
        }
    }
}
