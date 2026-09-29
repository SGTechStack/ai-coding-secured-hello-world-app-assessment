package com.example.auth.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub EmailService: no real SMTP. To respect Story 48 (reset tokens must never
 * appear in application logs), the token-bearing link is emitted only at DEBUG
 * (off by default). At INFO we record that an email was dispatched, without the
 * token. In real deployment this class would be replaced by an SMTP sender and
 * the token would travel only to the recipient's inbox.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String email, String resetLink) {
        log.info("event=password_reset_email_dispatched email={}", email);
        log.debug("event=password_reset_email_link email={} link={}", email, resetLink);
    }
}
